package com.longlifeio.fineprint.egress

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val TAG = "FinePrint"

/** Progress of the latest batch of tracker scans. */
data class ScanProgress(
    val done: Int = 0,
    val total: Int = 0,
    val elapsedMs: Long = 0,
    val running: Boolean = false,
)

/**
 * What FinePrint knows for the life of the process: the installed apps and their tracker-scan
 * results, held in memory only. Nothing is written to disk and nothing leaves the device.
 */
class ScanSession(
    private val context: Context,
    /** Signatures from the downloaded trackers.json, if a copy is cached; else the bundled asset is used. */
    private val cachedSignatures: () -> TrackerSignatures? = { null },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val heap = Runtime.getRuntime().maxMemory()

    /** Scans at once. Each holds one dex entry (typically 5-30 MB), so size it to the heap. */
    private val workers = (heap / (48L shl 20)).toInt().coerceIn(1, 3)
    private val maxDexBytes = minOf(DEFAULT_MAX_DEX_BYTES, heap / 3)

    /** Every scan holds a permit. The semaphore is FIFO, so a detail screen's scan goes next. */
    private val permits = Semaphore(workers)

    private val _signatures = MutableStateFlow<TrackerSignatures?>(null)
    val signatures: StateFlow<TrackerSignatures?> = _signatures.asStateFlow()

    private val matcher: SignatureMatcher by lazy {
        val loaded = cachedSignatures() ?: loadTrackerSignatures(context.assets)
        _signatures.value = loaded
        SignatureMatcher(loaded.trackers)
    }

    private val _apps = MutableStateFlow<List<InstalledApp>?>(null)
    val apps: StateFlow<List<InstalledApp>?> = _apps.asStateFlow()

    private val _results = MutableStateFlow<Map<String, TrackerScanResult>>(emptyMap())

    /** Keyed by [InstalledApp.scanKey]. */
    val results: StateFlow<Map<String, TrackerScanResult>> = _results.asStateFlow()

    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    private val _includeSystem = MutableStateFlow(false)
    val includeSystem: StateFlow<Boolean> = _includeSystem.asStateFlow()

    private val inFlight = HashMap<String, Deferred<TrackerScanResult>>() // guarded by itself
    private val batchRequests = Channel<Unit>(Channel.CONFLATED)
    private var refreshJob: Job? = null // main thread only

    init {
        scope.launch { for (request in batchRequests) runBatch() }
    }

    /** Re-reads the installed apps, then scans any that are new or updated. Cheap to call often. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch(Dispatchers.IO) {
            matcher // parse the signatures before the first scan needs them
            val appOps = context.getSystemService(AppOpsManager::class.java)
            _apps.value = scanInstalledApps(context.packageManager, appOps)
            batchRequests.trySend(Unit)
        }
    }

    fun setIncludeSystem(include: Boolean) {
        _includeSystem.value = include
        if (include) batchRequests.trySend(Unit)
    }

    /** Scans [app] ahead of the background batch (or joins its scan if one is running). */
    fun scanNow(app: InstalledApp) {
        scanAsync(app)
    }

    private suspend fun runBatch() {
        val includeSystem = _includeSystem.value
        val pending = _apps.value.orEmpty().filter {
            (includeSystem || !it.isSystem) && _results.value[it.scanKey].let { r -> r == null || r.outOfMemory }
        }
        if (pending.isEmpty()) return
        val started = SystemClock.elapsedRealtime()
        _progress.value = ScanProgress(total = pending.size, running = true)
        val queue = Channel<InstalledApp>(Channel.UNLIMITED)
        pending.forEach { queue.trySend(it) }
        queue.close()
        coroutineScope {
            repeat(workers) {
                launch {
                    for (app in queue) {
                        scanAsync(app).await()
                        _progress.update {
                            it.copy(done = it.done + 1, elapsedMs = SystemClock.elapsedRealtime() - started)
                        }
                    }
                }
            }
        }
        // Scans that ran out of memory get one more try each, alone, with nothing else in memory.
        for (app in pending.filter { _results.value[it.scanKey]?.outOfMemory == true }) {
            exclusively { publish(app.scanKey, scan(app)) } // published before queued scans get a permit
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        _progress.value = ScanProgress(done = pending.size, total = pending.size, elapsedMs = elapsed)
        val scanned = pending.mapNotNull { _results.value[it.scanKey] }
        Log.i(
            TAG,
            "Tracker scan of ${pending.size} apps took $elapsed ms with $workers workers: " +
                "${scanned.count { it.trackers.isNotEmpty() }} with trackers, " +
                "${scanned.sumOf { it.classes }} classes in ${scanned.sumOf { it.dexFiles }} dex files, " +
                "${scanned.count { it.problems.isNotEmpty() }} with read problems",
        )
    }

    private fun scanAsync(app: InstalledApp): Deferred<TrackerScanResult> {
        synchronized(inFlight) {
            // A finished scan is in results before it leaves inFlight, so one of the two has it.
            _results.value[app.scanKey]?.takeUnless { it.outOfMemory }?.let { return CompletableDeferred(it) }
            return inFlight.getOrPut(app.scanKey) {
                scope.async(Dispatchers.IO) {
                    val result = permits.withPermit {
                        // An out-of-memory retry may have finished while this waited for a permit.
                        _results.value[app.scanKey]?.takeUnless { it.outOfMemory }
                            ?: scan(app).also { publish(app.scanKey, it) }
                    }
                    synchronized(inFlight) { inFlight.remove(app.scanKey) }
                    result
                }
            }
        }
    }

    /** Records [result], unless it would replace a complete result with an out-of-memory one. */
    private fun publish(key: String, result: TrackerScanResult) {
        _results.update { m -> if (result.outOfMemory && m[key]?.outOfMemory == false) m else m + (key to result) }
    }

    /** Runs [block] holding every permit, so no other scan is in memory at the same time. */
    private suspend fun <T> exclusively(block: () -> T): T {
        repeat(workers) { permits.acquire() }
        try {
            return block()
        } finally {
            repeat(workers) { permits.release() }
        }
    }

    private fun scan(app: InstalledApp): TrackerScanResult {
        val result = try {
            scanForTrackers(app.apkPaths, matcher, maxDexBytes)
        } catch (e: OutOfMemoryError) {
            TrackerScanResult(emptyList(), 0, 0, 0, listOf("Ran out of memory reading this app's code"), outOfMemory = true)
        } catch (e: Exception) {
            TrackerScanResult(emptyList(), 0, 0, 0, listOf(e.message ?: e.javaClass.simpleName))
        }
        if (debuggable) { // package names stay out of release logs
            Log.d(
                TAG,
                "${app.packageName}: ${result.trackers.size} trackers, ${result.dexFiles} dex, " +
                    "${result.classes} classes, ${result.durationMs} ms" +
                    if (result.problems.isEmpty()) "" else ", problems: ${result.problems}",
            )
        }
        return result
    }
}

package com.longlifeio.fineprint.bundle

import android.net.TrafficStats
import android.util.Log
import com.longlifeio.fineprint.egress.TrackerSignatures
import com.longlifeio.fineprint.egress.parseTrackerSignatures
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "FinePrint"

/** Tag for every socket this app opens; StrictMode (debug) flags any untagged one. */
const val BUNDLE_SOCKET_TAG = 0xF1F1

const val BUNDLE_FILE = "bundle.json"
const val TRACKERS_FILE = "trackers.json"
private const val MAX_FILE_BYTES = 8L shl 20

/** What the app has: the cached bundle files, or nothing yet. */
data class BundleState(
    val bundle: Bundle? = null,
    val signatures: TrackerSignatures? = null,
    val error: String? = null,
    val refreshing: Boolean = false,
)

/**
 * The app's only network use: GET the two bundle files whole from [baseUrl] (never a per-app query,
 * which would reveal what is installed), keep them in [dir], and work from that copy offline.
 */
class BundleClient(private val dir: File, private val baseUrl: String, private val logRequests: Boolean) {

    /** The cached files, or an empty state when there are none (or they no longer parse). */
    fun cached(): BundleState = try {
        val bundle = File(dir, BUNDLE_FILE).takeIf { it.exists() }?.readText()?.let(::parseBundle)
        val signatures = File(dir, TRACKERS_FILE).takeIf { it.exists() }?.readText()?.let(::parseTrackerSignatures)
        BundleState(bundle, signatures)
    } catch (e: Exception) {
        BundleState(error = "cached bundle unreadable: ${e.message}")
    }

    /** Downloads both files; replaces the cache only when both arrive and parse. Call off the main thread. */
    fun refresh(): BundleState {
        return try {
            val bundleText = get(BUNDLE_FILE)
            val trackersText = get(TRACKERS_FILE)
            val bundle = parseBundle(bundleText)
            val signatures = parseTrackerSignatures(trackersText)
            dir.mkdirs()
            writeAtomically(File(dir, TRACKERS_FILE), trackersText)
            writeAtomically(File(dir, BUNDLE_FILE), bundleText)
            BundleState(bundle, signatures)
        } catch (e: Exception) {
            cached().copy(error = "couldn't update the bundle: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun get(name: String): String {
        val url = URL(baseUrl.trimEnd('/') + "/" + name)
        val started = System.nanoTime()
        TrafficStats.setThreadStatsTag(BUNDLE_SOCKET_TAG)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true // GitHub release assets redirect to their CDN
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code != 200) throw IOException("HTTP $code for $url")
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > MAX_FILE_BYTES) throw IOException("$name is larger than ${MAX_FILE_BYTES shr 20} MB")
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            if (logRequests) Log.i(TAG, "NET GET $url -> $code, ${bytes.size} bytes, ${(System.nanoTime() - started) / 1_000_000} ms")
            return String(bytes, Charsets.UTF_8)
        } catch (e: IOException) {
            if (logRequests) Log.i(TAG, "NET GET $url -> failed: ${e.message}")
            throw e
        } finally {
            connection.disconnect()
            TrafficStats.clearThreadStatsTag()
        }
    }

    private fun writeAtomically(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) throw IOException("couldn't save ${file.name}")
    }
}

package com.longlifeio.fineprint.preview

import android.content.Context
import com.longlifeio.fineprint.design.parseFixture
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.ScanProgress
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.egress.TrackerSignatures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The scan fixture in the preview's assets, copied at build time from the one the tests read (src/debug/assets). */
const val FIXTURE = "preview/scan-fixture.json"

/**
 * The preview's scanner: the scan fixture's sample apps and their scans, read with the tests' own parser and
 * handed to the screens in ScanSession's shape (the apps, and results keyed by scan key), so tiers, chips,
 * Reviewed marks and every section work as on a phone. It never reads this phone's apps: there is nothing to
 * refresh, and every app is already scanned.
 */
class FixtureScanner(context: Context, signatures: () -> TrackerSignatures?) {
    private val fixture = parseFixture(context.assets.open(FIXTURE).bufferedReader().use { it.readText() })
    private val _includeSystem = MutableStateFlow(false)

    val apps: StateFlow<List<InstalledApp>?> = MutableStateFlow(fixture.first)
    val results: StateFlow<Map<String, TrackerScanResult>> = MutableStateFlow(fixture.second)
    val progress: StateFlow<ScanProgress> = MutableStateFlow(ScanProgress(done = fixture.first.size, total = fixture.first.size))
    val signatures: StateFlow<TrackerSignatures?> = MutableStateFlow(signatures())
    val includeSystem: StateFlow<Boolean> = _includeSystem.asStateFlow()

    fun refresh() = Unit

    fun setIncludeSystem(include: Boolean) {
        _includeSystem.value = include
    }

    @Suppress("UNUSED_PARAMETER")
    fun scanNow(app: InstalledApp) = Unit
}

package com.longlifeio.fineprint.review

import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.design.parseFixture
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Each app record's Reviewed fingerprint, on the scan fixture, pinned. A migration or a rewording never moves it (schema
 * 1.6's checked_on and forum didn't); new structure does, and the commit that re-pins a value says what moved it.
 */
class MigrationTest {

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())
    private val fixture = parseFixture(File("src/debug/assets/scan-fixture.json").readText())

    @Test
    fun everyAppRecordKeepsItsFingerprint() {
        // Computed on bundle 2026.10.09 with tracker batches 1 and 2 (PRs #4 and #5). Facebook's and Maps' are as on bundle
        // 2026.10.08 at 77f9f18. Life360's moved for IAB Open Measurement's flow; TikTok's for that and Pangle's three.
        val before = mapOf(
            "com.facebook.katana" to -2137339735,
            "com.google.android.apps.maps" to 997033919,
            "com.life360.android.safetymapd" to -2068985189,
            "com.zhiliaoapp.musically" to -1966096936,
        )
        val now = fixture.first.filter { it.packageName in bundle.apps }.associate { it.packageName to fingerprint(it, fixture.second[it.scanKey], bundle).hashCode() }
        assertEquals(before, now)
    }
}

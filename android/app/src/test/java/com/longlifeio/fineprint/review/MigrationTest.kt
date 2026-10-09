package com.longlifeio.fineprint.review

import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.design.parseFixture
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Schema 1.6's migration (checked_on on the app records, forum on every alleged item) changes no Reviewed mark:
 * each app record's fingerprint, on the scan fixture, is the one it had before the migration.
 */
class MigrationTest {

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())
    private val fixture = parseFixture(File("src/debug/assets/scan-fixture.json").readText())

    @Test
    fun everyAppRecordKeepsItsFingerprint() {
        // Computed on bundle 2026.10.08 as built at 77f9f18, before any record had checked_on or forum.
        val before = mapOf(
            "com.facebook.katana" to -2137339735,
            "com.google.android.apps.maps" to 997033919,
            "com.life360.android.safetymapd" to 562818112,
            "com.zhiliaoapp.musically" to -415487696,
        )
        val now = fixture.first.filter { it.packageName in bundle.apps }.associate { it.packageName to fingerprint(it, fixture.second[it.scanKey], bundle).hashCode() }
        assertEquals(before, now)
    }
}

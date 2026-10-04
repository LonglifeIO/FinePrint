package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.egress.TrackerSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExplanationTest {

    private val bundle = parseBundle(File("src/test/resources/bundle-fixture.json").readText())

    private fun app(pkg: String, granted: List<String> = emptyList(), reach: List<String> = emptyList()) = InstalledApp(
        packageName = pkg, label = pkg, versionName = "1.0", versionCode = 1, lastUpdateTime = 0, isSystem = false,
        hasCode = true, apkPaths = emptyList(),
        permissions = granted.map { RequestedPermission(it, granted = true, dangerous = true) } +
            RequestedPermission("android.permission.READ_CONTACTS", granted = false, dangerous = true),
        deviceReach = reach,
    )

    private fun tracker(id: String, name: String, vararg categories: String) = DetectedTracker(id, name, categories.toList(), "x.Y")

    private fun scan(trackers: List<DetectedTracker>, referencedOnly: List<String> = emptyList()) =
        TrackerScanResult(trackers, dexFiles = 1, classes = 10, durationMs = 1, problems = emptyList(), referencedOnly = referencedOnly)

    @Test
    fun parsesTheBundle() {
        assertEquals("2026.10.04", bundle.version)
        val record = bundle.apps.getValue("com.example.family")
        assertEquals(3, record.exodusReport?.trackerCount)
        assertTrue(record.stale)
        assertEquals("Partners", record.dataFlows.single().recipientLabel)
        assertEquals("partners for their own use", record.dataFlows.single().sources.single().quote)
        assertEquals(listOf("Unit", "Parent Co"), bundle.trackers.getValue("fp-arity").ownerChain)
        assertEquals("Parent Co", bundle.companies.getValue("co-parent").name)
        // An undated page has no as_of, only the day it was read.
        val undated = record.consequences.single().sources.single()
        assertNull(undated.asOf)
        assertEquals("2026-10-02", undated.accessed)
        val note = bundle.trackers.getValue("fp-arity").dataFlows.single().proceduralNote
        assertEquals("Part dismissed; on appeal.", note?.text)
        assertEquals("https://example.org/appeal", note?.sources?.single()?.url)
    }

    @Test
    fun regulatoryActionAgainstSomeoneElseCarriesItsQualifier() {
        assertEquals(
            listOf("driving data", "regulatory action against Parent Co concerning this app's data"),
            riskTagLabels(bundle.apps.getValue("com.example.family")),
        )
    }

    @Test
    fun curatedAppJoinsItsOwnFlowsAndItsTrackersRecords() {
        val e = explain(
            app("com.example.family", granted = listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.CAMERA"), reach = listOf("autostart", "vpn_service")),
            scan(listOf(tracker("exodus-12", "AppsFlyer", "Analytics"), tracker("fp-arity", "Arity", "Location")), referencedOnly = listOf("exodus-65", "exodus-72")),
            bundle,
            emptyMap(),
        )
        assertEquals("curated", e.coverage)
        val elsewhere = e.flows.getValue("goes_elsewhere")
        assertEquals("Partners", elsewhere[0].recipient)
        val arity = elsewhere.single { it.data == "movement_and_driving" }
        assertEquals("Unit → Parent Co", arity.recipient) // owner chain from the tracker record
        assertEquals("alleged", arity.status)
        // Every source is shown, primary first, and the procedural note travels with the line.
        assertEquals(listOf("https://example.org/opinion", "https://example.org/petition"), arity.sources.map { it.url })
        assertEquals("Part dismissed; on appeal.", arity.proceduralNote?.text)
        assertEquals(2, e.tags.size)
        // AppsFlyer has no tracker record: derived from its Exodus category, marked auto (no status).
        assertNull(e.flows.getValue("stays_here").single { it.recipient == "AppsFlyer" }.status)
        // Only granted permissions that feed a shown data kind; CAMERA feeds nothing shown here.
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION"), e.applies.map { it.permission })
        assertEquals(listOf("autostart"), e.reach.map { it.id }) // vpn_service has no boilerplate
        assertTrue(e.stale)
        assertEquals("Exodus lists 3 trackers; 2 are adapter references with no code in this app.", e.exodusNote)
    }

    @Test
    fun appWithoutARecordRendersTheAutoView() {
        val signatures = mapOf("exodus-312" to TrackerSignature("exodus-312", "Google AdMob", "x", listOf("Advertisement")))
        val e = explain(app("com.example.other"), scan(listOf(tracker("exodus-312", "Google AdMob", "Advertisement"))), bundle, signatures)
        assertEquals("auto", e.coverage)
        assertTrue(e.tags.isEmpty())
        assertTrue(e.summary.contains("Google AdMob"))
        assertEquals(setOf("device_identifiers", "app_activity"), e.flows.getValue("goes_elsewhere").map { it.data }.toSet())
        assertNull(e.lastReviewed)
        assertNull(e.exodusNote)
    }

    @Test
    fun noBundleAtAllStillExplainsFromTheScan() {
        val e = explain(app("com.example.other"), scan(listOf(tracker("exodus-27", "Crashlytics", "Crash reporting"))), null, emptyMap())
        assertEquals("crash_diagnostics", e.flows.getValue("stays_here").single().data)
        assertTrue(e.applies.isEmpty())
    }

    @Test
    fun firstPartyAdvertisingIsUsedForMoreNotElsewhere() {
        val flows = deriveFlows("Meta", listOf("Advertisement"), "first_party")
        assertTrue(flows.all { it.first == "used_for_more" })
    }

    @Test
    fun exodusNoteOnlyWhenExodusCountsMore() {
        val s = scan(listOf(tracker("exodus-12", "A")), referencedOnly = listOf("exodus-65"))
        assertNull(exodusNote(1, listOf("exodus-12"), s))
        assertEquals("Exodus lists 2 trackers; 1 is an adapter reference with no code in this app.", exodusNote(2, listOf("exodus-12", "exodus-65"), s))
        assertEquals("Exodus may list up to 2; 1 is an adapter reference with no code in this app.", exodusNote(null, emptyList(), s))
    }
}

package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Source
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** One test per rule of the published formula (docs/METHOD.md, "Tiers"), plus its two limits. */
class TierTest {

    private fun src(status: String = "reported", id: String? = null, derivesFrom: String? = null, single: Boolean = false) =
        Source("https://example.org/$id", "t", "journalism", status, "2026-01-01", null, "q", id, derivesFrom, single)

    private fun flow(data: String, bucket: String, status: String?, sources: List<Source> = listOf(src()), via: String? = null) =
        FlowLine(data, bucket, "Recipient", "p", status, null, false, sources, null, via)

    private fun event(status: String, kind: String? = null, concerns: Boolean = true) = TierEvent(status, kind, concerns, listOf(src(status)))

    private fun rate(curated: Boolean = true, flows: List<FlowLine> = emptyList(), events: List<TierEvent> = emptyList(), reach: List<String> = emptyList()) =
        tier(curated, "App", flows, events, reach)

    private val twoReports = listOf(src(id = "a"), src(id = "b"))

    @Test
    fun f1SensitiveDataGoesElsewhereByTheAppsOwnAccountOrARuling() {
        assertEquals(
            TierResult(Tier.FLAGGED, "Location data goes elsewhere — App's own policy", "F1"),
            rate(flows = listOf(flow("precise_location", GOES_ELSEWHERE, "self_disclosed"))),
        )
        assertEquals(
            TierResult(Tier.FLAGGED, "Your contact list goes elsewhere — a court or regulator's decision", "F1"),
            rate(flows = listOf(flow("contacts", GOES_ELSEWHERE, "adjudicated"))),
        )
        // A tracker's own disclosure names the tracker, not the app.
        assertEquals(
            "Driving data goes elsewhere — Arity's own disclosure",
            rate(flows = listOf(flow("movement_and_driving", GOES_ELSEWHERE, "self_disclosed", via = "Arity"))).reason,
        )
    }

    @Test
    fun f2ARulingConcerningThisAppsData() {
        assertEquals(TierResult(Tier.FLAGGED, "A court or regulator has ruled on this app's data", "F2"), rate(events = listOf(event("adjudicated", "settlement"))))
        assertEquals(Tier.EXPECTED, rate(events = listOf(event("adjudicated", "ruling", concerns = false))).tier)
    }

    @Test
    fun aRulingThatSetsTheTierIsNamedNewestFirst() {
        val older = TierEvent("adjudicated", "consent_order", true, listOf(src("adjudicated")), "FTC privacy order", "2012-08-10")
        val newer = TierEvent("adjudicated", "ruling", true, listOf(src("adjudicated")), "Irish DPC fine", "2024-12-17")
        assertEquals(TierResult(Tier.FLAGGED, "A 2024 ruling on this app's data: Irish DPC fine", "F2"), rate(events = listOf(older, newer)))
        val suit = TierEvent("alleged", "filed", true, listOf(src("alleged")), "Texas v. Example", "2025-01-13")
        assertEquals("A lawsuit over this app's data has been filed: Texas v. Example (not proven in court)", rate(events = listOf(suit)).reason)
    }

    @Test
    fun f3ALawsuitThatSurvivedAMotionToDismiss() {
        assertEquals("F3", rate(events = listOf(event("alleged", "survived_motion_to_dismiss"))).rule)
        assertEquals(Tier.EXPECTED, rate(events = listOf(event("alleged", "survived_motion_to_dismiss", concerns = false))).tier)
    }

    @Test
    fun c1UsedForMoreByOwnAccountTwoReportsOrARuling() {
        assertEquals(
            TierResult(Tier.CAUTION, "Your advertising ID is used for more — App's own policy", "C1"),
            rate(flows = listOf(flow("device_identifiers", USED_FOR_MORE, "self_disclosed"))),
        )
        assertEquals("C1", rate(flows = listOf(flow("app_activity", USED_FOR_MORE, "reported", twoReports))).rule)
        assertEquals("C1", rate(flows = listOf(flow("app_activity", USED_FOR_MORE, "adjudicated"))).rule)
    }

    @Test
    fun c2EverythingElseThatGoesElsewhere() {
        assertEquals("C2", rate(flows = listOf(flow("device_identifiers", GOES_ELSEWHERE, "self_disclosed"))).rule)
        // Sensitive data with weaker evidence than F1 needs is Caution, not Flagged.
        assertEquals(
            TierResult(Tier.CAUTION, "Location data goes elsewhere — reported by two or more sources", "C2"),
            rate(flows = listOf(flow("precise_location", GOES_ELSEWHERE, "reported", twoReports))),
        )
        assertEquals("C2", rate(flows = listOf(flow("movement_and_driving", GOES_ELSEWHERE, "alleged"))).rule)
        // Lines inferred from tracker code count too, and name the tracker.
        assertEquals(
            TierResult(Tier.CAUTION, "Your advertising ID goes elsewhere — Google AdMob code in this app", "C2"),
            rate(curated = false, flows = listOf(flow("device_identifiers", GOES_ELSEWHERE, null, emptyList(), via = "Google AdMob"))),
        )
    }

    @Test
    fun c3AFiledLawsuit() {
        assertEquals(TierResult(Tier.CAUTION, "A lawsuit over this app's data has been filed (not proven in court)", "C3"), rate(events = listOf(event("alleged", "filed"))))
        assertEquals(Tier.EXPECTED, rate(events = listOf(event("alleged", "dismissed"))).tier)
    }

    @Test
    fun c4DeepDeviceAccess() {
        assertEquals(TierResult(Tier.CAUTION, "Can read your notifications", "C4"), rate(reach = listOf("autostart", "notification_listener")))
        assertEquals(Tier.EXPECTED, rate(reach = listOf("autostart", "overlay", "background_ble_scan")).tier)
    }

    @Test
    fun expectedOtherwiseButOnlyWithAReviewedRecord() {
        val staysHere = listOf(flow("crash_diagnostics", STAYS_HERE, "self_disclosed"))
        assertEquals(TierResult(Tier.EXPECTED, "Nothing found beyond running the app", "E"), rate(flows = staysHere))
        // Without a record the same findings are "No record yet", with what the scan found as the line.
        val facts = "No third-party trackers found · 12 permissions"
        assertEquals(TierResult(null, facts, "N"), tier(false, "App", staysHere, emptyList(), emptyList(), scanFacts = facts))
        assertEquals(TierResult(null, facts, "N"), tier(false, "App", emptyList(), emptyList(), listOf("autostart"), scanFacts = facts))
    }

    @Test
    fun aSingleIndependentReportNeverRaisesATier() {
        assertEquals(Tier.EXPECTED, rate(flows = listOf(flow("precise_location", GOES_ELSEWHERE, "reported"))).tier)
        assertEquals(Tier.EXPECTED, rate(flows = listOf(flow("app_activity", USED_FOR_MORE, "reported"))).tier)
        // A re-report doesn't count as a second source, and neither does a second story from one investigation.
        val copied = listOf(src(id = "a"), src(id = "b", derivesFrom = "a"))
        assertEquals(Tier.EXPECTED, rate(flows = listOf(flow("precise_location", GOES_ELSEWHERE, "reported", copied))).tier)
        val oneInvestigation = listOf(src(id = "a", single = true), src(id = "b"))
        assertEquals(Tier.EXPECTED, rate(flows = listOf(flow("precise_location", GOES_ELSEWHERE, "reported", oneInvestigation))).tier)
    }

    @Test
    fun withoutAReviewedRecordAnAppCanNeverBeFlagged() {
        val sensitive = listOf(flow("precise_location", GOES_ELSEWHERE, "self_disclosed", via = "Some SDK"))
        assertEquals(
            TierResult(Tier.CAUTION, "Location data goes elsewhere — Some SDK's own disclosure", "F1", capped = true),
            rate(curated = false, flows = sensitive),
        )
        assertEquals(TierResult(Tier.CAUTION, "A court or regulator has ruled on this app's data", "F2", capped = true), rate(curated = false, events = listOf(event("adjudicated", "ruling"))))
    }

    @Test
    fun theReasonNamesTheStrongestFinding() {
        val both = rate(
            flows = listOf(flow("device_identifiers", GOES_ELSEWHERE, "self_disclosed"), flow("precise_location", GOES_ELSEWHERE, "self_disclosed")),
            events = listOf(event("alleged", "survived_motion_to_dismiss")),
        )
        assertEquals(TierResult(Tier.FLAGGED, "Location data goes elsewhere — App's own policy", "F1"), both)
        assertEquals("C1", rate(flows = listOf(flow("device_identifiers", GOES_ELSEWHERE, "self_disclosed"), flow("app_activity", USED_FOR_MORE, "self_disclosed"))).rule)
    }

    /** The reviewed Life360 record in bundle/bundle.json, as the owner's read test sees it. */
    @Test
    fun life360IsFlaggedByItsOwnPolicyAndShowsTheActionAgainstAllstate() {
        val bundle = parseBundle(File("../../bundle/bundle.json").readText())
        val life360 = InstalledApp(
            packageName = "com.life360.android.safetymapd", label = "Life360", versionName = "26.37.0", versionCode = 2924500,
            lastUpdateTime = 0, isSystem = false, hasCode = true, apkPaths = listOf("base.apk"),
            permissions = listOf(RequestedPermission("android.permission.ACCESS_FINE_LOCATION", granted = true, dangerous = true)),
        )
        val scan = TrackerScanResult(listOf(DetectedTracker("fp-arity", "Arity", listOf("Location"), "com.arity.coreengine.x")), 9, 1, 1, emptyList())
        val e = explain(life360, scan, bundle, emptyMap())
        assertEquals(TierResult(Tier.FLAGGED, "Location data goes elsewhere — Life360's own policy", "F1"), e.tier)
        // The federal class action and the Texas case, newest first, each joined by the record's own words.
        val actions = e.onTheRecord.actions
        assertEquals(listOf("2025-04-10", "2025-01-13"), actions.map { it.date })
        assertTrue(actions.all { it.subject == "Action against Allstate/Arity concerning this app's data" && it.namesThisApp })
        assertTrue(actions.all { a -> a.details.any { "Life360 is not a defendant" in it } && a.details.any { "not proven in court" in it } })
        val breach = e.onTheRecord.alsoReported.single()
        assertEquals("2024-03 · Have I Been Pwned: Life360", breach.line)
        assertEquals("Names, phone numbers and emails of 442,519 users were scraped through a flaw in Life360's login API in 2024.", breach.details.first())
        assertTrue("Precise location" in e.collects)
    }
}

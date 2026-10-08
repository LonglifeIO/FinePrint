package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.review.fingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The order FinePrint's lines are read in (docs/METHOD.md, Where data goes), on a made-up app with one line of
 * each kind: analytics and crash reports, ads, the app's own ads, a sale to data brokers and a government purchase.
 */
class LineOrderTest {

    private val bundle = parseBundle(File("src/test/resources/line-order-fixture.json").readText(), File("src/test/resources/jurisdictions-fixture.json").readText())
    private val today = LocalDate.of(2026, 10, 8)

    private fun app(pkg: String) = InstalledApp(pkg, pkg, "1.0", 1, 0, false, true, emptyList(),
        listOf(RequestedPermission("android.permission.ACCESS_FINE_LOCATION", granted = true, dangerous = true)))

    private fun scan(vararg trackers: DetectedTracker) = TrackerScanResult(trackers.toList(), 1, 10, 1, emptyList())

    private val lines = app("com.example.lines")
    private val e = explain(lines, scan(), bundle, emptyMap(), today)

    @Test
    fun theFinePrintReadsOtherCompaniesThenOwnUsesThenOneLineForRunningTheApp() {
        assertEquals(
            listOf(
                "Precise location → Data brokers: Sold to data brokers",                    // other companies, sensitive, the app's own account
                "Precise location → US government agencies: Bought from data brokers",     // other companies, sensitive, reported
                "Device and advertising IDs → Advertising partners: Target ads to you in other apps",
                "What you do in the app → Example Lines Inc.: The company's own ads, across its apps",
            ),
            finePrint(e).map { it.text },
        )
        val folded = alsoCollected(e)!!
        assertEquals("Also collected to run the app: usage and crash data", folded.text)
        assertEquals(
            listOf("What you do in the app → Analytics provider: Usage statistics for the developer", "Crash and performance data → Crash reporting provider: Crash reports for the developer"),
            folded.lines.map { it.text },
        )
        assertTrue(folded.lines.all { it.status == "self_disclosed" && it.sources.isNotEmpty() }) // each opens with its badge and source
    }

    @Test
    fun theOrderChangesNoTierAndNoReviewedMark() {
        // Computed by the code before this change, on this same fixture and date.
        assertEquals(Tier.FLAGGED, e.tier.tier)
        assertEquals("F1", e.tier.rule)
        assertEquals("Location data goes elsewhere — Example Lines' own policy", e.tier.reason)
        assertEquals(1220419973, fingerprint(lines, scan(), bundle).hashCode())
    }

    @Test
    fun eachPlaceListsItsLinesInTheSameOrderAndKeepsItsPlaceInTheList() {
        assertEquals(BUCKETS, e.flows.keys.toList())
        assertEquals(listOf("Data brokers", "Advertising partners"), e.flows.getValue(GOES_ELSEWHERE).map { it.recipient }) // sensitive first
        assertEquals(listOf("Analytics provider", "Crash reporting provider"), e.flows.getValue(STAYS_HERE).map { it.recipient })
        assertTrue(e.governmentFlows.single().recipient == "US government agencies") // under Jurisdictions, not a place
    }

    private val mystery = DetectedTracker("exodus-0", "Mystery SDK", emptyList(), "x.Y")
    private val analytics = DetectedTracker("exodus-1", "Some Analytics", listOf("Analytics"), "x.Y")
    private val ads = DetectedTracker("exodus-2", "Some Ads", listOf("Advertisement"), "x.Y")

    @Test
    fun aTrackerWithNoPurposeSignalIsInTheFirstGroupWithTheFallbackWording() {
        val unknown = explain(app("org.example.mystery"), scan(analytics, mystery), bundle, emptyMap(), today)
        val line = unknown.unrecorded.single()
        assertEquals("Data from this app → Mystery SDK: What it's used for isn't recorded", line.claim())
        assertEquals(LineGroup.OTHER_COMPANIES, line.group())
        assertEquals(line, readingOrder(unknown).first())
        // Outside the three places: no place, chip or tier reads it.
        assertEquals(listOf(STAYS_HERE), unknown.flows.keys.toList())
        val without = explain(app("org.example.mystery"), scan(analytics), bundle, emptyMap(), today)
        assertEquals(without.tier.tier, unknown.tier.tier)
        assertEquals(without.tier.rule, unknown.tier.rule)
        assertEquals("Some of it can go to other companies", whereHeadline(unknown))
    }

    @Test
    fun theHomeHeadlineCountsTheFirstGroupAndTheChipsCountEachPlaceAsBefore() {
        val apps = listOf(lines, app("org.example.mystery"), app("org.example.analytics"))
        val explanations = mapOf(
            lines.packageName to e,
            "org.example.mystery" to explain(apps[1], scan(analytics, mystery), bundle, emptyMap(), today),
            "org.example.analytics" to explain(apps[2], scan(analytics), bundle, emptyMap(), today),
        )
        val g = glance(apps, explanations, emptyMap())
        assertEquals(mapOf(STAYS_HERE to 3, USED_FOR_MORE to 1, GOES_ELSEWHERE to 1), g.perBucket)
        assertEquals(2, g.othersForMore)
        assertEquals("For 2 of your 3 apps, FinePrint lists data that can go to other companies for more than running the app.", glanceHeadline(g))
    }

    @Test
    fun anAppWithoutARecordNamesItsTrackersInTheSameOrder() {
        val auto = explain(app("org.example.auto"), scan(analytics, ads, mystery), bundle, emptyMap(), today)
        assertTrue(auto.summary, auto.summary.contains("in this app: Some Ads, Mystery SDK, Some Analytics."))
    }
}

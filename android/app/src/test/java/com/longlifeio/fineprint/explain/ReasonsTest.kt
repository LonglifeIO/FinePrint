package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Why an app has its tier (docs/METHOD.md, An app's page), on the reasons fixture: a ruling, a case a court let go
 * ahead, a complaint merely filed, a tier only alleged lines reach, and an app two of its own lines set. A reason is
 * always a fact; what a claimant alleges never leads.
 */
class ReasonsTest {

    private val bundle = parseBundle(File("src/test/resources/reasons-fixture.json").readText(), File("src/test/resources/jurisdictions-fixture.json").readText())
    private val today = LocalDate.of(2026, 10, 8)
    private fun app(pkg: String) = InstalledApp(pkg, pkg, "1.0", 1, 0, false, true, emptyList(), emptyList())
    private fun e(pkg: String, vararg found: DetectedTracker) = explain(app(pkg), TrackerScanResult(found.toList(), 1, 10, 1, emptyList()), bundle, emptyMap(), today)

    @Test
    fun aRulingSetsTheTierAndItsItemOnTheRecordIsMarked() {
        val e = e("com.example.ruled")
        assertEquals("F2", e.tier.rule)
        assertEquals("Why: A court or regulator has ruled on this app's data", whyLine(e))
        assertEquals(listOf(e.onTheRecord.actions.single()), e.tier.events.map { it.line })
        assertTrue(readingOrder(e).none { it.setsTier })
    }

    @Test
    fun aCaseACourtLetGoAheadIsTheCourtsStepNeverTheClaim() {
        val e = e("com.example.proceeding")
        assertEquals("F3", e.tier.rule)
        assertEquals("Why: A court has let a case about this app's data go ahead (not proven in court)", whyLine(e))
        // The claim leads nothing: the line it alleges comes after every line that isn't alleged, and isn't marked.
        assertEquals(listOf("self_disclosed", "alleged"), finePrint(e).map { it.status })
        assertTrue(finePrint(e).none { it.setsTier })
    }

    @Test
    fun aComplaintMerelyFiledSetsNoTierAndLeadsNothing() {
        val e = e("com.example.complaint")
        assertEquals(Tier.EXPECTED, e.tier.tier)
        assertEquals("Why: Nothing found beyond running the app", whyLine(e))
        assertTrue(e.tier.events.isEmpty())
        assertEquals("filed", e.onTheRecord.actions.single().statusKind)
    }

    @Test
    fun whenOnlyAllegedLinesCountTheReasonIsTheCourtsStepAndNothingIsMarked() {
        val e = e("com.example.alleged")
        assertEquals(TierResult(Tier.CAUTION, "A court has let a case go ahead over where this app's data goes (not proven in court)", "C2"), e.tier)
        assertTrue(e.tier.reasons.isEmpty())
        assertTrue(readingOrder(e).none { it.setsTier })
    }

    @Test
    fun theLinesThatSetTheTierAreMarkedAndReadFirst() {
        val e = e("com.example.flows")
        assertEquals("F1", e.tier.rule)
        assertEquals("Why: Location data goes elsewhere — Example Flows' own policy, and 1 more marked below", whyLine(e))
        // Location and contacts set it (Flagged's sensitive kinds); advertising IDs don't, and come after them.
        assertEquals(
            listOf(
                "Precise location → Data brokers: Sold to data brokers", "Contacts → Marketing partners: Marketing",
                "Device and advertising IDs → Advertising partners: Ads in other apps", "What you do in the app → Example Inc.: The company's own ads",
            ),
            finePrint(e).map { it.text },
        )
        assertEquals(listOf(true, true, false, false), finePrint(e).map { it.setsTier })
        assertEquals(listOf(true, true, false), e.flows.getValue(GOES_ELSEWHERE).map { it.setsTier })
    }

    @Test
    fun theSummaryNamesTheTrackerThatSetTheTierFirst() {
        // Own Kit's own disclosure sets Caution (C1); Ad Kit's inferred lines would otherwise be named first, as other companies' uses.
        val e = e("org.example.kits", DetectedTracker("exodus-6", "Ad Kit", listOf("Advertisement"), "x.Ads"), DetectedTracker("exodus-5", "Own Kit", listOf("Analytics"), "y.Own"))
        assertEquals("C1", e.tier.rule)
        assertTrue(e.summary, e.summary.contains("in this app: Own Kit, Ad Kit."))
    }

    @Test
    fun eachPlaceHasAPlainGloss() {
        assertEquals(
            listOf("Stays here · to run the app", "Used for more · beyond running the app", "Goes elsewhere · to other companies"),
            BUCKETS.map { "${BUCKET_TEXT.getValue(it).title} · ${BUCKET_GLOSS.getValue(it)}" },
        )
        assertEquals(listOf("Flagged for this", "Caution for this"), listOf(Tier.FLAGGED, Tier.CAUTION).map(::reasonMarker))
    }
}

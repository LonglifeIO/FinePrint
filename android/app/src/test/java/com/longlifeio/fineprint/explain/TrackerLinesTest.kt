package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.design.parseFixture
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * A tracker's legal and reported lines on every app carrying it (docs/METHOD.md, Tiers): shown about the tracker's
 * company, and scored only when the case names the app; a past practice reported, under Past. On the tracker-lines
 * fixture, then on the real records of InMobi, Mintegral and Arity.
 */
class TrackerLinesTest {

    private val today = LocalDate.of(2026, 10, 9)
    private fun app(pkg: String) = InstalledApp(pkg, pkg, "1.0", 1, 0, false, true, emptyList(), emptyList())
    private fun scan(vararg found: DetectedTracker) = TrackerScanResult(found.toList(), 1, 10, 1, emptyList())

    private val fixture = parseBundle(File("src/test/resources/tracker-lines-fixture.json").readText(), File("src/test/resources/jurisdictions-fixture.json").readText())
    private val kit = DetectedTracker("exodus-7", "Kit", listOf("Analytics"), "com.kit.Kit")

    @Test
    fun aTrackersCourtStepNeverRatesAnAppItDoesntName() {
        // An order still in force and a case a court let go ahead, neither naming the app: shown, about Kit Co, unscored.
        val quiet = explain(app("com.example.quiet"), scan(kit), fixture, emptyMap(), today)
        assertEquals(TierResult(Tier.EXPECTED, "Nothing found beyond running the app", "E"), quiet.tier)
        assertTrue(quiet.tier.events.isEmpty())
        val about = quiet.onTheRecord.actions.filter { it.line.endsWith("about Kit Co") }
        assertEquals("the order, the case, and the fine naming another app", 3, about.size)
        assertTrue(about.none { it.namesThisApp })
        // An app without a record: the same lines, the same way; it isn't rated on them.
        val unrecorded = explain(app("org.example.unrecorded"), scan(kit), fixture, emptyMap(), today)
        assertEquals(null, unrecorded.tier.tier)
        assertEquals(3, unrecorded.onTheRecord.actions.count { it.line.endsWith("about Kit Co") && !it.namesThisApp })
    }

    @Test
    fun aTrackersRulingThatNamesTheAppRatesIt() {
        val named = explain(app("com.example.named"), scan(kit), fixture, emptyMap(), today)
        assertEquals(Tier.FLAGGED, named.tier.tier)
        assertEquals("F2", named.tier.rule)
        val fine = named.onTheRecord.actions.single { it.namesThisApp }
        assertFalse(fine.line.contains("about"))
        assertEquals(listOf(fine), named.tier.events.map { it.line })
    }

    @Test
    fun aPastPracticeReportedIsListedUnderPast() {
        val quiet = explain(app("com.example.quiet"), scan(kit), fixture, emptyMap(), today)
        val report = quiet.onTheRecord.past.single { it.historical }
        assertEquals("reported", report.status)
        assertTrue(report.line, report.line.startsWith("2020-08-24 · The report") && report.line.endsWith("about Kit Co"))
        assertTrue(quiet.onTheRecord.alsoReported.none { it.historical })
        assertEquals("three actions and the report", 4, quiet.onTheRecord.count)
    }

    @Test
    fun aTrackersLineTheAppsOwnRecordAlreadyCitesIsShownOnce() {
        // Facebook's own record and Meta's tracker record both cite Consumer Reports' study.
        val (apps, scans) = parseFixture(File("src/debug/assets/scan-fixture.json").readText())
        val facebook = apps.single { it.packageName == "com.facebook.katana" }
        val e = explain(facebook, scans[facebook.scanKey], bundle, emptyMap(), today)
        assertEquals(1, e.onTheRecord.alsoReported.count { it.line.contains("Who Shares Your Information With Facebook?") })
    }

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())
    private val inMobi = DetectedTracker("exodus-106", "Inmobi", listOf("Advertisement"), "com.inmobi.ads.InMobiBanner")
    private val mintegral = DetectedTracker("exodus-200", "Mintegral", listOf("Advertisement"), "com.mbridge.msdk.MBridgeSDK")
    private val arity = DetectedTracker("fp-arity", "Arity", listOf("Location"), "com.arity.coreengine.CoreEngineManager")

    @Test
    fun inMobiMintegralAndArityOnLife360AndOnAnAppWithoutARecord() {
        val life360 = "com.life360.android.safetymapd"
        for (pkg in listOf(life360, "org.example.unrecorded")) {
            val e = explain(app(pkg), scan(arity, inMobi, mintegral), bundle, emptyMap(), today)
            // InMobi's FTC consent order (in force) and Caldwell v. InMobi (let go ahead): shown on every app, never scored.
            val inMobiLines = e.onTheRecord.actions.filter { it.line.endsWith("about InMobi") }
            assertEquals(pkg, 2, inMobiLines.size)
            assertTrue(pkg, inMobiLines.none { it.namesThisApp } && e.tier.events.none { it.line in inMobiLines })
            // Mintegral's Snyk report, a past practice: under Past, about Mintegral's owner.
            val snyk = e.onTheRecord.past.single { it.historical && it.title.startsWith("In 2020 the security firm Snyk reported") }
            assertTrue(pkg, snyk.line.endsWith("about Mobvista/Mintegral"))
            // Arity's Texas line names Life360 alone. On Life360, its own record's lines say it and name the app, and the
            // tracker's copy, citing the same court papers, is left out; elsewhere the tracker's line is about Allstate.
            fun saying(start: String) = e.onTheRecord.actions.filter { line -> line.details.any { it.startsWith(start) } }
            val own = saying("The Texas AG and a federal class action allege that Allstate's Arity paid apps such as Life360")
            val tracker = saying("The Texas AG and a federal class action allege that Allstate's Arity paid app makers")
            if (pkg == life360) {
                assertTrue(own.isNotEmpty() && own.all { it.namesThisApp } && tracker.isEmpty())
            } else {
                assertTrue(own.isEmpty() && tracker.single().line.endsWith("about Allstate") && !tracker.single().namesThisApp)
            }
        }
    }
}

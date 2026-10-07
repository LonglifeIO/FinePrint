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

/** The fine print, the page's footnote numbers and its card headlines, on the real records. */
class FinePrintTest {

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())

    private fun app(pkg: String) = InstalledApp(
        packageName = pkg, label = pkg, versionName = "1.0", versionCode = 1, lastUpdateTime = 0, isSystem = false,
        hasCode = true, apkPaths = emptyList(),
        permissions = listOf(RequestedPermission("android.permission.ACCESS_FINE_LOCATION", granted = true, dangerous = true)),
    )

    /** Trackers with their εxodus categories, as the scanner reports them. */
    private fun scan(vararg trackers: Triple<String, String, String>) =
        TrackerScanResult(trackers.map { DetectedTracker(it.first, it.second, listOf(it.third), "x.Y") }, 1, 10, 1, emptyList())

    private fun explanation(pkg: String, s: TrackerScanResult) = explain(app(pkg), s, bundle, emptyMap())

    private val life360 = explanation("com.life360.android.safetymapd", scan(Triple("fp-arity", "Arity", "Location"), Triple("exodus-312", "Google AdMob", "Advertisement")))
    private val facebook = explanation("com.facebook.katana", scan(Triple("exodus-65", "Facebook Ads", "Advertisement")))
    private val unknown = explanation("org.example.ads", scan(Triple("exodus-312", "Google AdMob", "Advertisement")))

    @Test
    fun theLineThatSetTheTierComesFirstThenOneLinePerBucket() {
        for (e in listOf(life360, facebook, unknown)) {
            val lines = finePrint(e)
            assertTrue(lines.size in 1..FINE_PRINT_MAX)
            assertEquals(lines.size, lines.distinct().size) // nothing repeated
        }
        // Life360: Flagged by a flow it discloses itself (F1); that flow is the first line.
        assertEquals("F1", life360.tier.rule)
        assertEquals(life360.tier.flow!!.claim(), finePrint(life360).first().text)
        assertEquals("self_disclosed", finePrint(life360).first().status)
        // Facebook: Flagged by a ruling (F2); the ruling's On the record line comes first.
        assertEquals("F2", facebook.tier.rule)
        val ruling = facebook.tier.event!!.line!!
        assertEquals(FinePrintLine(ruling.line, "adjudicated", ruling.sources), finePrint(facebook).first())
        assertTrue(ruling.line.startsWith(ruling.date))
    }

    @Test
    fun anAppWithoutARecordShowsTheInferredLineItsTierNames() {
        assertEquals("C2", unknown.tier.rule)
        val first = finePrint(unknown).first()
        assertEquals(null, first.status) // Auto
        assertTrue(first.text, first.text.startsWith("Device and advertising IDs → "))
    }

    @Test
    fun eachBucketAddsTheFirstLineTheTierRulesWouldNameThatIsntShownYet() {
        val first = finePrint(life360).first().text
        val expected = BUCKETS.mapNotNull { b -> life360.flows[b].orEmpty().sortedWith(NAMED_FIRST).firstOrNull { it.claim() != first } }.map { it.claim() }
        assertEquals(expected.take(FINE_PRINT_MAX - 1), finePrint(life360).drop(1).map { it.text })
        // Goes elsewhere's first line set the tier, so that bucket adds its next one.
        val elsewhere = life360.flows.getValue(GOES_ELSEWHERE).map { it.claim() }
        assertEquals(2, finePrint(life360).count { it.text in elsewhere })
    }

    private fun source(url: String, title: String) = Source(url, title, "privacy_policy", "self_disclosed", "2026-01-01", null, "q")

    @Test
    fun footnotesNumberEachDocumentAndSectionOnceInPageOrder() {
        val a = source("https://a.example", "Policy, §1")
        val b = source("https://a.example", "Policy, §2")
        val notes = Footnotes(listOf(a, b, a.copy(quote = "another quote from §1")))
        assertEquals(listOf(a, b), notes.ordered)
        assertEquals("¹", notes.marks(listOf(a)))
        assertEquals("¹,²", notes.marks(listOf(b, a, a)))
        assertEquals("", Footnotes.NONE.marks(listOf(a)))
        assertEquals("¹²", superscript(12))
    }

    @Test
    fun aPagesFootnotesCoverEveryClaimOnIt() {
        val notes = footnotes(life360, whatYouCanDo(app("com.life360.android.safetymapd"), life360, bundle.apps["com.life360.android.safetymapd"], emptyMap(), emptySet()))
        val claimed = BUCKETS.flatMap { life360.flows[it].orEmpty().flatMap { l -> l.sources } }
        assertTrue(claimed.all { notes.number(it) != null })
        assertEquals((1..notes.ordered.size).toList(), notes.ordered.map { notes.number(it) })
    }

    @Test
    fun headlinesSayGoesOnlyWhereASourcedLineDoes() {
        assertEquals("Some of it goes to other companies", whereHeadline(life360))
        assertEquals("Some of it can go to other companies", whereHeadline(unknown))
        assertEquals("What com.life360.android.safetymapd does with what it collects".replace("com.life360.android.safetymapd", life360.appName), summaryHeadline(life360))
        assertEquals("What the tracker code in it suggests", summaryHeadline(unknown))
    }
}

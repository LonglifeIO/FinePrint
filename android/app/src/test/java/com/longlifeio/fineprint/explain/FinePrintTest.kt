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
    fun theFinePrintIsTheReadingOrderUpToFourLinesWithRunningTheAppFoldedAfter() {
        for (e in listOf(life360, facebook, unknown)) {
            val lines = finePrint(e)
            assertTrue(lines.size in 1..FINE_PRINT_MAX)
            assertEquals(lines.size, lines.distinct().size) // nothing repeated
            val order = readingOrder(e)
            assertEquals(order.filter { it.group() != LineGroup.RUNS_THE_APP }.take(FINE_PRINT_MAX).map { it.claim() }, lines.map { it.text })
            assertEquals(order.filter { it.group() == LineGroup.RUNS_THE_APP }.map { it.claim() }, alsoCollected(e)?.lines.orEmpty().map { it.text })
        }
        // Life360: what goes to other companies comes first, sensitive data first, its own account before reports.
        val first = life360.flows.getValue(GOES_ELSEWHERE).first()
        assertEquals(first.claim(), finePrint(life360).first().text)
        assertTrue(first.data in SENSITIVE_DATA && !first.historical && first.status == "self_disclosed")
        assertTrue(finePrint(life360).all { line -> life360.flows.getValue(GOES_ELSEWHERE).any { it.claim() == line.text } })
        // Facebook: nothing goes to other companies, so its fine print is Meta's own uses; the ruling stays under the tier and On the record.
        assertEquals("F2", facebook.tier.rule)
        assertTrue(finePrint(facebook).all { line -> facebook.flows.getValue(USED_FOR_MORE).any { it.claim() == line.text } })
        assertTrue(finePrint(facebook).none { it.text == facebook.tier.event!!.line!!.line })
    }

    @Test
    fun anAppWithoutARecordShowsItsInferredLinesInTheSameOrder() {
        assertEquals("C2", unknown.tier.rule)
        val first = finePrint(unknown).first()
        assertEquals(null, first.status) // Auto
        assertTrue(first.text, first.text.startsWith("Device and advertising IDs → "))
    }

    @Test
    fun runningTheAppIsOneFoldedLineNamingItsDataKinds() {
        val e = explanation("org.example.analytics", scan(Triple("exodus-27", "Some Crashes", "Crash reporting"), Triple("exodus-49", "Some Analytics", "Analytics"), Triple("exodus-312", "Google AdMob", "Advertisement")))
        assertTrue(finePrint(e).none { it.text.startsWith("Crash") || it.text.contains("Usage statistics") })
        val folded = alsoCollected(e)!!
        assertEquals("Also collected to run the app: usage and crash data", folded.text)
        assertEquals(2, folded.lines.size)
        assertEquals(null, alsoCollected(life360)) // nothing of Life360's is only for running it
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

package com.longlifeio.fineprint.ui

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.longlifeio.fineprint.bundle.StoreTagline
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.GOES_ELSEWHERE
import com.longlifeio.fineprint.explain.SOURCES
import com.longlifeio.fineprint.explain.SUMMARY_AUTO
import com.longlifeio.fineprint.explain.SUMMARY_CURATED
import com.longlifeio.fineprint.explain.THEIR_WORDS
import com.longlifeio.fineprint.explain.THE_FINE_PRINT
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.FINE_PRINT_MAX
import com.longlifeio.fineprint.explain.finePrint
import com.longlifeio.fineprint.explain.spoken
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The G5 detail: Their words only with a store tagline, the fine print under it, the Summary either way, and the Sources card. */
@RunWith(AndroidJUnit4::class)
class DetailTest {

    @get:Rule val compose = createComposeRule()

    /** Accessibility Test Framework checks (touch targets, contrast, labels) on every action in these tests. */
    @Before fun accessibilityChecks() = compose.enableAccessibilityChecks()

    private val bundle = InstrumentationRegistry.getInstrumentation().context.assets.let { assets ->
        parseBundle(assets.open("bundle.json").bufferedReader().use { it.readText() }, assets.open("jurisdictions.json").bufferedReader().use { it.readText() })
    }

    private fun app(pkg: String, label: String) = InstalledApp(
        packageName = pkg, label = label, versionName = "1.0", versionCode = 1, lastUpdateTime = 0, isSystem = false, hasCode = true,
        apkPaths = listOf("base.apk"), permissions = listOf(RequestedPermission("android.permission.ACCESS_FINE_LOCATION", granted = true, dangerous = true)),
    )

    private val life360 = app("com.life360.android.safetymapd", "Life360")
    private val adApp = app("org.example.ads", "Ad Example")
    private val scan = TrackerScanResult(
        listOf(DetectedTracker("fp-arity", "Arity", listOf("Location"), "x"), DetectedTracker("exodus-312", "Google AdMob", listOf("Advertisement"), "x")),
        9, 100, 1, emptyList(),
    )
    private val tagline = StoreTagline(
        "Family locator & phone tracker: real-time GPS location sharing + SOS",
        "https://play.google.com/store/apps/details?id=com.life360.android.safetymapd&hl=en_CA&gl=CA", "2026-10-07",
    )

    private fun show(a: InstalledApp, e: Explanation) {
        compose.setContent {
            AppDetailScreen(
                app = a, explanation = e, check = whatYouCanDo(a, e, bundle.apps[a.packageName], emptyMap(), emptySet()),
                review = ReviewView(ReviewStatus.NOT_REVIEWED), result = scan, signatures = null, bundleVersion = bundle.version,
                onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> }, buckets = OpenBuckets.allOpen(),
            )
        }
    }

    @Test
    fun withAStoreTaglineThePageOpensWithTheirWordsAndTheFinePrint() {
        val e = explain(life360, scan, bundle, emptyMap()).copy(storeTagline = tagline)
        show(life360, e)
        compose.onNodeWithText(THEIR_WORDS.title).assertExists()
        compose.onNodeWithText("“${tagline.text}”").assertExists() // read without the asterisk that points at the fine print
        compose.onNodeWithText("— Google Play listing (Canada), read 2026-10-07").assertExists()
        compose.onNodeWithText(THE_FINE_PRINT.title).assertExists()
        // At most four fine-print lines, each one stop for TalkBack; their asterisks are drawn, not read.
        val lines = finePrint(e)
        assertTrue(lines.size in 1..FINE_PRINT_MAX)
        compose.onAllNodesWithText("*").assertCountEquals(0)
        lines.forEach { compose.onNodeWithText(spoken(it.text)).assertExists() } // as TalkBack reads it: "→" said as "to"
        // The Summary card still follows: the hero doesn't replace a section.
        compose.onNodeWithTag("detail").performScrollToNode(hasText(SUMMARY_CURATED.subtitle))
        assertEquals(emptyList<String>(), compose.smallTargetsWhileScrolling("detail"))
    }

    @Test
    fun withoutOneThePageOpensWithItsSummary() {
        show(adApp, explain(adApp, scan, bundle, emptyMap()))
        compose.onAllNodesWithText(THEIR_WORDS.title).assertCountEquals(0)
        compose.onNodeWithText(SUMMARY_AUTO.subtitle).assertExists()
    }

    @Test
    fun aBucketShowsItsFirstFourLinesThenSeeAllAndKeepsItOpen() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("detail-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val e = explain(life360, scan, bundle, emptyMap())
        val count = e.flows.getValue(GOES_ELSEWHERE).size
        assertTrue(count > BUCKET_FIRST)
        compose.setContent {
            AppDetailScreen(
                app = life360, explanation = e, check = whatYouCanDo(life360, e, bundle.apps[life360.packageName], emptyMap(), emptySet()),
                review = ReviewView(ReviewStatus.NOT_REVIEWED), result = scan, signatures = null, bundleVersion = bundle.version,
                onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
                buckets = OpenBuckets.of(prefs),
            )
        }
        val detail = compose.onNodeWithTag("detail")
        // The chip keeps the full count while four lines show.
        detail.performScrollToNode(hasText("Goes elsewhere: $count lines", substring = true))
        compose.onNodeWithText("Goes elsewhere: $count lines", useUnmergedTree = true).assertExists()
        detail.performScrollToNode(hasText("See all $count"))
        compose.onNodeWithText("See all $count").performClick()
        detail.performScrollToNode(hasText("Show the first $BUCKET_FIRST"))
        assertTrue(prefs.getBoolean("all:$GOES_ELSEWHERE", false))
        assertTrue(OpenBuckets.of(prefs).isOpen(GOES_ELSEWHERE)) // the next launch shows them all too
        InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences("detail-test") // leaves nothing in the app's data
    }

    @Test
    fun theLastCardListsEveryFootnoteNumber() {
        show(life360, explain(life360, scan, bundle, emptyMap()))
        compose.onNodeWithTag("detail").performScrollToNode(hasText(SOURCES.subtitle))
        compose.onNodeWithTag("detail").performScrollToNode(hasText("[1] ", substring = true))
        compose.onNodeWithText("[1] ", substring = true).assertExists()
    }
}

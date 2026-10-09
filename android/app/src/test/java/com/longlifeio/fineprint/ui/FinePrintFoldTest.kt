package com.longlifeio.fineprint.ui

import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.NOT_RECORDED
import com.longlifeio.fineprint.explain.alsoCollected
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.finePrint
import com.longlifeio.fineprint.explain.spoken
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * The fine print's last line, "Also collected to run the app", and the Purpose not recorded lines, on the
 * line-order fixture (docs/METHOD.md, Where data goes): what TalkBack reads, in which order, and what opens.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class FinePrintFoldTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = parseBundle(File("src/test/resources/line-order-fixture.json").readText(), File("src/test/resources/jurisdictions-fixture.json").readText())
    private val today = LocalDate.of(2026, 10, 8)

    private fun app(pkg: String) = InstalledApp(pkg, pkg, "1.0", 1, 0, false, true, emptyList(),
        listOf(RequestedPermission("android.permission.ACCESS_FINE_LOCATION", granted = true, dangerous = true)))

    private fun detail(app: InstalledApp, vararg trackers: DetectedTracker, onView: (View) -> Unit = {}) {
        val scan = TrackerScanResult(trackers.toList(), 1, 10, 1, emptyList())
        val e = explain(app, scan, bundle, emptyMap(), today)
        val check = whatYouCanDo(app, e, bundle.apps[app.packageName], bundle.permissions.mapValues { it.value.feeds }, emptySet())
        compose.setContent {
            onView(LocalView.current)
            AppDetailScreen(
                app = app, explanation = e, check = check, review = ReviewView(ReviewStatus.NOT_REVIEWED), result = scan, signatures = null,
                bundleVersion = bundle.version, onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {},
                onTick = { _, _ -> }, buckets = OpenBuckets({ _, default -> default }, { _, _ -> }),
            )
        }
    }

    private val lines = app("com.example.lines")

    @Test
    fun theFoldIsOneButtonAfterTheFourLinesAndOpensToItsLinesWithTheirBadges() {
        pretendAScreenReaderIsOn()
        lateinit var view: View
        detail(lines) { view = it }
        val e = explain(lines, TrackerScanResult(emptyList(), 1, 10, 1, emptyList()), bundle, emptyMap(), today)
        val fold = compose.onNodeWithTag("also-collected")
            .assert(hasText("Also collected to run the app: usage and crash data"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        val height = fold.getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue("the fold is $height tall", height >= TOUCH)
        // TalkBack: the four lines in reading order, then the fold, one stop each. It reads what's on screen, and TalkBack
        // scrolls as it moves, so the page is first scrolled to the fold.
        compose.onNodeWithTag("detail").performScrollToNode(hasTestTag("also-collected"))
        val order = talkBackOrder(view, compose)
        val stops = finePrint(e).map { line -> order.indexOfFirst { it.startsWith(spoken(line.text)) } } +
            order.indexOf("Also collected to run the app: usage and crash data")
        assertTrue("stops $stops in $order", stops.all { it >= 0 } && stops == stops.sorted())
        // Closed, its lines aren't on the page; open, each shows with its badge.
        compose.onAllNodes(hasTestTag("also-collected-line")).assertCountEquals(0)
        fold.performSemanticsAction(SemanticsActions.OnClick)
        fold.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        val folded = alsoCollected(e)!!.lines
        compose.onAllNodes(hasTestTag("also-collected-line")).assertCountEquals(folded.size)
        folded.forEach { compose.onNode(hasText(spoken(it.text)) and hasAnyAncestor(hasTestTag("also-collected-line")), useUnmergedTree = true).assertExists() }
        compose.onAllNodes(hasText("Self-disclosed") and hasAnyAncestor(hasTestTag("also-collected-line")), useUnmergedTree = true).assertCountEquals(folded.size)
    }

    @Test
    fun aTrackerWithNoRecordedPurposeHasItsOwnHeadingUnderWhereItGoes() {
        detail(app("org.example.mystery"), DetectedTracker("exodus-1", "Some Analytics", listOf("Analytics"), "x.Y"), DetectedTracker("exodus-0", "Mystery SDK", emptyList(), "x.Y"))
        compose.onNodeWithTag("detail").performScrollToNode(hasText(NOT_RECORDED.title))
        compose.onNode(hasText(NOT_RECORDED.title) and isHeading()).assertExists()
        compose.onNodeWithText(NOT_RECORDED.subtitle).assertExists()
        val line = spoken("→ Mystery SDK: What it's used for isn't recorded")
        compose.onNodeWithTag("detail").performScrollToNode(hasText(line))
        compose.onNodeWithText(line).assertExists()
    }
}

package com.longlifeio.fineprint.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Why an app has its tier, on its page (docs/METHOD.md, An app's page): the Why line, the heading over what set it, the places' glosses. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class ReasonsScreenTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = parseBundle(File("src/test/resources/reasons-fixture.json").readText(), File("src/test/resources/jurisdictions-fixture.json").readText())

    private fun detail(pkg: String) {
        val app = InstalledApp(pkg, pkg, "1.0", 1, 0, false, true, emptyList(), emptyList())
        val scan = TrackerScanResult(emptyList(), 1, 10, 1, emptyList())
        val e = explain(app, scan, bundle, emptyMap(), LocalDate.of(2026, 10, 8))
        compose.setContent {
            AppDetailScreen(
                app = app, explanation = e, check = whatYouCanDo(app, e, bundle.apps[pkg], emptyMap(), emptySet()), review = ReviewView(ReviewStatus.NOT_REVIEWED),
                result = scan, signatures = null, bundleVersion = bundle.version, onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {},
                onClearMark = {}, onTick = { _, _ -> }, buckets = OpenBuckets.allOpen(),
            )
        }
    }

    private fun count(text: String) = compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().size
    private fun top(text: String) = compose.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    /** Tall enough that the whole page is laid out: every heading on it is counted. */
    @Test @Config(qualifiers = "w411dp-h3000dp-420dpi")
    fun theLinesThatSetItComeFirstUnderOneHeadingInTheFinePrintAndWhereItGoes() {
        detail("com.example.flows")
        compose.onNodeWithTag("why").assert(hasText("Why: Example Flows says your location goes to other companies. 1 more reason below."))
        assertEquals("one in the fine print, one in Goes elsewhere", 2, count("Why it's Flagged"))
        assertEquals("over the rest of each; Used for more has nothing that set it, so no heading", 2, count("Also"))
        compose.onAllNodes(hasText("for this", substring = true), useUnmergedTree = true).assertCountEquals(0) // no marker on a line
        compose.onAllNodes(hasText("to other companies"), useUnmergedTree = true).assertCountEquals(1) // Goes elsewhere's gloss
        compose.onAllNodes(hasText("beyond running the app"), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun aRulingThatSetsTheTierComesFirstOnTheRecordUnderItsHeading() {
        detail("com.example.ruled")
        compose.onNodeWithTag("why").assert(hasText("Why: A court or regulator has ruled on this app's data"))
        val page = compose.onNodeWithTag("detail")
        page.performScrollToNode(hasText("On the record", substring = true) and hasClickAction())
        assertEquals("nothing on the closed card", 0, count("Why it's Flagged"))
        compose.onNode(hasText("On the record", substring = true) and hasClickAction()).performClick()
        val row = hasText("2026-03-01 · The decision", substring = true) // its row: date, first source, outcome (the Sources card lists the title too)
        page.performScrollToNode(row)
        assertEquals(1, count("Why it's Flagged"))
        assertTrue(top("Why it's Flagged") < compose.onNode(row).fetchSemanticsNode().boundsInRoot.top)
        assertEquals("its only item, so no Ongoing or Past group", 0, count("Ongoing") + count("Past"))
    }

    @Test
    fun aComplaintMerelyFiledLeadsNothing() {
        detail("com.example.complaint")
        compose.onNodeWithTag("why").assert(hasText("Why: Nothing found beyond running the app"))
        compose.onNodeWithTag("detail").performScrollToNode(hasText("On the record", substring = true) and hasClickAction())
        compose.onNode(hasText("On the record", substring = true) and hasClickAction()).performClick()
        assertEquals(0, count("Why it's Flagged") + count("Why it's Caution") + count("Also"))
    }
}

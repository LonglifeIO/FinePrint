package com.longlifeio.fineprint.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A company's changes of ownership (owner_history), as its Sources sheet shows them under Jurisdictions: Affle's first. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class OwnerHistoryTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())

    @Test
    fun affleRecordsBuyingAdColonyFromDigitalTurbine() {
        val affle = bundle.companies.getValue("co-affle")
        assertEquals(1, affle.ownerHistory.size)
        val change = affle.ownerHistory.single()
        assertEquals("2026", change.date)
        assertEquals("Digital Turbine, Inc. (DT)", change.from)
        assertEquals("Affle MEA FZ-LLC, a step-down subsidiary of Affle 3i Limited", change.to)
        assertEquals(4, change.sources.size)
    }

    @Test
    fun aCompanysSheetShowsItsChangesOfOwnershipAsADatedChain() {
        // An app carrying AdColony, whose data goes to Affle (India).
        val app = InstalledApp("org.example.adcolony", "Example", "1.0", 1, 0, false, true, emptyList(), emptyList())
        val scan = TrackerScanResult(listOf(DetectedTracker("exodus-90", "AdColony", listOf("Advertisement"), "com.adcolony.sdk.AdColony")), 1, 10, 1, emptyList())
        val e = explain(app, scan, bundle, emptyMap())
        compose.setContent {
            AppDetailScreen(
                app = app, explanation = e, check = whatYouCanDo(app, e, null, bundle.permissions.mapValues { it.value.feeds }, emptySet()),
                review = ReviewView(ReviewStatus.NOT_REVIEWED), result = scan, signatures = null, bundleVersion = bundle.version,
                onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
                buckets = OpenBuckets.allOpen(),
            )
        }
        val page = compose.onNodeWithTag("detail")
        page.performScrollToNode(hasText("Tap to show the laws"))
        compose.onNodeWithText("Tap to show the laws").performClick()
        val affle = hasText("Affle 3i Limited: headquartered here and subject to its law", substring = true)
        page.performScrollToNode(affle)
        // Its Sources row, just under it: its two registry sources and the change's four.
        val top = compose.onNode(affle).fetchSemanticsNode().boundsInRoot.top
        val row = compose.onAllNodes(hasText("Sources (6)")).fetchSemanticsNodes().filter { it.boundsInRoot.top > top }.minBy { it.boundsInRoot.top }
        compose.onNode(SemanticsMatcher("Affle's Sources row") { it.id == row.id }).performClick()
        val sheet = compose.onNodeWithTag("sources")
        sheet.performScrollToNode(hasText(CHANGES_OF_OWNERSHIP))
        // Read as TalkBack has it: "→" said as "to".
        sheet.performScrollToNode(hasText("2026 · Digital Turbine, Inc. (DT) to Affle MEA FZ-LLC, a step-down subsidiary of Affle 3i Limited"))
        sheet.performScrollToNode(hasText("Bought the AdColony business", substring = true))
    }
}

package com.longlifeio.fineprint.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.percentOffset
import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.bundle.ProceduralNote
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.Explanation
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

/**
 * A record's confidence note (confidence_note): its words and its own sources, last on the Sources sheet of every line
 * from that record, under About these sources. The level (confidence) never shows, and the Sources row still counts
 * only the line's own sources.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class AboutTheseSourcesTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())
    private val app = InstalledApp("org.example.sdk", "Example", "1.0", 1, 0, false, true, emptyList(), emptyList())
    private fun scan(id: String, name: String) = TrackerScanResult(listOf(DetectedTracker(id, name, listOf("Advertisement"), "com.example.Sdk")), 1, 10, 1, emptyList())

    @Test
    fun everyLineFromARecordWithANoteCarriesIt() {
        val vungle = bundle.trackers.getValue("exodus-169").confidenceNote!!
        assertTrue(vungle.text.startsWith("Vungle's Google Play tables disagree."))
        val lines = explain(app, scan("exodus-169", "Vungle"), bundle, emptyMap()).flows.values.flatten()
        assertTrue(lines.isNotEmpty() && lines.all { it.about == vungle })
        // A tracker's legal lines too: InMobi's, about InMobi, on an app it doesn't name.
        val inmobi = explain(app, scan("exodus-106", "InMobi"), bundle, emptyMap())
        val record = inmobi.onTheRecord.actions + inmobi.onTheRecord.alsoReported + inmobi.onTheRecord.pastReports
        assertTrue(record.isNotEmpty() && record.all { it.about == bundle.trackers.getValue("exodus-106").confidenceNote })
        // A record without a note gives its lines none: Arity's.
        val arity = explain(app, scan("fp-arity", "Arity"), bundle, emptyMap())
        assertTrue(arity.flows.values.flatten().all { it.about == null })
        assertTrue((arity.onTheRecord.actions + arity.onTheRecord.alsoReported).all { it.about == null })
    }

    @Test
    fun aLinesSheetShowsTheNoteLastAndNeverTheLevel() {
        val scan = scan("exodus-169", "Vungle")
        val e = explain(app, scan, bundle, emptyMap())
        val line = e.flows.values.flatten().first { it.id == "flow-vungle-ids" }
        val note = line.about!!
        show(e, scan)
        val words = hasText("Your advertising ID, App Set ID", substring = true)
        compose.onNodeWithTag("detail").performScrollToNode(words)
        // The row counts the line's own sources; the note's open with them.
        sourcesRowUnder(words).also { assertEquals("Sources (${line.sources.size})", it.text()) }.performClick()
        val sheet = compose.onNodeWithTag("sources")
        sheet.performScrollToNode(hasText(ABOUT_THESE_SOURCES))
        sheet.performScrollToNode(hasText(note.text))
        sheet.performScrollToNode(hasText(note.sources.last().quote, substring = true))
        for (word in listOf("confidence", "medium")) {
            assertEquals(word, 0, compose.onAllNodes(hasText(word, substring = true, ignoreCase = true)).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun anOnTheRecordLinesSheetShowsItsTrackersNote() {
        val scan = scan("exodus-106", "InMobi")
        val e = explain(app, scan, bundle, emptyMap())
        val first = e.onTheRecord.ongoing.first()
        show(e, scan)
        val page = compose.onNodeWithTag("detail")
        val toggle = hasText("items on the record", substring = true)
        page.performScrollToNode(toggle)
        compose.onNode(toggle).performClick()
        page.performScrollToNode(hasText(first.line, substring = true))
        // Its badge sits at the row's centre and opens its own definition: tap the words.
        compose.onNode(hasText(first.line, substring = true)).performTouchInput { click(percentOffset(0.1f, 0.5f)) }
        val sheet = compose.onNodeWithTag("sources")
        sheet.performScrollToNode(hasText(ABOUT_THESE_SOURCES))
        sheet.performScrollToNode(hasText(first.about!!.text))
    }

    @Test
    fun aCompanysOwnSheetShowsItsNote() {
        // No company record has a note yet: one is given to Affle's here, on an app carrying AdColony.
        val affle = bundle.companies.getValue("co-affle")
        val note = ProceduralNote("Who controls the AdColony business after the sale isn't settled.", affle.jurisdictionSources.take(1))
        val noted = Bundle(
            bundle.version, bundle.generatedAt, bundle.apps, bundle.trackers, bundle.companies + ("co-affle" to affle.copy(confidenceNote = note)),
            bundle.permissions, bundle.deviceReach, bundle.jurisdictions,
        )
        val scan = scan("exodus-90", "AdColony")
        show(explain(app, scan, noted, emptyMap()), scan)
        val page = compose.onNodeWithTag("detail")
        page.performScrollToNode(hasText("Tap to show the laws"))
        compose.onNodeWithText("Tap to show the laws").performClick()
        val place = hasText("Affle 3i Limited: headquartered here and subject to its law", substring = true)
        page.performScrollToNode(place)
        // Its two registry sources and the change of ownership's four: the note's own source isn't counted.
        sourcesRowUnder(place).also { assertEquals("Sources (6)", it.text()) }.performClick()
        val sheet = compose.onNodeWithTag("sources")
        sheet.performScrollToNode(hasText(CHANGES_OF_OWNERSHIP))
        sheet.performScrollToNode(hasText(ABOUT_THESE_SOURCES))
        sheet.performScrollToNode(hasText(note.text))
    }

    private fun show(e: Explanation, scan: TrackerScanResult) = compose.setContent {
        AppDetailScreen(
            app = app, explanation = e, check = whatYouCanDo(app, e, null, bundle.permissions.mapValues { it.value.feeds }, emptySet()),
            review = ReviewView(ReviewStatus.NOT_REVIEWED), result = scan, signatures = null, bundleVersion = bundle.version,
            onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
            buckets = OpenBuckets.allOpen(),
        )
    }

    /** The first Sources row below the line [above] matches. */
    private fun sourcesRowUnder(above: SemanticsMatcher): SemanticsNodeInteraction {
        val top = compose.onNode(above).fetchSemanticsNode().boundsInRoot.top
        val row = compose.onAllNodes(hasText("Sources (", substring = true)).fetchSemanticsNodes()
            .filter { it.boundsInRoot.top > top }.minBy { it.boundsInRoot.top }
        return compose.onNode(SemanticsMatcher("the Sources row") { it.id == row.id })
    }

    private fun SemanticsNodeInteraction.text() = fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }
}

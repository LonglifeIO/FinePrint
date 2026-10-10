package com.longlifeio.fineprint.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.design.parseFixture
import com.longlifeio.fineprint.egress.ScanProgress
import com.longlifeio.fineprint.egress.parseTrackerSignatures
import com.longlifeio.fineprint.explain.BUCKETS
import com.longlifeio.fineprint.explain.BUCKET_TEXT
import com.longlifeio.fineprint.explain.REVIEWED
import com.longlifeio.fineprint.explain.TIER_SECTIONS
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.glance
import com.longlifeio.fineprint.explain.glanceHeadline
import com.longlifeio.fineprint.explain.latestChange
import com.longlifeio.fineprint.explain.limitedLine
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
 * The home after the phone test: At a glance reads its headline, the bar and its line, What changed, then the records and
 * bundle line, with no chips counting apps per place; a row in a tier's section carries no tier chip, since the
 * section's header does, and says Reviewed once you've marked it; a search result keeps its chip.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class HomeGlanceTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())
    private val signatures = parseTrackerSignatures(File("src/main/assets/trackers.json").readText()).trackers.associateBy { it.id }
    private val fixture = parseFixture(File("src/debug/assets/scan-fixture.json").readText())
    private val apps = fixture.first.filterNot { it.isSystem }
    private val explanations = apps.associate { it.packageName to explain(it, fixture.second[it.scanKey], bundle, signatures) }
    private val checks = apps.associate {
        it.packageName to whatYouCanDo(it, explanations.getValue(it.packageName), bundle.apps[it.packageName], bundle.permissions.mapValues { p -> p.value.feeds }, emptySet())
    }
    private val reviews = mapOf("com.facebook.katana" to ReviewView(ReviewStatus.REVIEWED, "2026-10-07"))

    private fun home() = compose.setContent {
        AppListScreen(
            apps = apps, explanations = explanations, reviews = reviews, checks = checks, results = fixture.second, progress = ScanProgress(),
            includeSystem = false, onIncludeSystemChange = {}, onOpen = {}, listState = rememberLazyListState(),
            bundleLine = "bundle: ${bundle.version}", onAbout = {}, onHowToRead = {},
            openSections = OpenSections.allOpen(),
        )
    }

    private val inGlance = hasAnyAncestor(hasTestTag("glance"))
    private fun top(text: String, substring: Boolean = false) = compose.onNode(hasText(text, substring = substring) and inGlance, useUnmergedTree = true).getUnclippedBoundsInRoot().top

    @Test
    fun atAGlanceReadsTheHeadlineTheBarWhatChangedThenTheFooterAndCountsNoPlaces() {
        home()
        val g = glance(apps, explanations, checks)
        assertTrue("some ways to limit", g.flows > 0)
        compose.onAllNodes(hasText("Apps by where their data goes") and inGlance, useUnmergedTree = true).assertCountEquals(0)
        BUCKETS.forEach { compose.onAllNodes(hasText("${BUCKET_TEXT.getValue(it).title}:", substring = true) and inGlance, useUnmergedTree = true).assertCountEquals(0) }
        val (app, change) = latestChange(apps, explanations)!!
        val tops = listOf(
            top(glanceHeadline(g)), top(limitedLine(g)), top("What changed · ${app.label} · ${change.date}"),
            top("Records:", substring = true), top("bundle: ${bundle.version}"),
        )
        assertEquals("top to bottom: $tops", tops.sorted(), tops)
        assertTrue(limitedLine(g), limitedLine(g).matches(Regex("""\d+ of \d+ ways your apps use or share data (is|are) limited by your settings""")))
    }

    /** Tall enough that every section is laid out. */
    @Test @Config(qualifiers = "w411dp-h3000dp-420dpi")
    fun aRowInItsSectionCarriesNoTierChipAndAReviewedRowSaysSo() {
        home()
        // A chip speaks as "Tier: Flagged, 3 apps": only the sections' headers have one.
        compose.onAllNodes(hasText("Tier: ", substring = true) and hasAnyAncestor(hasTestTag("list")), useUnmergedTree = true).assertCountEquals(TIER_SECTIONS.size)
        compose.onNode(hasText("Facebook") and hasClickAction() and hasAnyAncestor(hasTestTag("list"))).assert(hasText(REVIEWED))
        compose.onNode(hasText("Life360") and hasClickAction() and hasAnyAncestor(hasTestTag("list"))).assert(!hasText(REVIEWED))
    }

    @Test
    fun aSearchResultKeepsItsChip() {
        home()
        compose.onNodeWithTag("search").performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("Facebook")
        compose.onNode(hasText("Tier: Flagged, Reviewed") and hasAnyAncestor(hasTestTag("results")), useUnmergedTree = true).assertExists()
    }
}

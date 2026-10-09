package com.longlifeio.fineprint.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.design.parseFixture
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.ScanProgress
import com.longlifeio.fineprint.egress.parseTrackerSignatures
import com.longlifeio.fineprint.explain.FILTERS
import com.longlifeio.fineprint.explain.SEARCH_HINT
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.whatYouCanDo
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
 * The home's search (docs/METHOD.md, The home), on the test emulator's apps: the search view opens from the bar and
 * from Filters, results follow typing, the chips live only there, the count is read out, rows are 48dp, and TalkBack
 * reads the field, the chips, the count and the results in that order.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class HomeSearchTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())
    private val signatures = parseTrackerSignatures(File("src/main/assets/trackers.json").readText()).trackers.associateBy { it.id }
    private val fixture = parseFixture(File("src/debug/assets/scan-fixture.json").readText())
    private val apps = fixture.first
    private val explanations = apps.associate { it.packageName to explain(it, fixture.second[it.scanKey], bundle, signatures) }
    private val checks = apps.associate { it.packageName to whatYouCanDo(it, explanations.getValue(it.packageName), bundle.apps[it.packageName], emptyMap(), emptySet()) }
    private var opened: InstalledApp? = null

    private fun home() = compose.setContent {
        AppListScreen(
            apps = apps, explanations = explanations, reviews = emptyMap(), checks = checks, results = fixture.second, progress = ScanProgress(),
            includeSystem = false, onIncludeSystemChange = {}, onOpen = { opened = it }, listState = rememberLazyListState(),
            bundleLine = "bundle: ${bundle.version}", onAbout = {}, onHowToRead = {}, openSections = OpenSections({ _, default -> default }, { _, _ -> }),
        )
    }

    private val field = hasSetTextAction() and hasAnyAncestor(isDialog())
    private fun count() = compose.onNodeWithTag("count")

    @Test
    fun theBarOpensTheSearchViewAndTheResultsFollowWhatYouType() {
        home()
        compose.onAllNodesWithTag("filters").assertCountEquals(0) // no chip row at rest
        compose.onNodeWithTag("search").performClick()
        compose.onNodeWithTag("filters").assertExists()
        count().assert(hasText("10 apps"))
        compose.onNode(field).performTextInput("Life360")
        count().assert(hasText("1 app"))
        compose.onNode(hasText("Life360", substring = true) and hasClickAction() and hasAnyAncestor(hasTestTag("results"))).performClick()
        assertEquals("com.life360.android.safetymapd", opened?.packageName)
    }

    @Test
    fun itFindsAnAppByItsPackageAndByTheCompaniesItsLinesName() {
        home()
        compose.onNodeWithTag("search").performClick()
        compose.onNode(field).performTextInput("com.zhiliaoapp")
        count().assert(hasText("1 app"))
        compose.onNode(hasText("TikTok", substring = true) and hasAnyAncestor(hasTestTag("results"))).assertExists()
        // Meta: Facebook's maker, and named in the lines of four more (their Meta SDKs).
        compose.onNode(field).performTextReplacement("Meta")
        count().assert(hasText("5 apps"))
        listOf("Facebook", "franceinfo", "Lean for Instapaper", "Life360", "TikTok").forEach {
            compose.onNode(hasText(it, substring = true) and hasAnyAncestor(hasTestTag("results"))).assertExists()
        }
    }

    @Test
    fun filtersOpenTheSameViewWithNoKeyboardOverTheChips() {
        home()
        compose.onNodeWithContentDescription(FILTERS).performClick()
        compose.onNodeWithTag("filters").assertExists()
        compose.onNode(field).assertIsNotFocused()
        compose.onNodeWithTag("filter:FLAGGED").performClick()
        count().assert(hasText("3 apps"))
    }

    @Test
    fun theCountIsReadOutAndEveryResultIsAtLeast48dp() {
        home()
        compose.onNodeWithTag("search").performClick()
        count().assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        val rows = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("results"))).fetchSemanticsNodes()
        assertTrue("${rows.size} rows", rows.isNotEmpty())
        rows.forEach { row -> with(compose.density) { assertTrue("a ${row.size.height.toDp()} row", row.size.height.toDp() >= TOUCH) } }
    }

    @Test
    fun talkBackReadsTheFieldTheChipsTheCountThenTheResults() {
        pretendAScreenReaderIsOn()
        home()
        compose.onNodeWithTag("search").performClick()
        compose.waitForIdle()
        val order = talkBackOrder(composeViews().last(), compose)
        val at = listOf(SEARCH_HINT, "Flagged", "Reviewed", "10 apps").map { word -> order.indexOfFirst { it.startsWith(word) } }
        assertTrue("$at in $order", at.all { it >= 0 } && at == at.sorted())
        assertTrue(order.drop(at.last() + 1).first().startsWith("Facebook"))
    }
}

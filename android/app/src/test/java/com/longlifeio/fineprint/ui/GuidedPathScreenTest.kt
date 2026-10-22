package com.longlifeio.fineprint.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.bundle.GuidedPath
import com.longlifeio.fineprint.bundle.PathStep
import com.longlifeio.fineprint.bundle.Source
import com.longlifeio.fineprint.bundle.VerifiedOn
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.design.parseFixture
import com.longlifeio.fineprint.egress.parseTrackerSignatures
import com.longlifeio.fineprint.explain.CheckItem
import com.longlifeio.fineprint.explain.HIDE_THE_STEPS
import com.longlifeio.fineprint.explain.SHOW_ME_HOW
import com.longlifeio.fineprint.explain.TICK_ONCE_CHANGED
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.tickedOnLine
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import org.json.JSONArray
import org.json.JSONObject
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
 * Show me how under an item you tick (docs/METHOD.md, What you can do): the path to the setting, one card at a time, each
 * a heading TalkBack reads again when the step changes; the last card opens the app or the web page and says when a
 * person last walked the path; under it, the item's own tick.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1600dp-420dpi")
class GuidedPathScreenTest {

    @get:Rule val compose = createComposeRule()

    private val steps = listOf(
        PathStep("Life360", "Settings", "In the upper left corner."),
        PathStep("Settings", "Privacy Center"),
        PathStep("Privacy Center", "Your Privacy Choices"),
    )
    private val ticks = mutableListOf<Pair<String, Boolean>>()
    private val links = mutableListOf<String>()
    private var opened = 0

    /** Life360's Your Privacy Choices, its path from a help page dated 2026-05-11, walked on [walked] if at all; [stale] as build.py marks it. */
    private fun item(path: Boolean = true, ticked: Boolean = false, tickedOn: String? = null, url: String? = null, stale: Boolean = false, walked: String? = "2026-10-20") = CheckItem(
        "ctl-l360-privacy-choices", "Your Privacy Choices", "In the Life360 app: Settings > Privacy Center > Your Privacy Choices",
        automatic = false, ticked = ticked, inApp = true, tickedOn = tickedOn,
        path = GuidedPath(steps, url, emptyList(), helpDate = "2026-05-11", stale = stale).takeIf { path },
        verifiedOn = walked?.let { VerifiedOn("26.37.0", it) }.takeIf { path },
    )

    private fun show(item: CheckItem, openers: PathOpeners = PathOpeners({ opened++ }, null) { links += it }, onTick: (String, Boolean) -> Unit = { id, on -> ticks += id to on }) =
        compose.setContent { FieldNotesTheme { Column { CheckRow(item, onTick, {}, null, "Life360", openers) } } }

    /** A card as TalkBack meets it: its count, a live region it announces on each step, then the screen's name, the heading. */
    private fun card(step: String, screen: String) {
        compose.onNodeWithTag("step-count").assert(hasText(step))
        compose.onNode(isHeading() and hasTestTag("step-heading")).assert(hasText(screen))
    }
    private fun next() = compose.onNode(hasText("Next") and hasClickAction()).performClick()
    private fun toTheLastCard() = repeat(steps.size - 1) { next() }

    @Test
    fun showMeHowOpensThePathInPlaceOneCardAtATime() {
        show(item())
        compose.onAllNodesWithTag("step-card").assertCountEquals(0)
        compose.onNodeWithText(SHOW_ME_HOW).performClick()
        card("Step 1 of 3", "Life360")
        compose.onNodeWithTag("wireframe").assert(hasContentDescription("Tap Settings"))
        compose.onNodeWithText("Settings", useUnmergedTree = true).assertExists() // the highlighted row, labelled
        compose.onNodeWithText("In the upper left corner.").assertExists()
        compose.onNode(hasText("Back") and hasClickAction()).assertIsNotEnabled()
        next()
        card("Step 2 of 3", "Settings")
        compose.onNodeWithTag("wireframe").assert(hasContentDescription("Tap Privacy Center"))
        compose.onAllNodesWithText("In the upper left corner.").assertCountEquals(0)
        compose.onAllNodesWithTag("step-card").assertCountEquals(1)
        // A swipe moves a step too, either way; Back goes back.
        compose.onNodeWithTag("step-card").performTouchInput { swipeLeft() }
        card("Step 3 of 3", "Privacy Center")
        compose.onNodeWithTag("step-card").performTouchInput { swipeRight() }
        card("Step 2 of 3", "Settings")
        compose.onNode(hasText("Back") and hasClickAction()).performClick()
        card("Step 1 of 3", "Life360")
        compose.onNodeWithText(HIDE_THE_STEPS).performClick()
        compose.onAllNodesWithTag("step-card").assertCountEquals(0)
    }

    @Test
    fun theLastCardOpensTheAppSaysWhenThePathWasWalkedAndHoldsTheItemsTick() {
        show(item())
        compose.onNodeWithText(SHOW_ME_HOW).performClick()
        compose.onAllNodesWithText("Open Life360").assertCountEquals(0)
        compose.onAllNodesWithTag("path-tick").assertCountEquals(0)
        toTheLastCard()
        compose.onNode(hasText("Next") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Verified for Life360 26.37.0, 2026-10-20.").assertExists()
        compose.onNode(hasText("Open Life360") and hasClickAction()).assertIsEnabled().performClick()
        assertEquals(1, opened)
        compose.onNodeWithTag("path-tick").assert(hasText(TICK_ONCE_CHANGED)).performClick()
        assertEquals(listOf("ctl-l360-privacy-choices" to true), ticks)
    }

    /** Ticking under the path is the item's own tick: both boxes show it, with the same day. */
    @Test
    fun aTickFromThePathIsTheItemsTickWithItsDay() {
        var ticked by mutableStateOf(false)
        compose.setContent {
            FieldNotesTheme {
                Column { CheckRow(item(ticked = ticked, tickedOn = "2026-10-22".takeIf { ticked }), { _, on -> ticked = on }, {}, null, "Life360") }
            }
        }
        compose.onNodeWithText(SHOW_ME_HOW).performClick()
        toTheLastCard()
        compose.onNodeWithTag("path-tick").performClick()
        val on = SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On)
        compose.onAllNodes(on).assertCountEquals(2)
        compose.onAllNodes(hasText(tickedOnLine("2026-10-22"), substring = true)).assertCountEquals(2)
        compose.onNodeWithTag("path-tick").performClick()
        compose.onAllNodes(on).assertCountEquals(0)
    }

    @Test
    fun aWebPageEndOpensInTheBrowserByTheSettingsName() {
        show(item(url = "https://example.com/settings"))
        compose.onNodeWithText(SHOW_ME_HOW).performClick()
        toTheLastCard()
        compose.onNode(hasText("Open Your Privacy Choices") and hasClickAction()).performClick()
        assertEquals(listOf("https://example.com/settings"), links)
        assertEquals(0, opened)
    }

    @Test
    fun whereTheAppCantBeOpenedOpenIsOffAndSaysWhy() {
        show(item(), PathOpeners(null, "Not available in the preview"))
        compose.onNodeWithText(SHOW_ME_HOW).performClick()
        toTheLastCard()
        compose.onNode(hasText("Open Life360") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Not available in the preview").assertExists()
    }

    /** Nobody has walked it: the last card says the path comes from the help page, and its date. */
    @Test
    fun aPathNobodyHasWalkedSaysItComesFromTheHelpPage() {
        show(item(walked = null))
        compose.onNodeWithText(SHOW_ME_HOW).performClick()
        toTheLastCard()
        compose.onNodeWithText("From Life360's help page, dated 2026-05-11. Not yet checked in the app.").assertExists()
        compose.onAllNodesWithText("Verified for", substring = true).assertCountEquals(0)
        compose.onAllNodes(hasText("Stale"), useUnmergedTree = true).assertCountEquals(0)
    }

    /** A walk more than 90 days before the bundle: when, marked Stale, as a record is. */
    @Test
    fun anOldWalkSaysWhenItWasLastCheckedAndIsMarkedStale() {
        show(item(stale = true, walked = "2026-06-30"))
        compose.onNodeWithText(SHOW_ME_HOW).performClick()
        toTheLastCard()
        compose.onNodeWithText("Last checked 2026-06-30; the app may have changed.").assertExists()
        compose.onNode(hasText("Stale"), useUnmergedTree = true).assertExists() // the marker records use, with its definition
        compose.onAllNodesWithText("Verified for", substring = true).assertCountEquals(0)
    }

    /** A path nobody has walked is never Stale: the help page's date is the company's, and the watcher re-reads the page. */
    @Test
    fun aPathNobodyHasWalkedIsNeverStale() {
        show(item(stale = true, walked = null)) // build.py never marks one, but the card wouldn't show it if it did
        compose.onNodeWithText(SHOW_ME_HOW).performClick()
        toTheLastCard()
        compose.onNodeWithText("From Life360's help page, dated 2026-05-11. Not yet checked in the app.").assertExists()
        compose.onAllNodes(hasText("Stale"), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Last checked", substring = true).assertCountEquals(0)
    }

    /** The help page's quotes are one tap away under the cards, on a sheet of their own, as every claim's sources are. */
    @Test
    fun theHelpPagesQuotesAreOneTapAwayUnderTheCards() {
        val help = Source(
            "https://support.life360.com/hc/en-us/articles/23053465742743-Privacy-Center", "Life360 Help Center, Privacy Center", "company_site",
            "self_disclosed", "2026-05-11", null, "Tap on Privacy Center.",
        )
        val sheets = mutableListOf<SheetContent>()
        compose.setContent {
            FieldNotesTheme { Column { CheckRow(item().copy(path = GuidedPath(steps, null, listOf(help), helpDate = "2026-05-11")), { _, _ -> }, { sheets += it }, null, "Life360") } }
        }
        compose.onNodeWithText(SHOW_ME_HOW).performClick()
        compose.onNode(hasText("Sources (1)", substring = true) and hasClickAction()).performClick()
        assertEquals("How to reach Your Privacy Choices", sheets.single().heading)
        assertEquals(listOf(help), sheets.single().sources)
    }

    @Test
    fun anItemWithoutAPathShowsWhatItShowsToday() {
        show(item(path = false))
        compose.onAllNodesWithText(SHOW_ME_HOW).assertCountEquals(0)
        compose.onAllNodesWithTag("show-me-how").assertCountEquals(0)
    }

    /**
     * TalkBack: the count is a live region of its own, so "Step 2 of 3" is announced on each card, however the step changed;
     * the screen's name is the card's heading; Next is a button, so no step needs the swipe; every target is 48dp or more.
     */
    @Test
    fun theCountIsAnnouncedOnEachCardTheScreenIsItsHeadingAndEveryTargetIs48dp() {
        show(item())
        compose.onNodeWithText(SHOW_ME_HOW).performClick()
        val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        compose.onNodeWithTag("step-count").assert(polite).assert(hasText("Step 1 of 3"))
        compose.onNodeWithTag("step-heading").assert(isHeading()).assert(hasText("Life360"))
        compose.onNode(hasText("Next") and hasClickAction()).performClick()
        compose.onNodeWithTag("step-count").assert(polite).assert(hasText("Step 2 of 3"))
        compose.onNodeWithTag("step-heading").assert(hasText("Settings"))
        toTheLastCard()
        compose.onNodeWithTag("step-count").assert(polite).assert(hasText("Step 3 of 3"))
        val small = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { it.boundsInRoot.height < with(it.layoutInfo.density) { 48.dp.toPx() } }
        assertTrue(small.joinToString { it.config.toString() }, small.isEmpty())
        assertTrue(compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size >= 5) // Show me how, Back, Next, Open, the ticks
        compose.onNodeWithTag("path-tick").getUnclippedBoundsInRoot().let { assertTrue(it.bottom - it.top >= 48.dp) }
    }

    /** Life360's page, from the real bundle with a path put on Your Privacy Choices: Show me how under each item that has one, and only those. */
    @Test
    fun onLife360sPageShowMeHowSitsUnderEachItemWithAPath() {
        val pkg = "com.life360.android.safetymapd"
        val doc = JSONObject(File("../../bundle/bundle.json").readText())
        val life360 = doc.getJSONArray("apps").let { a -> List(a.length()) { a.getJSONObject(it) } }.single { it.getString("package_id") == pkg }
        val controls = life360.getJSONArray("controls").let { a -> List(a.length()) { a.getJSONObject(it) } }
        controls.single { it.getString("id") == "ctl-l360-privacy-choices" }.apply {
            val help = "https://support.life360.com/hc/en-us/articles/23053465742743-Privacy-Center"
            val source = JSONObject().put("url", help).put("type", "company_site").put("status", "self_disclosed").put("as_of", "2026-05-11").put("quote", "Tap on Privacy Center.")
            put("path", JSONObject().put("steps", JSONArray().put(JSONObject().put("screen", "Settings").put("tap", "Privacy Center")))
                .put("ends_in", JSONObject().put("kind", "open_app")).put("sources", JSONArray().put(source)))
            put("help_url", help)
            put("verified_on", JSONObject().put("app_version", "26.37.0").put("date", "2026-10-20"))
        }
        val bundle = parseBundle(doc.toString(), File("../../bundle/jurisdictions.json").readText())
        val signatures = parseTrackerSignatures(File("src/main/assets/trackers.json").readText()).trackers.associateBy { it.id }
        val fixture = parseFixture(File("src/debug/assets/scan-fixture.json").readText())
        val app = fixture.first.single { it.packageName == pkg }
        val e = explain(app, fixture.second[app.scanKey], bundle, signatures)
        val check = whatYouCanDo(app, e, bundle.apps[pkg], bundle.permissions.mapValues { it.value.feeds }, emptySet())
        compose.setContent {
            FieldNotesTheme {
                AppDetailScreen(
                    app = app, explanation = e, check = check, review = ReviewView(ReviewStatus.NOT_REVIEWED), result = fixture.second[app.scanKey],
                    signatures = null, bundleVersion = bundle.version, onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {},
                    onClearMark = {}, onTick = { _, _ -> },
                )
            }
        }
        compose.onNodeWithTag("detail").performScrollToNode(hasText(SHOW_ME_HOW))
        val withPaths = check.items.count { it.path != null }
        assertEquals(bundle.apps.getValue(pkg).controls.count { it.path != null }, withPaths)
        assertTrue(withPaths >= 1)
        compose.onAllNodesWithText(SHOW_ME_HOW).assertCountEquals(withPaths)
    }
}

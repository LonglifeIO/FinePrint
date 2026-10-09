package com.longlifeio.fineprint.ui

import android.view.View
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.design.parseFixture
import com.longlifeio.fineprint.egress.ScanProgress
import com.longlifeio.fineprint.egress.parseTrackerSignatures
import com.longlifeio.fineprint.explain.FILTERS
import com.longlifeio.fineprint.explain.HOME_WORDMARK
import com.longlifeio.fineprint.explain.SEARCH_HINT
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.whatYouCanDo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The home's top, on the screenshot matrix's three sizes: a 64dp wordmark bar that never collapses, the search bar
 * docked straight under it, At a glance under that at rest, both bars put while the list scrolls, no chip row, and on
 * the small phone the whole card above the fold with the system bars counted (the card is never shrunk to make room).
 * TalkBack reads the wordmark, Filters, the menu, the search field, then At a glance.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class HomeBarTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())
    private val signatures = parseTrackerSignatures(File("src/main/assets/trackers.json").readText()).trackers.associateBy { it.id }
    private val fixture = parseFixture(File("src/debug/assets/scan-fixture.json").readText())
    private val apps = fixture.first
    private val explanations = apps.associate { it.packageName to explain(it, fixture.second[it.scanKey], bundle, signatures) }
    private val checks = apps.associate {
        it.packageName to whatYouCanDo(it, explanations.getValue(it.packageName), bundle.apps[it.packageName], bundle.permissions.mapValues { p -> p.value.feeds }, emptySet())
    }

    private fun home(onView: (View) -> Unit = {}) = compose.setContent {
        onView(LocalView.current)
        AppListScreen(
            apps = apps, explanations = explanations, reviews = emptyMap(), checks = checks, results = fixture.second, progress = ScanProgress(),
            includeSystem = false, onIncludeSystemChange = {}, onOpen = {}, listState = rememberLazyListState(),
            bundleLine = "bundle: ${bundle.version}", onAbout = {}, onHowToRead = {},
            openSections = OpenSections({ _, default -> default }, { _, _ -> }),
        )
    }

    private fun bounds(matcher: SemanticsMatcher): DpRect = compose.onNode(matcher).getUnclippedBoundsInRoot()
    private val bar get() = bounds(hasTestTag("bar"))
    private val glance get() = bounds(hasTestTag("glance"))
    private val search get() = bounds(hasTestTag("search"))

    private fun theTop() {
        home()
        val atRest = bar
        val field = search
        assertEquals("bar height", 64f, (atRest.bottom - atRest.top).value, 0.5f)
        assertEquals("search straight under the bar", atRest.bottom.value, field.top.value, 0.5f)
        assertEquals("At a glance 8dp under the search bar", 8f, (glance.top - field.bottom).value, 0.5f)
        compose.onAllNodes(hasTestTag("filters")).assertCountEquals(0)
        compose.onAllNodesWithText("Your apps").assertCountEquals(0)
        compose.onNodeWithTag("list").performScrollToIndex(6)
        assertEquals("the bar neither grows nor collapses while scrolling", atRest, bar)
        assertEquals("the search bar stays docked", field, search)
    }

    @Test fun phone() = theTop()

    @Test @Config(qualifiers = "w800dp-h1280dp-xhdpi") fun tablet() = theTop()

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi") fun smallPhone() {
        theTop()
        compose.onNodeWithTag("list").performScrollToIndex(0)
        // Robolectric draws no system bars. fineprint37 at 360 × 780 has a 43dp status bar over the app
        // and a 24dp gesture bar under it (dumpsys window), so the card needs that much room here.
        val fold = compose.onRoot().getUnclippedBoundsInRoot().bottom - 67.dp
        assertTrue("At a glance ends ${(glance.bottom - fold).value}dp below the fold on 360 × 780", glance.bottom <= fold)
    }

    @Test fun atTwiceTheTextTheWordmarkStillFitsTheBar() {
        RuntimeEnvironment.setFontScale(2f)
        home()
        val wordmark = hasText(HOME_WORDMARK) and hasAnyAncestor(hasTestTag("bar"))
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(wordmark).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        // didOverflowWidth is no use here: the bar lays the title out at its full width, so it's set even at 100%.
        val text = layouts.single()
        assertEquals("one line", 1, text.lineCount)
        assertFalse("taller than its box", text.didOverflowHeight)
        assertTrue("glyphs past the box's right edge", text.getLineRight(0) <= text.size.width)
        val word = bounds(wordmark)
        assertTrue("the wordmark sits inside the bar", word.top >= bar.top && word.bottom <= bar.bottom)
    }

    @Test fun theWordmarkIsAHeadingInTheBarAndTheBarIsReadFirst() {
        pretendAScreenReaderIsOn()
        lateinit var view: View
        home { view = it }
        compose.onNode(hasText(HOME_WORDMARK) and hasAnyAncestor(hasTestTag("bar")))
            .assert(isHeading())
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        // The list under the bar also starts at the top of the screen; TalkBack still starts in the bar.
        assertEquals(listOf(HOME_WORDMARK, FILTERS, "More options", SEARCH_HINT, "At a glance"), talkBackOrder(view, compose).take(5))
    }
}

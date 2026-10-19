package com.longlifeio.fineprint.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.design.parseFixture
import com.longlifeio.fineprint.egress.parseTrackerSignatures
import com.longlifeio.fineprint.explain.THEIR_WORDS
import com.longlifeio.fineprint.explain.THE_FINE_PRINT
import com.longlifeio.fineprint.explain.Tier
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.finePrint
import com.longlifeio.fineprint.explain.spoken
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
 * Their words (docs/METHOD.md, An app's page), on Life360's page from the real bundle and the sample phone: closed when
 * the page opens, with the quote, the fine print's first line and how many more; its row, a heading with its state,
 * opens the rest where it is, and it stays open for the visit. With one line or none it shows whole. The app's package
 * and version sit at the foot of the page, beside the bundle line.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h2800dp-420dpi")
class TheirWordsTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())
    private val signatures = parseTrackerSignatures(File("src/main/assets/trackers.json").readText()).trackers.associateBy { it.id }
    private val fixture = parseFixture(File("src/debug/assets/scan-fixture.json").readText())
    private val life360 = fixture.first.single { it.packageName == "com.life360.android.safetymapd" }
    private val e = explain(life360, fixture.second[life360.scanKey], bundle, signatures)
    private val tagline = e.storeTagline!!
    private val lines = finePrint(e)
    private val source = "— Google Play listing (Canada), read ${tagline.asOf}"
    /** The first line's source, as its line under it gives it ("Life360 Privacy Policy, §IV.E, 2026-08-26"). */
    private val firstSource = lines.first().sources.first().let { "${it.title}, ${sourceDate(it)}" }

    @Composable
    private fun Page(cards: OpenCards = rememberOpenCards()) = AppDetailScreen(
        app = life360, explanation = e, check = whatYouCanDo(life360, e, bundle.apps[life360.packageName], bundle.permissions.mapValues { it.value.feeds }, emptySet()),
        review = ReviewView(ReviewStatus.NOT_REVIEWED), result = fixture.second[life360.scanKey], signatures = null, bundleVersion = bundle.version,
        onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
        buckets = OpenBuckets({ _, default -> default }, { _, _ -> }), cards = cards,
    )

    private val toggle = hasTestTag("their-words-toggle")
    private fun state(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun closedItShowsTheQuoteTheFinePrintsFirstLineAndHowManyMore() {
        compose.setContent { Page() }
        assertTrue(lines.size > 1)
        compose.onNode(hasText("${THEIR_WORDS.title}: “${tagline.text}”")).assertExists()
        compose.onNode(hasText(THE_FINE_PRINT.title)).assertExists()
        compose.onNode(hasText(spoken(lines.first().text))).assertExists()
        compose.onNode(toggle).assert(hasText("${lines.size - 1} more lines")).assert(isHeading()).assert(state("Collapsed")).assert(hasClickAction())
        // Not until it opens: its eyebrow, the quote's source, both definitions, the other lines, what it collects to run
        // the app and its Sources row.
        listOf(THEIR_WORDS.title, source, THEIR_WORDS.subtitle, THE_FINE_PRINT.subtitle).forEach { compose.onAllNodes(hasText(it)).assertCountEquals(0) }
        lines.drop(1).forEach { compose.onAllNodes(hasText(spoken(it.text))).assertCountEquals(0) }
        // The first line without its source line: that returns when the card opens, and its Sources sheet has it anyway.
        compose.onAllNodes(hasText(firstSource, substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasTestTag("also-collected")).assertCountEquals(0)
        compose.onAllNodes(hasText("Sources (", substring = true) and hasAnyAncestor(hasTestTag("their-words"))).assertCountEquals(0)
    }

    @Test
    fun itsRowOpensEveryLineWhereItIsAndItStaysOpenForTheVisit() {
        val restore = StateRestorationTester(compose)
        restore.setContent { Page() }
        val card = compose.onNodeWithTag("their-words")
        val top = card.getUnclippedBoundsInRoot().top
        compose.onNode(toggle).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(toggle).assert(state("Expanded")).assert(hasText("Show the first line"))
        assertEquals("it opens where it is", top, card.getUnclippedBoundsInRoot().top)
        listOf(THEIR_WORDS.title, source, THEIR_WORDS.subtitle, THE_FINE_PRINT.subtitle).forEach { compose.onNode(hasText(it)).assertExists() }
        lines.forEach { compose.onNode(hasText(spoken(it.text))).assertExists() }
        compose.onAllNodes(hasText(firstSource, substring = true)).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        compose.onNode(hasText("Sources (", substring = true) and hasAnyAncestor(hasTestTag("their-words"))).assertExists()
        // Kept for the visit, a rotation included; its row closes it again.
        restore.emulateSavedInstanceStateRestore()
        compose.onNode(toggle).assert(state("Expanded")).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(toggle).assert(state("Collapsed"))
        lines.drop(1).forEach { compose.onAllNodes(hasText(spoken(it.text))).assertCountEquals(0) }
    }

    @Test
    fun talkBackReadsTheQuoteTheFirstLineThenTheRowAsAHeading() {
        pretendAScreenReaderIsOn()
        lateinit var view: View
        compose.setContent { view = LocalView.current; Page() }
        val order = talkBackOrder(view, compose)
        val stops = listOf("${THEIR_WORDS.title}: “${tagline.text}”", THE_FINE_PRINT.title, spoken(lines.first().text), "${lines.size - 1} more lines")
            .map { said -> order.indexOfFirst { it.startsWith(said) } }
        assertTrue("stops $stops in $order", stops.all { it >= 0 } && stops == stops.sorted())
    }

    @Test
    fun oneLineOrNoneShowsWholeWithNoRow() {
        compose.setContent { FieldNotesTheme { TheirWords(tagline, lines.take(1), null, Tier.FLAGGED, open = false, onToggle = {}) {} } }
        compose.onAllNodes(toggle).assertCountEquals(0)
        listOf(THEIR_WORDS.title, source, THE_FINE_PRINT.subtitle, spoken(lines.first().text)).forEach { compose.onNode(hasText(it)).assertExists() }
        compose.onNode(hasText("Sources (", substring = true)).assertExists()
    }

    /** Closed, an alleged first line still reads not proven in court (docs/METHOD.md, Before each release); only its source goes. */
    @Test
    fun aClosedAllegedFirstLineKeepsItsStatus() {
        val alleged = lines.first().copy(status = "alleged")
        compose.setContent { FieldNotesTheme { TheirWords(tagline, listOf(alleged) + lines.drop(1), null, Tier.FLAGGED, open = false, onToggle = {}) {} } }
        compose.onNode(hasText("Alleged (not proven in court)", substring = true)).assertExists()
        compose.onAllNodes(hasText(firstSource, substring = true)).assertCountEquals(0)
    }

    @Test
    fun thePackageAndVersionSitAtTheFootBesideTheBundleLine() {
        compose.setContent { Page() }
        compose.onNodeWithTag("detail").performScrollToNode(hasTestTag("app-identity"))
        val bundleLine = compose.onNode(hasText("Knowledge bundle ${bundle.version}.")).getUnclippedBoundsInRoot()
        val identity = compose.onNodeWithTag("app-identity").getUnclippedBoundsInRoot()
        assertTrue("right under the bundle line", identity.top >= bundleLine.bottom && identity.top - bundleLine.bottom < bundleLine.bottom - bundleLine.top)
        // Once on the page, and not in the header. Nor is the header's old progress line: What you can do says it.
        compose.onAllNodes(hasText("Your settings limit", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription(life360.packageName)).assertCountEquals(1)
        compose.onAllNodes(hasText("Version 26.37.0", substring = true)).assertCountEquals(1)
        assertTrue(compose.onNode(hasText("Version 26.37.0", substring = true)).getUnclippedBoundsInRoot().top > bundleLine.top)
    }
}

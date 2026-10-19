package com.longlifeio.fineprint.ui

import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.design.parseFixture
import com.longlifeio.fineprint.egress.parseTrackerSignatures
import com.longlifeio.fineprint.explain.APPLIES
import com.longlifeio.fineprint.explain.BUCKETS
import com.longlifeio.fineprint.explain.BUCKET_TEXT
import com.longlifeio.fineprint.explain.COLLECTS
import com.longlifeio.fineprint.explain.DEVICE_ACCESS
import com.longlifeio.fineprint.explain.EVIDENCE
import com.longlifeio.fineprint.explain.JURISDICTIONS
import com.longlifeio.fineprint.explain.ON_THE_RECORD
import com.longlifeio.fineprint.explain.SOURCES
import com.longlifeio.fineprint.explain.SUMMARY_CURATED
import com.longlifeio.fineprint.explain.THE_FINE_PRINT
import com.longlifeio.fineprint.explain.WHAT_YOU_CAN_DO
import com.longlifeio.fineprint.explain.WHERE_IT_GOES
import com.longlifeio.fineprint.explain.appliesHeadline
import com.longlifeio.fineprint.explain.basedInSummary
import com.longlifeio.fineprint.explain.canDoSummary
import com.longlifeio.fineprint.explain.collectsHeadline
import com.longlifeio.fineprint.explain.evidenceHeadline
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.footnotes
import com.longlifeio.fineprint.explain.reachHeadline
import com.longlifeio.fineprint.explain.recordSummary
import com.longlifeio.fineprint.explain.summaryHeadline
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.explain.whereSummary
import com.longlifeio.fineprint.explain.whyLabel
import com.longlifeio.fineprint.explain.whyLine
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * An app's page below the fine print (docs/METHOD.md, An app's page), on Facebook's from the real bundle: every section
 * a closed row with its name and one-line summary, opening in place, read by TalkBack as a heading with its state, and
 * kept open for the visit. The window is tall enough for the whole page, so every row is on screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h2800dp-420dpi")
class DetailSectionsTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())
    private val signatures = parseTrackerSignatures(File("src/main/assets/trackers.json").readText()).trackers.associateBy { it.id }
    private val fixture = parseFixture(File("src/debug/assets/scan-fixture.json").readText())
    private fun app(pkg: String) = fixture.first.single { it.packageName == pkg }
    private val facebook = app("com.facebook.katana")
    private val e = explain(facebook, fixture.second[facebook.scanKey], bundle, signatures)
    private val check = whatYouCanDo(facebook, e, bundle.apps[facebook.packageName], bundle.permissions.mapValues { it.value.feeds }, emptySet())

    @androidx.compose.runtime.Composable
    private fun Page(pkg: String = facebook.packageName, cards: OpenCards = rememberOpenCards()) {
        val app = app(pkg)
        val e = explain(app, fixture.second[app.scanKey], bundle, signatures)
        AppDetailScreen(
            app = app, explanation = e, check = whatYouCanDo(app, e, bundle.apps[pkg], bundle.permissions.mapValues { it.value.feeds }, emptySet()),
            review = ReviewView(ReviewStatus.NOT_REVIEWED), result = fixture.second[app.scanKey], signatures = null, bundleVersion = bundle.version,
            onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
            buckets = OpenBuckets({ _, default -> default }, { _, _ -> }), cards = cards,
        )
    }

    /** A section's top: the heading with its name that opens and closes it. */
    private fun top(title: String) = hasText(title) and hasClickAction() and isHeading()
    private fun state(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    /** Facebook's sections below the fine print, in page order, each with the summary its row shows. */
    private val rows = listOfNotNull(
        SUMMARY_CURATED.title to summaryHeadline(e),
        COLLECTS.title to collectsHeadline(e),
        (WHERE_IT_GOES.title to whereSummary(e, check)).takeIf { e.flows.isNotEmpty() },
        (JURISDICTIONS.title to basedInSummary(e.governments)).takeIf { e.governments.blocks.isNotEmpty() },
        (APPLIES.title to appliesHeadline(e)).takeIf { e.applies.isNotEmpty() },
        (WHAT_YOU_CAN_DO.title to canDoSummary(check)).takeIf { check.items.isNotEmpty() || check.inAppText != null },
        (DEVICE_ACCESS.title to reachHeadline(e)).takeIf { e.reach.isNotEmpty() },
        ON_THE_RECORD.title to recordSummary(e.onTheRecord, e.changes),
        EVIDENCE.title to evidenceHeadline(facebook, fixture.second[facebook.scanKey]),
        SOURCES.title to "${footnotes(e, check).ordered.size} sources",
    )

    @Test
    fun belowTheFinePrintEverySectionIsAClosedRowWithItsSummary() {
        compose.setContent { Page() }
        // The header and the fine print stay open.
        compose.onNodeWithTag("why").assert(hasText(whyLine(e)!!))
        compose.onNode(hasText(THE_FINE_PRINT.title)).assertExists()
        assertTrue("Facebook's page has at least its seven sections", rows.size >= 7)
        assertEquals("2 ways, 0 limited by your settings", rows.toMap()[WHERE_IT_GOES.title])
        assertTrue(rows.toMap().getValue(ON_THE_RECORD.title), rows.toMap().getValue(ON_THE_RECORD.title).matches(Regex("""\d+ matters, latest \d{4}""")))
        for ((title, summary) in rows) {
            compose.onNode(top(title)).assert(hasText(summary)).assert(state("Collapsed"))
                .assert(SemanticsMatcher("opens with Show …") { it.config[SemanticsActions.OnClick].label?.startsWith("Show ") == true })
        }
        // What the sections hold isn't on the page until they open.
        compose.onAllNodes(hasText("Tracker code in this app")).assertCountEquals(0)
        compose.onAllNodes(hasText("[1] ", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText(e.summary)).assertCountEquals(0)
        BUCKETS.forEach { compose.onAllNodes(hasText(BUCKET_TEXT.getValue(it).subtitle)).assertCountEquals(0) }
    }

    @Test
    fun tappingARowOpensItInPlaceAndTappingAgainClosesIt() {
        compose.setContent { Page() }
        val where = compose.onNode(top(WHERE_IT_GOES.title))
        val before = where.getUnclippedBoundsInRoot().top
        where.performClick()
        where.assert(state("Expanded")).assert(hasText(whereSummary(e, check)))
        assertEquals("the row stays where it was", before, where.getUnclippedBoundsInRoot().top)
        val places = e.flows.keys.map { BUCKET_TEXT.getValue(it).subtitle }
        places.forEach { compose.onNode(hasText(it)).assertExists() }
        // Only that one opened.
        compose.onNode(top(ON_THE_RECORD.title)).assert(state("Collapsed"))
        where.performClick()
        where.assert(state("Collapsed"))
        places.forEach { compose.onAllNodes(hasText(it)).assertCountEquals(0) }
    }

    @Test
    fun anOpenSectionStaysOpenForTheVisitARotationIncluded() {
        val restore = StateRestorationTester(compose)
        restore.setContent { Page() }
        compose.onNode(top(ON_THE_RECORD.title)).performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNode(top(ON_THE_RECORD.title)).assert(state("Expanded"))
        compose.onNode(top(WHERE_IT_GOES.title)).assert(state("Collapsed"))
    }

    @Test
    fun talkBackReadsEachRowAsAHeadingWithItsStateInPageOrder() {
        pretendAScreenReaderIsOn()
        lateinit var view: View
        compose.setContent { view = LocalView.current; Page() }
        val order = talkBackOrder(view, compose)
        // One stop per row: its name, its summary and its definition.
        val stops = rows.map { (title, summary) -> order.indexOfFirst { it.startsWith("$title $summary") } }
        assertTrue("rows $stops in $order", stops.all { it >= 0 } && stops == stops.sorted())
        rows.forEach { (title, _) -> compose.onNode(top(title)).assert(isHeading()).assert(state("Collapsed")) }
    }

    @Test
    fun withoutTheirWordsTheSummaryOpensThePageAndStaysOpen() {
        val maps = app("com.google.android.apps.maps")
        val e = explain(maps, fixture.second[maps.scanKey], bundle, signatures)
        assertEquals(null, e.storeTagline)
        compose.setContent { Page(maps.packageName) }
        compose.onNode(hasText(e.summary)).assertExists()
        compose.onAllNodes(top(SUMMARY_CURATED.title)).assertCountEquals(0)
        compose.onNode(top(WHERE_IT_GOES.title)).assert(state("Collapsed"))
    }

    @Test
    fun theWhyLineOpensWithTheTiersOwnPhraseInTheFinePrintHeadingsWeight() {
        compose.setContent { Page() }
        val label = whyLabel(e.tier.tier!!)
        assertEquals("Why it's Flagged:", label)
        val why = compose.onNodeWithTag("why").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertTrue(why.text, why.text.startsWith("Why it's Flagged: a regulator ruled on a case about this app's data — Data Protection Commission (Ireland), 2024."))
        val span = why.spanStyles.single()
        assertEquals(0 until label.length, span.start until span.end)
        assertEquals(FontWeight(600), span.item.fontWeight)
    }

    @Test
    fun notCheckedYetHasNoWhyLineOnlyWhatTheScanFound() {
        val nextcloud = app("com.nextcloud.client")
        val e = explain(nextcloud, fixture.second[nextcloud.scanKey], bundle, signatures)
        assertEquals(null, e.tier.tier)
        compose.setContent { Page(nextcloud.packageName) }
        compose.onNodeWithTag("why").assert(hasText(e.tier.reason))
        assertFalse(e.tier.reason.startsWith("Why"))
    }
}

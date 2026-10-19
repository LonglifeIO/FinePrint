package com.longlifeio.fineprint.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.design.parseFixture
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.parseTrackerSignatures
import com.longlifeio.fineprint.explain.CHECK_LEGEND
import com.longlifeio.fineprint.explain.COLLECTS
import com.longlifeio.fineprint.explain.NOTHING_TO_LIMIT
import com.longlifeio.fineprint.explain.OPEN_APP_INFO
import com.longlifeio.fineprint.explain.WHAT_YOU_CAN_DO
import com.longlifeio.fineprint.explain.WhatYouCanDo
import com.longlifeio.fineprint.explain.appInfoNote
import com.longlifeio.fineprint.explain.canDoSummary
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.spoken
import com.longlifeio.fineprint.explain.tickedOnLine
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
 * What you can do on an app's page (docs/METHOD.md, What you can do), from the real bundle and the sample phone: what
 * FinePrint reads from Android in dashed boxes, before and after; what you tick in solid ones, with the day; why there's
 * nothing to limit when nothing applies; Open app info and its note; and the permissions read again when you come back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h2800dp-420dpi")
class WhatYouCanDoScreenTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())
    private val signatures = parseTrackerSignatures(File("src/main/assets/trackers.json").readText()).trackers.associateBy { it.id }
    private val fixture = parseFixture(File("src/debug/assets/scan-fixture.json").readText())
    private val feeds = bundle.permissions.mapValues { it.value.feeds }
    private fun app(pkg: String) = fixture.first.single { it.packageName == pkg }
    private val facebook = app("com.facebook.katana")
    private val check = whatYouCanDo(facebook, explain(facebook, fixture.second[facebook.scanKey], bundle, signatures), bundle.apps[facebook.packageName], feeds, emptySet())

    /** [app] as the phone reports it now, its checklist as [check] works it out, and [onReturn] for coming back to the page. */
    @Composable
    private fun Page(
        pkg: String = facebook.packageName, app: InstalledApp = app(pkg),
        check: (InstalledApp) -> WhatYouCanDo = { a -> whatYouCanDo(a, explain(a, fixture.second[a.scanKey], bundle, signatures), bundle.apps[pkg], feeds, emptySet()) },
        onReturn: () -> Unit = {},
    ) {
        AppDetailScreen(
            app = app, explanation = explain(app, fixture.second[app.scanKey], bundle, signatures), check = check(app),
            review = ReviewView(ReviewStatus.NOT_REVIEWED), result = fixture.second[app.scanKey], signatures = null, bundleVersion = bundle.version,
            onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
            buckets = OpenBuckets({ _, default -> default }, { _, _ -> }), onReturn = onReturn,
        )
    }

    private fun top(title: String) = hasText(title) and hasClickAction() and isHeading()
    private fun state(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)
    private fun SemanticsNodeInteraction.top() = getUnclippedBoundsInRoot().top

    /** Facebook asked for location and contacts, but neither feeds its two ways: nothing to limit, and why; Open app info last. */
    @Test
    fun whatYouCanDoSaysWhyThereIsNothingToLimitAndHoldsOpenAppInfo() {
        compose.setContent { Page() }
        val todo = compose.onNode(top(WHAT_YOU_CAN_DO.title)).assert(hasText(NOTHING_TO_LIMIT))
        assertEquals(NOTHING_TO_LIMIT, canDoSummary(check))
        val why = compose.onNode(hasText("No permission FinePrint can check feeds a way Facebook uses or shares data."))
        compose.onAllNodesWithTag("check-legend", useUnmergedTree = true).assertCountEquals(0) // no box to explain
        // One Open app info, in the card, with its note; the header no longer has one.
        val button = compose.onNode(hasText(OPEN_APP_INFO) and hasClickAction()).assertIsEnabled()
        compose.onAllNodes(hasText("Open app settings")).assertCountEquals(0)
        val note = compose.onNode(hasText(appInfoNote("Facebook")))
        assertEquals("Android opens Facebook's app info page. When you come back, FinePrint checks its permissions again.", appInfoNote("Facebook"))
        assertTrue(why.top() > todo.top() && button.top() > why.top() && note.top() > button.top())
        assertTrue("the card ends before the next section", note.top() < compose.onNode(top(COLLECTS.title)).top())
    }

    /**
     * Life360 on the sample phone: precise location on, background location and physical activity off. Each Android item
     * has a dashed box and shows the setting before and after; each item you tick has a solid one, with its day.
     */
    @Test
    fun aDashedBoxIsWhatFinePrintChecksAndASolidOneWhatYouTickWithItsDay() {
        val pkg = "com.life360.android.safetymapd"
        val ticked = setOf("ctl-l360-privacy-choices")
        val days = mapOf("ctl-l360-privacy-choices" to "2026-10-19")
        lateinit var shown: WhatYouCanDo
        compose.setContent {
            Page(pkg, check = { a -> whatYouCanDo(a, explain(a, fixture.second[a.scanKey], bundle, signatures), bundle.apps[pkg], feeds, ticked, days).also { shown = it } })
        }
        // One line, behind one string; TalkBack reads it without the dot.
        assertEquals("Dashed: FinePrint checks this · Solid: you mark it", CHECK_LEGEND)
        compose.onNodeWithTag("check-legend", useUnmergedTree = true).assert(hasText("Dashed: FinePrint checks this. Solid: you mark it"))
        val android = shown.items.filter { it.automatic }
        assertEquals(
            listOf("Use precise location: On → Off", "Location: Allowed only while in use", "Physical activity: Not allowed"),
            android.map { it.setting },
        )
        compose.onAllNodesWithTag("dashed-box", useUnmergedTree = true).assertCountEquals(android.size)
        android.forEach { compose.onNode(hasText(spoken(it.setting!!), substring = true) and state(if (it.ticked) "Off" else "Still on")).assertExists() }
        val yours = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        compose.onAllNodes(yours).assertCountEquals(shown.items.count { !it.automatic })
        compose.onNode(yours and hasText(tickedOnLine("2026-10-19"), substring = true) and hasText("Your Privacy Choices", substring = true)).assertExists()
        compose.onAllNodes(hasText("You ticked this on", substring = true)).assertCountEquals(1)
    }

    /** What you can do shows what Android reports each time the page reads it: here, precise location turned off. */
    @Test
    fun comingBackToThePageReadsThePermissionsAgain() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            override val lifecycle: Lifecycle get() = registry
        }
        val life360 = app("com.life360.android.safetymapd")
        var phone by mutableStateOf(life360)
        var returns = 0
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { Page(life360.packageName, app = phone, onReturn = { returns++ }) }
        }
        compose.onNode(hasText("Use precise location: On to Off", substring = true) and state("Still on")).assertExists()
        assertEquals("not when the page first shows", 0, returns)
        // Android's app info on top, then back: the page asks for the permissions again.
        listOf(Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME).forEach(owner.registry::handleLifecycleEvent)
        assertEquals(1, returns)
        // What they say now is what the row shows.
        phone = life360.copy(permissions = life360.permissions.map { if (it.name == "android.permission.ACCESS_FINE_LOCATION") it.copy(granted = false) else it })
        compose.onNode(hasText("Use precise location: Off", substring = true) and state("Off")).assertExists()
    }
}

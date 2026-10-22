package com.longlifeio.fineprint.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.longlifeio.fineprint.bundle.GuidedPath
import com.longlifeio.fineprint.bundle.PathStep
import com.longlifeio.fineprint.bundle.VerifiedOn
import com.longlifeio.fineprint.explain.CheckItem
import com.longlifeio.fineprint.explain.SHOW_ME_HOW
import com.longlifeio.fineprint.explain.tickedOnLine
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A guided path on a phone: Show me how, each card in turn (by Next and by a swipe), Open, the tick; every target 48dp or more. */
@RunWith(AndroidJUnit4::class)
class GuidedPathTest {

    @get:Rule val compose = createComposeRule()

    /** Accessibility Test Framework checks (touch targets, contrast, labels) on every action in these tests. */
    @Before fun accessibilityChecks() = compose.enableAccessibilityChecks()

    private val steps = listOf(
        PathStep("Life360", "Settings", "In the upper left corner."),
        PathStep("Settings", "Privacy Center"),
        PathStep("Privacy Center", "Your Privacy Choices"),
        PathStep("Your Privacy Choices", "Include my data", "Scroll down to it, then turn it off."),
    )
    private var opened = 0

    private fun show() = compose.setContent {
        var ticked by remember { mutableStateOf(false) }
        val item = CheckItem(
            "ctl-l360-privacy-choices", "Your Privacy Choices", "In the Life360 app: Settings > Privacy Center > Your Privacy Choices",
            automatic = false, ticked = ticked, inApp = true, tickedOn = "2026-10-22".takeIf { ticked },
            path = GuidedPath(steps, null, emptyList()), verifiedOn = VerifiedOn("26.37.0", "2026-10-22"),
        )
        // As on an app's page: the theme, and a surface under the card that gives text its colour.
        FieldNotesTheme {
            Surface(color = LocalPalette.current.card, contentColor = LocalPalette.current.ink) {
                LazyColumn(Modifier.testTag("path-list")) {
                    item { CheckRow(item, { _, on -> ticked = on }, {}, null, "Life360", PathOpeners({ opened++ })) }
                }
            }
        }
    }

    /** The count (a live region TalkBack announces on each card) and the screen's name, the card's heading. */
    private fun card(step: String, screen: String) {
        compose.onNodeWithTag("step-count").assert(hasText(step)).assertIsDisplayed()
        compose.onNode(isHeading() and hasText(screen)).assertIsDisplayed()
    }

    private fun shows(text: String) {
        compose.onNodeWithTag("path-list").performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    @Test
    fun thePathStepsThroughToOpenAndTheTickWithEveryTargetAtLeast48dp() {
        show()
        compose.onNodeWithText(SHOW_ME_HOW).performClick()
        card("Step 1 of 4", "Life360")
        compose.onNode(hasText("Next") and hasClickAction()).performClick()
        card("Step 2 of 4", "Settings")
        compose.onNodeWithTag("step-card").performTouchInput { swipeLeft() }
        card("Step 3 of 4", "Privacy Center")
        compose.onNode(hasText("Next") and hasClickAction()).performClick()
        shows("Scroll down to it, then turn it off.")
        shows("Verified for Life360 26.37.0, 2026-10-22.")
        assertEquals(emptyList<String>(), compose.smallTargetsWhileScrolling("path-list"))
        compose.onNodeWithTag("path-list").performScrollToNode(hasText("Open Life360"))
        compose.onNode(hasText("Open Life360") and hasClickAction()).performClick()
        assertEquals(1, opened)
        compose.onNodeWithTag("path-list").performScrollToNode(hasText("Tick this once you've changed it."))
        compose.onNodeWithTag("path-tick").performClick()
        // The same tick: the path's box and the item's own both show it, with the day.
        compose.onNodeWithTag("path-tick").assert(hasText(tickedOnLine("2026-10-22"), substring = true)).assertIsDisplayed()
        assertEquals(2, compose.onAllNodesWithText(tickedOnLine("2026-10-22"), substring = true).fetchSemanticsNodes().size)
    }
}

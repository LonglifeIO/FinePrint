package com.longlifeio.fineprint.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The introduction: three pages, Next and Skip, and "Show my apps" at the end; nothing under 48dp. */
@RunWith(AndroidJUnit4::class)
class IntroTest {

    @get:Rule val compose = createComposeRule()

    @Before fun accessibilityChecks() = compose.enableAccessibilityChecks()

    private var done = false

    @Test
    fun nextTwiceThenShowMyApps() {
        compose.setContent { OnboardingScreen(onDone = { done = true }) }
        compose.onNodeWithText("See where your apps' data can go").assertExists()
        compose.onNodeWithContentDescription("Page 1 of 3").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Three places your data can go").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText(INTRO_STANCE).assertExists()
        compose.onAllNodesWithText("Skip").assertCountEquals(0) // the last page has nothing left to skip
        compose.onNodeWithText("Show my apps").performClick()
        assertTrue(done)
        assertTrue(compose.smallTargetsWhileScrolling("intro").isEmpty())
    }

    @Test
    fun skipEndsItAtOnce() {
        compose.setContent { OnboardingScreen(onDone = { done = true }) }
        compose.onNodeWithText("Skip").performClick()
        assertTrue(done)
    }
}

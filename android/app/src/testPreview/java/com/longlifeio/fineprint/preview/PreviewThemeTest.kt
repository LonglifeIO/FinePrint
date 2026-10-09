package com.longlifeio.fineprint.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.longlifeio.fineprint.GLANCE_NOTICE
import com.longlifeio.fineprint.MainActivity
import com.longlifeio.fineprint.ThemeFrame
import com.longlifeio.fineprint.ui.FieldNotesTheme
import com.longlifeio.fineprint.ui.FinePrintTheme
import com.longlifeio.fineprint.ui.LocalPalette
import com.longlifeio.fineprint.ui.markIntroSeen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The preview's theme switch under its banner in At a glance: Light and Dark set every screen's theme whatever the
 * phone's mode, System follows the phone, and the choice lives in memory only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class PreviewThemeTest {

    private val compose = createAndroidComposeRule<MainActivity>()

    // The introduction counts as seen, so it opens on the home; the choice starts at System, as in a new process.
    @get:Rule val rules: RuleChain = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() {
                markIntroSeen(RuntimeEnvironment.getApplication())
                themeChoice.value = ThemeChoice.SYSTEM
            }
        })
        .around(compose)

    /** The At a glance card's fill, read from the screen at its left edge: the dark palette's is dark. */
    private fun glanceIsDark(): Boolean {
        val card = compose.onNodeWithTag("glance").captureToImage().asAndroidBitmap()
        return androidx.compose.ui.graphics.Color(card.getPixel(4, card.height / 2)).luminance() < 0.5f
    }

    @Test
    fun theSwitchSitsUnderTheBannerWithSystemChosen() {
        compose.onNodeWithText(GLANCE_NOTICE!!).assertExists()
        compose.onNodeWithText("System").assertIsSelected()
        compose.onNodeWithText("Light").assertIsNotSelected()
        compose.onNodeWithText("Dark").assertIsNotSelected()
        val density = compose.activity.resources.displayMetrics.density
        for (label in listOf("System", "Light", "Dark")) {
            val tall = compose.onNode(hasText(label)).fetchSemanticsNode().touchBoundsInRoot.height / density
            assertTrue("$label is $tall dp tall", tall >= 48f)
        }
    }

    @Test
    fun darkAndLightOverrideALightPhone() {
        assertEquals(false, glanceIsDark())
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithText("Dark").assertIsSelected()
        assertEquals(true, glanceIsDark())
        compose.onNodeWithText("Light").performClick()
        assertEquals(false, glanceIsDark())
        compose.onNodeWithText("System").performClick()
        assertEquals(false, glanceIsDark())
    }

    @Test
    @Config(qualifiers = "night")
    fun lightOverridesADarkPhoneAndSystemFollowsIt() {
        assertEquals(true, glanceIsDark())
        compose.onNodeWithText("Light").performClick()
        assertEquals(false, glanceIsDark())
        compose.onNodeWithText("System").performClick()
        assertEquals(true, glanceIsDark())
    }

    @Test
    fun theChoiceIsKeptInMemoryOnly() {
        val data = compose.activity.dataDir
        fun files() = data.walkTopDown().filter { it.isFile }.associate { it.path to it.lastModified() }
        val before = files()
        compose.onNodeWithText("Dark").performClick()
        compose.waitForIdle()
        // Nothing written: no preference, no file.
        assertEquals(before, files())
        // It outlasts the activity (a rotation), not the process: themeChoice is a plain in-memory value.
        compose.activityRule.scenario.recreate()
        assertEquals(true, glanceIsDark())
        assertEquals(ThemeChoice.DARK, themeChoice.value)
    }
}

/** The frame sets the dark flag both theme wrappers read: FinePrintTheme's colours and the Field notes palette. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreviewThemeFrameTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun bothThemeWrappersFollowTheChoice() {
        themeChoice.value = ThemeChoice.SYSTEM
        var material = false
        var palette = false
        compose.setContent {
            ThemeFrame {
                FinePrintTheme { material = MaterialTheme.colorScheme.background.luminance() < 0.5f }
                FieldNotesTheme { palette = LocalPalette.current.dark }
            }
        }
        assertEquals(false to false, material to palette)
        themeChoice.value = ThemeChoice.DARK
        compose.waitForIdle()
        assertEquals(true to true, material to palette)
        themeChoice.value = ThemeChoice.LIGHT
        compose.waitForIdle()
        assertEquals(false to false, material to palette)
        themeChoice.value = ThemeChoice.SYSTEM
    }
}

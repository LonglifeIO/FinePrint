package com.longlifeio.fineprint.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.longlifeio.fineprint.BuildConfig
import com.longlifeio.fineprint.bundle.BundleState
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.parseTrackerSignatures
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** About on a phone, with the real bundle: what FinePrint is, this version, the links to GitHub, the licences; nothing under 48dp. */
@RunWith(AndroidJUnit4::class)
class AboutTest {

    @get:Rule val compose = createComposeRule()

    /** Accessibility Test Framework checks (touch targets, contrast, labels) on every action in these tests. */
    @Before fun accessibilityChecks() = compose.enableAccessibilityChecks()

    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private fun asset(name: String) = assets.open(name).bufferedReader().use { it.readText() }
    private val state = BundleState(parseBundle(asset("bundle.json"), asset("jurisdictions.json")), parseTrackerSignatures(asset("trackers.json")))
    private val opened = mutableListOf<String>()

    private fun shows(text: String, substring: Boolean = false) {
        compose.onNodeWithTag("about").performScrollToNode(hasText(text, substring = substring))
        compose.onNodeWithText(text, substring = substring).assertIsDisplayed()
    }

    private fun about() = compose.setContent { FinePrintTheme { AboutScreen(state, "bundle: test", "Downloaded whole from https://example.org/.", {}, {}) { opened += it } } }

    @Test
    fun everyButtonAndLinkIsAtLeast48dp() {
        about()
        assertEquals(emptyList<String>(), compose.smallTargetsWhileScrolling("about"))
    }

    @Test
    fun itShowsWhatFinePrintIsThisVersionItsLinksAndItsLicences() {
        about()
        shows(ABOUT_NOTE)
        shows(FUNDING)
        shows("App version ${BuildConfig.VERSION_NAME}.")
        shows("Knowledge bundle ${state.bundle!!.version}, dated ", substring = true)
        shows(LINKS_NOTE)
        ABOUT_LINKS.forEach { link ->
            shows(link.label)
            compose.onNodeWithText(link.label).performClick()
        }
        assertEquals(ABOUT_LINKS.map { it.url }, opened)
        shows("FinePrint isn't part of Exodus Privacy and isn't endorsed by it.")
        THIRD_PARTY.forEach { shows(it) }
        shows("Show the licence texts")
        compose.onNodeWithText("Show the licence texts").performClick()
        shows("The majority of smali/baksmali is written and copyrighted by me (Ben Gruver)", substring = true)
        shows("Hide the licence texts")
    }
}

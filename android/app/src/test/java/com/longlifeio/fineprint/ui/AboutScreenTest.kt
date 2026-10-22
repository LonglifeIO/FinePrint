package com.longlifeio.fineprint.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.longlifeio.fineprint.BuildConfig
import com.longlifeio.fineprint.bundle.BundleState
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.parseTrackerSignatures
import com.longlifeio.fineprint.explain.ABOUT_REVIEW_REQUESTS
import com.longlifeio.fineprint.openInBrowser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.OffsetDateTime

/**
 * About: what FinePrint is and isn't, this version and its bundle's, the links to GitHub and the licences. A link opens
 * only when tapped, and the app hands its address to the browser; it sends nothing itself.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class AboutScreenTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = parseBundle(File("src/test/resources/bundle-fixture.json").readText(), File("src/test/resources/jurisdictions-fixture.json").readText())
    private val signatures = parseTrackerSignatures(File("src/main/assets/trackers.json").readText())
    private val opened = mutableListOf<String>()

    private fun about(state: BundleState = BundleState(bundle, signatures)) =
        compose.setContent { AboutScreen(state, "bundle: none yet (scanning still works offline)", "Downloaded whole from https://example.org/.", {}, {}) { opened += it } }

    private fun shows(text: String, substring: Boolean = false) {
        compose.onNodeWithTag("about").performScrollToNode(hasText(text, substring = substring))
        compose.onNodeWithText(text, substring = substring).assertIsDisplayed()
    }

    @Test
    fun itSaysWhatFinePrintIsWhoPaysForItAndWhichVersionThisIs() {
        about()
        shows(ABOUT_NOTE)
        shows(FUNDING)
        shows("App version ${BuildConfig.VERSION_NAME}.")
        shows("Knowledge bundle 2026.10.04, dated 2026-10-04 (", substring = true)
        shows("Downloaded whole from https://example.org/.")
        shows("Update now")
    }

    @Test
    fun withNoBundleYetItSaysSoInPlaceOfTheVersion() {
        about(state = BundleState())
        shows("bundle: none yet (scanning still works offline)")
    }

    @Test
    fun theLicencesNameEachPartOfTheAppAndTheTrackerAttributionStaysAsItIs() {
        about()
        shows("FinePrint's code: AGPL-3.0-or-later.")
        shows("FinePrint's records (bundle.json) and its table of laws (jurisdictions.json): CC BY 4.0, attribution FinePrint.")
        shows("Tracker list (trackers.json): ODbL 1.0, its contents DbCL 1.0, from εxodus (above).")
        THIRD_PARTY.forEach { shows(it) }
        shows(signatures.attribution)
        shows("FinePrint isn't part of Exodus Privacy and isn't endorsed by it.")
        shows("FinePrint's own tracker signatures (fp-*) are part of the same file, under the same licence.")
        // The texts open in place, dexlib2's among them.
        shows("Show the licence texts")
        compose.onNodeWithText("Show the licence texts").performClick()
        shows("The majority of smali/baksmali is written and copyrighted by me (Ben Gruver)", substring = true)
        shows("Hide the licence texts")
    }

    /** The fonts About names are the app's; the mockups' fonts are in debug builds only (src/debug). */
    @Test
    fun theFontsAndIconsAboutNamesAreTheOnesTheAppShips() {
        assertEquals(setOf("atkinson_next", "atkinson_next_italic", "fraunces", "fraunces_italic"), File("src/main/res/font").list()!!.map { it.substringBefore('.') }.toSet())
        // Every icon is Material Symbols' (ms_), Material Icons' (its comment says so) or one of FinePrint's tier shapes.
        val own = setOf("ic_tier_caution.xml", "ic_tier_flagged.xml", "ic_tier_no_record.xml")
        File("src/main/res/drawable").listFiles()!!.filterNot { it.name.startsWith("ms_") || it.name in own }.forEach {
            assertTrue(it.name, it.readText().contains("<!-- Material \""))
        }
        assertEquals(
            setOf("ofl_atkinson_hyperlegible_next.txt", "ofl_fraunces.txt", "licence_material_symbols.txt", "licence_dexlib2.txt"),
            File("src/main/res/raw").list()!!.toSet(),
        )
    }

    @Test
    fun eachLinkOpensItsPageOnlyWhenTapped() {
        about()
        shows(LINKS_NOTE)
        assertEquals("nothing opens with the screen", emptyList<String>(), opened)
        ABOUT_LINKS.forEach { link ->
            shows(link.label)
            compose.onNodeWithText(link.label).performClick()
        }
        assertEquals(ABOUT_LINKS.map { it.url }, opened)
        shows(LINKS_PUBLIC)
        shows(ABOUT_REVIEW_REQUESTS)
    }

    @Test
    fun theLinksGoToTheRepositorysOwnFormsAndPrivacyPage() {
        assertEquals(
            listOf(
                "Source code" to "https://github.com/LonglifeIO/FinePrint",
                "Report an error" to "https://github.com/LonglifeIO/FinePrint/issues/new?template=record-error.md",
                "Ask for a correction" to "https://github.com/LonglifeIO/FinePrint/issues/new?template=company-correction.yml",
                "Privacy" to "https://github.com/LonglifeIO/FinePrint/blob/main/docs/privacy.md",
            ),
            ABOUT_LINKS.map { it.label to it.url },
        )
        listOf("../../.github/ISSUE_TEMPLATE/record-error.md", "../../.github/ISSUE_TEMPLATE/company-correction.yml", "../../docs/privacy.md")
            .forEach { assertTrue(it, File(it).isFile) }
    }

    @Test
    fun theAppHandsEachAddressToTheBrowserAndSendsNothingItself() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        ABOUT_LINKS.forEach { link ->
            activity.openInBrowser(link.url)
            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals(link.url, intent.dataString)
        }
    }

    @Test
    fun withNoBrowserAToastSaysSo() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        shadowOf(activity.application).checkActivities(true) // nothing on this phone handles the address
        activity.openInBrowser(ABOUT_LINKS.first().url)
        assertEquals("There's no browser on this phone to open GitHub.", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun theBundleLineGivesItsVersionItsDateAndItsAge() {
        val now = OffsetDateTime.parse("2026-10-20T09:00:00-03:00")
        assertEquals("Knowledge bundle 2026.10.04, dated 2026-10-04 (15 days old).", bundleLine(bundle, now))
        assertEquals("Knowledge bundle 2026.10.04, dated 2026-10-04 (1 day old).", bundleLine(bundle, OffsetDateTime.parse("2026-10-05T13:00:00-03:00")))
        val undated = File("src/test/resources/bundle-fixture.json").readText().replace("\"2026-10-04T12:00:00-03:00\"", "\"yesterday\"")
        assertEquals("Knowledge bundle 2026.10.04.", bundleLine(parseBundle(undated, null), now))
    }
}

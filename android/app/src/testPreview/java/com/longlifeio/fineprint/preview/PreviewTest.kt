package com.longlifeio.fineprint.preview

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import com.longlifeio.fineprint.FinePrintApp
import com.longlifeio.fineprint.GLANCE_NOTICE
import com.longlifeio.fineprint.MainActivity
import com.longlifeio.fineprint.REVIEW_UNAVAILABLE
import com.longlifeio.fineprint.SETTINGS_UNAVAILABLE
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.design.parseFixture
import com.longlifeio.fineprint.egress.parseTrackerSignatures
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.glance
import com.longlifeio.fineprint.explain.glanceHeadline
import com.longlifeio.fineprint.review.fingerprint
import com.longlifeio.fineprint.ui.markIntroSeen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The preview flavour (src/preview) as a phone runs it: the real MainActivity over the scan fixture's sample apps
 * and the built-in bundle. It asks for no package list and no network; its apps, tiers, counts and Reviewed
 * fingerprints are the device flavour's on the same fixture; its home says what it is; and it opens no other app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class PreviewTest {

    private val compose = createAndroidComposeRule<MainActivity>()

    // The introduction counts as seen before the activity starts, so it opens on the home.
    @get:Rule val rules: RuleChain = RuleChain
        .outerRule(object : ExternalResource() { override fun before() = markIntroSeen(RuntimeEnvironment.getApplication()) })
        .around(compose)

    private val app get() = compose.activity.application as FinePrintApp

    // What the device flavour's tests read (ScreenshotTest): the fixture, the bundle and the signatures, from disk.
    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())
    private val signatures = parseTrackerSignatures(File("src/main/assets/trackers.json").readText()).trackers.associateBy { it.id }
    private val fixture = parseFixture(File("src/debug/assets/scan-fixture.json").readText())
    private val onDevice: Map<String, Explanation> = fixture.first.associate { it.packageName to explain(it, fixture.second[it.scanKey], bundle, signatures) }

    @Test
    fun itAsksForNoPackageListNoNetworkAndNoLocalNetwork() {
        val pm = app.packageManager
        val requested = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        val none = listOf(Manifest.permission.QUERY_ALL_PACKAGES, Manifest.permission.INTERNET, "android.permission.ACCESS_LOCAL_NETWORK")
        assertEquals(emptyList<String>(), requested.filter { it in none })
        assertEquals("com.longlifeio.fineprint.preview", app.packageName)
        assertEquals("FinePrint preview", app.applicationInfo.loadLabel(pm).toString())
    }

    @Test
    fun itShowsTheFixtureAppsWithTheDeviceBuildsTiersCountsAndFingerprints() {
        val apps = app.session.apps.value.orEmpty()
        val results = app.session.results.value
        val built = app.bundle.state.value
        assertEquals(fixture.first, apps)
        assertEquals(fixture.second, results)
        assertEquals(bundle.version, built.bundle?.version)
        val byId = built.signatures!!.trackers.associateBy { it.id }
        val inPreview = apps.associate { it.packageName to explain(it, results[it.scanKey], built.bundle, byId) }
        for (a in apps) {
            assertEquals(a.packageName, onDevice.getValue(a.packageName).tier, inPreview.getValue(a.packageName).tier)
            assertEquals(a.packageName, fingerprint(a, fixture.second[a.scanKey], bundle), fingerprint(a, results[a.scanKey], built.bundle))
        }
        val counts = glance(apps, inPreview, emptyMap())
        assertEquals(glance(fixture.first, onDevice, emptyMap()), counts)
        // The home: what these apps are, the device build's headline, and a bundle that was never downloaded.
        compose.onNodeWithText(GLANCE_NOTICE!!).assertIsDisplayed()
        compose.onNodeWithText(glanceHeadline(counts)).assertIsDisplayed()
        compose.onNodeWithText("bundle: ${bundle.version}, built in").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/preview-home.png")
    }

    @Test
    fun noAppsSettingsOpenAndTheLineSaysWhy() {
        compose.onNodeWithTag("list").performScrollToNode(hasText("Life360"))
        compose.onNodeWithText("Life360").performClick()
        compose.onNodeWithText("Version 26.37.0", substring = true).assertIsDisplayed() // a scanned sample app keeps its version
        compose.onNodeWithText("Open app settings").assertIsNotEnabled().performClick()
        compose.onAllNodesWithText(SETTINGS_UNAVAILABLE!!).onFirst().assertIsDisplayed()
        compose.onNodeWithTag("detail").performScrollToNode(hasText("Open app settings to change these"))
        compose.onNodeWithText("Open app settings to change these").performClick()
        assertNull("nothing opened", shadowOf(compose.activity).nextStartedActivity)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/preview-detail.png")
    }

    @Test
    fun anAppThatWasNotScannedSaysSoInPlaceOfItsVersion() {
        compose.onNodeWithTag("list").performScrollToNode(hasText("Google Maps"))
        compose.onNodeWithText("Google Maps").performClick()
        compose.onNodeWithText("Not scanned in this preview").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Version", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("APKs", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun theReviewRequestShowsItsDisclosureButCantOpenGitHub() {
        compose.onNodeWithTag("list").performScrollToNode(hasText("Don du Sang")) // no record
        compose.onNodeWithText("Don du Sang").performClick()
        compose.onNodeWithText("Ask FinePrint to review this app").performClick()
        compose.onNodeWithText("This opens GitHub", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Open GitHub").assertIsNotEnabled().performClick()
        compose.onNode(hasText(REVIEW_UNAVAILABLE!!) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        assertNull("nothing opened", shadowOf(compose.activity).nextStartedActivity)
    }

    @Test
    fun aboutSaysTheBundleIsBuiltInAndOffersNoUpdate() {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("About FinePrint").performClick()
        compose.onNodeWithText("Built into this preview; it downloads nothing.").assertIsDisplayed()
        compose.onNodeWithText("bundle: ${bundle.version}, built in").assertIsDisplayed()
        compose.onAllNodesWithText("Update now").fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }
}

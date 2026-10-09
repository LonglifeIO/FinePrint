package com.longlifeio.fineprint.ui

import android.content.Context
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.ScanProgress
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.NO_RECORD_DEFINITION
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The G5 home: sections that remember being opened, "Flagged ✓" for an app you've reviewed, and What changed. */
@RunWith(AndroidJUnit4::class)
class HomeTest {

    @get:Rule val compose = createComposeRule()

    /** Accessibility Test Framework checks (touch targets, contrast, labels) on every action in these tests. */
    @Before fun accessibilityChecks() = compose.enableAccessibilityChecks()

    private val bundle = InstrumentationRegistry.getInstrumentation().context.assets.let { assets ->
        parseBundle(
            assets.open("bundle.json").bufferedReader().use { it.readText() },
            assets.open("jurisdictions.json").bufferedReader().use { it.readText() },
        )
    }

    private fun app(pkg: String, label: String) = InstalledApp(
        packageName = pkg, label = label, versionName = "1.0", versionCode = 1, lastUpdateTime = 0, isSystem = false,
        hasCode = true, apkPaths = listOf("base.apk"),
        permissions = listOf(RequestedPermission("android.permission.ACCESS_FINE_LOCATION", granted = true, dangerous = true)),
    )

    private val life360 = app("com.life360.android.safetymapd", "Life360")
    private val unread = app("org.example.unread", "Unread Example")
    private val scans = mapOf(
        life360.scanKey to TrackerScanResult(listOf(DetectedTracker("fp-arity", "Arity", listOf("Location"), "com.arity.x")), 9, 100, 1, emptyList()),
        unread.scanKey to TrackerScanResult(emptyList(), 1, 10, 1, emptyList()),
    )

    private var opened: InstalledApp? = null

    private fun show(apps: List<InstalledApp>, b: Bundle = bundle, reviews: Map<String, ReviewView> = emptyMap(), open: OpenSections = OpenSections.allOpen()) {
        val explanations = apps.associate { it.packageName to explain(it, scans[it.scanKey], b, emptyMap()) }
        compose.setContent {
            AppListScreen(
                apps = apps, explanations = explanations, reviews = reviews,
                checks = apps.associate { it.packageName to whatYouCanDo(it, explanations.getValue(it.packageName), b.apps[it.packageName], emptyMap(), emptySet()) },
                results = scans, progress = ScanProgress(), includeSystem = false, onIncludeSystemChange = {}, onOpen = { opened = it },
                listState = rememberLazyListState(), bundleLine = "bundle: test", onAbout = {}, onHowToRead = {}, openSections = open,
            )
        }
    }

    private fun stateOf(definition: String) =
        compose.onNodeWithText(definition).fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    @Test
    fun aSectionYouOpenStaysOpenAndAReviewedAppReadsFlaggedWithACheck() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("home-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        show(listOf(life360, unread), reviews = mapOf(life360.packageName to ReviewView(ReviewStatus.REVIEWED, "2026-10-07")), open = OpenSections.of(prefs))

        // Flagged starts open, and Life360, marked reviewed, shows "Flagged ✓".
        compose.onNodeWithText("Tier: Flagged, Reviewed", useUnmergedTree = true).assertExists()
        // Not checked yet starts closed: scrolled to the end, its app isn't there.
        compose.onNodeWithTag("list").performScrollToNode(hasText(NO_RECORD_DEFINITION))
        assertEquals("Collapsed", stateOf(NO_RECORD_DEFINITION))
        compose.onAllNodesWithText("Unread Example").assertCountEquals(0)

        compose.onNodeWithText(NO_RECORD_DEFINITION).performClick()
        assertEquals("Expanded", stateOf(NO_RECORD_DEFINITION))
        compose.onNodeWithTag("list").performScrollToNode(hasText("Unread Example"))
        // Kept on this phone: the next launch opens it too.
        assertTrue(prefs.getBoolean("open:NO_RECORD", false))
        assertTrue(OpenSections.of(prefs).isOpen(null))
        InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences("home-test") // leaves nothing in the app's data
    }

    @Test
    fun whatChangedShowsTheNewestChangeAndOpensItsApp() {
        val changes = app("org.example.changes", "Changes Example")
        show(listOf(changes, unread), b = parseBundle(CHANGES_FIXTURE))
        compose.onNodeWithText("What changed · Changes Example · 2026-09-12").performClick()
        assertEquals(changes, opened)
    }

    @Test
    fun whatChangedIsHiddenWhenNoneOfYourAppsHasAChange() {
        show(listOf(life360, unread))
        compose.onAllNodesWithText("What changed", substring = true).assertCountEquals(0)
        compose.onNodeWithText("At a glance").assertExists()
    }
}

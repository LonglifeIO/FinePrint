package com.longlifeio.fineprint.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.ScanProgress
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Apps that came with the phone: grouped by maker in the System view, and a Google app inheriting Google's policy lines. */
@RunWith(AndroidJUnit4::class)
class SystemAppsTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = InstrumentationRegistry.getInstrumentation().context.assets.let { assets ->
        parseBundle(
            assets.open("bundle.json").bufferedReader().use { it.readText() },
            assets.open("jurisdictions.json").bufferedReader().use { it.readText() },
        )
    }

    private fun preinstalled(pkg: String, label: String) = InstalledApp(
        packageName = pkg, label = label, versionName = "1.0", versionCode = 1, lastUpdateTime = 0, isSystem = true,
        hasCode = true, apkPaths = listOf("base.apk"), permissions = emptyList(),
    )

    private val gmail = preinstalled("com.google.android.gm", "Gmail")
    private val maps = preinstalled("com.google.android.apps.maps", "Maps")
    private val clock = preinstalled("com.android.deskclock", "Clock")
    private val scan = TrackerScanResult(emptyList(), 1, 1, 1, emptyList())

    private fun explanation(a: InstalledApp) = explain(a, scan, bundle, emptyMap())

    @Test
    fun theSystemViewGroupsAppsByMaker() {
        val apps = listOf(gmail, maps, clock)
        compose.setContent {
            FinePrintTheme {
                AppListScreen(
                    apps = apps, explanations = apps.associate { it.packageName to explanation(it) }, reviews = emptyMap(),
                    checks = apps.associate { it.packageName to whatYouCanDo(it, explanation(it), bundle.apps[it.packageName], emptyMap(), emptySet()) },
                    results = emptyMap(), progress = ScanProgress(), includeSystem = true, onIncludeSystemChange = {}, onOpen = {},
                    listState = rememberLazyListState(), bundleLine = "bundle: test", onAbout = {}, onHowToRead = {},
                )
            }
        }
        compose.onNodeWithTag("filter:SYSTEM").performScrollTo().performClick() // the last chip, off-screen to the right
        // Maps has its own record and Gmail inherits; both are Google's. Clock's package name doesn't say who made it.
        compose.onNodeWithText("Google · 2 apps").assertExists()
        compose.onNodeWithText("From Google's privacy policy, which covers these apps.").assertExists()
        compose.onNodeWithTag("list").performScrollToNode(hasText("Other preinstalled apps · 1 app"))
        compose.onNodeWithText("FinePrint can't tell from their package names who made these.").assertExists()
        assertEquals(emptyList<String>(), compose.smallTargetsWhileScrolling("list"))
    }

    @Test
    fun aGoogleAppWithoutARecordShowsGooglesPolicyLines() {
        val e = explanation(gmail)
        compose.setContent {
            FinePrintTheme {
                AppDetailScreen(
                    app = gmail, explanation = e, check = whatYouCanDo(gmail, e, null, bundle.permissions.mapValues { it.value.feeds }, emptySet()),
                    review = ReviewView(ReviewStatus.NOT_REVIEWED), result = scan, signatures = null, bundleVersion = bundle.version,
                    onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> }, buckets = OpenBuckets.allOpen(),
                )
            }
        }
        compose.onNodeWithText("No record yet · from Google's policy").assertExists()
        assertTrue(compose.onAllNodesWithText("From Google's privacy policy, which covers this app.").fetchSemanticsNodes().isNotEmpty())
        assertEquals(emptyList<String>(), compose.smallTargetsWhileScrolling("detail"))
    }
}

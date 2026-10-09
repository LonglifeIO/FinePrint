package com.longlifeio.fineprint.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A law line, drawn from the real reviewed laws: who it binds, under the line and in its Sources sheet. */
@RunWith(AndroidJUnit4::class)
class LawLinesTest {

    @get:Rule val compose = createComposeRule()

    /** Accessibility Test Framework checks (touch targets, contrast, labels) on every action in these tests. */
    @Before fun accessibilityChecks() = compose.enableAccessibilityChecks()

    private val bundle = InstrumentationRegistry.getInstrumentation().context.assets.let { assets ->
        parseBundle(
            assets.open("bundle.json").bufferedReader().use { it.readText() },
            assets.open("jurisdictions.json").bufferedReader().use { it.readText() },
        )
    }

    // Life360 with Arity (Allstate, a US company), so the US laws show under Jurisdictions.
    private val life360 = InstalledApp(
        packageName = "com.life360.android.safetymapd", label = "Life360", versionName = "1.0", versionCode = 1, lastUpdateTime = 0,
        isSystem = false, hasCode = true, apkPaths = listOf("base.apk"),
        permissions = listOf(RequestedPermission("android.permission.ACCESS_FINE_LOCATION", granted = true, dangerous = true)),
        deviceReach = emptyList(),
    )
    private val scan = TrackerScanResult(
        listOf(DetectedTracker("fp-arity", "Arity", listOf("Location"), "com.arity.coreengine.x")),
        dexFiles = 1, classes = 10, durationMs = 1, problems = emptyList(),
    )

    @Test
    fun aLawLineSaysWhoItBindsAndItsSheetSourcesIt() {
        val e = explain(life360, scan, bundle, emptyMap())
        compose.setContent {
            FinePrintTheme {
                AppDetailScreen(
                    app = life360, explanation = e,
                    check = whatYouCanDo(life360, e, bundle.apps[life360.packageName], bundle.permissions.mapValues { it.value.feeds }, emptySet()),
                    review = ReviewView(ReviewStatus.NOT_REVIEWED), result = scan, signatures = null, bundleVersion = bundle.version,
                    onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = null, onClearMark = {}, onTick = { _, _ -> }, buckets = OpenBuckets.allOpen(),
                )
            }
        }
        val detail = compose.onNodeWithTag("detail")
        detail.performScrollToNode(hasText("Tap to show the laws"))
        compose.onNodeWithText("Tap to show the laws").performClick()
        // FISA 702: its short line first, its full text closed under it until Show it in full opens it.
        val fisa = bundle.jurisdictions.getValue("US").laws.single { it.id == "law-us-fisa-702" }
        detail.performScrollToNode(hasText(fisa.short!!))
        compose.onAllNodesWithText(fisa.text).assertCountEquals(0)
        compose.openLawInFull(fisa.short!!)
        detail.performScrollToNode(hasText(fisa.text))
        compose.onNodeWithText(fisa.text).assertExists()
        // Its scope, under its line.
        val scope = fisa.scope!!
        detail.performScrollToNode(hasText(scope.text))
        // Its Sources sheet: the line's 6, the scope's 1 and the Current status note's 6.
        detail.performScrollToNode(hasText("Sources (13)"))
        compose.onNodeWithText("Sources (13)").performClick()
        val sheet = compose.onNodeWithTag("sources")
        sheet.performScrollToNode(hasText(WHO_IT_BINDS))
        compose.onNodeWithText(WHO_IT_BINDS).assertExists()
        sheet.performScrollToNode(hasText(scope.sources.single().title!!))
        compose.onNodeWithText(scope.sources.single().title!!).assertExists()
    }
}

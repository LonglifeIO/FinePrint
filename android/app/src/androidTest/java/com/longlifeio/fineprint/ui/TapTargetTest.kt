package com.longlifeio.fineprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.ScanProgress
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.STALE_NOTE
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fails on any tappable or long-pressable element smaller than 48dp in the list, detail and How to
 * read screens. It checks each element's own layout size, not the touch area Compose expands it to:
 * expanded areas of small neighbours overlap, which is how one citation used to open another.
 */
@RunWith(AndroidJUnit4::class)
class TapTargetTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = InstrumentationRegistry.getInstrumentation().context.assets.let { assets ->
        parseBundle(
            assets.open("bundle.json").bufferedReader().use { it.readText() },
            assets.open("jurisdictions.json").bufferedReader().use { it.readText() },
        )
    }

    private fun app(pkg: String, label: String, granted: List<String> = emptyList(), reach: List<String> = emptyList()) = InstalledApp(
        packageName = pkg, label = label, versionName = "1.0", versionCode = 1, lastUpdateTime = 0, isSystem = false,
        hasCode = true, apkPaths = listOf("base.apk"),
        permissions = granted.map { RequestedPermission(it, granted = true, dangerous = true) } +
            RequestedPermission("android.permission.CAMERA", granted = false, dangerous = true),
        deviceReach = reach,
    )

    private val life360 = app(
        "com.life360.android.safetymapd", "Life360",
        granted = listOf("android.permission.ACCESS_FINE_LOCATION", "com.google.android.gms.permission.AD_ID"),
        reach = listOf("autostart", "background_ble_scan"),
    )
    private val adApp = app("org.example.ads", "Ad Example")
    private val unread = app("org.example.unread", "Unread Example")

    private val scans = mapOf(
        life360.scanKey to TrackerScanResult(
            listOf(
                DetectedTracker("fp-arity", "Arity", listOf("Location"), "com.arity.coreengine.x"),
                DetectedTracker("exodus-312", "Google AdMob", listOf("Advertisement"), "com.google.android.gms.ads.x"),
            ),
            dexFiles = 9, classes = 100, durationMs = 1, problems = emptyList(),
        ),
        adApp.scanKey to TrackerScanResult(
            listOf(DetectedTracker("exodus-312", "Google AdMob", listOf("Advertisement"), "com.google.android.gms.ads.x")),
            dexFiles = 1, classes = 10, durationMs = 1, problems = emptyList(),
        ),
    )

    private fun explanation(a: InstalledApp) = explain(a, scans[a.scanKey], bundle, emptyMap())

    private fun check(a: InstalledApp) =
        whatYouCanDo(a, explanation(a), bundle.apps[a.packageName], bundle.permissions.mapValues { it.value.feeds }, emptySet())

    private val changed = ReviewView(ReviewStatus.CHANGED, "2026-10-01", listOf("new tracker code: Google AdMob"))

    @Test
    fun listScreen() {
        val apps = listOf(life360, adApp, unread)
        compose.setContent {
            FinePrintTheme {
                AppListScreen(
                    apps = apps, explanations = apps.associate { it.packageName to explanation(it) },
                    reviews = mapOf(life360.packageName to changed, adApp.packageName to ReviewView(ReviewStatus.REVIEWED, "2026-10-01")),
                    checks = apps.associate { it.packageName to check(it) }, results = scans,
                    progress = ScanProgress(), includeSystem = false, onIncludeSystemChange = {}, onOpen = {},
                    listState = rememberLazyListState(), bundleLine = "bundle: test", onAbout = {}, onHowToRead = {},
                )
            }
        }
        assertEquals(emptyList<String>(), compose.smallTargetsWhileScrolling("list"))
    }

    @Test
    fun life360DetailScreenWithTheRecordAndEvidenceOpen() {
        compose.setContent {
            FinePrintTheme {
                AppDetailScreen(
                    app = life360, explanation = explanation(life360), check = check(life360), review = changed,
                    result = scans[life360.scanKey], signatures = null, bundleVersion = bundle.version,
                    onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
                )
            }
        }
        openTheRecordAndEvidence(seeAll = false)
        assertEquals(emptyList<String>(), compose.smallTargetsWhileScrolling("detail"))
    }

    @Test
    fun autoCoverageDetailScreen() {
        compose.setContent {
            FinePrintTheme {
                AppDetailScreen(
                    app = adApp, explanation = explanation(adApp), check = check(adApp), review = ReviewView(ReviewStatus.NOT_REVIEWED),
                    result = scans[adApp.scanKey], signatures = null, bundleVersion = bundle.version,
                    onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = null, onClearMark = {}, onTick = { _, _ -> },
                )
            }
        }
        assertEquals(emptyList<String>(), compose.smallTargetsWhileScrolling("detail"))
    }

    /** Google's settlements, none naming Maps: the record's note and three lines about Google. */
    @Test
    fun mapsDetailScreenWithTheRecordOpen() {
        showCurated(app("com.google.android.apps.maps", "Maps", granted = listOf("android.permission.ACCESS_FINE_LOCATION")))
        openTheRecordAndEvidence(seeAll = false)
        assertEquals(emptyList<String>(), compose.smallTargetsWhileScrolling("detail"))
    }

    /** The heaviest record: ten items, all shown. */
    @Test
    fun facebookDetailScreenWithAllOfTheRecordOpen() {
        showCurated(app("com.facebook.katana", "Facebook", granted = listOf("android.permission.ACCESS_FINE_LOCATION")))
        openTheRecordAndEvidence(seeAll = true)
        assertEquals(emptyList<String>(), compose.smallTargetsWhileScrolling("detail"))
    }

    /** Recent changes shows the latest change (a worsening); History, inside On the record, shows both directions. */
    @Test
    fun detailScreenWithChanges() {
        val fixture = parseBundle(CHANGES_FIXTURE)
        val a = app("org.example.changes", "Changes Example")
        val e = explain(a, TrackerScanResult(emptyList(), 1, 1, 1, emptyList()), fixture, emptyMap())
        compose.setContent {
            FinePrintTheme {
                AppDetailScreen(
                    app = a, explanation = e, check = whatYouCanDo(a, e, fixture.apps[a.packageName], emptyMap(), emptySet()),
                    review = ReviewView(ReviewStatus.NOT_REVIEWED), result = null, signatures = null, bundleVersion = fixture.version,
                    onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = null, onClearMark = {}, onTick = { _, _ -> },
                )
            }
        }
        compose.onNodeWithText("Recent changes").assertExists()
        compose.onNodeWithText("Tier: Caution → Flagged").assertExists()
        compose.onNode(hasContentDescription("Change: Worsened", substring = true), useUnmergedTree = true).assertExists()
        val detail = compose.onNodeWithTag("detail")
        detail.performScrollToNode(hasText("On the record · 2 items"))
        compose.onNodeWithText("On the record · 2 items").performClick()
        detail.performScrollToNode(hasText("2025-11-03 · Added a setting to turn off partner sharing."))
        compose.onNode(hasContentDescription("Change: Improved", substring = true), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("2026-09-12 · Now lets partners use your precise location for their own purposes.").assertExists()
        detail.performScrollToIndex(0)
        assertEquals(emptyList<String>(), compose.smallTargetsWhileScrolling("detail"))
    }

    /** TalkBack reads a law line's kind once, "Can compel. A company…", not "Can compel.. A company…". */
    @Test
    fun lawLinesReadTheirKindWithOneFullStop() {
        compose.setContent {
            FinePrintTheme {
                AppDetailScreen(
                    app = life360, explanation = explanation(life360), check = check(life360), review = changed,
                    result = scans[life360.scanKey], signatures = null, bundleVersion = bundle.version,
                    onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
                )
            }
        }
        val detail = compose.onNodeWithTag("detail")
        detail.performScrollToNode(hasText("Tap to show the laws"))
        compose.onNodeWithText("Tap to show the laws").performClick()
        detail.performScrollToNode(hasText("CLOUD Act (18 U.S.C. § 2713)"))
        assertTrue(compose.onAllNodes(hasContentDescription("Can compel. A company", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.onAllNodes(hasContentDescription("..", substring = true), useUnmergedTree = true).assertCountEquals(0)
    }

    /** A law's line says it was repealed, and its Sources sheet heads the law's status "Current status", not the lawsuit heading. */
    @Test
    fun aLawShowsItsCurrentStatus() {
        compose.setContent {
            FinePrintTheme {
                AppDetailScreen(
                    app = life360, explanation = explanation(life360), check = check(life360), review = changed,
                    result = scans[life360.scanKey], signatures = null, bundleVersion = bundle.version,
                    onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
                )
            }
        }
        val detail = compose.onNodeWithTag("detail")
        detail.performScrollToNode(hasText("Tap to show the laws"))
        compose.onNodeWithText("Tap to show the laws").performClick()
        detail.performScrollToNode(hasText("Repealed 2026-06-12; directives issued before then stay in effect until they expire.", substring = true))
        compose.onNodeWithText("Sources (8)").performClick()
        compose.onNodeWithText("Current status").assertExists()
        compose.onAllNodes(hasText("Where the case stands"), useUnmergedTree = true).assertCountEquals(0)
    }

    /** A law line shows when it was last reviewed and, once stale, the same marker and note as a stale record. */
    @Test
    fun aLawLineShowsItsReviewDateAndGoesStaleLikeARecord() {
        val stale = Bundle(bundle.version, bundle.generatedAt, bundle.apps, bundle.trackers, bundle.companies, bundle.permissions,
            bundle.deviceReach, bundle.jurisdictions.mapValues { (_, j) -> j.copy(laws = j.laws.map { it.copy(stale = true) }) })
        compose.setContent {
            FinePrintTheme {
                AppDetailScreen(
                    app = life360, explanation = explain(life360, scans[life360.scanKey], stale, emptyMap()), check = check(life360),
                    review = changed, result = scans[life360.scanKey], signatures = null, bundleVersion = bundle.version,
                    onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
                )
            }
        }
        val detail = compose.onNodeWithTag("detail")
        detail.performScrollToNode(hasText("Tap to show the laws"))
        compose.onNodeWithText("Tap to show the laws").performClick()
        detail.performScrollToNode(hasText("CLOUD Act (18 U.S.C. § 2713)"))
        assertTrue(compose.onAllNodes(hasText("Record last reviewed 2026-10-06.")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText(STALE_NOTE)).fetchSemanticsNodes().isNotEmpty())
        // Life360's own record isn't stale, so a Stale marker here is a law's.
        assertTrue(compose.onAllNodes(hasContentDescription("Stale. ", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun howToReadScreen() {
        compose.setContent { FinePrintTheme { HowToReadScreen(onBack = {}) } }
        assertEquals(emptyList<String>(), compose.smallTargetsWhileScrolling("howto"))
    }

    /** The check itself must catch a small target, or a passing run would prove nothing. */
    @Test
    fun theCheckCatchesASmallTarget() {
        compose.setContent {
            LazyColumn(Modifier.testTag("probe")) {
                item { Box(Modifier.size(20.dp).clickable {}.semantics { contentDescription = "tiny" }) }
                item { Box(Modifier.size(48.dp).clickable {}.semantics { contentDescription = "fine" }) }
            }
        }
        assertEquals(listOf("tiny: 20×20dp"), compose.smallTargetsWhileScrolling("probe"))
    }

    private fun showCurated(a: InstalledApp) {
        val e = explain(a, TrackerScanResult(emptyList(), 1, 1, 1, emptyList()), bundle, emptyMap())
        compose.setContent {
            FinePrintTheme {
                AppDetailScreen(
                    app = a, explanation = e, check = whatYouCanDo(a, e, bundle.apps[a.packageName], bundle.permissions.mapValues { it.value.feeds }, emptySet()),
                    review = ReviewView(ReviewStatus.NOT_REVIEWED), result = null, signatures = null, bundleVersion = bundle.version,
                    onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = null, onClearMark = {}, onTick = { _, _ -> },
                )
            }
        }
    }

    /** Opens Jurisdictions, On the record (and its See all) and Evidence, then goes back to the top for the check. */
    private fun openTheRecordAndEvidence(seeAll: Boolean) {
        val detail = compose.onNodeWithTag("detail")
        detail.performScrollToNode(hasText("Tap to show the laws"))
        compose.onNodeWithText("Tap to show the laws").performClick()
        detail.performScrollToNode(hasText("On the record ·", substring = true))
        compose.onNodeWithText("On the record ·", substring = true).performClick()
        if (seeAll) {
            detail.performScrollToNode(hasText("See all"))
            compose.onNodeWithText("See all").performClick()
        }
        detail.performScrollToNode(hasText("Evidence"))
        compose.onNodeWithText("Evidence").performClick()
        detail.performScrollToIndex(0)
    }
}

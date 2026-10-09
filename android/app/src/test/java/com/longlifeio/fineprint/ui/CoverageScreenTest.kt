package com.longlifeio.fineprint.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.ScanProgress
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.FILTERS
import com.longlifeio.fineprint.explain.ListFilter
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Coverage on the screens: each home row's small label, the filter chips, At a glance's count and the detail line. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class CoverageScreenTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = parseBundle(File("src/test/resources/schema16-fixture.json").readText(), File("src/test/resources/jurisdictions-fixture.json").readText())
    private fun app(pkg: String, label: String) = InstalledApp(pkg, label, "1.0", 1, 0, false, true, emptyList(), emptyList())
    private val apps = listOf(app("com.example.conditional", "Conditional"), app("com.example.regulator", "Regulator"), app("org.example.none", "None"))
    private val scan = TrackerScanResult(emptyList(), 1, 10, 1, emptyList())
    private val explanations = apps.associate { it.packageName to explain(it, scan, bundle, emptyMap(), LocalDate.of(2026, 10, 8)) }

    @Test
    fun theHomeShowsEachAppsCoverageTheChipsAndTheCount() {
        compose.setContent {
            AppListScreen(
                apps = apps, explanations = explanations, reviews = emptyMap(), checks = emptyMap(), results = apps.associate { it.scanKey to scan },
                progress = ScanProgress(3, 3), includeSystem = false, onIncludeSystemChange = {}, onOpen = {}, listState = rememberLazyListState(),
                bundleLine = "bundle: ${bundle.version}", onAbout = {}, onHowToRead = {}, openSections = OpenSections({ _, _ -> true }, { _, _ -> }),
            )
        }
        compose.onNodeWithText("Records: 1 checked, 1 their words only, 1 no record yet.").assertExists()
        compose.onNodeWithTag("list").performScrollToNode(hasText("Checked by FinePrint · 2026-10-08"))
        compose.onNodeWithTag("list").performScrollToNode(hasText("Their words only"))
        // The filters live in the search view, which Filters opens.
        compose.onNodeWithContentDescription(FILTERS).performClick()
        listOf(ListFilter.CHECKED, ListFilter.THEIR_WORDS, ListFilter.NO_RECORD).forEach { compose.onNodeWithTag("filter:${it.name}").assert(hasText(it.label)) }
        compose.onAllNodesWithText("Has record").assertCountEquals(0)
    }

    @Test
    fun theDetailSaysHowFarFinePrintHasLooked() {
        val app = apps[1]
        val e = explanations.getValue(app.packageName)
        compose.setContent {
            AppDetailScreen(
                app = app, explanation = e, check = whatYouCanDo(app, e, bundle.apps[app.packageName], emptyMap(), emptySet()),
                review = ReviewView(ReviewStatus.NOT_REVIEWED), result = scan, signatures = null, bundleVersion = bundle.version, onBack = {},
                onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
            )
        }
        compose.onNodeWithTag("coverage").assert(hasText("Their words only — a reviewer hasn't looked at this app yet"))
    }
}

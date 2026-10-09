package com.longlifeio.fineprint.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
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

/** Schema 1.6 on an app's page: a conditional line names its condition, and a regulator's matter is "not yet decided". */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class Schema16ScreenTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = parseBundle(File("src/test/resources/schema16-fixture.json").readText(), File("src/test/resources/jurisdictions-fixture.json").readText())

    private fun detail(pkg: String) {
        val app = InstalledApp(pkg, pkg, "1.0", 1, 0, false, true, emptyList(), listOf(RequestedPermission("android.permission.ACCESS_FINE_LOCATION", true, true)))
        val scan = TrackerScanResult(emptyList(), 1, 10, 1, emptyList())
        val e = explain(app, scan, bundle, emptyMap(), LocalDate.of(2026, 10, 8))
        val check = whatYouCanDo(app, e, bundle.apps[pkg], emptyMap(), emptySet())
        compose.setContent {
            AppDetailScreen(
                app = app, explanation = e, check = check, review = ReviewView(ReviewStatus.NOT_REVIEWED), result = scan, signatures = null,
                bundleVersion = bundle.version, onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {},
                onTick = { _, _ -> }, buckets = OpenBuckets({ _, _ -> true }, { _, _ -> }),
            )
        }
    }

    @Test
    fun aConditionalLineSaysWhatItDependsOn() {
        detail("com.example.conditional")
        compose.onNodeWithTag("detail").performScrollToNode(hasText("If the developer turns on data sharing. FinePrint can't see that setting."))
    }

    @Test
    fun aRegulatorsMatterIsNotYetDecided() {
        detail("com.example.regulator")
        compose.onNodeWithText("Nothing found beyond running the app").assertExists() // merely filed, the complaint sets no tier
        // On the record opens to the line; its badge speaks as one description: "Status: Alleged (not yet decided). <what Alleged means>".
        compose.onNodeWithTag("detail").performScrollToNode(hasText("On the record", substring = true) and hasClickAction())
        compose.onNode(hasText("On the record", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithTag("detail").performScrollToNode(hasContentDescription("Status: Alleged (not yet decided)", substring = true))
    }
}

package com.longlifeio.fineprint.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.ASK_FOR_REVIEW
import com.longlifeio.fineprint.explain.OPEN_GITHUB
import com.longlifeio.fineprint.explain.REVIEW_DISCLOSURE
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.reviewRequestUrl
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import com.longlifeio.fineprint.reviewOpener
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** The review request on an app's page: the disclosure always comes first, and only Open GitHub opens the browser. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class AskForReviewTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = parseBundle(File("src/test/resources/schema16-fixture.json").readText(), File("src/test/resources/jurisdictions-fixture.json").readText())
    private var opened = 0

    private fun detail(pkg: String) {
        val app = InstalledApp(pkg, pkg, "1.0", 1, 0, false, true, emptyList(), emptyList())
        val scan = TrackerScanResult(emptyList(), 1, 10, 1, emptyList())
        val e = explain(app, scan, bundle, emptyMap(), LocalDate.of(2026, 10, 8))
        compose.setContent {
            AppDetailScreen(
                app = app, explanation = e, check = whatYouCanDo(app, e, bundle.apps[pkg], emptyMap(), emptySet()),
                review = ReviewView(ReviewStatus.NOT_REVIEWED), result = scan, signatures = null, bundleVersion = bundle.version, onBack = {},
                onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
                onAskForReview = { opened++ },
            )
        }
    }

    @Test
    fun theDisclosureComesFirstAndOnlyOpenGitHubOpensTheBrowser() {
        detail("com.example.regulator") // Their words only
        compose.onNodeWithText(ASK_FOR_REVIEW).performClick()
        compose.onNodeWithText(REVIEW_DISCLOSURE).assertExists()
        assertEquals("nothing opens with the disclosure", 0, opened)
        compose.onNodeWithText("Cancel").performClick()
        compose.onAllNodesWithText(REVIEW_DISCLOSURE).assertCountEquals(0)
        assertEquals("Cancel opens nothing", 0, opened)
        compose.onNodeWithText(ASK_FOR_REVIEW).performClick()
        compose.onNodeWithText(OPEN_GITHUB).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun aCheckedAppHasNoRequest() {
        detail("com.example.conditional") // Checked by FinePrint
        compose.onAllNodesWithText(ASK_FOR_REVIEW).assertCountEquals(0)
    }

    @Test
    fun theAppOpensTheFormInTheBrowserAndSendsNothingItself() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val url = reviewRequestUrl("Lean for Instapaper", "com.olivierpayen.leaninstapaper")
        activity.reviewOpener(url)!!.invoke()
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(url, intent.dataString)
    }
}

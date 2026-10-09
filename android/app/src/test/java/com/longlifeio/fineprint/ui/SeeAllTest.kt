package com.longlifeio.fineprint.ui

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.design.parseFixture
import com.longlifeio.fineprint.egress.parseTrackerSignatures
import com.longlifeio.fineprint.explain.GOES_ELSEWHERE
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.finePrint
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The fine print's See all and a place's can show the same words ("See all 16"); TalkBack hears which is which. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class SeeAllTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun eachSeeAllSaysWhatItOpens() {
        val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())
        val signatures = parseTrackerSignatures(File("src/main/assets/trackers.json").readText()).trackers.associateBy { it.id }
        val (apps, scans) = parseFixture(File("src/debug/assets/scan-fixture.json").readText())
        val app = apps.single { it.packageName == "com.life360.android.safetymapd" }
        val e = explain(app, scans[app.scanKey], bundle, signatures)
        val (lines, place) = finePrint(e).size to e.flows.getValue(GOES_ELSEWHERE).size
        assertTrue("both have more than they show first", lines > 4 && place > 4)
        compose.setContent {
            AppDetailScreen(
                app = app, explanation = e, check = whatYouCanDo(app, e, bundle.apps[app.packageName], bundle.permissions.mapValues { it.value.feeds }, emptySet()),
                review = ReviewView(ReviewStatus.NOT_REVIEWED), result = scans[app.scanKey], signatures = null, bundleVersion = bundle.version,
                onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
                buckets = OpenBuckets({ _, default -> default }, { _, _ -> }),
            )
        }
        val page = compose.onNodeWithTag("detail")
        page.performScrollToNode(hasContentDescription("See all $lines in the fine print"))
        page.performScrollToNode(hasContentDescription("See all $place for Goes elsewhere"))
    }
}

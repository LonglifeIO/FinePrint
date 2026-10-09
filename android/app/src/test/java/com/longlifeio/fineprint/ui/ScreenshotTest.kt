package com.longlifeio.fineprint.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.github.takahirom.roborazzi.captureRoboImage
import com.longlifeio.fineprint.explain.ASK_FOR_REVIEW
import com.longlifeio.fineprint.explain.BUCKETS
import com.longlifeio.fineprint.explain.STAYS_HERE
import com.longlifeio.fineprint.explain.Tier
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.design.parseFixture
import com.longlifeio.fineprint.egress.ScanProgress
import com.longlifeio.fineprint.egress.parseTrackerSignatures
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The design review in pictures (G5 stop C): the home, Life360's page, its Sources sheet, How to read,
 * the introduction and franceinfo's review request, light and dark, drawn by Robolectric at the emulator's size (411 × 914dp,
 * 420dpi) and saved to build/outputs/roborazzi. The README's screenshots are copied from here
 * (readmeScreenshots), never captured by hand. The data is real: the bundle, and the test emulator's
 * apps and scans (scan-fixture.json); the bundle line leaves out its age so the images don't change by day.
 */
@OptIn(ExperimentalLayoutApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())
    private val signatures = parseTrackerSignatures(File("src/main/assets/trackers.json").readText()).trackers.associateBy { it.id }
    private val fixture = parseFixture(File("src/debug/assets/scan-fixture.json").readText())
    private val apps = fixture.first
    private val scans = fixture.second
    private val explanations = apps.associate { it.packageName to explain(it, scans[it.scanKey], bundle, signatures) }
    private val checks = apps.associate {
        it.packageName to whatYouCanDo(it, explanations.getValue(it.packageName), bundle.apps[it.packageName], bundle.permissions.mapValues { p -> p.value.feeds }, emptySet())
    }
    /** As on the emulator: Facebook marked reviewed, so its row reads "Flagged ✓". */
    private val reviews = mapOf("com.facebook.katana" to ReviewView(ReviewStatus.REVIEWED, "2026-10-07"))
    private val life360 = apps.single { it.packageName == "com.life360.android.safetymapd" }
    private val franceinfo = apps.single { it.packageName == "fr.francetv.apps.info" }

    /**
     * Saves the screen as <name>-light|dark[-150|-200][-w360|-w800].png, and what TalkBack would read as
     * <name>.txt. [device] is one of the matrix's other sizes (the default is the 411 × 914dp phone).
     */
    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit, scale: Float = 1f, device: Device? = null, then: () -> Unit = {}) {
        device?.let { RuntimeEnvironment.setQualifiers(it.qualifiers) }
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        if (scale != 1f) RuntimeEnvironment.setFontScale(scale)
        compose.setContent(content)
        compose.waitForIdle()
        then()
        compose.mainClock.advanceTimeBy(1_000) // the introduction's glyphs come in over 750ms
        compose.waitForIdle()
        val suffix = (if (dark) "dark" else "light") + (if (scale != 1f) "-${(scale * 100).toInt()}" else "") + (device?.let { "-${it.tag}" } ?: "")
        captureScreenRoboImage("build/outputs/roborazzi/$name-$suffix.png")
        // The merged semantics tree: the items TalkBack moves through and what it reads for each.
        if (!dark && scale == 1f && device == null) File("build/outputs/talkback").apply { mkdirs() }.resolve("$name.txt").writeText(compose.onAllNodes(isRoot()).printToString(Int.MAX_VALUE)) // a sheet is a second window
    }

    /** The matrix's other two sizes: a small phone, and a tablet where cards stop at MAX_CONTENT. */
    enum class Device(val tag: String, val qualifiers: String) {
        SMALL("w360", "w360dp-h780dp-xxhdpi"),
        TABLET("w800", "w800dp-h1280dp-xhdpi"),
    }

    @Composable
    private fun Home() = AppListScreen(
        apps = apps, explanations = explanations, reviews = reviews, checks = checks, results = scans, progress = ScanProgress(),
        includeSystem = false, onIncludeSystemChange = {}, onOpen = {}, listState = rememberLazyListState(),
        bundleLine = "bundle: ${bundle.version}", onAbout = {}, onHowToRead = {},
        // First launch: Flagged and Caution open, nothing kept.
        openSections = OpenSections({ _, default -> default }, { _, _ -> }),
    )

    /** Scrolls to the hero's Sources row (below the fold) and opens the sheet. */
    private fun openHeroSources() {
        compose.onNodeWithTag("detail").performScrollToNode(hasText("Sources (", substring = true))
        compose.onAllNodesWithText("Sources (", substring = true).onFirst().performClick()
    }

    @Composable
    private fun Life360() = AppDetailScreen(
        app = life360, explanation = explanations.getValue(life360.packageName), check = checks.getValue(life360.packageName),
        review = ReviewView(ReviewStatus.NOT_REVIEWED), result = scans[life360.scanKey], signatures = null, bundleVersion = bundle.version,
        onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
        buckets = OpenBuckets({ _, default -> default }, { _, _ -> }),
    )

    /** franceinfo has no record, so its page asks for a reviewer: the disclosure, open. */
    @Composable
    private fun Franceinfo() = AppDetailScreen(
        app = franceinfo, explanation = explanations.getValue(franceinfo.packageName), check = checks.getValue(franceinfo.packageName),
        review = ReviewView(ReviewStatus.NOT_REVIEWED), result = scans[franceinfo.scanKey], signatures = null, bundleVersion = bundle.version,
        onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
        buckets = OpenBuckets({ _, default -> default }, { _, _ -> }), onAskForReview = {},
    )

    private fun openReviewRequest() {
        compose.onNodeWithTag("detail").performScrollToNode(hasText(ASK_FOR_REVIEW))
        compose.onNodeWithText(ASK_FOR_REVIEW).performClick()
    }

    @Test fun homeLight() = shoot("home", dark = false, { Home() })
    @Test fun homeDark() = shoot("home", dark = true, { Home() })
    @Test fun detailLight() = shoot("detail", dark = false, { Life360() })
    @Test fun detailDark() = shoot("detail", dark = true, { Life360() })
    // The hero's Sources row: the listing and the fine print's own sources.
    @Test fun sourcesLight() = shoot("sources", dark = false, { Life360() }) { openHeroSources() }
    @Test fun sourcesDark() = shoot("sources", dark = true, { Life360() }) { openHeroSources() }
    @Test fun askForReviewLight() = shoot("ask-for-review", dark = false, { Franceinfo() }) { openReviewRequest() }
    @Test fun askForReviewDark() = shoot("ask-for-review", dark = true, { Franceinfo() }) { openReviewRequest() }
    @Test fun askForReviewLargest() = shoot("ask-for-review", dark = false, { Franceinfo() }, scale = 2f) { openReviewRequest() }
    @Test fun askForReviewSmall() = shoot("ask-for-review", dark = false, { Franceinfo() }, device = Device.SMALL) { openReviewRequest() }
    @Test fun howToReadLight() = shoot("how-to-read", dark = false, { FinePrintTheme { HowToReadScreen(onBack = {}) } })
    @Test fun howToReadDark() = shoot("how-to-read", dark = true, { FinePrintTheme { HowToReadScreen(onBack = {}) } })
    // The introduction's three pages, as intro-1, intro-2 and intro-3.
    @Test fun intro1Light() = shoot("intro-1", dark = false, { OnboardingScreen(onDone = {}) })
    @Test fun intro1Dark() = shoot("intro-1", dark = true, { OnboardingScreen(onDone = {}) })
    @Test fun intro2Light() = shoot("intro-2", dark = false, { OnboardingScreen(onDone = {}, startPage = 1) })
    @Test fun intro2Dark() = shoot("intro-2", dark = true, { OnboardingScreen(onDone = {}, startPage = 1) })
    @Test fun intro3Light() = shoot("intro-3", dark = false, { OnboardingScreen(onDone = {}, startPage = 2) })
    @Test fun intro3Dark() = shoot("intro-3", dark = true, { OnboardingScreen(onDone = {}, startPage = 2) })

    // Large text: 150% (the review size) and 200% (the brief's test size).
    @Test fun intro1Large() = shoot("intro-1", dark = false, { OnboardingScreen(onDone = {}) }, scale = 1.5f)
    @Test fun intro2Large() = shoot("intro-2", dark = false, { OnboardingScreen(onDone = {}, startPage = 1) }, scale = 1.5f)
    @Test fun intro3Large() = shoot("intro-3", dark = false, { OnboardingScreen(onDone = {}, startPage = 2) }, scale = 1.5f)
    @Test fun intro1Largest() = shoot("intro-1", dark = false, { OnboardingScreen(onDone = {}) }, scale = 2f)
    @Test fun intro3Largest() = shoot("intro-3", dark = false, { OnboardingScreen(onDone = {}, startPage = 2) }, scale = 2f)
    @Test fun homeLargest() = shoot("home", dark = false, { Home() }, scale = 2f)
    @Test fun detailLargest() = shoot("detail", dark = false, { Life360() }, scale = 2f)
    @Test fun sourcesLargest() = shoot("sources", dark = false, { Life360() }, scale = 2f) { openHeroSources() }
    @Test fun homeLarge() = shoot("home", dark = false, { Home() }, scale = 1.5f)

    // The device matrix: 360 × 780dp and 800 × 1280dp beside the 411 × 914dp above.
    @Test fun homeSmall() = shoot("home", dark = false, { Home() }, device = Device.SMALL)
    @Test fun homeTablet() = shoot("home", dark = false, { Home() }, device = Device.TABLET)
    @Test fun detailSmall() = shoot("detail", dark = false, { Life360() }, device = Device.SMALL)
    @Test fun detailTablet() = shoot("detail", dark = false, { Life360() }, device = Device.TABLET)
    @Test fun sourcesSmall() = shoot("sources", dark = false, { Life360() }, device = Device.SMALL) { openHeroSources() }
    @Test fun sourcesTablet() = shoot("sources", dark = false, { Life360() }, device = Device.TABLET) { openHeroSources() }
    @Test fun howToReadSmall() = shoot("how-to-read", dark = false, { FinePrintTheme { HowToReadScreen(onBack = {}) } }, device = Device.SMALL)
    @Test fun howToReadTablet() = shoot("how-to-read", dark = false, { FinePrintTheme { HowToReadScreen(onBack = {}) } }, device = Device.TABLET)
    @Test fun introSmall() = shoot("intro-1", dark = false, { OnboardingScreen(onDone = {}) }, device = Device.SMALL)
    @Test fun introTablet() = shoot("intro-1", dark = false, { OnboardingScreen(onDone = {}) }, device = Device.TABLET)

    // The chips alone, at the shipped tint: on a card and on the page.
    @Composable
    private fun Chips() = FieldNotesTheme {
        val p = LocalPalette.current
        @Composable
        fun Rows() {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                TierChip(Tier.FLAGGED, count = 3, noun = "apps")
                TierChip(Tier.CAUTION, count = 3, noun = "apps")
                TierChip(Tier.EXPECTED, count = 1, noun = "apps", reviewed = true)
                TierChip(null, count = 2, noun = "apps")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                BUCKETS.forEach { BucketChip(it, if (it == STAYS_HERE) 6 else 2, "apps") }
            }
        }
        Column(Modifier.background(p.surface).padding(Space.l).testTag("chips"), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Eyebrow("On a card")
            Column(Modifier.clip(RoundedCornerShape(Corner.card)).background(p.card).padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) { Rows() }
            Eyebrow("On the page")
            Rows()
        }
    }

    private fun chips(dark: Boolean, scale: Float = 1f) {
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        if (scale != 1f) RuntimeEnvironment.setFontScale(scale)
        compose.setContent { Chips() }
        compose.waitForIdle()
        val suffix = (if (dark) "dark" else "light") + if (scale != 1f) "-${(scale * 100).toInt()}" else ""
        compose.onNodeWithTag("chips").captureRoboImage("build/outputs/roborazzi/chips-$suffix.png")
    }

    @Test fun chipsLight() = chips(dark = false)
    @Test fun chipsDark() = chips(dark = true)
    @Test fun chipsLarge() = chips(dark = false, scale = 1.5f)
}

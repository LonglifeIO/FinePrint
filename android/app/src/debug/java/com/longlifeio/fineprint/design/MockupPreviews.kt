package com.longlifeio.fineprint.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.TRACKERS_ASSET
import com.longlifeio.fineprint.egress.parseTrackerSignatures

/*
 * The three directions as @Previews: home and detail, light and dark, and the detail at 150% font
 * scale. The data is real: bundle.json and jurisdictions.json from bundle/ (copied into the debug assets
 * at build time by debugBundleAssets) and the test emulator's scans from scan-fixture.json, so each preview matches its PNG from
 * MockupActivity, app icons aside (a preview has no PackageManager to load them from).
 */

@Composable
private fun rememberPreviewData(): MockupData {
    val assets = LocalContext.current.assets
    return remember {
        fun text(name: String) = assets.open(name).bufferedReader().use { it.readText() }
        val (apps, scans) = parseFixture(text(FIXTURE_ASSET))
        val signatures = parseTrackerSignatures(text(TRACKERS_ASSET)).trackers.associateBy { it.id }
        checkNotNull(mockupData(apps, scans, parseBundle(text("bundle.json"), text("jurisdictions.json")), signatures) { null }) {
            "$FIXTURE_ASSET has no Life360 scan"
        }
    }
}

/** One screen in one look, on the palette's surface; it scrolls in interactive mode and on a device. */
@Composable
private fun Mock(direction: String, screen: String, dark: Boolean) {
    val data = rememberPreviewData()
    val (palette, fonts) = look(direction, dark)
    MockupTheme(palette, fonts) {
        Box(Modifier.fillMaxSize().background(palette.surface).verticalScroll(rememberScrollState())) { MockupScreen(direction, screen, data) }
    }
}

// 411dp is the emulator's width (1,080px at 420dpi), as in the PNGs; each height clears its screen.
private const val W = 411

@Preview(name = "home · light", group = "Ledger cards", widthDp = W, heightDp = 1600)
@Composable fun LedgerHomeLight() = Mock("ledger", "home", dark = false)
@Preview(name = "home · dark", group = "Ledger cards", widthDp = W, heightDp = 1600)
@Composable fun LedgerHomeDark() = Mock("ledger", "home", dark = true)
@Preview(name = "detail · light", group = "Ledger cards", widthDp = W, heightDp = 2500)
@Composable fun LedgerDetailLight() = Mock("ledger", "detail", dark = false)
@Preview(name = "detail · dark", group = "Ledger cards", widthDp = W, heightDp = 2500)
@Composable fun LedgerDetailDark() = Mock("ledger", "detail", dark = true)
@Preview(name = "detail · light · 150%", group = "Ledger cards", widthDp = W, heightDp = 3800, fontScale = 1.5f)
@Composable fun LedgerDetailLarge() = Mock("ledger", "detail", dark = false)

@Preview(name = "home · light", group = "Calm dashboard", widthDp = W, heightDp = 1600)
@Composable fun DashboardHomeLight() = Mock("dashboard", "home", dark = false)
@Preview(name = "home · dark", group = "Calm dashboard", widthDp = W, heightDp = 1600)
@Composable fun DashboardHomeDark() = Mock("dashboard", "home", dark = true)
@Preview(name = "detail · light", group = "Calm dashboard", widthDp = W, heightDp = 1400)
@Composable fun DashboardDetailLight() = Mock("dashboard", "detail", dark = false)
@Preview(name = "detail · dark", group = "Calm dashboard", widthDp = W, heightDp = 1400)
@Composable fun DashboardDetailDark() = Mock("dashboard", "detail", dark = true)
@Preview(name = "detail · light · 150%", group = "Calm dashboard", widthDp = W, heightDp = 2000, fontScale = 1.5f)
@Composable fun DashboardDetailLarge() = Mock("dashboard", "detail", dark = false)

@Preview(name = "home · light", group = "Label-first", widthDp = W, heightDp = 1500)
@Composable fun LabelHomeLight() = Mock("label", "home", dark = false)
@Preview(name = "home · dark", group = "Label-first", widthDp = W, heightDp = 1500)
@Composable fun LabelHomeDark() = Mock("label", "home", dark = true)
@Preview(name = "detail · light", group = "Label-first", widthDp = W, heightDp = 1600)
@Composable fun LabelDetailLight() = Mock("label", "detail", dark = false)
@Preview(name = "detail · dark", group = "Label-first", widthDp = W, heightDp = 1600)
@Composable fun LabelDetailDark() = Mock("label", "detail", dark = true)
@Preview(name = "detail · light · 150%", group = "Label-first", widthDp = W, heightDp = 2300, fontScale = 1.5f)
@Composable fun LabelDetailLarge() = Mock("label", "detail", dark = false)

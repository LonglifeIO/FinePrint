package com.longlifeio.fineprint

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import com.longlifeio.fineprint.preview.BuiltInBundle
import com.longlifeio.fineprint.preview.FixtureScanner
import com.longlifeio.fineprint.preview.PreviewTheme
import com.longlifeio.fineprint.preview.ThemeSwitch

/*
 * The preview flavour: a debug build to browse on any phone as the app evolves. Its apps are the scan fixture's
 * sample apps and its bundle is built in (assets/preview/, copied at build time); it never reads this phone's
 * apps, not even their icons, and its manifest has no network or package-query permission. The device flavour
 * (src/device) defines the same names for the app itself.
 */

typealias AppScanner = FixtureScanner
typealias BundleSource = BuiltInBundle

internal fun FinePrintApp.newBundleSource(): BundleSource = BuiltInBundle(this)
internal fun FinePrintApp.newScanner(): AppScanner = FixtureScanner(this) { bundle.state.value.signatures }

val GLANCE_NOTICE: String? = "Preview — sample apps, not your phone."

/** Under the notice: the theme switch (System, Light, Dark). */
val GLANCE_SWITCH: (@Composable () -> Unit)? = { ThemeSwitch() }

/** The screens, in the switch's choice of theme. */
@Composable
fun ThemeFrame(content: @Composable () -> Unit) = PreviewTheme(content)

val INTRO_WHAT_IT_READS: String? = "This preview shows sample apps. It reads nothing on this phone."

val INTRO_ITS_RECORDS: String? = "This preview has its records built in. The app itself downloads them whole, never one app at a time."

/** The sample apps may not be on this phone, and the preview doesn't look: no settings to open. */
@Suppress("UNUSED_PARAMETER", "UnusedReceiverParameter")
fun Activity.settingsOpener(packageName: String): (() -> Unit)? = null

val SETTINGS_UNAVAILABLE: String? = "Not available in the preview"

/** The disclosure still shows; Open GitHub is off, with [REVIEW_UNAVAILABLE]. */
@Suppress("UNUSED_PARAMETER", "UnusedReceiverParameter")
fun Activity.reviewOpener(url: String): (() -> Unit)? = null

val REVIEW_UNAVAILABLE: String? = "Not available in the preview"

/** The sample apps show their initials, never this phone's icons. */
@Suppress("UNUSED_PARAMETER")
fun appIcon(pm: PackageManager, packageName: String): Drawable? = null

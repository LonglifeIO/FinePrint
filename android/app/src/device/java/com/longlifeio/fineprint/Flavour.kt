package com.longlifeio.fineprint

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.longlifeio.fineprint.bundle.BundleSession
import com.longlifeio.fineprint.bundle.BundleState
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.ScanSession
import com.longlifeio.fineprint.ui.bundleStatus

/*
 * The device flavour, the app itself: it scans this phone's apps and downloads the bundle. The preview flavour
 * (src/preview) defines the same names over its sample apps and built-in bundle; shared code uses only these
 * names, so which build it is in is settled when it compiles.
 */

typealias AppScanner = ScanSession
typealias BundleSource = BundleSession

internal fun FinePrintApp.newBundleSource(): BundleSource = BundleSession(this)
internal fun FinePrintApp.newScanner(): AppScanner = ScanSession(this) { bundle.state.value.signatures }

/** A line under "At a glance"; none here, where the apps are your own. */
val GLANCE_NOTICE: String? = null

/** The introduction's line on what FinePrint reads, where a build says it differently; this one keeps its own. */
val INTRO_WHAT_IT_READS: String? = null

/** In place of an app's version and APK count in its header; never here, where every app is installed and scanned. */
@Suppress("UNUSED_PARAMETER", "UnusedReceiverParameter")
fun ScanSession.installLine(app: InstalledApp): String? = null

/** Opens an app's Android settings; every app listed is installed here, so it always can. */
fun Activity.settingsOpener(packageName: String): (() -> Unit)? = { openAppSettings(packageName) }

/** Why an app's settings can't be opened, when they can't; never here. */
val SETTINGS_UNAVAILABLE: String? = null

/** "bundle: 2026.10.04, 0 days old", on At a glance and in About. */
fun BundleSession.status(state: BundleState): String = bundleStatus(state)

/** Where the bundle comes from, in About. */
val BundleSession.origin: String get() = "Downloaded whole from $baseUrl; the app never asks a server about a particular app."

/** About's "Update now". */
val BundleSession.update: (() -> Unit)? get() = ::refreshNow

/** An app's own icon, from this phone. */
fun appIcon(pm: PackageManager, packageName: String): Drawable? = pm.getApplicationIcon(packageName)

private fun Activity.openAppSettings(packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) { // some OEM builds strip the app-info screen
        Toast.makeText(this, "This device has no app settings screen to open.", Toast.LENGTH_LONG).show()
    }
}

package com.longlifeio.fineprint

import android.app.Application
import android.os.Build
import android.os.StrictMode
import com.longlifeio.fineprint.review.ReviewStore
import dalvik.system.ZipPathValidator
import java.io.File

/**
 * Owns the session, so scan results outlive activity recreation for as long as the process lives. Which scanner
 * and bundle it builds is the flavour's (Flavour.kt): this phone's apps and the downloaded bundle, or the preview's.
 */
class FinePrintApp : Application() {
    val bundle: BundleSource by lazy { newBundleSource() }
    val session: AppScanner by lazy { newScanner() }
    /** Your Reviewed marks and ticks: in the no-backup directory, and never sent anywhere. */
    val reviews: ReviewStore by lazy { ReviewStore(File(noBackupFilesDir, "reviews.json")) }

    override fun onCreate() {
        super.onCreate()
        // Apps targeting 34+ get a check that makes ZipFile reject any APK with an entry named like
        // "../x". It guards extraction to disk, which FinePrint never does, and would otherwise
        // hide such an app's code from the scan.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ZipPathValidator.clearCallback()
        if (BuildConfig.DEBUG) {
            // Proof of the one-network-call rule: BundleClient tags its sockets, so StrictMode logs
            // any other socket this process opens ("untagged socket") to logcat.
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectUntaggedSockets().penaltyLog().build())
        }
    }
}

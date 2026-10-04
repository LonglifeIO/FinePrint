package com.longlifeio.fineprint

import android.app.Application
import android.os.Build
import com.longlifeio.fineprint.egress.ScanSession
import dalvik.system.ZipPathValidator

/** Owns the session, so scan results outlive activity recreation for as long as the process lives. */
class FinePrintApp : Application() {
    val session: ScanSession by lazy { ScanSession(this) }

    override fun onCreate() {
        super.onCreate()
        // Apps targeting 34+ get a check that makes ZipFile reject any APK with an entry named like
        // "../x". It guards extraction to disk, which Fine Print never does, and would otherwise
        // hide such an app's code from the scan.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ZipPathValidator.clearCallback()
    }
}

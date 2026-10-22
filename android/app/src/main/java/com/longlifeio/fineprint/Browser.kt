package com.longlifeio.fineprint

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Hands [url] to the browser (an ACTION_VIEW intent); the app itself sends nothing. With no browser, a toast says so. */
fun Activity.openInBrowser(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "There's no browser on this phone to open GitHub.", Toast.LENGTH_LONG).show()
    }
}

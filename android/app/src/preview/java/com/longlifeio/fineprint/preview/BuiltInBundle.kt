package com.longlifeio.fineprint.preview

import android.content.Context
import com.longlifeio.fineprint.bundle.BUNDLE_FILE
import com.longlifeio.fineprint.bundle.BundleState
import com.longlifeio.fineprint.bundle.JURISDICTIONS_FILE
import com.longlifeio.fineprint.bundle.TRACKERS_FILE
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.parseTrackerSignatures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The preview's bundle: the bundle's three files as built into its assets (assets/preview/, copied from bundle/
 * at build time), read once. It never constructs the downloader, so there is nothing to fetch or update.
 */
class BuiltInBundle(context: Context) {
    val state: StateFlow<BundleState> = MutableStateFlow(read(context))

    fun refreshOnce() = Unit

    /** "bundle: 2026.10.08, built in", on At a glance and in About. */
    fun status(state: BundleState): String = "bundle: ${state.bundle?.version ?: "none"}, built in"

    /** Where the bundle comes from, in About. */
    val origin = "Built into this preview; it downloads nothing."

    /** No "Update now": there is nothing to download. */
    val update: (() -> Unit)? = null

    private fun read(context: Context): BundleState {
        fun text(name: String) = context.assets.open("preview/$name").bufferedReader().use { it.readText() }
        return BundleState(parseBundle(text(BUNDLE_FILE), text(JURISDICTIONS_FILE)), parseTrackerSignatures(text(TRACKERS_FILE)))
    }
}

package com.longlifeio.fineprint.design

import android.content.res.Configuration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.longlifeio.fineprint.FinePrintApp
import com.longlifeio.fineprint.bundle.BundleState
import com.longlifeio.fineprint.bundle.StoreTagline
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.ScanProgress
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.egress.TrackerSignatures
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.WhatYouCanDo
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import com.longlifeio.fineprint.review.fingerprint
import com.longlifeio.fineprint.review.reviewView
import com.longlifeio.fineprint.ui.AppDetailScreen
import com.longlifeio.fineprint.ui.AppListScreen
import com.longlifeio.fineprint.ui.OpenBuckets
import com.longlifeio.fineprint.ui.rememberOpenBuckets
import com.longlifeio.fineprint.ui.bundleStatus
import org.json.JSONObject

/*
 * Debug only (G5 step 2): the app's real screens, drawn by MockupActivity for review screenshots at
 * full length, in light or dark and at any font scale, without touching the phone's settings. The
 * state is worked out as MainActivity does, from the same session, bundle and review marks.
 */

class RealState(
    val apps: List<InstalledApp>?,
    val results: Map<String, TrackerScanResult>,
    val progress: ScanProgress,
    val signatures: TrackerSignatures?,
    val bundle: BundleState,
    val explanations: Map<String, Explanation>,
    val reviews: Map<String, ReviewView>,
    val checks: Map<String, WhatYouCanDo>,
)

@Composable
fun rememberRealState(app: FinePrintApp): RealState {
    val apps by app.session.apps.collectAsState()
    val results by app.session.results.collectAsState()
    val progress by app.session.progress.collectAsState()
    val signatures by app.session.signatures.collectAsState()
    val state by app.bundle.state.collectAsState()
    val marks by app.reviews.entries.collectAsState()
    val bundle = state.bundle
    val explanations = remember(apps, results, bundle, signatures) {
        val byId = signatures?.trackers.orEmpty().associateBy { it.id }
        apps.orEmpty().associate { it.packageName to explain(it, results[it.scanKey], bundle, byId) }
    }
    val reviews = remember(apps, results, bundle, marks, signatures) {
        val names = signatures?.trackers.orEmpty().associate { it.id to it.name }
        apps.orEmpty().associate { it.packageName to reviewView(marks[it.packageName]?.mark, fingerprint(it, results[it.scanKey], bundle), names) }
    }
    val checks = remember(apps, explanations, marks, bundle) {
        val feeds = bundle?.permissions?.mapValues { it.value.feeds }.orEmpty()
        apps.orEmpty().associate {
            it.packageName to whatYouCanDo(it, explanations.getValue(it.packageName), bundle?.apps?.get(it.packageName), feeds, marks[it.packageName]?.ticked.orEmpty())
        }
    }
    return RealState(apps, results, progress, signatures, state, explanations, reviews, checks)
}

/** The real home, with this phone's apps, scans, bundle and Reviewed marks; tier sections as you left them. */
@Composable
fun RealHome(s: RealState) {
    AppListScreen(
        apps = s.apps, explanations = s.explanations, reviews = s.reviews, checks = s.checks, results = s.results, progress = s.progress,
        includeSystem = false, onIncludeSystemChange = {}, onOpen = {}, listState = rememberLazyListState(),
        bundleLine = bundleStatus(s.bundle), onAbout = {}, onHowToRead = {},
    )
}

/**
 * The real detail screen for [pkg]. [draftTaglines] stand in for store taglines the bundle doesn't have
 * yet, so the hero can be reviewed before its JSON is approved; they never reach the bundle from here.
 */
@Composable
fun RealDetail(s: RealState, pkg: String, draftTaglines: Map<String, StoreTagline>, allBuckets: Boolean = false) {
    val app = s.apps?.find { it.packageName == pkg } ?: return
    val e = s.explanations.getValue(pkg).let { it.copy(storeTagline = it.storeTagline ?: draftTaglines[pkg]) }
    AppDetailScreen(
        app = app, explanation = e, check = s.checks.getValue(pkg), review = s.reviews[pkg] ?: ReviewView(ReviewStatus.NOT_REVIEWED),
        result = s.results[app.scanKey], signatures = s.signatures, bundleVersion = s.bundle.bundle?.version,
        onBack = {}, onOpenSettings = {}, onHowToRead = {}, onMarkReviewed = {}, onClearMark = {}, onTick = { _, _ -> },
        buckets = if (allBuckets) OpenBuckets.allOpen() else rememberOpenBuckets(),
    )
}

/** pipeline/drafts/store-taglines.json's shape: package -> {store_tagline: {text, source_url, as_of}}. */
fun parseDraftTaglines(json: String): Map<String, StoreTagline> {
    val root = JSONObject(json)
    return root.keys().asSequence().associateWith { pkg ->
        root.getJSONObject(pkg).getJSONObject("store_tagline").let { StoreTagline(it.getString("text"), it.getString("source_url"), it.getString("as_of")) }
    }
}

/** The configuration with night mode forced on or off, so isSystemInDarkTheme() answers [dark]. */
fun Configuration.withNight(dark: Boolean): Configuration = Configuration(this).apply {
    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
}

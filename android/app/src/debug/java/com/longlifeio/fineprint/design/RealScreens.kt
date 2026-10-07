package com.longlifeio.fineprint.design

import android.content.res.Configuration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.longlifeio.fineprint.FinePrintApp
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.fingerprint
import com.longlifeio.fineprint.review.reviewView
import com.longlifeio.fineprint.ui.AppListScreen
import com.longlifeio.fineprint.ui.bundleStatus

/*
 * Debug only (G5 step 2): the app's real screens, drawn by MockupActivity for review screenshots at
 * full length, in light or dark and at any font scale, without touching the phone's settings. The
 * state is worked out as MainActivity does, from the same session, bundle and review marks.
 */

/** The real home, with this phone's apps, scans, bundle and Reviewed marks; tier sections as you left them. */
@Composable
fun RealHome(app: FinePrintApp) {
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
    AppListScreen(
        apps = apps, explanations = explanations, reviews = reviews, checks = checks, results = results, progress = progress,
        includeSystem = false, onIncludeSystemChange = {}, onOpen = {}, listState = rememberLazyListState(),
        bundleLine = bundleStatus(state), onAbout = {}, onHowToRead = {},
    )
}

/** The configuration with night mode forced on or off, so isSystemInDarkTheme() answers [dark]. */
fun Configuration.withNight(dark: Boolean): Configuration = Configuration(this).apply {
    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
}

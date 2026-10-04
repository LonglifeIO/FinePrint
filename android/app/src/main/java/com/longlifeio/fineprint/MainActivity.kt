package com.longlifeio.fineprint

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.longlifeio.fineprint.egress.ScanSession
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.whatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import com.longlifeio.fineprint.review.fingerprint
import com.longlifeio.fineprint.review.reviewView
import com.longlifeio.fineprint.ui.AboutScreen
import com.longlifeio.fineprint.ui.AppDetailScreen
import com.longlifeio.fineprint.ui.AppListScreen
import com.longlifeio.fineprint.ui.FinePrintTheme
import com.longlifeio.fineprint.ui.HowToReadScreen
import com.longlifeio.fineprint.ui.bundleStatus
import java.time.OffsetDateTime

class MainActivity : ComponentActivity() {
    private val session: ScanSession get() = (application as FinePrintApp).session
    private val bundleSession get() = (application as FinePrintApp).bundle
    private val reviewStore get() = (application as FinePrintApp).reviews

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FinePrintTheme {
                val apps by session.apps.collectAsState()
                val results by session.results.collectAsState()
                val progress by session.progress.collectAsState()
                val signatures by session.signatures.collectAsState()
                val includeSystem by session.includeSystem.collectAsState()
                val bundleState by bundleSession.state.collectAsState()
                val marks by reviewStore.entries.collectAsState()
                var showAbout by rememberSaveable { mutableStateOf(false) }
                var showHowTo by rememberSaveable { mutableStateOf(false) }
                val listState = rememberLazyListState() // hoisted so the list keeps its place
                var openPackage by rememberSaveable { mutableStateOf<String?>(null) }
                val open = openPackage?.let { name -> apps?.find { it.packageName == name } }
                // Every app's explanation (and so its tier) is worked out on the device, from the scan and the bundle.
                val explanations = remember(apps, results, bundleState.bundle, signatures) {
                    val byId = signatures?.trackers.orEmpty().associateBy { it.id }
                    apps.orEmpty().associate { it.packageName to explain(it, results[it.scanKey], bundleState.bundle, byId) }
                }
                // Your marks and ticks (on this phone only) against what the app looks like now.
                val reviews = remember(apps, results, bundleState.bundle, marks, signatures) {
                    val names = signatures?.trackers.orEmpty().associate { it.id to it.name }
                    apps.orEmpty().associate {
                        it.packageName to reviewView(marks[it.packageName]?.mark, fingerprint(it, results[it.scanKey], bundleState.bundle), names)
                    }
                }
                val checks = remember(apps, explanations, marks, bundleState.bundle) {
                    val feeds = bundleState.bundle?.permissions?.mapValues { it.value.feeds }.orEmpty()
                    apps.orEmpty().associate {
                        it.packageName to whatYouCanDo(
                            it, explanations.getValue(it.packageName), bundleState.bundle?.apps?.get(it.packageName), feeds,
                            marks[it.packageName]?.ticked.orEmpty(),
                        )
                    }
                }
                when {
                    showHowTo -> {
                        BackHandler { showHowTo = false }
                        HowToReadScreen(onBack = { showHowTo = false })
                    }
                    showAbout -> {
                        BackHandler { showAbout = false }
                        AboutScreen(bundleState, bundleSession.baseUrl, onBack = { showAbout = false }, onRefresh = bundleSession::refreshNow)
                    }
                    open == null -> AppListScreen(
                        apps = apps,
                        explanations = explanations,
                        reviews = reviews,
                        checks = checks,
                        results = results,
                        progress = progress,
                        includeSystem = includeSystem,
                        onIncludeSystemChange = session::setIncludeSystem,
                        onOpen = { openPackage = it.packageName },
                        listState = listState,
                        bundleLine = bundleStatus(bundleState),
                        onAbout = { showAbout = true },
                        onHowToRead = { showHowTo = true },
                    )
                    else -> {
                        BackHandler { openPackage = null }
                        LaunchedEffect(open.scanKey) { session.scanNow(open) }
                        val explanation = explanations[open.packageName]
                            ?: explain(open, results[open.scanKey], bundleState.bundle, signatures?.trackers.orEmpty().associateBy { it.id })
                        val now = fingerprint(open, results[open.scanKey], bundleState.bundle)
                        AppDetailScreen(
                            app = open,
                            explanation = explanation,
                            check = checks[open.packageName] ?: whatYouCanDo(open, explanation, null, emptyMap(), emptySet()),
                            review = reviews[open.packageName] ?: ReviewView(ReviewStatus.NOT_REVIEWED),
                            result = results[open.scanKey],
                            signatures = signatures,
                            bundleVersion = bundleState.bundle?.version,
                            onBack = { openPackage = null },
                            onOpenSettings = { openAppSettings(open.packageName) },
                            onHowToRead = { showHowTo = true },
                            onMarkReviewed = now?.let { fp -> { reviewStore.markReviewed(open.packageName, fp, OffsetDateTime.now().toString()) } },
                            onClearMark = { reviewStore.clearMark(open.packageName) },
                            onTick = { id, ticked -> reviewStore.setTicked(open.packageName, id, ticked) },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        session.refresh() // picks up apps installed or updated while we were away
        bundleSession.refreshOnce() // the one network call: download the bundle, once per launch
    }

    private fun openAppSettings(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) { // some OEM builds strip the app-info screen
            Toast.makeText(this, "This device has no app settings screen to open.", Toast.LENGTH_LONG).show()
        }
    }
}

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.longlifeio.fineprint.egress.ScanSession
import com.longlifeio.fineprint.ui.AboutScreen
import com.longlifeio.fineprint.ui.AppDetailScreen
import com.longlifeio.fineprint.ui.AppListScreen
import com.longlifeio.fineprint.ui.FinePrintTheme
import com.longlifeio.fineprint.ui.bundleStatus

class MainActivity : ComponentActivity() {
    private val session: ScanSession get() = (application as FinePrintApp).session
    private val bundleSession get() = (application as FinePrintApp).bundle

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
                var showAbout by rememberSaveable { mutableStateOf(false) }
                val listState = rememberLazyListState() // hoisted so the list keeps its place
                var openPackage by rememberSaveable { mutableStateOf<String?>(null) }
                val open = openPackage?.let { name -> apps?.find { it.packageName == name } }
                if (showAbout) {
                    BackHandler { showAbout = false }
                    AboutScreen(bundleState, bundleSession.baseUrl, onBack = { showAbout = false }, onRefresh = bundleSession::refreshNow)
                } else if (open == null) {
                    AppListScreen(
                        apps = apps,
                        results = results,
                        progress = progress,
                        signatures = signatures,
                        includeSystem = includeSystem,
                        onIncludeSystemChange = session::setIncludeSystem,
                        onOpen = { openPackage = it.packageName },
                        listState = listState,
                        bundleLine = bundleStatus(bundleState),
                        onAbout = { showAbout = true },
                    )
                } else {
                    BackHandler { openPackage = null }
                    LaunchedEffect(open.scanKey) { session.scanNow(open) }
                    AppDetailScreen(
                        app = open,
                        result = results[open.scanKey],
                        signatures = signatures,
                        bundle = bundleState.bundle,
                        onBack = { openPackage = null },
                        onOpenSettings = { openAppSettings(open.packageName) },
                    )
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

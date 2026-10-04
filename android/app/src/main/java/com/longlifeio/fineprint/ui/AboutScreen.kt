package com.longlifeio.fineprint.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.BuildConfig
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.bundle.BundleState
import java.time.Duration
import java.time.OffsetDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(state: BundleState, baseUrl: String, onBack: () -> Unit, onRefresh: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Fine Print") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { SectionTitle("Knowledge bundle") }
            item { Note(bundleStatus(state)) }
            item { Note("Downloaded whole from $baseUrl; the app never asks a server about a particular app.") }
            state.error?.let { item { Note(it) } }
            item {
                OutlinedButton(onClick = onRefresh, enabled = !state.refreshing, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(if (state.refreshing) "Updating…" else "Update now")
                }
            }
            item { SectionTitle("Tracker data") }
            item {
                Note(
                    state.signatures?.attribution?.takeIf { it.isNotEmpty() }
                        ?: "Contains information from the εxodus tracker database (https://reports.exodus-privacy.eu.org/), " +
                        "made available under the Open Database License (ODbL) 1.0; individual contents under the " +
                        "Database Contents License (DbCL) 1.0.",
                )
            }
            item { Note("Fine Print's own tracker signatures (fp-*) are part of the same file, under the same licence.") }
            item { SectionTitle("Licences") }
            item { Note("Fine Print's code: AGPL-3.0-or-later.") }
            item { Note("Fine Print's records (bundle.json): CC BY 4.0, attribution Fine Print.") }
            item { Note("Tracker list (trackers.json): ODbL 1.0, from εxodus (above).") }
            item { Note("dexlib2 (smali): Apache License 2.0.") }
            item { Note("App version ${BuildConfig.VERSION_NAME}.") }
        }
    }
}

/** "bundle: 2026.10.04, 0 days old", from the bundle's own generated_at. */
fun bundleStatus(state: BundleState): String {
    val bundle = state.bundle ?: return if (state.refreshing) "bundle: downloading…" else "bundle: none yet (scanning still works offline)"
    val days = runCatching { Duration.between(OffsetDateTime.parse(bundle.generatedAt), OffsetDateTime.now()).toDays() }.getOrNull()
    val age = when (days) {
        null -> "age unknown"
        1L -> "1 day old"
        else -> "$days days old"
    }
    return "bundle: ${bundle.version}, $age"
}

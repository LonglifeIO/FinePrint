package com.longlifeio.fineprint.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.BuildConfig
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.bundle.BundleState
import java.time.Duration
import java.time.OffsetDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(state: BundleState, status: String, origin: String, onBack: () -> Unit, onRefresh: (() -> Unit)?) {
    var licences by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About FinePrint") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(TOUCH)) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        CentredList(padding) {
            item {
                Text(
                    "FinePrint — read it so you don't have to.",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item { SectionTitle("Knowledge bundle") }
            item { Note(status) }
            item { Note(origin) }
            state.error?.let { item { Note(it) } }
            onRefresh?.let { refresh ->
                item {
                    OutlinedButton(onClick = refresh, enabled = !state.refreshing, modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = TOUCH)) {
                        Text(if (state.refreshing) "Updating…" else "Update now")
                    }
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
            item { Note("FinePrint's own tracker signatures (fp-*) are part of the same file, under the same licence.") }
            item { SectionTitle("Licences") }
            item { Note("FinePrint's code: AGPL-3.0-or-later.") }
            item { Note("FinePrint's records (bundle.json): CC BY 4.0, attribution FinePrint.") }
            item { Note("Tracker list (trackers.json): ODbL 1.0, from εxodus (above).") }
            item { Note("dexlib2 (smali): Apache License 2.0.") }
            item { Note("Icons: Material Symbols and Material Icons (Google), Apache License 2.0.") }
            item {
                Note(
                    "Fonts: Atkinson Hyperlegible Next, copyright 2020-2024 The Atkinson Hyperlegible Next Project Authors, " +
                        "and Fraunces, copyright 2018 The Fraunces Project Authors; both under the SIL Open Font License 1.1.",
                )
            }
            item {
                TextButton(onClick = { licences = !licences }, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = TOUCH)) {
                    Text(if (licences) "Hide the font and icon licences" else "Show the font and icon licences")
                }
            }
            if (licences) LICENCE_TEXTS.forEach { res -> item { LicenceText(res) } }
            item { Note("App version ${BuildConfig.VERSION_NAME}.") }
        }
    }
}

/** The licence texts that ship with the fonts and icons (res/raw), shown in full on request. */
private val LICENCE_TEXTS = listOf(R.raw.ofl_atkinson_hyperlegible_next, R.raw.ofl_fraunces, R.raw.licence_material_symbols)

@Composable
private fun LicenceText(res: Int) {
    val resources = LocalContext.current.resources
    val text = remember(res) { resources.openRawResource(res).bufferedReader().use { it.readText() } }
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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

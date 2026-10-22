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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.BuildConfig
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.bundle.BundleState
import com.longlifeio.fineprint.explain.ABOUT_REVIEW_REQUESTS
import java.time.Duration
import java.time.OffsetDateTime

/** What FinePrint is, and isn't, under About's title. Plain words; nothing here is marked as awaiting review. */
const val ABOUT_NOTE = "FinePrint passes on what public sources say. It quotes the companies' own words. " +
    "Every source is dated, and it can go out of date. Check the source before you act on what FinePrint says. FinePrint isn't legal advice."

/** The funding rule, in one sentence, under the note. */
const val FUNDING = "It takes no money from any company it names."

/** A link in About: a page on GitHub, opened in the browser by a deliberate tap (openInBrowser). */
data class AboutLink(val label: String, val url: String)

private const val REPOSITORY = "https://github.com/LonglifeIO/FinePrint"

/** The forms are the repository's issue templates (.github/ISSUE_TEMPLATE), and Privacy is docs/privacy.md. */
val ABOUT_LINKS = listOf(
    AboutLink("Source code", REPOSITORY),
    AboutLink("Report an error", REPORT_ERROR_URL),
    AboutLink("Ask for a correction", "$REPOSITORY/issues/new?template=company-correction.yml"),
    AboutLink("Privacy", "$REPOSITORY/blob/main/docs/privacy.md"),
)

const val LINKS_NOTE = "These open GitHub in your browser. FinePrint itself sends nothing."

const val LINKS_PUBLIC = "Report an error if something FinePrint says is wrong. Ask for a correction if you speak for a company it names. " +
    "Either one is public once you post it, under your GitHub account."

/** What the APK holds besides FinePrint's own code and data, each under its own licence. */
val THIRD_PARTY = listOf(
    "dexlib2 (smali), which reads apps' code: BSD 3-Clause License, with parts under the Apache License 2.0.",
    "The app's other libraries, AndroidX and Kotlin among them: Apache License 2.0.",
    "Icons: Material Symbols and Material Icons (Google), Apache License 2.0.",
    "Fonts: Atkinson Hyperlegible Next, copyright 2020-2024 The Atkinson Hyperlegible Next Project Authors, " +
        "and Fraunces, copyright 2018 The Fraunces Project Authors; both under the SIL Open Font License 1.1.",
)

/** About: what FinePrint is, this version and its bundle, the links to GitHub, and the licences. [openLink] hands a link to the browser. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    state: BundleState,
    status: String,
    origin: String,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)?,
    openLink: (String) -> Unit,
) {
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
        CentredList(padding, Modifier.testTag("about")) {
            item {
                Text(
                    "FinePrint — read it so you don't have to.",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item { Paragraph(ABOUT_NOTE) }
            item { Paragraph(FUNDING) }
            item { SectionTitle("This version") }
            item { Note("App version ${BuildConfig.VERSION_NAME}.") }
            item { Note(state.bundle?.let { bundleLine(it) } ?: status) }
            item { Note(origin) }
            state.error?.let { item { Note(it) } }
            onRefresh?.let { refresh ->
                item {
                    OutlinedButton(onClick = refresh, enabled = !state.refreshing, modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = TOUCH)) {
                        Text(if (state.refreshing) "Updating…" else "Update now")
                    }
                }
            }
            item { SectionTitle("On GitHub") }
            item { Note(LINKS_NOTE) }
            ABOUT_LINKS.forEach { link -> item { LinkRow(link.label, R.drawable.ic_open_in_new) { openLink(link.url) } } }
            item { Note(LINKS_PUBLIC) }
            item { Note(ABOUT_REVIEW_REQUESTS) }
            item { SectionTitle("Tracker data") }
            item {
                Note(
                    state.signatures?.attribution?.takeIf { it.isNotEmpty() }
                        ?: "Contains information from the εxodus tracker database (https://reports.exodus-privacy.eu.org/), " +
                        "made available under the Open Database License (ODbL) 1.0; individual contents under the " +
                        "Database Contents License (DbCL) 1.0.",
                )
            }
            item { Note("FinePrint isn't part of Exodus Privacy and isn't endorsed by it.") }
            item { Note("FinePrint's own tracker signatures (fp-*) are part of the same file, under the same licence.") }
            item { SectionTitle("Licences") }
            item { Note("FinePrint's code: AGPL-3.0-or-later.") }
            item { Note("FinePrint's records (bundle.json) and its table of laws (jurisdictions.json): CC BY 4.0, attribution FinePrint.") }
            item { Note("Tracker list (trackers.json): ODbL 1.0, its contents DbCL 1.0, from εxodus (above).") }
            THIRD_PARTY.forEach { item { Note(it) } }
            item {
                TextButton(onClick = { licences = !licences }, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = TOUCH)) {
                    Text(if (licences) "Hide the licence texts" else "Show the licence texts")
                }
            }
            if (licences) LICENCE_TEXTS.forEach { res -> item { LicenceText(res) } }
        }
    }
}

/** The licence texts that ship with the fonts, the icons and the libraries (res/raw), shown in full on request. */
private val LICENCE_TEXTS = listOf(R.raw.ofl_atkinson_hyperlegible_next, R.raw.ofl_fraunces, R.raw.licence_material_symbols, R.raw.licence_dexlib2)

@Composable
private fun LicenceText(res: Int) {
    val resources = LocalContext.current.resources
    val text = remember(res) { resources.openRawResource(res).bufferedReader().use { it.readText() } }
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

/** "Knowledge bundle 2026.10.09, dated 2026-10-09 (11 days old).", from the bundle's own version and generated_at. */
fun bundleLine(bundle: Bundle, now: OffsetDateTime = OffsetDateTime.now()): String {
    val made = runCatching { OffsetDateTime.parse(bundle.generatedAt) }.getOrNull()
        ?: return "Knowledge bundle ${bundle.version}."
    return "Knowledge bundle ${bundle.version}, dated ${made.toLocalDate()} (${age(Duration.between(made, now).toDays())})."
}

/** "bundle: 2026.10.04, 0 days old", from the bundle's own generated_at. */
fun bundleStatus(state: BundleState): String {
    val bundle = state.bundle ?: return if (state.refreshing) "bundle: downloading…" else "bundle: none yet (scanning still works offline)"
    val days = runCatching { Duration.between(OffsetDateTime.parse(bundle.generatedAt), OffsetDateTime.now()).toDays() }.getOrNull()
    return "bundle: ${bundle.version}, ${days?.let(::age) ?: "age unknown"}"
}

private fun age(days: Long): String = if (days == 1L) "1 day old" else "$days days old"

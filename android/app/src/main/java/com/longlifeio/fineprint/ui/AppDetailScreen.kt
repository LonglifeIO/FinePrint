package com.longlifeio.fineprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.egress.TrackerSignatures
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.STALE_DEFINITION
import com.longlifeio.fineprint.explain.STALE_NOTE
import com.longlifeio.fineprint.explain.recordLastReviewed
import com.longlifeio.fineprint.explain.CHANGED
import com.longlifeio.fineprint.explain.REVIEWED
import com.longlifeio.fineprint.explain.WhatYouCanDo
import com.longlifeio.fineprint.explain.alsoCollected
import com.longlifeio.fineprint.explain.finePrint
import com.longlifeio.fineprint.explain.footnotes
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView

/** A GitHub issue template; the link carries nothing about the user's apps. */
const val REPORT_ERROR_URL = "https://github.com/LonglifeIO/FinePrint/issues/new?template=record-error.md"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    app: InstalledApp,
    explanation: Explanation,
    check: WhatYouCanDo,
    review: ReviewView,
    result: TrackerScanResult?,
    signatures: TrackerSignatures?,
    bundleVersion: String?,
    onBack: () -> Unit,
    /** Null where an app's settings can't be opened, with [settingsUnavailable] saying why. */
    onOpenSettings: (() -> Unit)?,
    onHowToRead: () -> Unit,
    /** Null until the app's code has been checked: a mark needs the tracker set it was given for. */
    onMarkReviewed: (() -> Unit)?,
    onClearMark: () -> Unit,
    onTick: (String, Boolean) -> Unit,
    /** Which buckets show all their lines; kept on this phone. */
    buckets: OpenBuckets = rememberOpenBuckets(),
    settingsUnavailable: String? = null,
    /** Said in place of the app's version and APK count, when they aren't known (a sample app never scanned). */
    installLine: String? = null,
    /** Opens GitHub's review-request form in the browser, after the disclosure; null where it can't, with [reviewUnavailable]. */
    onAskForReview: (() -> Unit)? = null,
    reviewUnavailable: String? = null,
) {
    var evidenceOpen by rememberSaveable { mutableStateOf(false) }
    var recordOpen by rememberSaveable { mutableStateOf(false) }
    var jurisdictionsOpen by rememberSaveable { mutableStateOf(false) }
    var recordAll by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<SheetContent?>(null) }
    val uriHandler = LocalUriHandler.current
    val notes = remember(explanation, check) { footnotes(explanation, check) }
    FieldNotesTheme {
        CompositionLocalProvider(LocalFootnotes provides notes) {
            val p = LocalPalette.current
            Scaffold(
                containerColor = p.surface,
                topBar = {
                    TopAppBar(
                        title = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = onBack, modifier = Modifier.size(TOUCH)) {
                                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = p.surface, scrolledContainerColor = p.surface),
                    )
                },
            ) { padding ->
                CentredList(padding, Modifier.testTag("detail")) {
                    item(key = "header") { DetailHeader(app, explanation, check, review, onOpenSettings, settingsUnavailable, installLine, onAskForReview, reviewUnavailable) }
                    // Their words: only when the record has the store's own description.
                    explanation.storeTagline?.let { t -> item(key = "their-words") { TheirWords(t, finePrint(explanation), alsoCollected(explanation)) { sheet = it } } }
                    detailSections(
                        explanation, check, onSources = { sheet = it }, onOpenSettings = onOpenSettings, settingsUnavailable = settingsUnavailable, onTick = onTick,
                        jurisdictionsOpen = jurisdictionsOpen, onToggleJurisdictions = { jurisdictionsOpen = !jurisdictionsOpen },
                        buckets = buckets,
                    )
                    onTheRecordSection(
                        explanation.onTheRecord, explanation.changes, recordOpen, onToggle = { recordOpen = !recordOpen; recordAll = false },
                        showAll = recordAll, onShowAll = { recordAll = true }, onDetails = { sheet = it },
                    )
                    evidenceSection(evidenceOpen, { evidenceOpen = !evidenceOpen }, app, result, signatures, explanation.exodusNote)
                    sourcesCard(notes)
                    item(key = "footer") {
                        Column(Modifier.padding(top = Space.l, bottom = Space.xl)) {
                            ReviewControls(review, onMarkReviewed, onClearMark)
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = p.divider)
                            Note(explanation.lastReviewed?.let(::recordLastReviewed) ?: "No reviewed record for this app yet.")
                            bundleVersion?.let { Note("Knowledge bundle $it.") }
                            if (explanation.stale) Note(STALE_NOTE)
                            if (result?.trackers?.isNotEmpty() == true) Note("Tracker names and signatures: εxodus Privacy, ODbL 1.0 (details under Evidence).")
                            LinkRow("How to read this", R.drawable.ic_chevron_right, onHowToRead)
                            LinkRow("Report an error", R.drawable.ic_open_in_new) { uriHandler.openUri(REPORT_ERROR_URL) }
                        }
                    }
                }
            }
            sheet?.let { SourcesSheet(it) { sheet = null } }
        }
    }
}

/** Your own Reviewed mark: set, cleared, or set again after something changed. The tier never moves. */
@Composable
private fun ReviewControls(review: ReviewView, onMarkReviewed: (() -> Unit)?, onClearMark: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        when (review.status) {
            ReviewStatus.NOT_REVIEWED -> Text(
                "Done reading? Mark this app reviewed. The mark stays on this phone and doesn't change its tier.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ReviewStatus.REVIEWED -> Text("You marked this app reviewed on ${review.reviewedOn}.", style = MaterialTheme.typography.bodyMedium)
            ReviewStatus.CHANGED -> Text(
                "You marked this app reviewed on ${review.reviewedOn}. $CHANGED: ${review.note}.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (review.status != ReviewStatus.REVIEWED) {
            Button(
                onClick = { onMarkReviewed?.invoke() },
                enabled = onMarkReviewed != null,
                modifier = Modifier.padding(top = 8.dp).heightIn(min = TOUCH),
            ) { Text(if (review.status == ReviewStatus.CHANGED) "Mark as reviewed again" else "Mark as reviewed") }
            if (onMarkReviewed == null) Note("Available once FinePrint has checked this app's code.")
        }
        if (review.status != ReviewStatus.NOT_REVIEWED) {
            TextButton(onClick = onClearMark, modifier = Modifier.heightIn(min = TOUCH)) { Text("Remove the $REVIEWED mark") }
        }
    }
}

/** "Stale": the record was last reviewed more than 180 days ago. */
@Composable
fun StaleMarker() {
    val c = MaterialTheme.colorScheme
    WithDefinition("Stale", STALE_DEFINITION) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(R.drawable.ic_stale), contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text("Stale", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
    }
}

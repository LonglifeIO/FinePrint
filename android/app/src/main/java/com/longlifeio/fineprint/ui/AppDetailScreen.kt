package com.longlifeio.fineprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.egress.TrackerSignatures
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.NO_RECORD
import com.longlifeio.fineprint.explain.STALE_DEFINITION
import com.longlifeio.fineprint.explain.CHANGED
import com.longlifeio.fineprint.explain.REVIEWED
import com.longlifeio.fineprint.explain.WhatYouCanDo
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
    onOpenSettings: () -> Unit,
    onHowToRead: () -> Unit,
    /** Null until the app's code has been checked: a mark needs the tracker set it was given for. */
    onMarkReviewed: (() -> Unit)?,
    onClearMark: () -> Unit,
    onTick: (String, Boolean) -> Unit,
) {
    var evidenceOpen by rememberSaveable { mutableStateOf(false) }
    var recordOpen by rememberSaveable { mutableStateOf(false) }
    var recordAll by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<SheetContent?>(null) }
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(TOUCH)) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().testTag("detail")) {
            item { Header(app, explanation, check, review, onOpenSettings) }
            detailSections(explanation, check, onSources = { sheet = it }, onOpenSettings = onOpenSettings, onTick = onTick)
            onTheRecordSection(
                explanation.onTheRecord, explanation.changes, recordOpen, onToggle = { recordOpen = !recordOpen; recordAll = false },
                showAll = recordAll, onShowAll = { recordAll = true }, onDetails = { sheet = it },
            )
            evidenceSection(evidenceOpen, { evidenceOpen = !evidenceOpen }, app, result, signatures, explanation.exodusNote)
            item {
                Column(Modifier.padding(top = 16.dp, bottom = 24.dp)) {
                    ReviewControls(review, onMarkReviewed, onClearMark)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Note(explanation.lastReviewed?.let { "Record last reviewed $it." } ?: "No reviewed record for this app yet.")
                    bundleVersion?.let { Note("Knowledge bundle $it.") }
                    if (explanation.stale) Note("This record is more than 180 days old and may be out of date.")
                    if (result?.trackers?.isNotEmpty() == true) Note("Tracker names and signatures: εxodus Privacy, ODbL 1.0 (details under Evidence).")
                    LinkRow("How to read this", R.drawable.ic_chevron_right, onHowToRead)
                    LinkRow("Report an error", R.drawable.ic_open_in_new) { uriHandler.openUri(REPORT_ERROR_URL) }
                }
            }
        }
    }
    sheet?.let { SourcesSheet(it) { sheet = null } }
}

/** Tier, its reason and coverage first: the answer to "should I care?" before the detail. */
@Composable
private fun Header(app: InstalledApp, e: Explanation, check: WhatYouCanDo, review: ReviewView, onOpenSettings: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TierBadge(e.tier.tier, reviewed = review.status == ReviewStatus.REVIEWED)
            if (e.coverage == "auto" && e.tier.tier != null) {
                Text(NO_RECORD, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (e.stale) StaleMarker()
        }
        Text(e.tier.reason, style = MaterialTheme.typography.bodyLarge)
        if (e.tier.capped) Note("Without a reviewed record, an app is rated Caution at most, never Flagged.")
        if (review.status == ReviewStatus.CHANGED) {
            Text("$CHANGED: ${review.note}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        check.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(
            app.packageName,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Version ${app.versionName ?: "unknown"} (${app.versionCode}) · " +
                (if (app.isSystem) "system app" else "user-installed") + " · " +
                (if (app.apkPaths.size == 1) "1 APK" else "${app.apkPaths.size} APKs"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 8.dp).heightIn(min = TOUCH)) { Text("Open app settings") }
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

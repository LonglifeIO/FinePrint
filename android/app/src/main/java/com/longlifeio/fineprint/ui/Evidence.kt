package com.longlifeio.fineprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.egress.TrackerSignatures
import com.longlifeio.fineprint.explain.EVIDENCE

/** Section 7: collapsed by default; the raw findings the sections above are built from. */
fun LazyListScope.evidenceSection(
    open: Boolean,
    onToggle: () -> Unit,
    app: InstalledApp,
    result: TrackerScanResult?,
    signatures: TrackerSignatures?,
    exodusNote: String?,
) {
    item(key = "section:evidence") {
        val trackers = result?.trackers?.size?.let { trackerCount(it) } ?: "scanning"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TOUCH)
                .clickable(onClickLabel = if (open) "Hide evidence" else "Show evidence", onClick = onToggle)
                .semantics { stateDescription = if (open) "Shown" else "Hidden" }
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(EVIDENCE.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(EVIDENCE.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "$trackers · ${app.permissions.size} permissions · " + if (open) "tap to hide" else "tap to show",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                painterResource(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (!open) return
    item { SectionTitle("Tracker code in this app") }
    trackerItems(app, result, signatures)
    exodusNote?.let { item { Note(it) } }
    item { SectionTitle("Permissions it declares") }
    item {
        Note("${app.permissions.size} declared · ${app.permissions.count { it.granted }} granted · ${app.permissions.count { it.dangerous }} dangerous")
    }
    items(app.permissions, key = { "permission:" + it.name }) { PermissionRow(it) }
}

private fun LazyListScope.trackerItems(app: InstalledApp, result: TrackerScanResult?, signatures: TrackerSignatures?) {
    if (result == null) {
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Scanning this app's code…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    item {
        when {
            result.dexFiles == 0 && result.problems.isNotEmpty() -> Note("Couldn't read this app's code, so it wasn't checked for trackers.")
            result.dexFiles == 0 && !app.hasCode -> Note("This package has no code of its own (for example, a resource overlay).")
            result.dexFiles == 0 -> Note("Its manifest declares code, but none was found in its APK files, so it couldn't be checked.")
            result.problems.isNotEmpty() -> Note("Some of this app's code couldn't be read, so this list may be incomplete.")
            result.trackers.isEmpty() -> Note("No tracker signatures matched.")
        }
    }
    items(result.trackers, key = { "tracker:" + it.id }) { TrackerRow(it) }
    item {
        val list = signatures?.let { s -> " · ${s.trackers.count { it.codeSignature.length > 3 }} tracker signatures from ${s.fetchedAt.take(10)}" }.orEmpty()
        Note("${result.dexFiles} dex files · ${"%,d".format(result.classes)} classes · ${seconds(result.durationMs)}$list")
    }
    items(result.problems) { problem ->
        Text(
            "Couldn't read $problem",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }
    val notice = signatures?.attribution.orEmpty()
    if (notice.isNotEmpty()) item { Note(notice) } // ODbL 4.3: required wherever εxodus data appears
}

@Composable
private fun TrackerRow(tracker: DetectedTracker) {
    ListItem(
        headlineContent = { Text(tracker.name) },
        supportingContent = {
            Column {
                Text((listOf(tracker.id) + tracker.categories).joinToString(" · "))
                Text(
                    tracker.matchedClass,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun PermissionRow(permission: RequestedPermission) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(permission.name.removePrefix("android.permission."), style = MaterialTheme.typography.bodyMedium) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (permission.dangerous) StatusLabel("Dangerous", colors.errorContainer, colors.onErrorContainer)
                if (permission.granted) {
                    StatusLabel("Granted", colors.primaryContainer, colors.onPrimaryContainer)
                } else {
                    StatusLabel("Denied", colors.surfaceVariant, colors.onSurfaceVariant)
                }
            }
        },
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

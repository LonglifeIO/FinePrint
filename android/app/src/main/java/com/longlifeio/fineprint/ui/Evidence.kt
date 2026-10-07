package com.longlifeio.fineprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.egress.TrackerSignatures
import com.longlifeio.fineprint.explain.EVIDENCE
import com.longlifeio.fineprint.explain.evidenceHeadline

/** Section 7: collapsed by default; the raw findings the sections above are built from. */
fun LazyListScope.evidenceSection(
    open: Boolean,
    onToggle: () -> Unit,
    app: InstalledApp,
    result: TrackerScanResult?,
    signatures: TrackerSignatures?,
    exodusNote: String?,
) {
    cardTop("evidence", EVIDENCE, evidenceHeadline(app, result), CardToggle(open, "the evidence", onToggle))
    if (!open) return
    cardItem { SectionTitle("Tracker code in this app") }
    trackerItems(app, result, signatures)
    exodusNote?.let { cardItem { Note(it) } }
    cardItem { SectionTitle("Permissions it declares") }
    cardItem {
        Note("${app.permissions.size} declared · ${app.permissions.count { it.granted }} granted · ${app.permissions.count { it.dangerous }} that Android asks you about first")
    }
    cardItems(app.permissions, key = { "permission:" + it.name }) { PermissionRow(it) }
    cardEnd("evidence")
}

private fun LazyListScope.trackerItems(app: InstalledApp, result: TrackerScanResult?, signatures: TrackerSignatures?) {
    if (result == null) {
        cardItem {
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
    cardItem {
        when {
            result.dexFiles == 0 && result.problems.isNotEmpty() -> Note("Couldn't read this app's code, so it wasn't checked for trackers.")
            result.dexFiles == 0 && !app.hasCode -> Note("This package has no code of its own (for example, a resource overlay).")
            result.dexFiles == 0 -> Note("Its manifest declares code, but none was found in its APK files, so it couldn't be checked.")
            result.problems.isNotEmpty() -> Note("Some of this app's code couldn't be read, so this list may be incomplete.")
            result.trackers.isEmpty() -> Note("No tracker signatures matched.")
        }
    }
    cardItems(result.trackers, key = { "tracker:" + it.id }) { TrackerRow(it) }
    cardItem {
        val list = signatures?.let { s -> " · ${s.trackers.count { it.codeSignature.length > 3 }} tracker signatures from ${s.fetchedAt.take(10)}" }.orEmpty()
        Note("${result.dexFiles} dex files · ${"%,d".format(result.classes)} classes · ${seconds(result.durationMs)}$list")
    }
    cardItems(result.problems) { problem ->
        Text(
            "Couldn't read $problem",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }
    val notice = signatures?.attribution.orEmpty()
    if (notice.isNotEmpty()) cardItem { Note(notice) } // ODbL 4.3: required wherever εxodus data appears
}

@Composable
private fun TrackerRow(tracker: DetectedTracker) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(permission.name.removePrefix("android.permission."), style = MaterialTheme.typography.bodyMedium) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Android calls these "dangerous"; FinePrint says what it means, without the alarm word or colour.
                if (permission.dangerous) StatusLabel("Asks first", colors.surface, colors.onSurface, outline = true)
                StatusLabel(if (permission.granted) "Granted" else "Denied", colors.surfaceContainerHigh, colors.onSurface)
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

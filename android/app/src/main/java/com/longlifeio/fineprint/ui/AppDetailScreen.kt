package com.longlifeio.fineprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.egress.TrackerSignatures

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    app: InstalledApp,
    result: TrackerScanResult?,
    signatures: TrackerSignatures?,
    bundle: Bundle?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var evidenceOpen by rememberSaveable { mutableStateOf(false) }
    val explanation = remember(app, result, bundle, signatures) {
        explain(app, result, bundle, signatures?.trackers.orEmpty().associateBy { it.id })
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { Header(app, explanation.privacyControls, onOpenSettings) }
            explanationItems(explanation, signatures?.attribution.orEmpty())
            item {
                val trackers = result?.trackers?.size?.let { trackerCount(it) } ?: "scanning"
                TextButton(onClick = { evidenceOpen = !evidenceOpen }, modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)) {
                    Text((if (evidenceOpen) "Hide evidence" else "Show evidence") + " ($trackers, ${app.permissions.size} permissions)")
                }
            }
            if (evidenceOpen) {
                item { SectionTitle("Tracker code in this app") }
                trackerItems(app, result, signatures)
                item { SectionTitle("Permissions it declares") }
                item {
                    Note(
                        "${app.permissions.size} declared · ${app.permissions.count { it.granted }} granted · " +
                            "${app.permissions.count { it.dangerous }} dangerous",
                    )
                }
                items(app.permissions, key = { "permission:" + it.name }) { PermissionRow(it) }
            }
        }
    }
}

@Composable
private fun Header(app: InstalledApp, privacyControls: String?, onOpenSettings: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(app.packageName, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        Note(
            "Version ${app.versionName ?: "unknown"} (${app.versionCode}) · " +
                (if (app.isSystem) "system app" else "user-installed") + " · " +
                (if (app.apkPaths.size == 1) "1 APK" else "${app.apkPaths.size} APKs"),
        )
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 12.dp)) { Text("Open app settings") }
        privacyControls?.let { Text("Inside the app: $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
    }
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
            result.dexFiles == 0 && result.problems.isNotEmpty() ->
                Note("Couldn't read this app's code, so it wasn't checked for trackers.")
            result.dexFiles == 0 && !app.hasCode -> Note("This package has no code of its own (for example, a resource overlay).")
            result.dexFiles == 0 -> Note("Its manifest declares code, but none was found in its APK files, so it couldn't be checked.")
            result.problems.isNotEmpty() -> Note("Some of this app's code couldn't be read, so this list may be incomplete.")
            result.trackers.isEmpty() -> Note("No tracker signatures matched.")
        }
    }
    items(result.trackers, key = { "tracker:" + it.id }) { TrackerRow(it) }
    item {
        val list = signatures?.let { s ->
            " · ${s.trackers.count { it.codeSignature.length > 3 }} tracker signatures from ${s.fetchedAt.take(10)}"
        }.orEmpty()
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
        headlineContent = {
            Text(permission.name.removePrefix("android.permission."), style = MaterialTheme.typography.bodyMedium)
        },
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

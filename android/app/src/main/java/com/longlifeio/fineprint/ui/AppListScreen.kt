package com.longlifeio.fineprint.ui

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.ScanProgress
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.egress.TrackerSignatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    apps: List<InstalledApp>?,
    results: Map<String, TrackerScanResult>,
    progress: ScanProgress,
    signatures: TrackerSignatures?,
    includeSystem: Boolean,
    onIncludeSystemChange: (Boolean) -> Unit,
    onOpen: (InstalledApp) -> Unit,
    listState: LazyListState,
    bundleLine: String,
    onAbout: () -> Unit,
) {
    val visible = remember(apps, includeSystem) { apps.orEmpty().filter { includeSystem || !it.isSystem } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fine Print") },
                actions = {
                    TextButton(onClick = onAbout) { Text("About") }
                    Text("System apps", style = MaterialTheme.typography.labelLarge)
                    Switch(
                        checked = includeSystem,
                        onCheckedChange = onIncludeSystemChange,
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScanStatus(apps, visible, results, progress, signatures)
            Text(
                bundleLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            HorizontalDivider()
            if (apps == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(visible, key = { it.packageName }) { app -> AppRow(app, results[app.scanKey], onOpen) }
                }
            }
        }
    }
}

@Composable
private fun ScanStatus(
    apps: List<InstalledApp>?,
    visible: List<InstalledApp>,
    results: Map<String, TrackerScanResult>,
    progress: ScanProgress,
    signatures: TrackerSignatures?,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            when {
                apps == null -> "Reading installed apps…"
                progress.running -> "Scanning app code for trackers: ${progress.done} of ${progress.total}"
                progress.total > 0 -> "Scanned ${progress.total} apps in ${seconds(progress.elapsedMs)}"
                else -> "Waiting to scan…"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (progress.running) {
            LinearProgressIndicator(
                progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
        if (apps != null) {
            val withTrackers = visible.count { results[it.scanKey]?.trackers?.isNotEmpty() == true }
            val list = signatures?.let { s ->
                " · ${s.trackers.count { it.codeSignature.length > 3 }} tracker signatures from ${s.fetchedAt.take(10)}"
            }.orEmpty()
            Text(
                "${visible.size} apps · $withTrackers with tracker code$list",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, result: TrackerScanResult?, onOpen: (InstalledApp) -> Unit) {
    ListItem(
        headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { AppIcon(app.packageName) },
        trailingContent = { TrackerSummary(app.hasCode, result) },
        modifier = Modifier.clickable { onOpen(app) },
    )
}

@Composable
private fun TrackerSummary(hasCode: Boolean, result: TrackerScanResult?) {
    val colors = MaterialTheme.colorScheme
    when {
        result == null ->
            Text("Scanning…", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        result.trackers.isNotEmpty() ->
            StatusLabel(trackerCount(result.trackers.size), colors.errorContainer, colors.onErrorContainer)
        result.dexFiles > 0 && result.problems.isNotEmpty() ->
            StatusLabel("Incomplete", colors.surfaceVariant, colors.onSurfaceVariant)
        result.dexFiles == 0 && result.problems.isNotEmpty() ->
            StatusLabel("Unreadable", colors.surfaceVariant, colors.onSurfaceVariant)
        result.dexFiles == 0 -> StatusLabel(if (hasCode) "Not inspectable" else "No code", colors.surfaceVariant, colors.onSurfaceVariant)
        else -> StatusLabel("None found", colors.surfaceVariant, colors.onSurfaceVariant)
    }
}

private const val ICON_PX = 96
private val iconCache = LruCache<String, ImageBitmap>(150)

@Composable
private fun AppIcon(packageName: String) {
    val pm = LocalContext.current.packageManager
    val icon by produceState(iconCache.get(packageName), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { pm.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull()
            }?.also { iconCache.put(packageName, it) }
        }
    }
    val bitmap = icon
    if (bitmap == null) {
        Spacer(Modifier.size(40.dp))
    } else {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
    }
}

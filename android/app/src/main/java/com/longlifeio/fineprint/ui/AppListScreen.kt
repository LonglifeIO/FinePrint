package com.longlifeio.fineprint.ui

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.ScanProgress
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.ListFilter
import com.longlifeio.fineprint.explain.CHANGED
import com.longlifeio.fineprint.explain.NO_RECORD
import com.longlifeio.fineprint.explain.WhatYouCanDo
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import com.longlifeio.fineprint.explain.Tier
import com.longlifeio.fineprint.explain.listOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    apps: List<InstalledApp>?,
    explanations: Map<String, Explanation>,
    reviews: Map<String, ReviewView>,
    checks: Map<String, WhatYouCanDo>,
    results: Map<String, TrackerScanResult>,
    progress: ScanProgress,
    includeSystem: Boolean,
    onIncludeSystemChange: (Boolean) -> Unit,
    onOpen: (InstalledApp) -> Unit,
    listState: LazyListState,
    bundleLine: String,
    onAbout: () -> Unit,
    onHowToRead: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable(stateSaver = FILTER_SAVER) { mutableStateOf(setOf()) }
    val installed = remember(apps, includeSystem) { apps.orEmpty().filter { includeSystem || !it.isSystem } }
    val visible = remember(installed, explanations, reviews, query, filters) {
        listOrder(installed, explanations, reviews.mapValues { it.value.status }, query, filters)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FinePrint") },
                actions = { OverflowMenu(includeSystem, onIncludeSystemChange, onAbout, onHowToRead) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScanStatus(apps, installed, explanations, progress, bundleLine)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps by name") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                ListFilter.entries.forEach { f ->
                    FilterChip(
                        selected = f in filters,
                        onClick = { filters = if (f in filters) filters - f else filters + f },
                        label = { Text(f.label) },
                        modifier = Modifier.height(TOUCH),
                    )
                }
            }
            HorizontalDivider()
            if (apps == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("list")) {
                    if (visible.isEmpty()) item { Note("No apps match.") }
                    items(visible, key = { it.packageName }) { app ->
                        AppRow(app, explanations[app.packageName], reviews[app.packageName], checks[app.packageName], results[app.scanKey], onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverflowMenu(includeSystem: Boolean, onIncludeSystemChange: (Boolean) -> Unit, onAbout: () -> Unit, onHowToRead: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(TOUCH)) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("How to read this") }, onClick = { open = false; onHowToRead() })
            DropdownMenuItem(text = { Text("About FinePrint") }, onClick = { open = false; onAbout() })
            DropdownMenuItem(
                text = { Text("Show system apps") },
                trailingIcon = { Checkbox(checked = includeSystem, onCheckedChange = null) },
                onClick = { onIncludeSystemChange(!includeSystem) },
            )
        }
    }
}

@Composable
private fun ScanStatus(
    apps: List<InstalledApp>?,
    installed: List<InstalledApp>,
    explanations: Map<String, Explanation>,
    progress: ScanProgress,
    bundleLine: String,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val counts = installed.groupingBy { explanations[it.packageName]?.tier?.tier }.eachCount()
        Text(
            when {
                apps == null -> "Reading installed apps…"
                progress.running -> "Checking app code for trackers: ${progress.done} of ${progress.total}"
                else -> "${installed.size} apps · " + Tier.entries.joinToString(" · ") { "${counts[it] ?: 0} ${it.label}" } +
                    " · ${counts[null] ?: 0} ${NO_RECORD.lowercase()}"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (progress.running) {
            LinearProgressIndicator(
                progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
        Text(bundleLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Icon, name, tier, and the one line that set the tier; what changed since you reviewed it, if
 * anything; then how many flows your settings limit, "No record yet" and "Stale" when they apply.
 */
@Composable
private fun AppRow(app: InstalledApp, e: Explanation?, review: ReviewView?, check: WhatYouCanDo?, result: TrackerScanResult?, onOpen: (InstalledApp) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(e?.tier?.reason ?: "Checking its code…", maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (review?.status == ReviewStatus.CHANGED) {
                    Text("$CHANGED: ${review.note}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
                }
                val notes = listOfNotNull(
                    check?.summary,
                    NO_RECORD.takeIf { e?.coverage != "curated" && e?.tier?.tier != null }, // a Caution badge without a record
                    "Stale".takeIf { e?.stale == true },
                )
                if (notes.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (e?.stale == true) Icon(painterResource(R.drawable.ic_stale), contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
                        Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = muted)
                    }
                }
            }
        },
        leadingContent = { AppIcon(app.packageName) },
        trailingContent = { TierBadge(e?.tier?.tier, interactive = false, reviewed = review?.status == ReviewStatus.REVIEWED, compact = true) },
        modifier = Modifier.clickable(onClickLabel = "Open details") { onOpen(app) },
    )
}

/** Filters survive rotation: saved as their names. */
private val FILTER_SAVER = listSaver<Set<ListFilter>, String>(save = { f -> f.map { it.name } }, restore = { names -> names.map(ListFilter::valueOf).toSet() })


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

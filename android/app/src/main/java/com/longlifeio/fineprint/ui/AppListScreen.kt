package com.longlifeio.fineprint.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.ScanProgress
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.ListFilter
import com.longlifeio.fineprint.explain.TIER_SECTIONS
import com.longlifeio.fineprint.explain.WhatYouCanDo
import com.longlifeio.fineprint.explain.glance
import com.longlifeio.fineprint.explain.latestChange
import com.longlifeio.fineprint.explain.listOrder
import com.longlifeio.fineprint.explain.systemGroups
import com.longlifeio.fineprint.review.ReviewView

/**
 * The home (G5): "At a glance" first, then search and filters, then your apps in four tier sections
 * (or, with System on, grouped by maker), and the ad tile that knows nothing about you. Drawn in the
 * Field notes design system; [openSections] keeps which sections you've opened.
 */
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
    openSections: OpenSections = rememberOpenSections(),
) = FieldNotesTheme {
    var query by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable(stateSaver = FILTER_SAVER) { mutableStateOf(setOf()) }
    var sheet by remember { mutableStateOf<SheetContent?>(null) }
    val grouped = includeSystem && ListFilter.SYSTEM in filters
    // The list keeps its place by app, so entering the grouped view would hide the first maker's header.
    LaunchedEffect(grouped) { if (grouped) listState.scrollToItem(0) }
    val installed = remember(apps, includeSystem) { apps.orEmpty().filter { includeSystem || !it.isSystem } }
    val visible = remember(installed, explanations, reviews, query, filters) {
        listOrder(installed, explanations, reviews.mapValues { it.value.status }, query, filters)
    }
    val summary = remember(installed, explanations, checks) { glance(installed, explanations, checks) }
    val latest = remember(installed, explanations) { latestChange(installed, explanations) }
    val p = LocalPalette.current
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = p.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text("Your apps") },
                actions = { OverflowMenu(includeSystem, onIncludeSystemChange, onAbout, onHowToRead) },
                scrollBehavior = bar,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.surface, scrolledContainerColor = p.surface),
            )
        },
    ) { padding ->
        if (apps == null) {
            Column(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Reading installed apps…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Space.l))
            }
            return@Scaffold
        }
        CentredList(padding, Modifier.testTag("list"), listState, extraBottom = Space.xxl) {
            if (progress.running) item(key = "progress") { ScanProgressLine(progress) }
            item(key = "glance") { AtAGlance(summary, latest, bundleLine, onOpen, Modifier.padding(horizontal = Space.screen)) }
            item(key = "search") { SearchAndFilters(query, { query = it }, filters, { filters = it }, includeSystem) }
            if (visible.isEmpty()) item(key = "none") { Note("No apps match.") }
            val row = @Composable { app: InstalledApp, index: Int, count: Int ->
                HomeRow(app, explanations[app.packageName], reviews[app.packageName], checks[app.packageName], index, count, onOpen)
            }
            if (grouped) {
                systemGroupItems(systemGroups(visible, explanations), row, onSources = { sheet = it })
            } else {
                // While searching or filtering, a tier with nothing to show drops out.
                val narrowed = query.isNotBlank() || filters.isNotEmpty()
                val sections = TIER_SECTIONS.map { tier -> tier to visible.filter { explanations[it.packageName]?.tier?.tier == tier } }
                tierSections(sections.filter { !narrowed || it.second.isNotEmpty() }, openSections, row)
            }
            item(key = "ad") { NoAdTile(Modifier.padding(start = Space.screen, end = Space.screen, top = Space.xl)) }
        }
    }
    sheet?.let { SourcesSheet(it) { sheet = null } }
}

/** "Checking app code for trackers: 5 of 9": the real step, with a determinate bar. */
@Composable
private fun ScanProgressLine(progress: ScanProgress) {
    Column(Modifier.padding(horizontal = Space.screen, vertical = Space.s)) {
        Text("Checking app code for trackers: ${progress.done} of ${progress.total}", style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
            modifier = Modifier.fillMaxWidth().padding(vertical = Space.s),
        )
    }
}

@Composable
private fun SearchAndFilters(query: String, onQuery: (String) -> Unit, filters: Set<ListFilter>, onFilters: (Set<ListFilter>) -> Unit, includeSystem: Boolean) {
    Column(Modifier.padding(top = Space.l, bottom = Space.xs)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("Search apps by name") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screen),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.screen, vertical = Space.xs),
        ) {
            ListFilter.entries.filter { it != ListFilter.SYSTEM || includeSystem }.forEach { f ->
                FilterChip(
                    selected = f in filters,
                    onClick = { onFilters(if (f in filters) filters - f else filters + f) },
                    label = { Text(f.label) },
                    modifier = Modifier.height(TOUCH).testTag("filter:${f.name}"),
                )
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

/** Filters survive rotation: saved as their names. */
private val FILTER_SAVER = listSaver<Set<ListFilter>, String>(save = { f -> f.map { it.name } }, restore = { names -> names.map(ListFilter::valueOf).toSet() })

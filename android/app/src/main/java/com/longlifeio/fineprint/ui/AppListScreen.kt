package com.longlifeio.fineprint.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.ScanProgress
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.FILTERS
import com.longlifeio.fineprint.explain.HOME_WORDMARK
import com.longlifeio.fineprint.explain.TIER_SECTIONS
import com.longlifeio.fineprint.explain.WhatYouCanDo
import com.longlifeio.fineprint.explain.glance
import com.longlifeio.fineprint.explain.latestChange
import com.longlifeio.fineprint.explain.listOrder
import com.longlifeio.fineprint.explain.recordsLine
import com.longlifeio.fineprint.review.ReviewView
import kotlinx.coroutines.launch

/**
 * The home (G5): the wordmark bar with the filter button, the search bar docked under it, then "At a glance" and your
 * apps in four tier sections, and the ad tile that knows nothing about you. Tapping the search bar opens the search
 * view ([SearchView]): results as you type, and the filter chips; no chip row at rest. Drawn in the Field notes design
 * system; [openSections] keeps which sections you've opened, [search] what you searched for.
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
    /** A line under "At a glance" saying what these apps are, if they aren't simply yours. */
    notice: String? = null,
    search: HomeSearch = rememberHomeSearch(),
) = FieldNotesTheme {
    var sheet by remember { mutableStateOf<SheetContent?>(null) }
    val scope = rememberCoroutineScope()
    val installed = remember(apps, includeSystem) { apps.orEmpty().filter { includeSystem || !it.isSystem } }
    val ordered = remember(installed, explanations, reviews) { listOrder(installed, explanations, reviews.mapValues { it.value.status }, "", emptySet()) }
    val summary = remember(installed, explanations, checks) { glance(installed, explanations, checks) }
    val records = remember(installed, explanations) { recordsLine(installed, explanations) }
    val latest = remember(installed, explanations) { latestChange(installed, explanations) }
    val p = LocalPalette.current
    Scaffold(
        containerColor = p.surface,
        topBar = {
            // The wordmark bar, 64dp and never collapsing, then the search bar under it. The list under them starts at
            // the same top, so they're put first for TalkBack: wordmark, Filters, menu, search, then the list.
            Column(Modifier.background(p.surface).semantics { traversalIndex = -1f; isTraversalGroup = true }) {
                TopAppBar(
                    title = { Text(HOME_WORDMARK, style = MaterialTheme.typography.titleLarge.copy(fontFamily = Fraunces), color = p.ink, modifier = Modifier.semantics { heading() }) },
                    actions = {
                        IconButton(onClick = { search.toChips = true; scope.launch { search.bar.animateToExpanded() } }, modifier = Modifier.size(TOUCH)) {
                            Icon(painterResource(R.drawable.ic_filter_list), contentDescription = FILTERS)
                        }
                        OverflowMenu(includeSystem, onIncludeSystemChange, onAbout, onHowToRead)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = p.surface, scrolledContainerColor = p.surface),
                    modifier = Modifier.testTag("bar"),
                )
                SearchBar(
                    state = search.bar, inputField = { SearchField(search) },
                    modifier = Modifier.fillMaxWidth().padding(start = Space.screen, end = Space.screen, bottom = Space.s).testTag("search"),
                )
            }
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
            item(key = "glance") { AtAGlance(summary, latest, bundleLine, onOpen, Modifier.padding(horizontal = Space.screen).testTag("glance"), notice, records) }
            val row = @Composable { app: InstalledApp, index: Int, count: Int ->
                HomeRow(app, explanations[app.packageName], reviews[app.packageName], checks[app.packageName], index, count, onOpen)
            }
            tierSections(TIER_SECTIONS.map { tier -> tier to ordered.filter { explanations[it.packageName]?.tier?.tier == tier } }, openSections, row)
            item(key = "ad") { NoAdTile(Modifier.padding(start = Space.screen, end = Space.screen, top = Space.xl)) }
        }
    }
    ExpandedFullScreenSearchBar(state = search.bar, inputField = { SearchField(search) }) {
        SearchView(search, installed, explanations, reviews, includeSystem, onOpen, onSources = { sheet = it })
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

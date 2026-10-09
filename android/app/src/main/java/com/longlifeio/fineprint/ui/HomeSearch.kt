package com.longlifeio.fineprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.ListFilter
import com.longlifeio.fineprint.explain.SEARCH_HINT
import com.longlifeio.fineprint.explain.coverageLabel
import com.longlifeio.fineprint.explain.listOrder
import com.longlifeio.fineprint.explain.matchCount
import com.longlifeio.fineprint.explain.systemGroups
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView

/*
 * The home's search (docs/METHOD.md, The home): a Material search bar under the wordmark bar. Tapping it opens the
 * search view, where the results follow what you type and the filter chips sit under the field; the filter button in
 * the bar opens the same view with the chips first. At rest the home has no chip row.
 */

/** What the search holds; kept above the home, so a result's page and Back return to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class HomeSearch internal constructor(val bar: SearchBarState, val text: TextFieldState, chosen: MutableState<Set<ListFilter>>) {
    var filters: Set<ListFilter> by chosen

    /** Opened from the filter button: the chips take focus, not the field, so no keyboard covers them. */
    var toChips by mutableStateOf(false)

    val query: String get() = text.text.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberHomeSearch(): HomeSearch {
    val bar = rememberSearchBarState()
    val text = rememberTextFieldState()
    val chosen = rememberSaveable(stateSaver = FILTER_SAVER) { mutableStateOf(setOf<ListFilter>()) }
    return remember(bar, text, chosen) { HomeSearch(bar, text, chosen) }
}

/** Filters survive rotation: saved as their names. */
private val FILTER_SAVER = listSaver<Set<ListFilter>, String>(save = { f -> f.map { it.name } }, restore = { names -> names.map(ListFilter::valueOf).toSet() })

/** The search field: the bar at rest, and the top of the search view. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchField(search: HomeSearch) {
    val keyboard = LocalSoftwareKeyboardController.current
    SearchBarDefaults.InputField(
        textFieldState = search.text,
        searchBarState = search.bar,
        onSearch = { keyboard?.hide() },
        // Opened from the filter button, the field can't take focus while the view opens (Material asks for it then).
        modifier = Modifier.focusProperties { canFocus = !search.toChips },
        placeholder = { Text(SEARCH_HINT, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = if (search.query.isEmpty()) null else {
            { IconButton(onClick = { search.text.clearText() }, modifier = Modifier.size(TOUCH)) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear search") } }
        },
    )
}

/**
 * The search view: the filter chips, the number of apps (read out as it changes), then the results, each a 48dp
 * row with the app's icon, name, tier chip and how far FinePrint has looked. With System on and chosen, grouped by maker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchView(
    search: HomeSearch,
    installed: List<InstalledApp>,
    explanations: Map<String, Explanation>,
    reviews: Map<String, ReviewView>,
    includeSystem: Boolean,
    onOpen: (InstalledApp) -> Unit,
    onSources: (SheetContent) -> Unit,
) {
    val results = remember(installed, explanations, reviews, search.query, search.filters) {
        listOrder(installed, explanations, reviews.mapValues { it.value.status }, search.query, search.filters)
    }
    val firstChip = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!search.toChips) return@LaunchedEffect
        // A frame on, Material's request for the field's focus has come and gone: a keyboard, switch or D-pad lands on
        // the first chip, and by touch nothing has focus, so no keyboard covers the chips. Then the field can be tapped.
        withFrameNanos { }
        firstChip.requestFocus()
        search.toChips = false
    }
    DisposableEffect(Unit) { onDispose { search.toChips = false } }
    Column(Modifier.fillMaxSize()) {
        FilterChips(search, includeSystem, firstChip)
        Text(
            matchCount(results.size), style = MaterialTheme.typography.labelLarge, color = LocalPalette.current.muted,
            modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.s).testTag("count").semantics { liveRegion = LiveRegionMode.Polite },
        )
        LazyColumn(Modifier.fillMaxSize().testTag("results")) {
            val row = @Composable { app: InstalledApp, _: Int, _: Int -> SearchResult(app, explanations[app.packageName], reviews[app.packageName], onOpen) }
            if (includeSystem && ListFilter.SYSTEM in search.filters) systemGroupItems(systemGroups(results, explanations), row, onSources)
            else items(results, key = { it.packageName }) { app -> row(app, 0, 0) }
        }
    }
}

/** The chips, wrapped so every one shows: tiers, how far FinePrint has looked, Reviewed, and System when it's on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChips(search: HomeSearch, includeSystem: Boolean, first: FocusRequester) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.xs).testTag("filters"),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        ListFilter.entries.filter { it != ListFilter.SYSTEM || includeSystem }.forEachIndexed { i, f ->
            FilterChip(
                selected = f in search.filters,
                onClick = { search.filters = if (f in search.filters) search.filters - f else search.filters + f },
                label = { Text(f.label) },
                modifier = Modifier.height(TOUCH).testTag("filter:${f.name}").then(if (i == 0) Modifier.focusRequester(first) else Modifier),
            )
        }
    }
}

/** One result: icon, name, tier chip ("Flagged ✓" once reviewed) and how far FinePrint has looked; the row opens the app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchResult(app: InstalledApp, e: Explanation?, review: ReviewView?, onOpen: (InstalledApp) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = TOUCH).clickable(onClickLabel = "Open details") { onOpen(app) }.padding(horizontal = Space.screen, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        AppIcon(app.packageName, label = app.label, tile = p.card) // the search view's own tone is the raised one
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs), itemVerticalAlignment = Alignment.CenterVertically) {
                TierChip(e?.tier?.tier, reviewed = review?.status == ReviewStatus.REVIEWED)
                e?.let(::coverageLabel)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = p.muted) }
            }
        }
    }
}

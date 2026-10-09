package com.longlifeio.fineprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.bundle.OwnerChange
import com.longlifeio.fineprint.bundle.ProceduralNote
import com.longlifeio.fineprint.bundle.Source
import com.longlifeio.fineprint.explain.spoken

/**
 * What the Sources sheet shows for one line: its sources; for a law, who it binds ([scope], under
 * "Who it binds"); for a company, its changes of ownership ([history], a dated chain, oldest first); for a
 * legal claim or a law, the procedural note's under [noteHeading]; and last, when the line's record has one,
 * the record's confidence note ([about], under "About these sources"), never its level.
 * [details] (On the record) come first: who it was against, the record's own words for it, notes.
 */
data class SheetContent(
    val heading: String,
    val sources: List<Source>,
    val note: ProceduralNote?,
    val details: List<String> = emptyList(),
    val noteHeading: String = WHERE_THE_CASE_STANDS,
    val scope: ProceduralNote? = null,
    val history: List<OwnerChange> = emptyList(),
    val about: ProceduralNote? = null,
)

/** A company's acquisitions and changes of control, in its sheet. */
const val CHANGES_OF_OWNERSHIP = "Changes of ownership"

/** What a record's sources leave uncertain, in its own words: the last part of the sheet of each of its lines. */
const val ABOUT_THESE_SOURCES = "About these sources"

/** "2026 · Digital Turbine, Inc. (DT) → Affle MEA FZ-LLC, …": one link of the chain. */
fun ownerLink(change: OwnerChange): String = "${change.date} · ${change.from} → ${change.to}"

/** The note's heading for a lawsuit or ruling, and for a law (a renewal, a repeal). */
const val WHERE_THE_CASE_STANDS = "Where the case stands"
const val CURRENT_STATUS = "Current status"
const val WHO_IT_BINDS = "Who it binds"

private val SOURCE_TYPES = mapOf(
    "privacy_policy" to "Privacy policy",
    "data_safety_label" to "Play data safety label",
    "regulator" to "Regulator",
    "lawsuit" to "Court document",
    "journalism" to "News report",
    "research" to "Research",
    "company_site" to "Company website",
    "breach_notice" to "Breach notice",
    "statute" to "Law",
    "filing" to "Company filing",
    "company_registry" to "Company registry",
    "store_listing" to "Store listing",
)

/** The source's own date, or when FinePrint read an undated page. */
fun sourceDate(source: Source): String = source.asOf ?: source.accessed?.let { "accessed $it" } ?: "undated"

/**
 * One 48dp row per claim, in place of a stack of small links. Its count leaves out the sources of [about], which is
 * about the whole record rather than this claim.
 */
@Composable
fun SourcesRow(
    heading: String,
    sources: List<Source>,
    note: ProceduralNote?,
    onOpen: (SheetContent) -> Unit,
    noteHeading: String = WHERE_THE_CASE_STANDS,
    scope: ProceduralNote? = null,
    history: List<OwnerChange> = emptyList(),
    about: ProceduralNote? = null,
) {
    val count = sources.size + (scope?.sources?.size ?: 0) + (note?.sources?.size ?: 0) + history.sumOf { it.sources.size }
    if (count == 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TOUCH)
            .clickable(onClickLabel = "Show sources", role = Role.Button) {
                onOpen(SheetContent(heading, sources, note, noteHeading = noteHeading, scope = scope, history = history, about = about))
            },
    ) {
        Text(
            "Sources ($count)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesSheet(content: SheetContent, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val all = content.sources + content.scope?.sources.orEmpty() + content.note?.sources.orEmpty() + content.history.flatMap { it.sources } +
        content.about?.sources.orEmpty()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // TalkBack announces the sheet by this name as it opens, in place of Material's "Bottom Sheet".
        modifier = Modifier.semantics { paneTitle = "Sources: ${spoken(content.heading)}" },
        sheetState = rememberModalBottomSheetState(),
        // The handle is tappable (expand, collapse): give it a 48dp target around Material's 32dp pill.
        dragHandle = { Box(Modifier.sizeIn(minWidth = TOUCH, minHeight = TOUCH), contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() } },
    ) {
        LazyColumn(Modifier.testTag("sources"), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    content.heading,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp).clearAndSetSemantics { text = AnnotatedString(spoken(content.heading)); heading() },
                )
            }
            itemsIndexed(content.details) { i, text ->
                Text(
                    text,
                    style = if (i == 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                )
            }
            itemsIndexed(content.sources) { i, s -> SourceCard(s, primary = i == 0, all) { uriHandler.openUri(s.url) } }
            content.scope?.let { notePart(WHO_IT_BINDS, it, all) { s -> uriHandler.openUri(s.url) } }
            if (content.history.isNotEmpty()) historyPart(content.history, all) { s -> uriHandler.openUri(s.url) }
            content.note?.let { notePart(content.noteHeading, it, all) { s -> uriHandler.openUri(s.url) } }
            content.about?.let { notePart(ABOUT_THESE_SOURCES, it, all) { s -> uriHandler.openUri(s.url) } }
        }
    }
}

/** Changes of ownership, oldest first: each change's date, who before and who after, what happened, then its own sources. */
private fun LazyListScope.historyPart(history: List<OwnerChange>, all: List<Source>, open: (Source) -> Unit) {
    item {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            HorizontalDivider()
            Text(CHANGES_OF_OWNERSHIP, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
        }
    }
    for ((i, change) in history.withIndex()) {
        item(key = "owner:$i") {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                Text(ownerLink(change), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.speaks(ownerLink(change)))
                Text(change.event.replaceFirstChar { it.uppercase() }.let { if (it.endsWith(".")) it else "$it." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        itemsIndexed(change.sources) { _, s -> SourceCard(s, primary = false, all) { open(s) } }
    }
}

/** A titled part of the sheet (who a law binds, where a matter stands, about these sources): its text, then its own sources. */
private fun LazyListScope.notePart(title: String, note: ProceduralNote, all: List<Source>, open: (Source) -> Unit) {
    item {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            HorizontalDivider()
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            Text(note.text, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
        }
    }
    itemsIndexed(note.sources) { _, s -> SourceCard(s, primary = false, all) { open(s) } }
}

@Composable
private fun SourceCard(source: Source, primary: Boolean, all: List<Source>, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(source.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            StatusBadge(source.status, historical = false)
        }
        Text(
            listOfNotNull(SOURCE_TYPES[source.type] ?: source.type, sourceDate(source), "primary source".takeIf { primary })
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("“${source.quote}”", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 4.dp))
        source.derivesFrom?.let { id -> all.firstOrNull { it.id == id } }?.let {
            Text(
                "Re-reports ${it.title}, so it doesn't count as independent.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onOpen, modifier = Modifier.padding(top = 4.dp).heightIn(min = TOUCH).semantics { contentDescription = "Open ${source.title}" }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Open")
            }
        }
    }
}

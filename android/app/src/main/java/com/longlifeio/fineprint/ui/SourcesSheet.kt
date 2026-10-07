package com.longlifeio.fineprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.bundle.ProceduralNote
import com.longlifeio.fineprint.bundle.Source

/**
 * What the Sources sheet shows for one line: its sources; for a law, who it binds ([scope], under
 * "Who it binds"); and, for a legal claim or a law, the procedural note's under [noteHeading].
 * [details] (On the record) come first: who it was against, the record's own words for it, notes.
 */
data class SheetContent(
    val heading: String,
    val sources: List<Source>,
    val note: ProceduralNote?,
    val details: List<String> = emptyList(),
    val noteHeading: String = WHERE_THE_CASE_STANDS,
    val scope: ProceduralNote? = null,
)

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

/** One 48dp row per claim, in place of a stack of small links. */
@Composable
fun SourcesRow(
    heading: String,
    sources: List<Source>,
    note: ProceduralNote?,
    onOpen: (SheetContent) -> Unit,
    noteHeading: String = WHERE_THE_CASE_STANDS,
    scope: ProceduralNote? = null,
) {
    val count = sources.size + (scope?.sources?.size ?: 0) + (note?.sources?.size ?: 0)
    if (count == 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TOUCH)
            .clickable(onClickLabel = "Show sources") { onOpen(SheetContent(heading, sources, note, noteHeading = noteHeading, scope = scope)) },
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
    val all = content.sources + content.scope?.sources.orEmpty() + content.note?.sources.orEmpty()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        LazyColumn(Modifier.testTag("sources"), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    content.heading,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
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
            content.note?.let { notePart(content.noteHeading, it, all) { s -> uriHandler.openUri(s.url) } }
        }
    }
}

/** A titled part of the sheet (who a law binds, or where a matter stands): its text, then its own sources. */
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
        OutlinedButton(onClick = onOpen, modifier = Modifier.padding(top = 4.dp).heightIn(min = TOUCH)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Open")
            }
        }
    }
}

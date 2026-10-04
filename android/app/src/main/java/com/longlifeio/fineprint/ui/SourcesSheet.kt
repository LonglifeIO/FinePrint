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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.bundle.ProceduralNote
import com.longlifeio.fineprint.bundle.Source

/** What the Sources sheet shows for one line: its sources and, for a legal claim, the procedural note's. */
data class SheetContent(val heading: String, val sources: List<Source>, val note: ProceduralNote?)

private val SOURCE_TYPES = mapOf(
    "privacy_policy" to "Privacy policy",
    "data_safety_label" to "Play data safety label",
    "regulator" to "Regulator",
    "lawsuit" to "Court document",
    "journalism" to "News report",
    "research" to "Research",
    "company_site" to "Company website",
    "breach_notice" to "Breach notice",
)

/** The source's own date, or when FinePrint read an undated page. */
fun sourceDate(source: Source): String = source.asOf ?: source.accessed?.let { "accessed $it" } ?: "undated"

/** One 48dp row per claim, in place of a stack of small links. */
@Composable
fun SourcesRow(heading: String, sources: List<Source>, note: ProceduralNote?, onOpen: (SheetContent) -> Unit) {
    val count = sources.size + (note?.sources?.size ?: 0)
    if (count == 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TOUCH)
            .clickable(onClickLabel = "Show sources") { onOpen(SheetContent(heading, sources, note)) },
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
    val all = content.sources + content.note?.sources.orEmpty()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    content.heading,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
                )
            }
            itemsIndexed(content.sources) { i, s -> SourceCard(s, primary = i == 0, all) { uriHandler.openUri(s.url) } }
            content.note?.let { note ->
                item {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                        HorizontalDivider()
                        Text(
                            "Where the case stands",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp).semantics { heading() },
                        )
                        Text(note.text, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                    }
                }
                itemsIndexed(note.sources) { _, s -> SourceCard(s, primary = false, all) { uriHandler.openUri(s.url) } }
            }
        }
    }
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

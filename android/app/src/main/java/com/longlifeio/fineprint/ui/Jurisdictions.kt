package com.longlifeio.fineprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.explain.BadgeText
import com.longlifeio.fineprint.explain.CAN_COMPEL
import com.longlifeio.fineprint.explain.CompanyPlace
import com.longlifeio.fineprint.explain.GOVERNMENT_LINES
import com.longlifeio.fineprint.explain.GovernmentLine
import com.longlifeio.fineprint.explain.Governments
import com.longlifeio.fineprint.explain.JURISDICTIONS
import com.longlifeio.fineprint.explain.NONE_PLACED
import com.longlifeio.fineprint.explain.NO_LAWS_REVIEWED
import com.longlifeio.fineprint.explain.STALE_NOTE
import com.longlifeio.fineprint.explain.UNPLACED
import com.longlifeio.fineprint.explain.recordLastReviewed

/**
 * Jurisdictions, at the end of Where it goes: one line saying where the companies that get the data
 * are based; open, each country in alphabetical order with the same wording: its companies, the laws
 * that let its government demand the data, and any purchase or use on record, each with its sources.
 */
fun LazyListScope.jurisdictionsItems(g: Governments, open: Boolean, onToggle: () -> Unit, onSources: (SheetContent) -> Unit) {
    if (g.blocks.isEmpty() && !g.unplaced) return
    item(key = "jurisdictions") {
        Column {
            SubHeader(JURISDICTIONS)
            val toggle = if (g.blocks.isEmpty()) Modifier else Modifier
                .heightIn(min = TOUCH)
                .clickable(onClickLabel = if (open) "Hide the laws" else "Show the laws", onClick = onToggle)
                .semantics { stateDescription = if (open) "Shown" else "Hidden" }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().then(toggle).padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(g.line ?: NONE_PLACED, style = MaterialTheme.typography.bodyMedium)
                    if (g.blocks.isNotEmpty()) {
                        Text(if (open) "Tap to hide the laws" else "Tap to show the laws", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (g.blocks.isNotEmpty()) {
                    Icon(painterResource(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (g.unplaced && g.line != null) Note(UNPLACED)
        }
    }
    if (!open) return
    for (block in g.blocks) {
        item(key = "country:${block.code}") {
            Text(
                block.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).semantics { heading() },
            )
        }
        items(block.companies) { CompanyPlaceRow(it, onSources) }
        items(block.lines) { GovernmentLineRow(it, onSources) }
        if (!block.lawsReviewed) item { Note(NO_LAWS_REVIEWED) }
    }
}

@Composable
private fun CompanyPlaceRow(place: CompanyPlace, onSources: (SheetContent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        Text(place.text, style = MaterialTheme.typography.bodyMedium)
        SourcesRow(place.name, place.sources, null, onSources)
    }
}

/** "Can compel · CLOUD Act (18 U.S.C. § 2713)", its status, what it lets the government do, its sources and, for a law, its review date. */
@Composable
private fun GovernmentLineRow(line: GovernmentLine, onSources: (SheetContent) -> Unit) {
    val kind = GOVERNMENT_LINES[line.kind]
    Column(Modifier.fillMaxWidth()) { GovernmentLineBody(line, kind, onSources); ReviewNotes(line) }
}

@Composable
private fun GovernmentLineBody(line: GovernmentLine, kind: BadgeText?, onSources: (SheetContent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            kind?.let { k ->
                WithDefinition(k.label, k.definition) {
                    Text(k.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(line.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
            StatusBadge(line.status, historical = false)
            if (line.stale) StaleMarker()
        }
        line.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        line.wording?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        SourcesRow(line.title, line.sources, line.note, onSources, noteHeading = if (line.kind == CAN_COMPEL) CURRENT_STATUS else WHERE_THE_CASE_STANDS)
    }
}

/** A law's review date and, once it's stale, the stale note: worded and drawn as under a record. Other lines have neither. */
@Composable
private fun ReviewNotes(line: GovernmentLine) {
    line.lastReviewed?.let { Note(recordLastReviewed(it)) }
    if (line.stale) Note(STALE_NOTE)
}

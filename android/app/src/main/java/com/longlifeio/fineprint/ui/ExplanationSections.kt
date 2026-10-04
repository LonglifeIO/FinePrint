package com.longlifeio.fineprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.bundle.Consequence
import com.longlifeio.fineprint.bundle.ProceduralNote
import com.longlifeio.fineprint.bundle.Source
import com.longlifeio.fineprint.explain.DATA_LABELS
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.FlowLine

private val BUCKET_TITLES = mapOf(
    "stays_here" to ("Stays here" to "Used to run or improve the app."),
    "used_for_more" to ("Used for more" to "Kept by the same company, but used beyond the app: ads, profiling, other products."),
    "goes_elsewhere" to ("Goes elsewhere" to "Shared with, licensed to, or sold to other companies."),
)

/** The data-first top of the explanation screen. */
fun LazyListScope.explanationItems(explanation: Explanation, attribution: String) {
    item { SectionTitle("In plain language") }
    item { Paragraph(explanation.summary) }
    if (explanation.tags.isNotEmpty()) item { Note("Flags: " + explanation.tags.joinToString(" · ")) }
    if (explanation.coverage == "auto") item { Note("No reviewed record for this app yet: lines marked Auto are inferred from tracker categories.") }

    item { SectionTitle("What it collects, who gets it") }
    if (explanation.flows.isEmpty()) item { Note("Nothing to show: no reviewed record and no tracker code found.") }
    for ((bucket, lines) in explanation.flows) {
        val (title, subtitle) = BUCKET_TITLES.getValue(bucket)
        item(key = "bucket:$bucket") {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(lines) { FlowRow(it) }
    }

    if (explanation.consequences.isNotEmpty()) {
        item { SectionTitle("On the record") }
        items(explanation.consequences) { ConsequenceRow(it) }
    }
    if (explanation.applies.isNotEmpty()) {
        item { SectionTitle("This applies to you because") }
        items(explanation.applies) { line ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("${line.permission.substringAfterLast('.')} is granted: ${line.plain}", style = MaterialTheme.typography.bodyMedium)
                Text(line.whyItMatters, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (explanation.reach.isNotEmpty()) {
        item { SectionTitle("What else it can reach") }
        items(explanation.reach) { r ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(r.plain, style = MaterialTheme.typography.bodyMedium)
                Text(r.whyItMatters, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    item {
        Column(Modifier.padding(top = 12.dp)) {
            explanation.exodusNote?.let { Note(it) }
            explanation.lastReviewed?.let { Note("Record last reviewed $it.") }
            if (explanation.stale) Note("This record is more than 180 days old and may be out of date.")
            if (attribution.isNotEmpty()) Note(attribution)
        }
    }
}

@Composable
private fun FlowRow(line: FlowLine) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(DATA_LABELS[line.data] ?: line.data, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            StatusBadge(line.status, line.historical)
        }
        Text("→ ${line.recipient}: ${line.purpose}", style = MaterialTheme.typography.bodyMedium)
        line.wording?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        line.sources.forEach { Citation(it) }
        line.proceduralNote?.let { ProceduralNoteBlock(it) }
    }
}

@Composable
private fun ConsequenceRow(consequence: Consequence) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        StatusBadge(consequence.status, consequence.historical)
        Text(consequence.text, style = MaterialTheme.typography.bodyMedium)
        consequence.wording?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        consequence.sources.forEach { Citation(it) }
        consequence.proceduralNote?.let { ProceduralNoteBlock(it) }
    }
}

/** Where the matter stands in court (a dismissal, an appeal), with its own sources. */
@Composable
private fun ProceduralNoteBlock(note: ProceduralNote) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(note.text, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
        note.sources.forEach { Citation(it) }
    }
}

/** Tappable: opens the source in the browser. Undated pages show when Fine Print read them. */
@Composable
private fun Citation(source: Source) {
    val uriHandler = LocalUriHandler.current
    val date = source.asOf ?: source.accessed?.let { "accessed $it" }
    Text(
        if (date == null) "Source: ${source.title}" else "Source: ${source.title}, $date",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.padding(top = 2.dp).clickable { uriHandler.openUri(source.url) },
    )
}

@Composable
private fun StatusBadge(status: String?, historical: Boolean) {
    val c = MaterialTheme.colorScheme
    val (label, container, content) = when (status) {
        "self_disclosed" -> Triple("Self-disclosed", c.primaryContainer, c.onPrimaryContainer)
        "reported" -> Triple("Reported", c.secondaryContainer, c.onSecondaryContainer)
        "alleged" -> Triple("Alleged", c.tertiaryContainer, c.onTertiaryContainer)
        "adjudicated" -> Triple("Adjudicated", c.errorContainer, c.onErrorContainer)
        else -> Triple("Auto", c.surfaceVariant, c.onSurfaceVariant)
    }
    StatusLabel(if (historical) "$label · historical" else label, container, content)
}

@Composable
private fun Paragraph(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
internal fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

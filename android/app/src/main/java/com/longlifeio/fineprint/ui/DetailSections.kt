package com.longlifeio.fineprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.bundle.SummaryNote
import com.longlifeio.fineprint.explain.APPLIES
import com.longlifeio.fineprint.explain.BUCKETS
import com.longlifeio.fineprint.explain.BUCKET_TEXT
import com.longlifeio.fineprint.explain.COLLECTS
import com.longlifeio.fineprint.explain.DATA_LABELS
import com.longlifeio.fineprint.explain.DEFAULTS
import com.longlifeio.fineprint.explain.DEVICE_ACCESS
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.FlowLine
import com.longlifeio.fineprint.explain.SUMMARY_AUTO
import com.longlifeio.fineprint.explain.SUMMARY_CURATED
import com.longlifeio.fineprint.explain.SUMMARY_INHERITED
import com.longlifeio.fineprint.explain.SectionText
import com.longlifeio.fineprint.explain.WHAT_YOU_CAN_DO
import com.longlifeio.fineprint.explain.WHERE_IT_GOES
import com.longlifeio.fineprint.explain.WhatYouCanDo
import com.longlifeio.fineprint.explain.appliesHeadline
import com.longlifeio.fineprint.explain.canDoHeadline
import com.longlifeio.fineprint.explain.collectsHeadline
import com.longlifeio.fineprint.explain.reachHeadline
import com.longlifeio.fineprint.explain.summaryHeadline
import com.longlifeio.fineprint.explain.whereHeadline

/**
 * The detail screen's sections up to Device access, each a card, always in this order; empty ones are
 * left out except What it collects. On the record (onTheRecordSection), Evidence and Sources follow.
 */
fun LazyListScope.detailSections(
    e: Explanation,
    check: WhatYouCanDo,
    onSources: (SheetContent) -> Unit,
    onOpenSettings: () -> Unit,
    onTick: (String, Boolean) -> Unit,
    jurisdictionsOpen: Boolean = false,
    onToggleJurisdictions: () -> Unit = {},
    buckets: OpenBuckets = OpenBuckets.allOpen(),
) {
    cardTop("summary", if (e.coverage == "curated") SUMMARY_CURATED else if (e.maker?.inherited == true) SUMMARY_INHERITED else SUMMARY_AUTO, summaryHeadline(e))
    cardItem { Paragraph(e.summary) }
    cardItems(e.summaryNotes) { SummaryNoteRow(it, onSources) }
    e.regionCaveat?.let { cardItem { Note(it) } }
    recentChange(e.changes, onSources)
    cardEnd("summary")

    cardTop("collects", COLLECTS, collectsHeadline(e))
    cardItem {
        if (e.collects.isEmpty()) {
            Note("Nothing found: no reviewed record, no tracker code and no data permissions granted.")
        } else {
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                e.collects.forEach { DataChip(it) }
            }
        }
    }
    cardEnd("collects")

    if (e.flows.isNotEmpty() || e.governments.blocks.isNotEmpty()) {
        cardTop("goes", WHERE_IT_GOES, whereHeadline(e))
        for (bucket in BUCKETS) {
            val lines = e.flows[bucket] ?: continue
            cardItem(key = "bucket:$bucket") { BucketHeader(bucket, lines.size) }
            // The first four, then See all N; the chip above keeps the full count.
            val all = buckets.isOpen(bucket) || lines.size <= BUCKET_FIRST
            cardItems(if (all) lines else lines.take(BUCKET_FIRST)) { FlowLineRow(it, onSources) }
            if (lines.size > BUCKET_FIRST) {
                cardItem(key = "more:$bucket") {
                    LinkRow(if (all) "Show the first $BUCKET_FIRST" else "See all ${lines.size}", if (all) R.drawable.ic_expand_less else R.drawable.ic_expand_more) { buckets.toggle(bucket) }
                }
            }
        }
        jurisdictionsItems(e.governments, jurisdictionsOpen, onToggleJurisdictions, onSources)
        cardEnd("goes")
    }

    if (e.applies.isNotEmpty()) {
        cardTop("applies", APPLIES, appliesHeadline(e))
        cardItems(e.applies) { line ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("${line.label}: allowed", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(line.plain, style = MaterialTheme.typography.bodyMedium)
                Text(line.whyItMatters, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        cardItem { LinkRow("Open app settings to change these", R.drawable.ic_open_in_new, onOpenSettings) }
        cardEnd("applies")
    }

    if (check.items.isNotEmpty() || check.inAppText != null) {
        cardTop("todo", WHAT_YOU_CAN_DO, canDoHeadline(check))
        check.summary?.let { cardItem { SegmentedBar(check.limited, check.total, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) } }
        cardItems(check.items, key = { "check:" + it.id }) { CheckRow(it, onTick, onSources, onOpenSettings) }
        check.inAppText?.let { cardItem { Note("Inside the app: $it") } }
        cardEnd("todo")
    }

    if (e.reach.isNotEmpty()) {
        cardTop("reach", DEVICE_ACCESS, reachHeadline(e))
        cardItems(e.reach) { r ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(r.plain, style = MaterialTheme.typography.bodyMedium)
                Text(r.whyItMatters, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        cardEnd("reach")
    }
}

/** A heading inside a card, with its definition (Recent changes, Jurisdictions, Ongoing, …). */
@Composable
internal fun SubHeader(text: SectionText) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 2.dp)) {
        Text(text.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(text.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The bucket's indicator chip with its number of lines, then its definition: the only colour in the card. */
@Composable
private fun BucketHeader(bucket: String, count: Int) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics(mergeDescendants = true) { heading() },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BucketChip(bucket, count, noun = if (count == 1) "line" else "lines")
        Text(BUCKET_TEXT.getValue(bucket).subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A sourced line under the summary, such as what the app's policy says it doesn't do; its footnote follows it. */
@Composable
internal fun SummaryNoteRow(note: SummaryNote, onSources: (SheetContent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        Text(note.text + LocalFootnotes.current.marks(note.sources), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(top = 4.dp)) { StatusBadge(note.status, historical = false) }
        note.wording?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        SourcesRow(note.text, note.sources, null, onSources)
    }
}

/** Data → recipient and purpose with its footnote, then status, attribution and its Sources row. */
@Composable
internal fun FlowLineRow(line: FlowLine, onSources: (SheetContent) -> Unit) {
    val label = DATA_LABELS[line.data] ?: line.data
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text("→ ${line.recipient}: ${line.purpose}" + LocalFootnotes.current.marks(line.sources), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(line.status, line.historical)
            DEFAULTS[line.default]?.let { d ->
                WithDefinition(d.label, d.definition) {
                    Text(d.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        line.wording?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        line.proceduralNote?.let {
            Text(it.text, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 4.dp))
        }
        SourcesRow("$label → ${line.recipient}", line.sources, line.proceduralNote, onSources)
    }
}

@Composable
private fun DataChip(label: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(Corner.chip)) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** A text link with an icon, 48dp tall. */
@Composable
fun LinkRow(text: String, icon: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = TOUCH).clickable(onClick = onClick).padding(horizontal = 16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun Paragraph(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

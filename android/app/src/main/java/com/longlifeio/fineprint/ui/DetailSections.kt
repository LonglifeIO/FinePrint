package com.longlifeio.fineprint.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.explain.APPLIES
import com.longlifeio.fineprint.explain.BUCKETS
import com.longlifeio.fineprint.explain.BUCKET_TEXT
import com.longlifeio.fineprint.explain.COLLECTS
import com.longlifeio.fineprint.explain.DATA_LABELS
import com.longlifeio.fineprint.explain.DEVICE_ACCESS
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.FlowLine
import com.longlifeio.fineprint.explain.GOES_ELSEWHERE
import com.longlifeio.fineprint.explain.STAYS_HERE
import com.longlifeio.fineprint.explain.SUMMARY_AUTO
import com.longlifeio.fineprint.explain.SUMMARY_CURATED
import com.longlifeio.fineprint.explain.SectionText
import com.longlifeio.fineprint.explain.WHAT_YOU_CAN_DO
import com.longlifeio.fineprint.explain.WHERE_IT_GOES
import com.longlifeio.fineprint.explain.WhatYouCanDo

/**
 * The detail screen's sections up to Device access, always in this order; empty ones are left out
 * except What it collects. On the record (onTheRecordSection) and Evidence follow, both collapsed.
 */
fun LazyListScope.detailSections(
    e: Explanation,
    check: WhatYouCanDo,
    onSources: (SheetContent) -> Unit,
    onOpenSettings: () -> Unit,
    onTick: (String, Boolean) -> Unit,
    jurisdictionsOpen: Boolean = false,
    onToggleJurisdictions: () -> Unit = {},
) {
    section("summary", if (e.coverage == "curated") SUMMARY_CURATED else SUMMARY_AUTO)
    item { Paragraph(e.summary) }
    items(e.summaryNotes) { note ->
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                StatusBadge(note.status, historical = false)
            }
            note.wording?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            SourcesRow(note.text, note.sources, null, onSources)
        }
    }
    recentChange(e.changes, onSources)

    section("collects", COLLECTS)
    item {
        if (e.collects.isEmpty()) {
            Note("Nothing found: no reviewed record, no tracker code and no data permissions granted.")
        } else {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                e.collects.forEach { DataChip(it) }
            }
        }
    }

    if (e.flows.isNotEmpty() || e.governments.blocks.isNotEmpty()) {
        section("goes", WHERE_IT_GOES)
        for (bucket in BUCKETS) {
            val lines = e.flows[bucket] ?: continue
            item(key = "bucket:$bucket") { BucketHeader(bucket) }
            items(lines) { FlowLineRow(it, onSources) }
        }
        jurisdictionsItems(e.governments, jurisdictionsOpen, onToggleJurisdictions, onSources)
    }

    if (e.applies.isNotEmpty()) {
        section("applies", APPLIES)
        items(e.applies) { line ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("${line.label}: allowed", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(line.plain, style = MaterialTheme.typography.bodyMedium)
                Text(line.whyItMatters, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { LinkRow("Open app settings to change these", R.drawable.ic_open_in_new, onOpenSettings) }
    }

    if (check.items.isNotEmpty() || check.inAppText != null) {
        section("todo", WHAT_YOU_CAN_DO)
        check.summary?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) } }
        items(check.items, key = { "check:" + it.id }) { CheckRow(it, onTick, onSources, onOpenSettings) }
        check.inAppText?.let { item { Note("Inside the app: $it") } }
    }

    if (e.reach.isNotEmpty()) {
        section("reach", DEVICE_ACCESS)
        items(e.reach) { r ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(r.plain, style = MaterialTheme.typography.bodyMedium)
                Text(r.whyItMatters, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun LazyListScope.section(key: String, text: SectionText) = item(key = "section:$key") { SectionHeader(text) }

@Composable
fun SectionHeader(text: SectionText) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)) {
        Text(text.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(text.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SubHeader(text: SectionText) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 2.dp)) {
        Text(text.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(text.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A bucket's colour, its icon and the colour for content drawn on it. */
@Composable
fun bucketColors(bucket: String): Triple<Color, Color, Int> {
    val s = LocalSignals.current
    return when (bucket) {
        STAYS_HERE -> Triple(s.stays, s.onStays, R.drawable.ic_stays_here)
        GOES_ELSEWHERE -> Triple(s.elsewhere, s.onElsewhere, R.drawable.ic_goes_elsewhere)
        else -> Triple(s.more, s.onMore, R.drawable.ic_used_for_more)
    }
}

/** Icon on the bucket's colour, then its name and definition: never colour alone. */
@Composable
private fun BucketHeader(bucket: String) {
    val (color, onColor, icon) = bucketColors(bucket)
    val text = BUCKET_TEXT.getValue(bucket)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp).background(color, CircleShape)) {
            Icon(painterResource(icon), contentDescription = null, tint = onColor, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(text.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(text.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Data → recipient, purpose, status and attribution; the bucket's colour runs down the left edge. */
@Composable
private fun FlowLineRow(line: FlowLine, onSources: (SheetContent) -> Unit) {
    val (color, _, _) = bucketColors(line.bucket)
    val label = DATA_LABELS[line.data] ?: line.data
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        Column(
            Modifier
                .drawBehind { drawRoundRect(color, size = Size(4.dp.toPx(), size.height), cornerRadius = CornerRadius(2.dp.toPx())) }
                .padding(start = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                StatusBadge(line.status, line.historical)
            }
            Text("→ ${line.recipient}: ${line.purpose}", style = MaterialTheme.typography.bodyMedium)
            line.wording?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            line.proceduralNote?.let {
                Text(it.text, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 4.dp))
            }
            SourcesRow("$label → ${line.recipient}", line.sources, line.proceduralNote, onSources)
        }
    }
}

@Composable
private fun DataChip(label: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, shape = RoundedCornerShape(16.dp)) {
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

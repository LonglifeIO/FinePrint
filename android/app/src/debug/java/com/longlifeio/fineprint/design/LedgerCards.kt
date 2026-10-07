package com.longlifeio.fineprint.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.explain.BUCKETS
import com.longlifeio.fineprint.explain.BUCKET_TEXT
import com.longlifeio.fineprint.explain.GOES_ELSEWHERE
import com.longlifeio.fineprint.explain.USED_FOR_MORE

/*
 * Direction 1, "Ledger cards": every section a tonally filled card with an eyebrow, a human
 * headline, and claims that end in a footnote number; the numbers resolve in a Sources card.
 * Palette A ("Harbour"); Atkinson Hyperlegible Next for text, Fraunces for display.
 */

@Composable
fun LedgerDetail(d: MockupData) {
    val p = LocalPalette.current
    val a = d.detail
    val notes = Footnotes()
    Column(Modifier.fillMaxWidth().background(p.surface).padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppIcon(a.icon, a.app.label, 56.dp)
            Column(Modifier.weight(1f)) {
                Text(a.app.label, style = MaterialTheme.typography.headlineMedium, color = p.onSurface)
                a.developer?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = p.muted) }
            }
        }
        TierChip(a.tier)
        Text(a.e.tier.reason, style = MaterialTheme.typography.bodyLarge, color = p.onSurface)

        LedgerCard("In plain words", "What ${a.app.label} does with what it collects") {
            val first = a.e.summary.substringBefore(". ").trimEnd('.') + "."
            Text(first + notes.mark(a.e.summaryNotes.flatMap { it.sources }.take(1)), style = MaterialTheme.typography.bodyLarge, color = p.onSurface)
        }

        LedgerCard("What it collects", "${a.e.collects.size} kinds of data from this phone") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                a.e.collects.forEach { Text(it, style = MaterialTheme.typography.labelLarge, color = p.onSurface, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(p.surface).padding(horizontal = 10.dp, vertical = 6.dp)) }
            }
        }

        val elsewhere = a.flows(GOES_ELSEWHERE).size
        LedgerCard("Where it goes", if (elsewhere > 0) "Some of it goes to other companies" else "It stays with ${a.app.label}") {
            BUCKETS.filter { a.flows(it).isNotEmpty() }.forEach { b -> LedgerBucket(a, b, notes) }
        }

        a.limitedLine?.let { line ->
            LedgerCard("What you can do", line) {
                SegmentedBar(a.check.limited, a.check.total, p.staysHere.accent, p.outline)
                a.check.items.take(3).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(painterResource(if (item.ticked) R.drawable.ms_check_circle else R.drawable.ms_radio_button_unchecked), contentDescription = null, tint = p.onSurface, modifier = Modifier.size(20.dp))
                        Text(item.label + if (item.ticked) " · done" else "", style = MaterialTheme.typography.bodyMedium, color = p.onSurface)
                    }
                }
            }
        }

        val record = a.e.onTheRecord
        if (record.count > 0) {
            LedgerCard("On the record", "${record.ongoing.size} ongoing · ${record.past.size} past") {
                Text("What regulators and courts have said. FinePrint relays the public record; it doesn't judge.", style = MaterialTheme.typography.bodyMedium, color = p.muted)
            }
        }

        LedgerCard("Sources", "Every number above opens one of these") {
            notes.sources.forEachIndexed { i, s ->
                Text("[${i + 1}] ${s.title} · ${s.dated()}", style = MaterialTheme.typography.bodySmall, color = p.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** One bucket inside Where it goes: its tinted sub-card, then up to three footnoted claims. */
@Composable
private fun LedgerBucket(a: MockApp, b: String, notes: Footnotes) {
    val p = LocalPalette.current
    val c = p.bucket(b)
    val lines = a.flows(b)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.container).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(bucketIcon(b)), contentDescription = null, tint = c.onContainer, modifier = Modifier.size(24.dp))
            Text(bucketWord(b), style = MaterialTheme.typography.titleMedium, color = c.onContainer)
        }
        Text(BUCKET_TEXT.getValue(b).subtitle, style = MaterialTheme.typography.bodySmall, color = c.onContainer)
        showcase(lines, if (b == USED_FOR_MORE) 2 else 3).forEach { line ->
            Text(line.claim() + notes.mark(line.sources.take(1)), style = MaterialTheme.typography.bodyMedium, color = c.onContainer)
            StatusBadgeOn(line.status)
        }
        if (lines.size > 3) Text("+ ${lines.size - 3} more", style = MaterialTheme.typography.labelLarge, color = c.onContainer)
        SourcesLink(lines.sumOf { it.sources.size })
    }
}

@Composable
private fun StatusBadgeOn(status: String?) = Row(Modifier.padding(bottom = 4.dp)) { StatusBadge(status) }

@Composable
private fun LedgerCard(eyebrow: String, headline: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    FillCard {
        Eyebrow(eyebrow, p.staysHere.accent)
        Text(headline, style = MaterialTheme.typography.titleMedium, color = p.onSurface)
        content()
    }
}

@Composable
fun LedgerHome(d: MockupData) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(p.surface).padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your apps", style = MaterialTheme.typography.headlineLarge, color = p.onSurface)
        Text("${d.apps.size} apps · knowledge bundle ${d.bundleVersion}", style = MaterialTheme.typography.bodyMedium, color = p.muted)
        d.apps.forEach { a ->
            FillCard(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(a.icon, a.app.label, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(a.app.label, style = MaterialTheme.typography.titleMedium, color = p.onSurface)
                        Text(a.e.tier.reason, style = MaterialTheme.typography.bodyMedium, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TierChip(a.tier)
                    a.limitedLine?.let { Text(it, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing), color = p.muted) }
                }
            }
        }
    }
}

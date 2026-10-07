package com.longlifeio.fineprint.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.explain.BUCKETS
import com.longlifeio.fineprint.explain.GOES_ELSEWHERE
import com.longlifeio.fineprint.explain.Tier

/*
 * Direction 2, "Calm dashboard": a home screen led by "Your phone at a glance" (plain counts, a
 * segmented bar of flows you've limited, what changed), then the four tiers as collapsible sections,
 * each with icon, word and count. Palette B ("Field notes"); IBM Plex Sans with Literata.
 */

private val EXPANDED = setOf(Tier.FLAGGED, Tier.CAUTION) // Expected and No record yet start collapsed

@Composable
fun DashboardHome(d: MockupData) {
    val p = LocalPalette.current
    val sharing = d.apps.count { it.flows(GOES_ELSEWHERE).isNotEmpty() }
    val limited = d.apps.sumOf { it.check.limited }
    val total = d.apps.sumOf { it.check.total }
    val latest = d.apps.flatMap { a -> a.e.changes.map { a.app.label to it } }.maxByOrNull { it.second.date }
    Column(Modifier.fillMaxWidth().background(p.surface).padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your apps", style = MaterialTheme.typography.headlineLarge, color = p.onSurface)
        FillCard(radius = 28.dp, padding = 24.dp) {
            Eyebrow("At a glance", p.staysHere.accent)
            Text(
                "On the record, $sharing of your ${d.apps.size} apps send some data to other companies. You've limited $limited of the $total flows you can change.",
                style = MaterialTheme.typography.titleLarge, color = p.onSurface,
            )
            Text("Apps with data in each place, on the record", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 4.dp))
            BucketCounts(Modifier.padding(bottom = 4.dp)) { b -> d.apps.count { it.flows(b).isNotEmpty() } }
            SegmentedBar(limited, total, p.staysHere.accent, p.outline, Modifier.padding(top = 4.dp))
            Text("$limited of $total flows limited by your settings", style = MaterialTheme.typography.labelLarge, color = p.onSurface)
            latest?.let { (name, change) ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ms_update), contentDescription = null, tint = p.muted, modifier = Modifier.size(18.dp))
                    Text("What changed: $name, ${change.date}. ${change.text}", style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        listOf(Tier.FLAGGED, Tier.CAUTION, Tier.EXPECTED, null).forEach { t ->
            val apps = d.apps.filter { it.tier == t }
            TierSection(t, apps, expanded = t in EXPANDED)
        }
        NoAdTile()
    }
}

@Composable
private fun TierSection(t: Tier?, apps: List<MockApp>, expanded: Boolean) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { role = Role.Button; stateDescription = if (expanded) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.align(Alignment.Top).padding(top = 4.dp)) { TierGlyph(t, 24.dp, p.onSurface) }
            Column(Modifier.weight(1f)) {
                Text(tierName(t), style = MaterialTheme.typography.titleMedium, color = p.onSurface)
                Text(tierDescriptor(t), style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
            Text("${apps.size}", style = MaterialTheme.typography.labelLarge, color = p.onTierChip,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(p.tierChip).padding(horizontal = 10.dp, vertical = 4.dp))
            Icon(painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more), contentDescription = null, tint = p.onSurface)
        }
        if (expanded && apps.isNotEmpty()) {
            FillCard(padding = 4.dp) {
                apps.forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 68.dp, end = 12.dp), color = p.outline)
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AppIcon(a.icon, a.app.label, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(a.app.label, style = MaterialTheme.typography.titleSmall, color = p.onSurface)
                            Text(a.e.tier.reason, style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardDetail(d: MockupData) {
    val p = LocalPalette.current
    val a = d.detail
    Column(Modifier.fillMaxWidth().background(p.surface).padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppIcon(a.icon, a.app.label, 56.dp)
            Column(Modifier.weight(1f)) {
                Text(a.app.label, style = MaterialTheme.typography.headlineMedium, color = p.onSurface)
                a.developer?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = p.muted) }
            }
        }
        FillCard(radius = 28.dp, padding = 24.dp) {
            Eyebrow("At a glance", p.staysHere.accent)
            TierChip(a.tier)
            Text(a.e.tier.reason, style = MaterialTheme.typography.titleMedium, color = p.onSurface)
            Text("Lines on record in each place", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 4.dp))
            BucketCounts(Modifier.padding(bottom = 4.dp)) { b -> a.flows(b).size }
            a.limitedLine?.let {
                SegmentedBar(a.check.limited, a.check.total, p.staysHere.accent, p.outline, Modifier.padding(top = 4.dp))
                Text(it, style = MaterialTheme.typography.labelLarge, color = p.onSurface)
            }
        }
        BUCKETS.filter { a.flows(it).isNotEmpty() }.forEach { b ->
            val c = p.bucket(b)
            FillCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(bucketIcon(b)), contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
                    Text("${bucketWord(b)} · ${a.flows(b).size}", style = MaterialTheme.typography.titleMedium, color = p.onSurface)
                }
                showcase(a.flows(b), 2).forEach { line ->
                    Text(line.claim(), style = MaterialTheme.typography.bodyMedium, color = p.onSurface)
                    StatusBadge(line.status)
                }
                if (a.flows(b).size > 2) Text("+ ${a.flows(b).size - 2} more", style = MaterialTheme.typography.labelLarge, color = p.onSurface)
                SourcesLink(a.flows(b).sumOf { it.sources.size })
            }
        }
        val record = a.e.onTheRecord
        if (record.count > 0) {
            FillCard {
                Eyebrow("On the record")
                Text("${record.ongoing.size} ongoing · ${record.past.size} past · tap to show", style = MaterialTheme.typography.titleMedium, color = p.onSurface)
            }
        }
    }
}

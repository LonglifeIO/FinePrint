package com.longlifeio.fineprint.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.explain.BUCKETS
import com.longlifeio.fineprint.explain.FlowLine
import com.longlifeio.fineprint.explain.GOES_ELSEWHERE
import com.longlifeio.fineprint.explain.USED_FOR_MORE

/*
 * Direction 3, "Label-first": the app's own store description as the hero, verbatim and
 * attributed, with FinePrint's sourced lines as its fine print; then the three buckets as a label
 * card that rhymes with privacy labels. Palette B ("Field notes"); Atkinson Hyperlegible Next with
 * Fraunces italic for the quoted words.
 */

private val SENSITIVE = setOf("precise_location", "movement_and_driving", "sensitive_personal_data", "health", "financial", "biometric", "childrens_data", "contacts")

/** The fine print under the quote: lines that go elsewhere or are used for more, sensitive data first. */
private fun finePrint(a: MockApp): List<FlowLine> =
    showcase((a.flows(GOES_ELSEWHERE) + a.flows(USED_FOR_MORE)).filter { it.status != null }.sortedBy { if (it.data in SENSITIVE) 0 else 1 }, 4)

private fun statusWord(s: String?): String = when (s) {
    "self_disclosed" -> "Self-disclosed"; "reported" -> "Reported"; "alleged" -> "Alleged (not proven in court)"; "adjudicated" -> "Adjudicated"; else -> "Auto"
}

@Composable
fun LabelDetail(d: MockupData) {
    val p = LocalPalette.current
    val a = d.detail
    Column(Modifier.fillMaxWidth().background(p.surface).padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppIcon(a.icon, a.app.label, 56.dp)
            Column(Modifier.weight(1f)) {
                Text(a.app.label, style = MaterialTheme.typography.headlineMedium, color = p.onSurface)
                a.developer?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = p.muted) }
            }
            TierChip(a.tier)
        }

        FillCard(radius = 28.dp, padding = 24.dp) {
            Eyebrow("Their words", p.staysHere.accent)
            Text(
                "“${d.tagline.text}” *",
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = LocalFonts.current.quote, fontStyle = FontStyle.Italic),
                color = p.onSurface,
            )
            Text("— ${d.tagline.where}, ${d.tagline.asOf}", style = MaterialTheme.typography.labelLarge, color = p.muted)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = p.outline)
            Eyebrow("The fine print", p.staysHere.accent)
            val lines = finePrint(a)
            lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("*", style = MaterialTheme.typography.titleMedium, color = p.onSurface)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(line.claim(), style = MaterialTheme.typography.bodySmall, color = p.onSurface)
                        val s = line.sources.firstOrNull()
                        Text(statusWord(line.status) + (s?.let { " · ${it.title}, ${it.dated()}" } ?: ""), style = MaterialTheme.typography.labelSmall, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            SourcesLink(lines.sumOf { it.sources.size })
        }

        LabelCard(a)

        a.limitedLine?.let { line ->
            FillCard {
                Eyebrow("What you can do")
                Text(line, style = MaterialTheme.typography.titleMedium, color = p.onSurface)
                SegmentedBar(a.check.limited, a.check.total, p.staysHere.accent, p.outline)
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

/** The three buckets as a label: fixed order, rules between groups, icon and word on every row. */
@Composable
private fun LabelCard(a: MockApp) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().border(2.dp, p.onSurface, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Where your data goes", style = MaterialTheme.typography.titleLarge, color = p.onSurface)
        HorizontalDivider(thickness = 6.dp, color = p.onSurface)
        BUCKETS.forEachIndexed { i, b ->
            if (i > 0) HorizontalDivider(thickness = 1.dp, color = p.onSurface)
            val c = p.bucket(b)
            val lines = a.flows(b)
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(bucketIcon(b)), contentDescription = null, tint = c.onContainer,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.container).padding(6.dp).size(20.dp))
                Text(bucketWord(b), style = MaterialTheme.typography.titleMedium, color = p.onSurface, modifier = Modifier.weight(1f))
                Text("${lines.size} ${if (lines.size == 1) "line" else "lines"}", style = MaterialTheme.typography.titleMedium, color = p.onSurface)
            }
            val kinds = lines.map { it.dataLabel() }.distinct()
            Text(if (kinds.isEmpty()) "Nothing on record" else kinds.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = p.muted)
        }
        HorizontalDivider(thickness = 4.dp, color = p.onSurface)
        Text("Each line has a source: the company's own policy, reporting, a lawsuit (not proven in court) or a ruling. Auto lines have none; they are inferred from code found in the app.",
            style = MaterialTheme.typography.labelSmall, color = p.muted)
    }
}

@Composable
fun LabelHome(d: MockupData) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(p.surface).padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your apps", style = MaterialTheme.typography.headlineLarge, color = p.onSurface)
        Text("What's on record for each app, in three places. A 0 means nothing on record, not nothing at all.", style = MaterialTheme.typography.bodyMedium, color = p.muted)
        d.apps.forEach { a ->
            FillCard(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(a.icon, a.app.label, 40.dp)
                    Text(a.app.label, style = MaterialTheme.typography.titleMedium, color = p.onSurface, modifier = Modifier.weight(1f))
                    TierChip(a.tier)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BUCKETS.forEach { b ->
                        val c = p.bucket(b)
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(c.container).padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(painterResource(bucketIcon(b)), contentDescription = null, tint = c.onContainer, modifier = Modifier.size(16.dp))
                            Text("${a.flows(b).size} ${shortWord(b)}", style = MaterialTheme.typography.labelSmall, color = c.onContainer, maxLines = 1)
                        }
                    }
                }
            }
        }
        NoAdTile()
    }
}

private fun shortWord(b: String) = when (b) { GOES_ELSEWHERE -> "elsewhere"; USED_FOR_MORE -> "for more"; else -> "here" }


package com.longlifeio.fineprint.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.explain.BUCKETS
import com.longlifeio.fineprint.explain.BUCKET_TEXT
import com.longlifeio.fineprint.explain.GOES_ELSEWHERE
import com.longlifeio.fineprint.explain.STAYS_HERE
import com.longlifeio.fineprint.explain.Tier

fun Palette.bucket(b: String): BucketColours = when (b) { STAYS_HERE -> staysHere; GOES_ELSEWHERE -> goesElsewhere; else -> usedForMore }
fun bucketIcon(b: String): Int = when (b) { STAYS_HERE -> R.drawable.ic_stays_here; GOES_ELSEWHERE -> R.drawable.ic_goes_elsewhere; else -> R.drawable.ic_used_for_more }
fun bucketWord(b: String): String = BUCKET_TEXT.getValue(b).title
/**
 * The brief's tier glyphs: a filled diamond (Flagged), a half-filled circle (Caution), a check in a
 * circle (Expected) and a dashed circle (No record yet). Shapes, not warning signs; the word is always beside them.
 */
@Composable
fun TierGlyph(t: Tier?, size: Dp, color: Color) {
    if (t == Tier.EXPECTED) {
        Icon(painterResource(R.drawable.ms_check_circle), contentDescription = null, tint = color, modifier = Modifier.size(size))
        return
    }
    Canvas(Modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val r = this.size.minDimension / 2 - stroke
        when (t) {
            Tier.FLAGGED -> drawPath(Path().apply {
                moveTo(center.x, center.y - r); lineTo(center.x + r, center.y); lineTo(center.x, center.y + r); lineTo(center.x - r, center.y); close()
            }, color)
            Tier.CAUTION -> {
                drawCircle(color, r, center, style = Stroke(stroke))
                drawArc(color, 90f, 180f, true, topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r))
            }
            else -> drawCircle(color, r, center, style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()))))
        }
    }
}

/** "WHERE IT GOES": small caps with tracking, a heading for TalkBack. */
@Composable
fun Eyebrow(text: String, color: Color = LocalPalette.current.muted) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.semantics { heading() })
}

/** A tier as a neutral chip: icon and word, never a hue; No record yet gets a dashed outline. */
@Composable
fun TierChip(t: Tier?) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(8.dp)
    val base = Modifier.clip(shape)
    val chip = if (t == null) base.dashedBorder(p.muted, 8.dp) else base.background(p.tierChip)
    Row(chip.heightIn(min = 32.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TierGlyph(t, 18.dp, if (t == null) p.muted else p.onTierChip)
        Text(tierName(t), style = MaterialTheme.typography.labelLarge, color = if (t == null) p.muted else p.onTierChip)
    }
}

/** A status badge in neutral ink: icon and word; Alleged always says it isn't proven. */
@Composable
fun StatusBadge(status: String?) {
    val p = LocalPalette.current
    val (icon, word) = when (status) {
        "self_disclosed" -> R.drawable.ms_campaign to "Self-disclosed"
        "reported" -> R.drawable.ms_article to "Reported"
        "alleged" -> R.drawable.ms_help to "Alleged (not proven in court)"
        "adjudicated" -> R.drawable.ms_gavel to "Adjudicated"
        else -> R.drawable.ms_code to "Auto: inferred from code"
    }
    Row(
        Modifier.border(1.dp, p.outline, RoundedCornerShape(8.dp)).heightIn(min = 28.dp).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = p.onSurface, modifier = Modifier.size(16.dp))
        Text(word, style = MaterialTheme.typography.labelSmall, color = p.onSurface)
    }
}

/** The one way into sources: "Sources (n)" and a chevron, 48dp tall. */
@Composable
fun SourcesLink(n: Int) {
    if (n == 0) return // lines inferred from code carry no sources
    val p = LocalPalette.current
    Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Sources ($n)", style = MaterialTheme.typography.labelLarge, color = p.primary)
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = p.primary, modifier = Modifier.size(18.dp))
    }
}

/** Your choices adding up: one segment per flow (continuous past 12). Not a ring, not a score. */
@Composable
fun SegmentedBar(limited: Int, total: Int, accent: Color, track: Color, modifier: Modifier = Modifier) {
    if (total <= 0) return
    Canvas(modifier.fillMaxWidth().height(10.dp).semantics { contentDescription = "$limited of $total flows limited" }) {
        val h = size.height
        if (total > 12) {
            drawRoundRect(track, size = Size(size.width, h), cornerRadius = CornerRadius(h / 2))
            drawRoundRect(accent, size = Size(size.width * limited / total, h), cornerRadius = CornerRadius(h / 2))
        } else {
            val gap = 4.dp.toPx()
            val w = (size.width - gap * (total - 1)) / total
            repeat(total) { i ->
                drawRoundRect(if (i < limited) accent else track, topLeft = Offset(i * (w + gap), 0f), size = Size(w, h), cornerRadius = CornerRadius(h / 2))
            }
        }
    }
}

/** An app's icon from this phone (the owner's own install), or its initial when there's none. */
@Composable
fun AppIcon(bitmap: ImageBitmap?, label: String, size: Dp) {
    val p = LocalPalette.current
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(p.tierChip), contentAlignment = Alignment.Center) {
            Text(label.take(1), style = MaterialTheme.typography.titleLarge, color = p.onTierChip, textAlign = TextAlign.Center)
        }
    }
}

/** A rounded, tonally filled card with 20dp padding. */
@Composable
fun FillCard(modifier: Modifier = Modifier, color: Color = LocalPalette.current.card, radius: Dp = 24.dp, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(radius)).background(color).padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

fun Modifier.dashedBorder(color: Color, radius: Dp) = drawBehind {
    val stroke = 1.5.dp.toPx()
    drawRoundRect(
        color, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()), style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/** The ironic ad tile: a dashed outline, plain words, no "Sponsored", nothing styled like a real ad. */
@Composable
fun NoAdTile() {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().dashedBorder(p.muted, 24.dp).padding(20.dp)
            .semantics { contentDescription = "A joke, not an ad: FinePrint has no ads and no trackers." },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("An ad that knows nothing about you", style = MaterialTheme.typography.titleMedium, color = p.onSurface)
        Text("FinePrint has no ads and no trackers, so this space stays empty.", style = MaterialTheme.typography.bodyMedium, color = p.muted)
    }
}

/** The three buckets' counts: tiles side by side, or full-width rows once large text no longer fits three across. */
@Composable
fun BucketCounts(modifier: Modifier = Modifier, count: (String) -> Int) {
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { BUCKETS.forEach { BucketCount(it, count(it), wide = true) } }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { BUCKETS.forEach { BucketCount(it, count(it), Modifier.weight(1f)) } }
    }
}

/** A bucket's icon, word and count, never colour alone. */
@Composable
private fun BucketCount(bucket: String, count: Int, modifier: Modifier = Modifier, wide: Boolean = false) {
    val c = LocalPalette.current.bucket(bucket)
    val tile = modifier.clip(RoundedCornerShape(16.dp)).background(c.container).padding(12.dp)
    val parts = @Composable {
        Icon(painterResource(bucketIcon(bucket)), contentDescription = null, tint = c.onContainer, modifier = Modifier.size(20.dp))
        Text("$count", style = MaterialTheme.typography.titleLarge, color = c.onContainer)
        Text(bucketWord(bucket), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing), color = c.onContainer)
    }
    if (wide) Row(tile.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { parts() }
    else Column(tile, verticalArrangement = Arrangement.spacedBy(4.dp)) { parts() }
}

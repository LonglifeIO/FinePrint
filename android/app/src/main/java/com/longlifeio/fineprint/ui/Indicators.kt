package com.longlifeio.fineprint.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.explain.BUCKET_TEXT
import com.longlifeio.fineprint.explain.GOES_ELSEWHERE
import com.longlifeio.fineprint.explain.NO_RECORD
import com.longlifeio.fineprint.explain.REVIEWED
import com.longlifeio.fineprint.explain.STAYS_HERE
import com.longlifeio.fineprint.explain.Tier

/** The brief's tier glyphs: a filled diamond, a half-filled circle, a check in a circle, a dashed circle. */
fun tierGlyph(tier: Tier?): Int = when (tier) {
    Tier.FLAGGED -> R.drawable.ic_tier_flagged
    Tier.CAUTION -> R.drawable.ic_tier_caution
    Tier.EXPECTED -> R.drawable.ms_check_circle
    null -> R.drawable.ic_tier_no_record
}

fun bucketGlyph(bucket: String): Int = when (bucket) {
    STAYS_HERE -> R.drawable.ic_stays_here
    GOES_ELSEWHERE -> R.drawable.ic_goes_elsewhere
    else -> R.drawable.ic_used_for_more
}

/** A tier's colour is its bucket's, by the formula that links them; Not checked yet is grey. */
fun Palette.tone(tier: Tier?): Tone = when (tier) {
    Tier.FLAGGED -> elsewhere
    Tier.CAUTION -> more
    Tier.EXPECTED -> stays
    null -> noRecord
}

fun Palette.tone(bucket: String): Tone = when (bucket) {
    STAYS_HERE -> stays
    GOES_ELSEWHERE -> elsewhere
    else -> more
}

/**
 * The one place tier and bucket colours appear: icon, word and, where there is one, a count, on the
 * tone's fill. The fills hold 3:1 against the page and the cards, so they need no border; [dashed]
 * draws "Not checked yet"'s edge. [reviewed] adds a check after the word ("Flagged ✓"). [spoken] is what
 * TalkBack reads instead.
 */
@Composable
fun IndicatorChip(
    icon: Int,
    word: String,
    tone: Tone,
    spoken: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    reviewed: Boolean = false,
    dashed: Boolean = false,
) {
    val shape = RoundedCornerShape(Corner.chip)
    val edge = when {
        dashed -> Modifier.dashedOutline(tone.content, Corner.chip)
        tone.border != null -> Modifier.border(1.dp, tone.border, shape) // a faint tint's hairline
        else -> Modifier
    }
    Row(
        modifier
            // Spoken as text, not a content description: a row that merges the chip then reads its own lines too.
            .clearAndSetSemantics { text = AnnotatedString(spoken) }
            .clip(shape)
            .background(tone.container)
            .then(edge)
            .heightIn(min = 32.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TextIcon(icon, 18.sp, tone.content)
        Text(word, style = MaterialTheme.typography.labelLarge, color = tone.content)
        count?.let { Text("$it", style = MaterialTheme.typography.labelLarge, color = tone.content) }
        if (reviewed) TextIcon(R.drawable.ic_check, 16.sp, tone.content)
    }
}

/**
 * An icon that sits beside words and means something with them (a chip's glyph, a badge's): sized in
 * sp, so it grows with the text when you choose a larger font size (TextIconTest).
 */
@Composable
fun TextIcon(icon: Int, size: TextUnit, tint: Color, modifier: Modifier = Modifier) =
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = modifier.size(with(LocalDensity.current) { size.toDp() }))

/** A tier as an indicator chip: "Flagged", "Flagged 3", "Flagged ✓"; [noun] names what's counted. */
@Composable
fun TierChip(tier: Tier?, modifier: Modifier = Modifier, count: Int? = null, noun: String = "apps", reviewed: Boolean = false) {
    val word = tier?.label ?: NO_RECORD
    val spoken = listOfNotNull("Tier: $word", count?.let { "$it $noun" }, REVIEWED.takeIf { reviewed }).joinToString(", ")
    IndicatorChip(tierGlyph(tier), word, LocalPalette.current.tone(tier), spoken, modifier, count, reviewed, dashed = tier == null)
}

/** A bucket as an indicator chip with its count: "Goes elsewhere 5". */
@Composable
fun BucketChip(bucket: String, count: Int, noun: String, modifier: Modifier = Modifier) {
    val word = BUCKET_TEXT.getValue(bucket).title
    IndicatorChip(bucketGlyph(bucket), word, LocalPalette.current.tone(bucket), "$word: $count $noun", modifier, count)
}

/**
 * Your settings adding up: one segment per flow you can change, a continuous bar past 12. Not a ring,
 * not a score, and drawn in ink, since it isn't a tier or a bucket. The words beside it say the same,
 * so TalkBack skips it.
 */
@Composable
fun SegmentedBar(limited: Int, total: Int, modifier: Modifier = Modifier) {
    if (total <= 0) return
    val p = LocalPalette.current
    Canvas(modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics { }) {
        val h = size.height
        if (total > 12) {
            drawRoundRect(p.track, size = Size(size.width, h), cornerRadius = CornerRadius(h / 2))
            drawRoundRect(p.ink, size = Size(size.width * limited / total, h), cornerRadius = CornerRadius(h / 2))
        } else {
            val gap = 4.dp.toPx()
            val w = (size.width - gap * (total - 1)) / total
            repeat(total) { i ->
                drawRoundRect(if (i < limited) p.ink else p.track, Offset(i * (w + gap), 0f), Size(w, h), CornerRadius(h / 2))
            }
        }
    }
}

/** A dashed rounded outline, for "Not checked yet" and the ad tile. */
fun Modifier.dashedOutline(color: Color, radius: Dp, width: Dp = 1.5.dp) = drawBehind {
    val stroke = width.toPx()
    drawRoundRect(
        color, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/** "AT A GLANCE": a card's eyebrow, upper case and tracked out; a heading for TalkBack, which reads it in ordinary case. */
@Composable
fun Eyebrow(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, color = LocalPalette.current.muted,
        modifier = Modifier.clearAndSetSemantics { this.text = AnnotatedString(text); heading() },
    )
}

/**
 * The ironic ad tile: a dashed outline on the plain surface, labelled in plain words. No "Sponsored",
 * no button, nothing styled like a real ad, and it says it's a joke to TalkBack.
 */
@Composable
fun NoAdTile(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .dashedOutline(p.outline, Corner.card)
            .clearAndSetSemantics { contentDescription = "A joke, not an ad: FinePrint has no ads and no trackers." }
            .padding(Space.card),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text("An ad that knows nothing about you", style = MaterialTheme.typography.titleMedium, color = p.ink)
        Text("FinePrint has no ads and no trackers, so this space stays empty.", style = MaterialTheme.typography.bodyMedium, color = p.muted)
    }
}

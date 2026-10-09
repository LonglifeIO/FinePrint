package com.longlifeio.fineprint.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.explain.Footnotes
import com.longlifeio.fineprint.explain.SOURCES
import com.longlifeio.fineprint.explain.SectionText
import com.longlifeio.fineprint.explain.spoken

/*
 * The detail screen's cards (G5 stop B, after the brief's "ledger of cards"): each section is one card
 * on the plain surface, its parts spread over several lazy items. The top carries the section's name
 * as an eyebrow, a plain headline in Fraunces and the section's definition; the items share the card's
 * fill; the end rounds it off. Neutral throughout: colour lives only on indicator chips.
 */

/** The page's footnote numbers, for any row that shows a sourced claim; none outside an app's page. */
val LocalFootnotes = staticCompositionLocalOf { Footnotes.NONE }

/**
 * A card's fill, rounded at its [top] and [bottom] corners (0dp where the card goes on above or below, as a section's
 * parts do), and the theme's hairline edge if it has one (Palette.cardEdge): drawn over what's inside, its sides on every
 * part and its top and bottom only where they're rounded, so a card drawn in parts reads as one. Every card is drawn
 * through here, so the edge is on all of them or none (PaletteContrastTest).
 */
fun Modifier.cardFill(p: Palette, top: Dp = Corner.card, bottom: Dp = Corner.card): Modifier {
    val filled = clip(RoundedCornerShape(top, top, bottom, bottom)).background(p.card) // the one card fill
    val edge = p.cardEdge ?: return filled
    return filled.drawWithContent {
        drawContent()
        val half = 0.5.dp.toPx()
        val (t, b) = top.toPx() to bottom.toPx()
        // An open end's line sits just past the part, where the clip hides it.
        val box = Rect(half, if (t > 0f) half else -3 * half, size.width - half, if (b > 0f) size.height - half else size.height + 3 * half)
        val corner = { r: Float -> CornerRadius((r - half).coerceAtLeast(0f)) }
        drawPath(Path().apply { addRoundRect(RoundRect(box, corner(t), corner(t), corner(b), corner(b))) }, edge, style = Stroke(2 * half))
    }
}

/** A card that opens and closes from its top (On the record, Evidence). */
data class CardToggle(val open: Boolean, val what: String, val onToggle: () -> Unit)

/** A card's top: its name, its headline and its definition. */
fun LazyListScope.cardTop(key: String, text: SectionText, headline: String, toggle: CardToggle? = null) =
    item(key = "section:$key") { CardTop(text, headline, toggle) }

@Composable
private fun CardTop(text: SectionText, headline: String, toggle: CardToggle?) {
    val p = LocalPalette.current
    val action = toggle?.let { t ->
        Modifier
            .clickable(onClickLabel = if (t.open) "Hide ${t.what}" else "Show ${t.what}", onClick = t.onToggle)
            .semantics { stateDescription = if (t.open) "Shown" else "Hidden" }
    } ?: Modifier
    Column(Modifier.padding(top = Space.l).padding(horizontal = Space.screen).fillMaxWidth().cardFill(p, bottom = if (toggle?.open == false) Corner.card else 0.dp).then(action)) {
        Row(Modifier.fillMaxWidth().heightIn(min = TOUCH).padding(start = Space.l, end = Space.l, top = Space.card, bottom = if (toggle?.open == false) Space.card else Space.s)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Eyebrow(text.title)
                Text(headline, style = CardHeadline, color = p.ink)
                Text(text.subtitle, style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
            if (toggle != null) {
                Icon(
                    painterResource(if (toggle.open) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                    contentDescription = null, tint = p.ink, modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

/** One part of a card: full width inside the card's fill. */
@Composable
fun CardBody(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = Space.screen).fillMaxWidth().cardFill(LocalPalette.current, 0.dp, 0.dp)) { content() }
}

fun LazyListScope.cardItem(key: Any? = null, content: @Composable () -> Unit) = item(key = key) { CardBody(content) }

fun <T> LazyListScope.cardItems(list: List<T>, key: ((T) -> Any)? = null, content: @Composable (T) -> Unit) =
    items(list, key = key) { CardBody { content(it) } }

/** The card's rounded end. */
fun LazyListScope.cardEnd(key: String) = item(key = "end:$key") {
    Column {
        Spacer(Modifier.padding(horizontal = Space.screen).fillMaxWidth().height(Space.l).cardFill(LocalPalette.current, top = 0.dp))
    }
}

/** The last card: every footnote number on the page, with its source's title and date. Reading only; each line's Sources row opens the sheet. */
fun LazyListScope.sourcesCard(notes: Footnotes) {
    if (notes.ordered.isEmpty()) return
    cardTop("sources", SOURCES, if (notes.ordered.size == 1) "1 source" else "${notes.ordered.size} sources")
    cardItems(notes.ordered.withIndex().toList(), key = { "source:${it.index}" }) { (i, s) ->
        Text(
            "[${i + 1}] ${s.title} · ${sourceDate(s)}",
            style = MaterialTheme.typography.bodySmall,
            color = LocalPalette.current.ink,
            modifier = Modifier.padding(horizontal = Space.l, vertical = 3.dp),
        )
    }
    cardEnd("sources")
}

/** Cards and text stop widening here: on a tablet the column sits in the middle of the screen. */
val MAX_CONTENT = 640.dp

/**
 * A screen's list, its content no wider than [MAX_CONTENT] and centred by padding, so the whole width
 * still scrolls it. [inner] is the Scaffold's padding; [extraBottom] adds room under the last item.
 */
@Composable
fun CentredList(
    inner: PaddingValues,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    extraBottom: Dp = 0.dp,
    content: LazyListScope.() -> Unit,
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val side = ((maxWidth - MAX_CONTENT) / 2).coerceAtLeast(0.dp)
    val direction = LocalLayoutDirection.current
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(
            start = inner.calculateStartPadding(direction) + side, end = inner.calculateEndPadding(direction) + side,
            top = inner.calculateTopPadding(), bottom = inner.calculateBottomPadding() + extraBottom,
        ),
        modifier = modifier.fillMaxSize(),
        content = content,
    )
}

/** Reads [text] to TalkBack as spoken() has it: no footnote marks, "→" as "to". */
fun Modifier.speaks(text: String) = clearAndSetSemantics { this.text = AnnotatedString(spoken(text)) }

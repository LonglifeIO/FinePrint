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

/** A card that opens and closes from its top (On the record, Evidence). */
data class CardToggle(val open: Boolean, val what: String, val onToggle: () -> Unit)

fun LazyListScope.cardTop(key: String, text: SectionText, headline: String, toggle: CardToggle? = null) =
    item(key = "section:$key") { CardTop(text, headline, toggle) }

@Composable
private fun CardTop(text: SectionText, headline: String, toggle: CardToggle?) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(topStart = Corner.card, topEnd = Corner.card, bottomStart = if (toggle?.open == false) Corner.card else 0.dp, bottomEnd = if (toggle?.open == false) Corner.card else 0.dp)
    val action = toggle?.let { t ->
        Modifier
            .clickable(onClickLabel = if (t.open) "Hide ${t.what}" else "Show ${t.what}", onClick = t.onToggle)
            .semantics { stateDescription = if (t.open) "Shown" else "Hidden" }
    } ?: Modifier
    Column(Modifier.padding(top = Space.l).padding(horizontal = Space.screen).fillMaxWidth().clip(shape).background(p.card).then(action)) {
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
    Box(Modifier.padding(horizontal = Space.screen).fillMaxWidth().background(LocalPalette.current.card)) { content() }
}

fun LazyListScope.cardItem(key: Any? = null, content: @Composable () -> Unit) = item(key = key) { CardBody(content) }

fun <T> LazyListScope.cardItems(list: List<T>, key: ((T) -> Any)? = null, content: @Composable (T) -> Unit) =
    items(list, key = key) { CardBody { content(it) } }

/** The card's rounded end. */
fun LazyListScope.cardEnd(key: String) = item(key = "end:$key") {
    Column {
        Spacer(Modifier.padding(horizontal = Space.screen).fillMaxWidth().height(Space.l).clip(RoundedCornerShape(bottomStart = Corner.card, bottomEnd = Corner.card)).background(LocalPalette.current.card))
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

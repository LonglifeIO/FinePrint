package com.longlifeio.fineprint.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.INTRO_ITS_RECORDS
import com.longlifeio.fineprint.INTRO_WHAT_IT_READS
import com.longlifeio.fineprint.explain.BUCKETS
import com.longlifeio.fineprint.explain.BUCKET_TEXT
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The introduction (G5 stop C), shown once on first launch: what FinePrint does, the three places
 * data can go, and what FinePrint is and isn't. Icons and type, no illustration, no scan theatre.
 */

const val INTRO_STANCE = "FinePrint relays the public record; it doesn't judge — nobody's telling you to uninstall anything."

private const val PAGES = 3

/** Whether the introduction has been seen, kept on this phone. */
fun introSeen(context: Context): Boolean = context.getSharedPreferences("intro", Context.MODE_PRIVATE).getBoolean("seen", false)

fun markIntroSeen(context: Context) = context.getSharedPreferences("intro", Context.MODE_PRIVATE).edit().putBoolean("seen", true).apply()

/** [startPage] is for the screenshot tests; the app always starts at the first page. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit, startPage: Int = 0) = FieldNotesTheme {
    val p = LocalPalette.current
    val pager = rememberPagerState(startPage) { PAGES }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(p.surface).safeDrawingPadding().testTag("intro"), horizontalAlignment = Alignment.CenterHorizontally) {
        // Keeps its height on the last page, where Skip is gone, so the title doesn't jump.
        Row(Modifier.widthIn(max = MAX_CONTENT).fillMaxWidth().heightIn(min = TOUCH).padding(horizontal = Space.s), horizontalArrangement = Arrangement.End) {
            if (pager.currentPage < PAGES - 1) TextButton(onClick = onDone, modifier = Modifier.heightIn(min = TOUCH)) { Text("Skip") }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(
                Modifier.fillMaxSize().wrapContentWidth().widthIn(max = MAX_CONTENT).verticalScroll(rememberScrollState()).padding(horizontal = Space.xl, vertical = Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.l),
            ) {
                when (page) {
                    0 -> WhatItDoes()
                    1 -> ThreePlaces()
                    else -> Stance()
                }
            }
        }
        Row(
            Modifier.widthIn(max = MAX_CONTENT).fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dots(pager.currentPage)
            Spacer(Modifier.weight(1f))
            if (pager.currentPage < PAGES - 1) {
                Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, modifier = Modifier.heightIn(min = TOUCH)) { Text("Next") }
            } else {
                Button(onClick = onDone, modifier = Modifier.heightIn(min = TOUCH)) { Text("Show my apps") }
            }
        }
    }
}

/** "1 of 3" as three dots, the current one filled; TalkBack reads the words. */
@Composable
private fun Dots(current: Int) {
    val p = LocalPalette.current
    Row(Modifier.clearAndSetSemantics { contentDescription = "Page ${current + 1} of $PAGES" }, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        repeat(PAGES) { i -> Box(Modifier.size(8.dp).clip(CircleShape).background(if (i == current) p.ink else p.track)) }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = LocalPalette.current.ink, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = LocalPalette.current.ink)
}

/** Page 1: the three buckets' glyphs come in one after another (decoration: page 2 reads them), then what FinePrint does and where. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhatItDoes() {
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { repeat(BUCKETS.size) { delay(250); shown = it + 1 } } // the animator scale applies; 0 snaps
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.heightIn(min = 40.dp).clearAndSetSemantics { }) {
        BUCKETS.forEachIndexed { i, b ->
            AnimatedVisibility(visible = shown > i, enter = fadeIn()) {
                val word = BUCKET_TEXT.getValue(b).title
                IndicatorChip(bucketGlyph(b), word, LocalPalette.current.tone(b), word)
            }
        }
    }
    Title("See where your apps' data can go")
    Body(INTRO_WHAT_IT_READS ?: "FinePrint reads the code of the apps on this phone and compares it with what it has checked. It shows where each app's data can go, and the source of each claim.")
    Body(INTRO_ITS_RECORDS ?: "It all happens on this phone. FinePrint downloads its records whole and never tells anyone which apps you have.")
}

/** Page 2: the three buckets as chips, each with its definition, one stop each for TalkBack. */
@Composable
private fun ThreePlaces() {
    Title("Three places your data can go")
    BUCKETS.forEach { b ->
        val text = BUCKET_TEXT.getValue(b)
        Column(Modifier.semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            IndicatorChip(bucketGlyph(b), text.title, LocalPalette.current.tone(b), text.title)
            Text(text.subtitle, style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.ink)
        }
    }
    Body("Each claim says where it comes from. That's the company itself, reporters, a lawsuit (not proven in court), a ruling, or tracker code in the app.")
}

/** Page 3: what FinePrint is and isn't, and why it asks Android for your app list. */
@Composable
private fun Stance() {
    Title(INTRO_STANCE)
    Body("You decide what to limit. FinePrint shows which settings change what, and lets you mark an app reviewed.")
    Body("To show your apps, FinePrint asks Android for the list of apps installed on this phone. The list stays on this phone.")
}

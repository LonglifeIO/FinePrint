package com.longlifeio.fineprint.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.bundle.GuidedPath
import com.longlifeio.fineprint.bundle.PathStep
import com.longlifeio.fineprint.explain.CheckItem
import com.longlifeio.fineprint.explain.HIDE_THE_STEPS
import com.longlifeio.fineprint.explain.SHOW_ME_HOW
import com.longlifeio.fineprint.explain.STEP_BACK
import com.longlifeio.fineprint.explain.STEP_NEXT
import com.longlifeio.fineprint.explain.TICK_ONCE_CHANGED
import com.longlifeio.fineprint.explain.howToReach
import com.longlifeio.fineprint.explain.openThe
import com.longlifeio.fineprint.explain.stepCount
import com.longlifeio.fineprint.explain.tapThe
import com.longlifeio.fineprint.explain.tickedOnLine
import com.longlifeio.fineprint.explain.pathLine

/**
 * What a guided path's last card opens: the app itself ([app], null where it can't, with [appUnavailable] saying why), or
 * a web page, handed to the browser ([link]); the app sends nothing itself.
 */
class PathOpeners(val app: (() -> Unit)? = null, val appUnavailable: String? = null, val link: (String) -> Unit = {})

/**
 * "Show me how" under an item you tick that has a path (docs/METHOD.md, What you can do). It opens, in place, the path to
 * the setting one card at a time, in the company's own help page's words. Each card names the screen, then draws a
 * wireframe of no app in particular: grey rows, with the one to tap highlighted and labelled. The last card opens the
 * app or the web page and says where the steps come from, a walk in the app or the help page; under it is the item's own
 * tick box. The help page's quotes are one tap away, under the cards, as every claim's sources are.
 */
@Composable
internal fun ShowMeHow(item: CheckItem, appName: String, openers: PathOpeners, onTick: (String, Boolean) -> Unit, onSources: (SheetContent) -> Unit) {
    val path = item.path ?: return
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { open = !open }, modifier = Modifier.padding(start = 64.dp).heightIn(min = TOUCH).testTag("show-me-how")) {
            Text(if (open) HIDE_THE_STEPS else SHOW_ME_HOW)
        }
        if (open) {
            var saved by rememberSaveable(item.id) { mutableIntStateOf(0) }
            val step = saved.coerceIn(0, path.steps.lastIndex) // a newer bundle's path may be shorter
            val last = step == path.steps.lastIndex
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StepCard(path.steps[step], step, path.steps.size, onSwipe = { saved = (step + it).coerceIn(0, path.steps.lastIndex) }) {
                    if (last) LastCardEnd(item, path, appName, openers)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { saved = step - 1 }, enabled = step > 0, modifier = Modifier.heightIn(min = TOUCH)) { Text(STEP_BACK) }
                    TextButton(onClick = { saved = step + 1 }, enabled = !last, modifier = Modifier.heightIn(min = TOUCH)) { Text(STEP_NEXT) }
                }
                SourcesRow(howToReach(item.label), path.sources, null, onSources)
                if (last) PathTick(item, onTick)
            }
        }
    }
}

/**
 * One step: its count, which TalkBack announces each time the step changes (a live region), then the screen's name, the
 * card's heading; the wireframe; the note. A swipe sideways moves a step, as Back and Next do.
 */
@Composable
private fun StepCard(step: PathStep, index: Int, count: Int, onSwipe: (Int) -> Unit, end: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val far = with(LocalDensity.current) { 48.dp.toPx() }
    Surface(
        shape = RoundedCornerShape(12.dp), color = p.surface, border = BorderStroke(1.dp, p.divider),
        modifier = Modifier.fillMaxWidth().testTag("step-card").pointerInput(index) {
            var moved = 0f
            detectHorizontalDragGestures(
                onDragStart = { moved = 0f },
                onDragEnd = { if (moved <= -far) onSwipe(1) else if (moved >= far) onSwipe(-1) },
            ) { _, dx -> moved += dx }
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(
                    stepCount(index + 1, count), style = MaterialTheme.typography.labelMedium, color = p.muted,
                    modifier = Modifier.testTag("step-count").semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(step.screen, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("step-heading").semantics { heading() })
            }
            Wireframe(step.tap)
            step.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            end()
        }
    }
}

/** Grey rows of no app in particular, the one to tap raised, edged and labelled in the help page's words; TalkBack reads only that. */
@Composable
private fun Wireframe(tap: String) {
    val p = LocalPalette.current
    val rounded = RoundedCornerShape(6.dp)
    Column(
        Modifier.fillMaxWidth().border(1.dp, p.divider, RoundedCornerShape(8.dp)).padding(12.dp).testTag("wireframe")
            .clearAndSetSemantics { contentDescription = tapThe(tap) },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Grey(0.45f)
        Grey(0.8f)
        Row(Modifier.fillMaxWidth().background(p.raised, rounded).border(2.dp, p.ink, rounded).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(tap, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = p.ink)
        }
        Grey(0.65f)
        Grey(0.7f)
    }
}

@Composable
private fun Grey(width: Float) = Box(Modifier.fillMaxWidth(width).height(12.dp).background(LocalPalette.current.track, RoundedCornerShape(6.dp)))

/**
 * The last card's end: Open the app (or the web page), then where the path comes from: a walk in the app, marked Stale
 * 90 days on, or the help page, which never is.
 */
@Composable
private fun ColumnScope.LastCardEnd(item: CheckItem, path: GuidedPath, appName: String, openers: PathOpeners) {
    val url = path.url
    val open: (() -> Unit)? = if (url != null) ({ openers.link(url) }) else openers.app
    Button(onClick = { open?.invoke() }, enabled = open != null, modifier = Modifier.heightIn(min = TOUCH)) {
        Text(openThe(if (url != null) item.label else appName))
    }
    if (open == null) openers.appUnavailable?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted) }
    if (path.stale && item.verifiedOn != null) StaleMarker()
    Text(pathLine(appName, path, item.verifiedOn), style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted)
}

/** The item's own tick box, again under the last card: the same tick, with the same day. */
@Composable
private fun PathTick(item: CheckItem, onTick: (String, Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = item.ticked, role = Role.Checkbox) { onTick(item.id, it) }.heightIn(min = TOUCH).testTag("path-tick"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { Checkbox(checked = item.ticked, onCheckedChange = null) }
        Column(Modifier.padding(start = 12.dp)) {
            Text(item.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(item.tickedOn?.takeIf { item.ticked }?.let(::tickedOnLine) ?: TICK_ONCE_CHANGED, style = MaterialTheme.typography.labelMedium)
        }
    }
}

package com.longlifeio.fineprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.explain.CHECK_LEGEND
import com.longlifeio.fineprint.explain.CheckItem
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.OPEN_APP_INFO
import com.longlifeio.fineprint.explain.WHAT_YOU_CAN_DO
import com.longlifeio.fineprint.explain.WhatYouCanDo
import com.longlifeio.fineprint.explain.appInfoNote
import com.longlifeio.fineprint.explain.canDoSummary
import com.longlifeio.fineprint.explain.tickedOnLine

/**
 * What you can do (docs/METHOD.md, What you can do), open with the page under the Summary: the bar, the key to its
 * boxes, then each item, the settings inside the app, or why there's nothing to limit; last, Open app info and its note.
 */
internal fun LazyListScope.whatYouCanDoSection(
    e: Explanation,
    check: WhatYouCanDo,
    cards: OpenCards,
    onSources: (SheetContent) -> Unit,
    onOpenSettings: (() -> Unit)?,
    settingsUnavailable: String?,
    onTick: (String, Boolean) -> Unit,
) = section("todo", WHAT_YOU_CAN_DO, canDoSummary(check), "what you can do", cards, headline = true) {
    check.summary?.let { cardItem { SegmentedBar(check.limited, check.total, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) } }
    if (check.items.isNotEmpty()) CHECK_LEGEND?.let { cardItem(key = "check-legend") { Legend(it) } }
    cardItems(check.items, key = { "check:" + it.id }) { CheckRow(it, onTick, onSources, onOpenSettings) }
    check.inAppText?.let { cardItem { Note("Inside the app: $it") } }
    check.nothing?.let { cardItem(key = "nothing-to-limit") { Paragraph(it) } }
    cardItem(key = "app-info") { AppInfo(e.appName, onOpenSettings, settingsUnavailable) }
}

/** "Dashed: FinePrint checks this · Solid: you mark it"; TalkBack reads it without the dot. Each item's last line says which it is too. */
@Composable
private fun Legend(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("check-legend")
            .clearAndSetSemantics { this.text = AnnotatedString(text.replace(" · ", ". ")) },
    )
}

/** Android's app info page for the app, and what happens when you come back; why it can't open, where it can't. */
@Composable
private fun AppInfo(appName: String, onOpenSettings: (() -> Unit)?, unavailable: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = { onOpenSettings?.invoke() }, enabled = onOpenSettings != null, modifier = Modifier.heightIn(min = TOUCH)) { Text(OPEN_APP_INFO) }
        (if (onOpenSettings != null) appInfoNote(appName) else unavailable)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted)
        }
    }
}

/**
 * An Android item FinePrint reads has a dashed box, ticked once Android shows it limits the app, and shows the setting
 * before and after ("Use precise location: On → Off"); the row opens the app's Android settings. An item FinePrint can't
 * see has a solid 48dp checkbox row you tick yourself, with the day you ticked it.
 */
@Composable
internal fun CheckRow(item: CheckItem, onTick: (String, Boolean) -> Unit, onSources: (SheetContent) -> Unit, onOpenSettings: (() -> Unit)?) {
    val row = if (item.automatic) {
        Modifier
            .then(onOpenSettings?.let { Modifier.clickable(onClickLabel = "Open this app's Android settings", onClick = it) } ?: Modifier)
            .semantics(mergeDescendants = true) { stateDescription = if (item.ticked) "Off" else "Still on" }
    } else {
        Modifier.toggleable(value = item.ticked, role = Role.Checkbox) { onTick(item.id, it) }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(row.fillMaxWidth().heightIn(min = TOUCH).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                if (item.automatic) CheckedBox(item.ticked) else Checkbox(checked = item.ticked, onCheckedChange = null)
            }
            Column(Modifier.padding(start = 12.dp)) {
                val title = item.setting ?: item.label
                Text(title + LocalFootnotes.current.marks(item.sources), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.speaks(title))
                Text(item.how, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.effect?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                item.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic) }
                item.tickedOn?.let { Text(tickedOnLine(it), style = MaterialTheme.typography.labelMedium, color = LocalPalette.current.ink) }
                Text(item.subtext, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (item.sources.isNotEmpty()) {
            Box(Modifier.padding(start = 76.dp, end = 16.dp)) { SourcesRow(item.label, item.sources, null, onSources) }
        }
    }
}

/** What FinePrint checks: a checkbox-sized box with a dashed edge, ticked when Android shows the setting limits the app. */
@Composable
private fun CheckedBox(ticked: Boolean) {
    val ink = LocalPalette.current.ink
    Box(Modifier.size(18.dp).dashedOutline(ink, 2.dp, width = 2.dp, dash = 3.dp, gap = 2.dp).testTag("dashed-box"), contentAlignment = Alignment.Center) {
        if (ticked) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = ink, modifier = Modifier.size(14.dp))
    }
}

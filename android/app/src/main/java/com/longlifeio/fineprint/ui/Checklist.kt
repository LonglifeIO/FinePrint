package com.longlifeio.fineprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.explain.CheckItem

/**
 * An Android item FinePrint reads shows a read-only status ("Off ✓" / "On ○"), and the row opens the
 * app's Android settings; an item FinePrint can't see is a 48dp checkbox row you tick yourself.
 */
@Composable
internal fun CheckRow(item: CheckItem, onTick: (String, Boolean) -> Unit, onSources: (SheetContent) -> Unit, onOpenSettings: () -> Unit) {
    val row = if (item.automatic) {
        Modifier
            .clickable(onClickLabel = "Open this app's Android settings", onClick = onOpenSettings)
            .semantics(mergeDescendants = true) { stateDescription = if (item.ticked) "Off" else "Still on" }
    } else {
        Modifier.toggleable(value = item.ticked, role = Role.Checkbox) { onTick(item.id, it) }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(row.fillMaxWidth().heightIn(min = TOUCH).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                if (item.automatic) {
                    Text(
                        if (item.ticked) "Off ✓" else "On ○",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (item.ticked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Checkbox(checked = item.ticked, onCheckedChange = null)
                }
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(item.label + LocalFootnotes.current.marks(item.sources), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.speaks(item.label))
                Text(item.how, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.effect?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                item.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic) }
                Text(item.subtext, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (item.sources.isNotEmpty()) {
            Box(Modifier.padding(start = 76.dp, end = 16.dp)) { SourcesRow(item.label, item.sources, null, onSources) }
        }
    }
}

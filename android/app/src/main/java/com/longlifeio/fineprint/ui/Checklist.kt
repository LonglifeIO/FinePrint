package com.longlifeio.fineprint.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.explain.CheckItem

/**
 * An Android item shows what Android reports and can't be ticked by hand; an in-app item is a
 * 48dp checkbox row you tick yourself.
 */
@Composable
internal fun CheckRow(item: CheckItem, onTick: (String, Boolean) -> Unit, onSources: (SheetContent) -> Unit) {
    val row = if (item.automatic) {
        Modifier.semantics(mergeDescendants = true) { stateDescription = if (item.ticked) "Off in Android settings" else "Still on" }
    } else {
        Modifier.toggleable(value = item.ticked, role = Role.Checkbox) { onTick(item.id, it) }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(row.fillMaxWidth().heightIn(min = TOUCH).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.ticked, onCheckedChange = null)
            Column(Modifier.padding(start = 12.dp)) {
                Text(item.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(item.how, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.effect?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    when {
                        !item.automatic -> "You tick this one."
                        item.ticked -> "Ticked by FinePrint: Android reports it's off."
                        else -> "FinePrint ticks this when Android reports it's off."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (item.sources.isNotEmpty()) {
            Box(Modifier.padding(start = 68.dp, end = 16.dp)) { SourcesRow(item.label, item.sources, null, onSources) }
        }
    }
}

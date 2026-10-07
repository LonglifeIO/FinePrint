package com.longlifeio.fineprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.bundle.Change
import com.longlifeio.fineprint.explain.DIRECTIONS
import com.longlifeio.fineprint.explain.RECENT_CHANGES
import com.longlifeio.fineprint.explain.tierMove

/** Improved, Worsened or Neutral, drawn as an outline like Adjudicated: the word says it, not an alarm colour. */
@Composable
fun DirectionBadge(direction: String) {
    val text = DIRECTIONS[direction] ?: return
    val c = MaterialTheme.colorScheme
    WithDefinition("Change: ${text.label}", text.definition) { StatusLabel(text.label, c.surface, c.onSurfaceVariant, outline = true) }
}

/** Recent changes, under the summary: the latest change, whether it's better or worse, any tier move, its sources. */
fun LazyListScope.recentChange(changes: List<Change>, onSources: (SheetContent) -> Unit) {
    val latest = changes.firstOrNull() ?: return
    cardItem(key = "recent-change") {
        Column {
            SubHeader(RECENT_CHANGES)
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(latest.date, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    DirectionBadge(latest.direction)
                }
                Text(latest.text + LocalFootnotes.current.marks(latest.sources), style = MaterialTheme.typography.bodyMedium)
                tierMove(latest.tierBefore, latest.tierAfter)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SourcesRow("Change of ${latest.date}", latest.sources, null, onSources)
            }
        }
    }
}

/** One History line in On the record: when and what changed, then its direction; it opens the details and sources. */
@Composable
fun ChangeLineRow(change: Change, onDetails: (SheetContent) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TOUCH)
            .clickable(onClickLabel = "Show details and sources") {
                val direction = DIRECTIONS[change.direction]?.let { "${it.label}: ${it.definition}" }
                onDetails(SheetContent("Change of ${change.date}", change.sources, null, listOfNotNull(change.text, direction, tierMove(change.tierBefore, change.tierAfter))))
            }
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Text("${change.date} · ${change.text}" + LocalFootnotes.current.marks(change.sources), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        DirectionBadge(change.direction)
    }
}

package com.longlifeio.fineprint.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.explain.COMPANY_HISTORY
import com.longlifeio.fineprint.explain.HistoryItem

/** On the record's Company history: the developer company's other actions, newest first. */
fun LazyListScope.companyHistoryItems(history: List<HistoryItem>, onSources: (SheetContent) -> Unit) {
    if (history.isEmpty()) return
    item(key = "section:history") { SubHeader(COMPANY_HISTORY) }
    items(history) { HistoryRow(it, onSources) }
}

/** "Action against Google LLC", the badge, what it was, then who, the outcome, the amount and the date. */
@Composable
private fun HistoryRow(item: HistoryItem, onSources: (SheetContent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.subject, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusBadge(item.status, historical = false)
        }
        Text(item.title, style = MaterialTheme.typography.bodyMedium)
        Text(item.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        item.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        item.proceduralNote?.let {
            Text(it.text, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 4.dp))
        }
        SourcesRow(item.title, item.sources, item.proceduralNote, onSources)
    }
}

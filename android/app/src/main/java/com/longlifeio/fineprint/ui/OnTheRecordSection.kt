package com.longlifeio.fineprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.explain.ALSO_REPORTED
import com.longlifeio.fineprint.explain.ONGOING
import com.longlifeio.fineprint.explain.ON_THE_RECORD
import com.longlifeio.fineprint.explain.OnTheRecord
import com.longlifeio.fineprint.explain.PAST
import com.longlifeio.fineprint.explain.RecordLine
import com.longlifeio.fineprint.explain.SectionText

private const val FIRST = 3

/**
 * The last section: collapsed by default ("On the record · 10 items"). Open, it lists one line per
 * item in groups, Ongoing, Past, then Also reported, each newest first: the first three in that
 * order, then "See all". Each line opens its details and sources.
 */
fun LazyListScope.onTheRecordSection(
    record: OnTheRecord,
    open: Boolean,
    onToggle: () -> Unit,
    showAll: Boolean,
    onShowAll: () -> Unit,
    onDetails: (SheetContent) -> Unit,
) {
    if (record.count == 0) return
    item(key = "section:record") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TOUCH)
                .clickable(onClickLabel = if (open) "Hide the record" else "Show the record", onClick = onToggle)
                .semantics { stateDescription = if (open) "Shown" else "Hidden" }
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${ON_THE_RECORD.title} · ${record.count} ${if (record.count == 1) "item" else "items"}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(ON_THE_RECORD.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (open) "Tap to hide" else "Tap to show", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Icon(
                painterResource(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (!open) return
    if (record.actions.none { it.namesThisApp }) item { Note("No action in FinePrint's record names this app.") }
    // The first three lines in page order, then See all for the rest.
    var room = if (showAll) Int.MAX_VALUE else FIRST
    for ((text, lines) in listOf(ONGOING to record.ongoing, PAST to record.past, ALSO_REPORTED to record.alsoReported)) {
        val shown = lines.take(room)
        room -= shown.size
        group(text, shown, onDetails)
    }
    if (!showAll && record.count > FIRST) item { LinkRow("See all", R.drawable.ic_expand_more, onShowAll) }
}

private fun LazyListScope.group(text: SectionText, lines: List<RecordLine>, onDetails: (SheetContent) -> Unit) {
    if (lines.isEmpty()) return
    item { SubHeader(text) }
    items(lines) { RecordLineRow(it, onDetails) }
}

/** "2022-11-14 · Attorneys general of 40 US states · settled · $391.5 million · about Google", then its status. */
@Composable
private fun RecordLineRow(line: RecordLine, onDetails: (SheetContent) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TOUCH)
            .clickable(onClickLabel = "Show details and sources") {
                onDetails(SheetContent(line.title, line.sources, line.proceduralNote, listOfNotNull(line.subject) + line.details))
            }
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Text(line.line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        StatusBadge(line.status, historical = false)
    }
}

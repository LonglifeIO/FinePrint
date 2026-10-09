package com.longlifeio.fineprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.bundle.Change
import com.longlifeio.fineprint.explain.ALSO_REPORTED
import com.longlifeio.fineprint.explain.HISTORY
import com.longlifeio.fineprint.explain.ONGOING
import com.longlifeio.fineprint.explain.ON_THE_RECORD
import com.longlifeio.fineprint.explain.OnTheRecord
import com.longlifeio.fineprint.explain.PAST
import com.longlifeio.fineprint.explain.RecordLine
import com.longlifeio.fineprint.explain.SectionText
import com.longlifeio.fineprint.explain.Tier

private const val FIRST = 3

/**
 * A card, collapsed by default ("10 items on the record"). Open, it lists one line per
 * item in groups, Ongoing, Past, Also reported, then the record's History, each newest first: the
 * first three in that order, then "See all". Each line opens its details and sources. The items that set the
 * app's [tier] ([reasons]) carry its marker, and so does the card's top, closed or open.
 */
fun LazyListScope.onTheRecordSection(
    record: OnTheRecord,
    changes: List<Change>,
    open: Boolean,
    onToggle: () -> Unit,
    showAll: Boolean,
    onShowAll: () -> Unit,
    onDetails: (SheetContent) -> Unit,
    tier: Tier? = null,
    reasons: Set<RecordLine> = emptySet(),
) {
    val count = record.count + changes.size
    if (count == 0) return
    cardTop(
        "record", ON_THE_RECORD, "$count ${if (count == 1) "item" else "items"} on the record", CardToggle(open, "the record", onToggle),
        marker = tier.takeIf { reasons.isNotEmpty() },
    )
    if (!open) return
    if (record.actions.none { it.namesThisApp }) cardItem { Note("No action in FinePrint's record names this app.") }
    // The first three lines in page order, then See all for the rest.
    var room = if (showAll) Int.MAX_VALUE else FIRST
    for ((text, lines) in listOf(ONGOING to record.ongoing, PAST to record.past, ALSO_REPORTED to record.alsoReported)) {
        val shown = lines.take(room)
        room -= shown.size
        group(text, shown, onDetails, tier, reasons)
    }
    changes.take(room).takeIf { it.isNotEmpty() }?.let { shown ->
        cardItem { SubHeader(HISTORY) }
        cardItems(shown) { ChangeLineRow(it, onDetails) }
    }
    if (!showAll && count > FIRST) cardItem { LinkRow("See all", R.drawable.ic_expand_more, onShowAll) }
    cardEnd("record")
}

private fun LazyListScope.group(text: SectionText, lines: List<RecordLine>, onDetails: (SheetContent) -> Unit, tier: Tier?, reasons: Set<RecordLine>) {
    if (lines.isEmpty()) return
    cardItem { SubHeader(text) }
    cardItems(lines) { RecordLineRow(it, onDetails, tier.takeIf { _ -> it in reasons }) }
}

/** "2022-11-14 · Attorneys general of 40 US states · settled · $391.5 million · about Google", then its status; [marker], when it set the tier. */
@Composable
private fun RecordLineRow(line: RecordLine, onDetails: (SheetContent) -> Unit, marker: Tier? = null) {
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
        Column(Modifier.weight(1f)) {
            Text(line.line + LocalFootnotes.current.marks(line.sources), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.speaks(line.line))
            ReasonMarker(marker, Modifier.padding(vertical = 4.dp))
        }
        StatusBadge(line.status, historical = false, forum = line.forum)
    }
}

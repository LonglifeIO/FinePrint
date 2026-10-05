package com.longlifeio.fineprint.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.explain.OTHER_PREINSTALLED
import com.longlifeio.fineprint.explain.OTHER_PREINSTALLED_NOTE
import com.longlifeio.fineprint.explain.SYSTEM
import com.longlifeio.fineprint.explain.SystemGroup
import com.longlifeio.fineprint.explain.fromPolicyForAll
import com.longlifeio.fineprint.explain.groupHeader

/**
 * The system-apps view: preinstalled apps grouped by maker ("Google · 14 apps"), each group headed by
 * the lines its maker's privacy policy gives for all its apps, shown once; then its apps in list order.
 */
fun LazyListScope.systemGroupItems(groups: List<SystemGroup>, row: @Composable (InstalledApp) -> Unit, onSources: (SheetContent) -> Unit) {
    for (group in groups) {
        val maker = group.maker
        item(key = "group:" + (maker?.id ?: "other")) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                Text(
                    groupHeader(maker?.name ?: OTHER_PREINSTALLED, group.apps.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                val note = when {
                    maker == null -> OTHER_PREINSTALLED_NOTE
                    maker.lines.isNotEmpty() || maker.notes.isNotEmpty() -> fromPolicyForAll(maker.name)
                    else -> null
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (maker != null) {
            // The header already says whose policy these come from, for all the group's apps.
            items(maker.notes) { SummaryNoteRow(it.copy(wording = null), onSources) }
            items(maker.lines) { FlowLineRow(it.copy(wording = null), onSources) }
        }
        items(group.apps, key = { it.packageName }) { row(it) }
    }
}

/** "System" on a preinstalled app; in a list row the row is the tap target, so it's just a label. */
@Composable
fun SystemLabel(interactive: Boolean) {
    val c = MaterialTheme.colorScheme
    if (interactive) {
        WithDefinition(SYSTEM.label, SYSTEM.definition) { StatusLabel(SYSTEM.label, c.surfaceVariant, c.onSurfaceVariant) }
    } else {
        StatusLabel(SYSTEM.label, c.surfaceVariant, c.onSurfaceVariant)
    }
}

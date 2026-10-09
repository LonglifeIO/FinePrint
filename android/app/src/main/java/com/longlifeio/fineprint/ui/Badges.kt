package com.longlifeio.fineprint.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.explain.AUTO
import com.longlifeio.fineprint.explain.BADGES
import com.longlifeio.fineprint.explain.HISTORICAL
import com.longlifeio.fineprint.explain.undecided
import kotlinx.coroutines.launch

/** Minimum touch target on every interactive element (Material and WCAG guidance). */
val TOUCH = 48.dp

/**
 * Wraps a small label so that tapping (or long-pressing) it shows its one-line [definition]. The
 * touch target is at least 48dp even though the label is smaller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithDefinition(spoken: String, definition: String, content: @Composable () -> Unit) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(definition) } },
        state = state,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .sizeIn(minWidth = TOUCH, minHeight = TOUCH)
                .clickable(onClickLabel = "Show what this means") { scope.launch { state.show() } }
                .clearAndSetSemantics { contentDescription = "$spoken. $definition" },
        ) { content() }
    }
}

/** A status's icon: never colour, so badges stay neutral ink and tell themselves apart by icon and word. */
private fun statusIcon(status: String?): Int = when (status) {
    "self_disclosed" -> R.drawable.ms_campaign
    "reported" -> R.drawable.ms_article
    "alleged" -> R.drawable.ms_help
    "adjudicated" -> R.drawable.ms_gavel
    else -> R.drawable.ms_code
}

/**
 * Self-disclosed / Reported / Alleged / Adjudicated, or Auto for lines FinePrint inferred: an outlined
 * label in ink with its icon. Alleged always reads "Alleged (not proven in court)", or "Alleged (not yet decided)" for a
 * matter before a regulator ([forum]).
 */
@Composable
fun StatusBadge(status: String?, historical: Boolean, forum: String? = null) {
    val c = MaterialTheme.colorScheme
    val badge = BADGES.getValue(status ?: AUTO)
    val word = if (status == "alleged") "${badge.label} (${undecided(forum)})" else badge.label
    val label = if (historical) "$word · ${BADGES.getValue(HISTORICAL).label.lowercase()}" else word
    val definition = if (historical) "${badge.definition} ${BADGES.getValue(HISTORICAL).definition}" else badge.definition
    WithDefinition("Status: $label", definition) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.border(1.dp, c.outlineVariant, RoundedCornerShape(Corner.chip)).padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            TextIcon(statusIcon(status), 16.sp, c.onSurface)
            Text(label, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing), color = c.onSurface)
        }
    }
}

/** A status in words, for small type such as the fine print: "Alleged (not proven in court)", or "(not yet decided)". */
fun statusWord(status: String?, forum: String? = null): String {
    val label = BADGES.getValue(status ?: AUTO).label
    return if (status == "alleged") "$label (${undecided(forum)})" else label
}

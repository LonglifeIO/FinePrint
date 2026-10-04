package com.longlifeio.fineprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.explain.AUTO
import com.longlifeio.fineprint.explain.BADGES
import com.longlifeio.fineprint.explain.HISTORICAL
import com.longlifeio.fineprint.explain.NO_RECORD
import com.longlifeio.fineprint.explain.NO_RECORD_DEFINITION
import com.longlifeio.fineprint.explain.REVIEWED
import com.longlifeio.fineprint.explain.REVIEWED_DEFINITION
import com.longlifeio.fineprint.explain.Tier
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

/** Self-disclosed / Reported / Alleged / Adjudicated, or Auto for lines FinePrint inferred. */
@Composable
fun StatusBadge(status: String?, historical: Boolean) {
    val c = MaterialTheme.colorScheme
    val badge = BADGES.getValue(status ?: AUTO)
    val (container, content) = when (status) {
        "self_disclosed" -> c.primaryContainer to c.onPrimaryContainer
        "reported" -> c.secondaryContainer to c.onSecondaryContainer
        "alleged" -> c.tertiaryContainer to c.onTertiaryContainer
        "adjudicated" -> c.surface to c.onSurfaceVariant // drawn as an outline: decided, but no alarm colour
        else -> c.surfaceVariant to c.onSurfaceVariant
    }
    val label = if (historical) "${badge.label} · ${BADGES.getValue(HISTORICAL).label.lowercase()}" else badge.label
    val definition = if (historical) "${badge.definition} ${BADGES.getValue(HISTORICAL).definition}" else badge.definition
    WithDefinition("Status: $label", definition) { StatusLabel(label, container, content, outline = status == "adjudicated") }
}

data class TierLook(val label: String, val icon: Int, val container: Color, val content: Color, val definition: String)

/** A tier's colour, icon and words; null is an app with no record and nothing to rate yet. */
@Composable
fun tierLook(tier: Tier?): TierLook {
    val s = LocalSignals.current
    return when (tier) {
        Tier.FLAGGED -> TierLook(tier.label, R.drawable.ic_flagged, s.elsewhere, s.onElsewhere, tier.definition)
        Tier.CAUTION -> TierLook(tier.label, R.drawable.ic_caution, s.more, s.onMore, tier.definition)
        Tier.EXPECTED -> TierLook(tier.label, R.drawable.ic_expected, s.stays, s.onStays, tier.definition)
        null -> TierLook(NO_RECORD, R.drawable.ic_no_record, s.none, s.onNone, NO_RECORD_DEFINITION)
    }
}

/**
 * The tier as icon + word on its colour. [interactive] shows its definition on tap; in list rows the
 * whole row is the tap target instead, so the badge is just announced. [reviewed] (your own mark)
 * mutes it: "Flagged · Reviewed", or in the [compact] list form "Flagged" with a check. The tier
 * itself never changes.
 */
@Composable
fun TierBadge(tier: Tier?, interactive: Boolean = true, reviewed: Boolean = false, compact: Boolean = false) {
    val base = tierLook(tier)
    val c = MaterialTheme.colorScheme
    val look = if (!reviewed) base else base.copy(
        label = if (compact) base.label else "${base.label} · $REVIEWED",
        container = c.surfaceVariant,
        content = c.onSurfaceVariant,
        definition = "${base.definition} $REVIEWED_DEFINITION",
    )
    val badge = @Composable { modifier: Modifier ->
        Surface(color = look.container, contentColor = look.content, shape = RoundedCornerShape(8.dp), modifier = modifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(painterResource(look.icon), contentDescription = null, modifier = Modifier.size(16.dp))
                Text(look.label, style = MaterialTheme.typography.labelLarge)
                if (reviewed && compact) Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
    if (interactive) {
        WithDefinition("Tier: ${look.label}", look.definition) { badge(Modifier) }
    } else {
        val spoken = if (reviewed && compact) "Tier: ${look.label}, $REVIEWED" else "Tier: ${look.label}"
        badge(Modifier.clearAndSetSemantics { contentDescription = spoken })
    }
}

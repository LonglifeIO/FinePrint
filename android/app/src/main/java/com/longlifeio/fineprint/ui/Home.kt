package com.longlifeio.fineprint.ui

import android.content.Context
import android.content.SharedPreferences
import android.util.LruCache
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.appIcon
import com.longlifeio.fineprint.bundle.Change
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.explain.BUCKETS
import com.longlifeio.fineprint.explain.CHANGED
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.Glance
import com.longlifeio.fineprint.explain.NO_RECORD_DEFINITION
import com.longlifeio.fineprint.explain.SYSTEM
import com.longlifeio.fineprint.explain.TIER_SECTIONS
import com.longlifeio.fineprint.explain.Tier
import com.longlifeio.fineprint.explain.WhatYouCanDo
import com.longlifeio.fineprint.explain.coverageLabel
import com.longlifeio.fineprint.explain.glanceHeadline
import com.longlifeio.fineprint.explain.limitedLine
import com.longlifeio.fineprint.explain.openByDefault
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The home screen's parts (G5 stop A): the "At a glance" card, the tier sections' headers, and an
 * app's row inside its section's card. AppListScreen puts them together.
 */

/** "At a glance": what FinePrint lists for your apps, apps per bucket, the flows you've limited, the newest change. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AtAGlance(
    g: Glance, latest: Pair<InstalledApp, Change>?, bundleLine: String, onOpen: (InstalledApp) -> Unit, modifier: Modifier = Modifier,
    notice: String? = null,
    /** "Records: 4 checked, 0 their words only, 6 no record yet." */
    records: String? = null,
) {
    val p = LocalPalette.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Corner.hero)).background(p.card).padding(Space.hero),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Eyebrow("At a glance")
        notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = p.ink) }
        Text(glanceHeadline(g), style = CardHeadline, color = p.ink)
        Text("Apps with lines in each place", style = MaterialTheme.typography.bodySmall, color = p.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            BUCKETS.forEach { BucketChip(it, g.perBucket[it] ?: 0, noun = if (g.perBucket[it] == 1) "app" else "apps") }
        }
        if (g.flows > 0) {
            SegmentedBar(g.limited, g.flows, Modifier.padding(top = Space.xs))
            Text(limitedLine(g), style = MaterialTheme.typography.labelLarge, color = p.ink)
        }
        latest?.let { (app, change) -> WhatChanged(app, change, onOpen) }
        // The card's footer, kept close together: how far FinePrint has looked at these apps, and the bundle it read.
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            records?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = p.muted) }
            Text(bundleLine, style = MaterialTheme.typography.labelSmall, color = p.muted)
        }
    }
}

/** "What changed": the newest change to any of your apps' records; it opens that app. */
@Composable
private fun WhatChanged(app: InstalledApp, change: Change, onOpen: (InstalledApp) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TOUCH)
            .clip(RoundedCornerShape(Corner.chip))
            .clickable(onClickLabel = "Open ${app.label}") { onOpen(app) }
            .padding(vertical = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        TextIcon(R.drawable.ms_update, 20.sp, p.muted)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("What changed · ${app.label} · ${change.date}", style = MaterialTheme.typography.labelLarge, color = p.ink)
            Text(change.text, style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Which tier sections are open, kept on this phone across launches. Before you've opened or closed
 * one, Flagged and Caution are open and Expected and No record yet are closed.
 */
class OpenSections(private val read: (String, Boolean) -> Boolean, private val write: (String, Boolean) -> Unit) {
    private val open = mutableStateMapOf<String, Boolean>().apply { TIER_SECTIONS.forEach { put(key(it), read(key(it), openByDefault(it))) } }

    fun isOpen(tier: Tier?): Boolean = open.getValue(key(tier))

    fun toggle(tier: Tier?) {
        val now = !isOpen(tier)
        open[key(tier)] = now
        write(key(tier), now)
    }

    companion object {
        private fun key(tier: Tier?) = "open:" + (tier?.name ?: "NO_RECORD")
        fun of(prefs: SharedPreferences) = OpenSections({ k, default -> prefs.getBoolean(k, default) }, { k, v -> prefs.edit().putBoolean(k, v).apply() })
        /** Every section open and nothing kept: for tests and screenshots. */
        fun allOpen() = OpenSections({ _, _ -> true }, { _, _ -> })
    }
}

@Composable
fun rememberOpenSections(): OpenSections {
    val context = LocalContext.current
    return remember { OpenSections.of(context.getSharedPreferences("home", Context.MODE_PRIVATE)) }
}

/** The tier sections: a header each (chip and count, the published definition, a chevron), then its apps as one card. */
fun LazyListScope.tierSections(
    sections: List<Pair<Tier?, List<InstalledApp>>>,
    open: OpenSections,
    row: @Composable (InstalledApp, Int, Int) -> Unit,
) {
    for ((tier, apps) in sections) {
        item(key = "section:${tier?.name ?: "NO_RECORD"}") { TierSectionHeader(tier, apps.size, open.isOpen(tier)) { open.toggle(tier) } }
        if (open.isOpen(tier)) itemsIndexed(apps, key = { _, app -> app.packageName }) { i, app -> row(app, i, apps.size) }
    }
}

@Composable
private fun TierSectionHeader(tier: Tier?, count: Int, open: Boolean, onToggle: () -> Unit) {
    val p = LocalPalette.current
    val turn by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "chevron")
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = if (open) "Close" else "Open") { onToggle() }
            .semantics { heading(); stateDescription = if (open) "Expanded" else "Collapsed" }
            .padding(start = Space.screen, end = Space.screen, top = Space.l, bottom = Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TierChip(tier, count = count, noun = if (count == 1) "app" else "apps")
            Spacer(Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, tint = p.ink, modifier = Modifier.rotate(turn))
        }
        Text(tier?.definition ?: NO_RECORD_DEFINITION, style = MaterialTheme.typography.bodySmall, color = p.muted)
    }
}

/** A row's share of its section's card: the first rounds the top, the last the bottom. */
fun Modifier.cardSegment(index: Int, count: Int, color: Color): Modifier {
    val top = if (index == 0) Corner.card else 0.dp
    val bottom = if (index == count - 1) Corner.card else 0.dp
    return padding(horizontal = Space.screen).clip(RoundedCornerShape(top, top, bottom, bottom)).background(color)
}

/**
 * An app in its section: icon, name, the one line that set its tier, what changed since you reviewed
 * it, then its tier chip ("Flagged ✓" once you've marked it reviewed) and the notes that apply.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeRow(app: InstalledApp, e: Explanation?, review: ReviewView?, check: WhatYouCanDo?, index: Int, count: Int, onOpen: (InstalledApp) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().cardSegment(index, count, p.card).clickable(onClickLabel = "Open details") { onOpen(app) }) {
        if (index > 0) HorizontalDivider(Modifier.padding(start = 72.dp, end = Space.l), color = p.divider)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = Space.l, vertical = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            AppIcon(app.packageName, label = app.label)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (app.isSystem) SystemTag()
                }
                Text(e?.tier?.reason ?: "Checking its code…", style = MaterialTheme.typography.bodyMedium, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (review?.status == ReviewStatus.CHANGED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        TextIcon(R.drawable.ms_update, 16.sp, p.ink)
                        Text("$CHANGED: ${review.note}", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing), color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Space.xs),
                ) {
                    TierChip(e?.tier?.tier, reviewed = review?.status == ReviewStatus.REVIEWED)
                    val notes = listOfNotNull(
                        check?.summary,
                        // How far FinePrint has looked (a preinstalled app says whose policy it shows); the No record yet
                        // section's own header already says so for the apps in it.
                        e?.let(::coverageLabel)?.takeIf { e.coverage == "curated" || e.tier.tier != null },
                        "Stale".takeIf { e?.stale == true },
                    )
                    // Each note wraps as a whole ("No record yet" never splits); the dot travels with the note after it.
                    notes.forEachIndexed { i, note ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            if (note == "Stale") TextIcon(R.drawable.ic_stale, 14.sp, p.muted)
                            Text(if (i == 0) note else "· $note", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing), color = p.muted)
                        }
                    }
                }
            }
        }
    }
}

/** "System" on a preinstalled app: a neutral outlined tag (the row is the tap target). */
@Composable
private fun SystemTag() {
    val p = LocalPalette.current
    Text(
        SYSTEM.label,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing),
        color = p.muted,
        modifier = Modifier.border(1.dp, p.divider, RoundedCornerShape(Corner.chip)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

private const val ICON_PX = 96
private val iconCache = LruCache<String, ImageBitmap>(150)

/**
 * The app's own icon from this phone (never bundled), loaded off the main thread and cached; its initial until then, or
 * if it has none, on a [tile] (the raised tone unless said).
 */
@Composable
internal fun AppIcon(packageName: String, size: Dp = 40.dp, label: String = "", tile: Color = Color.Unspecified) {
    val pm = LocalContext.current.packageManager
    val icon by produceState(iconCache.get(packageName), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { appIcon(pm, packageName)?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap() }.getOrNull()
            }?.also { iconCache.put(packageName, it) }
        }
    }
    val bitmap = icon
    if (bitmap == null) {
        val p = LocalPalette.current
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(tile.takeOrElse { p.raised }).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            Text(label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = p.ink)
        }
    } else {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    }
}

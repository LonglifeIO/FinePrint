package com.longlifeio.fineprint.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.bundle.Source
import com.longlifeio.fineprint.bundle.StoreTagline
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.explain.CHANGED
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.FinePrintLine
import com.longlifeio.fineprint.explain.NO_RECORD
import com.longlifeio.fineprint.explain.NO_RECORD_DEFINITION
import com.longlifeio.fineprint.explain.REVIEWED_DEFINITION
import com.longlifeio.fineprint.explain.THEIR_WORDS
import com.longlifeio.fineprint.explain.THE_FINE_PRINT
import com.longlifeio.fineprint.explain.WhatYouCanDo
import com.longlifeio.fineprint.explain.noRecordFrom
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView

/** The app, its tier as an indicator chip ("Flagged ✓" once reviewed) and the line that set it: the answer before the detail. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailHeader(app: InstalledApp, e: Explanation, check: WhatYouCanDo, review: ReviewView, onOpenSettings: () -> Unit) {
    val p = LocalPalette.current
    val reviewed = review.status == ReviewStatus.REVIEWED
    Column(Modifier.padding(horizontal = Space.screen, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, 56.dp, app.label)
            Text(app.label, style = MaterialTheme.typography.headlineSmall, color = p.ink, modifier = Modifier.padding(start = Space.l))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs), itemVerticalAlignment = Alignment.CenterVertically) {
            val tier = e.tier.tier
            val definition = (tier?.definition ?: NO_RECORD_DEFINITION) + if (reviewed) " $REVIEWED_DEFINITION" else ""
            WithDefinition("Tier: ${tier?.label ?: NO_RECORD}${if (reviewed) ", reviewed" else ""}", definition) { TierChip(tier, reviewed = reviewed) }
            if (e.coverage == "auto" && tier != null) {
                Text(e.maker?.takeIf { it.inherited }?.let { noRecordFrom(it.name) } ?: NO_RECORD, style = MaterialTheme.typography.labelLarge, color = p.muted)
            }
            if (app.isSystem) SystemLabel(interactive = true)
            if (e.stale) StaleMarker()
        }
        Text(e.tier.reason, style = MaterialTheme.typography.bodyLarge, color = p.ink)
        if (e.tier.capped) Note("Without a reviewed record, an app is rated Caution at most, never Flagged.")
        if (review.status == ReviewStatus.CHANGED) Text("$CHANGED: ${review.note}.", style = MaterialTheme.typography.bodyMedium, color = p.ink)
        check.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = p.muted) }
        Column {
            PackageName(app.packageName, color = p.muted)
            Text(
                "Version ${app.versionName ?: "unknown"} (${app.versionCode}) · " +
                    (if (app.isSystem) "system app" else "user-installed") + " · " +
                    (if (app.apkPaths.size == 1) "1 APK" else "${app.apkPaths.size} APKs"),
                style = MaterialTheme.typography.bodySmall, color = p.muted,
            )
        }
        Button(onClick = onOpenSettings, modifier = Modifier.heightIn(min = TOUCH)) { Text("Open app settings") }
    }
}

/** "Google Play listing (Canada)", from the listing's address. */
internal fun listingName(url: String): String {
    val uri = Uri.parse(url)
    val store = if (uri.host == "play.google.com") "Google Play listing" else "Store listing"
    val country = when (uri.getQueryParameter("gl")) { "CA" -> "Canada"; "US" -> "United States"; null -> null; else -> uri.getQueryParameter("gl") }
    return country?.let { "$store ($it)" } ?: store
}

/** The listing itself as a source, so the hero's Sources row opens it like any other. */
internal fun StoreTagline.asSource() = Source(sourceUrl, listingName(sourceUrl), "store_listing", "self_disclosed", null, asOf, text)

/**
 * The hero (docs/METHOD.md, An app's page): the app's own short description, verbatim and attributed,
 * then The fine print, at most four of FinePrint's lines, each marked with an asterisk, in small type.
 */
@Composable
internal fun TheirWords(tagline: StoreTagline, lines: List<FinePrintLine>, onSources: (SheetContent) -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.padding(horizontal = Space.screen, vertical = Space.s).fillMaxWidth().clip(RoundedCornerShape(Corner.hero)).background(p.card).padding(Space.hero),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Eyebrow(THEIR_WORDS.title)
        // The asterisk points at the fine print below; TalkBack reads the quote without it.
        Text("“${tagline.text}” *", style = Quote, color = p.ink, modifier = Modifier.clearAndSetSemantics { text = AnnotatedString("“${tagline.text}”") })
        Text("— ${listingName(tagline.sourceUrl)}, read ${tagline.asOf}", style = MaterialTheme.typography.labelLarge, color = p.muted)
        Text(THEIR_WORDS.subtitle, style = MaterialTheme.typography.bodySmall, color = p.muted)
        if (lines.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = Space.s), color = p.divider)
            Eyebrow(THE_FINE_PRINT.title)
            Text(THE_FINE_PRINT.subtitle, style = MaterialTheme.typography.bodySmall, color = p.muted)
            lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.padding(top = Space.xs).semantics(mergeDescendants = true) { }) {
                    Text("*", style = MaterialTheme.typography.titleMedium, color = p.ink, modifier = Modifier.clearAndSetSemantics { })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(line.text, style = MaterialTheme.typography.bodySmall, color = p.ink, modifier = Modifier.speaks(line.text))
                        val s = line.sources.firstOrNull()
                        Text(statusWord(line.status) + (s?.let { " · ${it.title}, ${sourceDate(it)}" } ?: ""), style = MaterialTheme.typography.labelSmall, color = p.muted)
                    }
                }
            }
        }
        val sources = (listOf(tagline.asSource()) + lines.flatMap { it.sources }).distinctBy { it.url + "|" + it.title }
        SourcesRow("${THEIR_WORDS.title} and ${THE_FINE_PRINT.title.lowercase()}", sources, null, onSources)
    }
}

/** "com.life360.android.safetymapd" with a break opportunity after each dot, so a long name wraps at a dot, never mid-word. */
internal fun breakAtDots(name: String) = name.replace(".", ".\u200B")

/** A package name in monospace; TalkBack reads it as written, without the break marks. */
@Composable
internal fun PackageName(name: String, color: Color, modifier: Modifier = Modifier) = Text(
    breakAtDots(name), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = color,
    modifier = modifier.semantics { contentDescription = name },
)

package com.longlifeio.fineprint.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.bundle.Source
import com.longlifeio.fineprint.bundle.StoreTagline
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.explain.CHANGED
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.FinePrintLine
import com.longlifeio.fineprint.explain.FoldedLines
import com.longlifeio.fineprint.explain.NO_RECORD
import com.longlifeio.fineprint.explain.NO_RECORD_DEFINITION
import com.longlifeio.fineprint.explain.REVIEWED_DEFINITION
import com.longlifeio.fineprint.explain.THEIR_WORDS
import com.longlifeio.fineprint.explain.THE_FINE_PRINT
import com.longlifeio.fineprint.explain.Tier
import com.longlifeio.fineprint.explain.WhatYouCanDo
import com.longlifeio.fineprint.explain.asksForReview
import com.longlifeio.fineprint.explain.conditionLine
import com.longlifeio.fineprint.explain.coverageLine
import com.longlifeio.fineprint.explain.whyLine
import com.longlifeio.fineprint.review.ReviewStatus
import com.longlifeio.fineprint.review.ReviewView

/** The app, its tier as an indicator chip ("Flagged ✓" once reviewed) and why ("Why: …"): the answer before the detail. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailHeader(
    app: InstalledApp, e: Explanation, check: WhatYouCanDo, review: ReviewView, onOpenSettings: (() -> Unit)?, settingsUnavailable: String?,
    installLine: String?, onAskForReview: (() -> Unit)?, reviewUnavailable: String?,
) {
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
            if (app.isSystem) SystemLabel(interactive = true)
            if (e.stale) StaleMarker()
        }
        // Why it has its tier: what set it, said as a fact; the lines or legal items that set it carry its marker below.
        Text(whyLine(e) ?: e.tier.reason, style = MaterialTheme.typography.bodyLarge, color = p.ink, modifier = Modifier.testTag("why"))
        // How far FinePrint has looked: checked by a reviewer, their words only, or no record yet.
        Text(coverageLine(e), style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.testTag("coverage"))
        if (e.asksForReview) AskForReview(onAskForReview, reviewUnavailable)
        if (e.tier.capped) Note("Without a reviewed record, an app is rated Caution at most, never Flagged.")
        if (review.status == ReviewStatus.CHANGED) Text("$CHANGED: ${review.note}.", style = MaterialTheme.typography.bodyMedium, color = p.ink)
        check.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = p.muted) }
        Column {
            PackageName(app.packageName, color = p.muted)
            Text(
                installLine ?: ("Version ${app.versionName ?: "unknown"} (${app.versionCode}) · " +
                    (if (app.isSystem) "system app" else "user-installed") + " · " +
                    (if (app.apkPaths.size == 1) "1 APK" else "${app.apkPaths.size} APKs")),
                style = MaterialTheme.typography.bodySmall, color = p.muted,
            )
        }
        Button(onClick = { onOpenSettings?.invoke() }, enabled = onOpenSettings != null, modifier = Modifier.heightIn(min = TOUCH)) { Text("Open app settings") }
        settingsUnavailable?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = p.muted) }
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
 * then The fine print, at most four of FinePrint's lines in reading order (those that set the tier first, with its
 * marker), each marked with an asterisk, in
 * small type, and last what it collects to run the app, folded into one line that opens to those lines.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TheirWords(tagline: StoreTagline, lines: List<FinePrintLine>, folded: FoldedLines?, tier: Tier?, onSources: (SheetContent) -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.padding(horizontal = Space.screen, vertical = Space.s).fillMaxWidth().cardFill(p, Corner.hero, Corner.hero).padding(Space.hero),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Eyebrow(THEIR_WORDS.title)
        // The asterisk points at the fine print below; TalkBack reads the quote without it.
        Text("“${tagline.text}” *", style = Quote, color = p.ink, modifier = Modifier.clearAndSetSemantics { text = AnnotatedString("“${tagline.text}”") })
        Text("— ${listingName(tagline.sourceUrl)}, read ${tagline.asOf}", style = MaterialTheme.typography.labelLarge, color = p.muted)
        Text(THEIR_WORDS.subtitle, style = MaterialTheme.typography.bodySmall, color = p.muted)
        if (lines.isNotEmpty() || folded != null) {
            HorizontalDivider(Modifier.padding(vertical = Space.s), color = p.divider)
            Eyebrow(THE_FINE_PRINT.title)
            Text(THE_FINE_PRINT.subtitle, style = MaterialTheme.typography.bodySmall, color = p.muted)
            lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.padding(top = Space.xs).semantics(mergeDescendants = true) { }) {
                    Text("*", style = MaterialTheme.typography.titleMedium, color = p.ink, modifier = Modifier.clearAndSetSemantics { })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(line.text, style = MaterialTheme.typography.bodySmall, color = p.ink, modifier = Modifier.speaks(line.text))
                        line.conditional?.let { Text(conditionLine(it), style = MaterialTheme.typography.labelSmall, color = p.ink) }
                        val s = line.sources.firstOrNull()
                        // The tier's marker leads the status words on a line that set the tier, on the same row.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs), itemVerticalAlignment = Alignment.CenterVertically) {
                            if (line.setsTier) ReasonMarker(tier)
                            Text(statusWord(line.status, line.forum) + (s?.let { " · ${it.title}, ${sourceDate(it)}" } ?: ""), style = MaterialTheme.typography.labelSmall, color = p.muted)
                        }
                    }
                }
            }
            folded?.let { FoldedRow(it) }
        }
        val sources = (listOf(tagline.asSource()) + lines.flatMap { it.sources } + folded?.lines.orEmpty().flatMap { it.sources })
            .distinctBy { it.url + "|" + it.title }
        SourcesRow("${THEIR_WORDS.title} and ${THE_FINE_PRINT.title.lowercase()}", sources, null, onSources)
    }
}

/** "* Also collected to run the app: usage and crash data", a 48dp row that opens to those lines, each with its badge. */
@Composable
private fun FoldedRow(folded: FoldedLines) {
    val p = LocalPalette.current
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        modifier = Modifier.fillMaxWidth().heightIn(min = TOUCH).testTag("also-collected")
            .clickable(onClickLabel = if (open) "Hide these lines" else "Show these lines", role = Role.Button) { open = !open }
            .semantics(mergeDescendants = true) { stateDescription = if (open) "Expanded" else "Collapsed" },
    ) {
        Text("*", style = MaterialTheme.typography.titleMedium, color = p.ink, modifier = Modifier.clearAndSetSemantics { })
        Text(folded.text, style = MaterialTheme.typography.bodySmall, color = p.ink, modifier = Modifier.weight(1f))
        Icon(painterResource(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more), contentDescription = null, tint = p.muted)
    }
    if (open) folded.lines.forEach { line ->
        Column(Modifier.padding(start = Space.l, top = Space.xs).testTag("also-collected-line").semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(line.text, style = MaterialTheme.typography.bodySmall, color = p.ink, modifier = Modifier.speaks(line.text))
            line.conditional?.let { Text(conditionLine(it), style = MaterialTheme.typography.labelSmall, color = p.ink) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                StatusBadge(line.status, line.historical, line.forum)
                line.sources.firstOrNull()?.let { Text("${it.title}, ${sourceDate(it)}", style = MaterialTheme.typography.labelSmall, color = p.muted) }
            }
        }
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

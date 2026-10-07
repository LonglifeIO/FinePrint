package com.longlifeio.fineprint.design

import androidx.compose.ui.graphics.ImageBitmap
import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.bundle.Source
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.egress.TrackerSignature
import com.longlifeio.fineprint.explain.BUCKETS
import com.longlifeio.fineprint.explain.DATA_LABELS
import com.longlifeio.fineprint.explain.Explanation
import com.longlifeio.fineprint.explain.FlowLine
import com.longlifeio.fineprint.explain.NO_RECORD
import com.longlifeio.fineprint.explain.NO_RECORD_DEFINITION
import com.longlifeio.fineprint.explain.Tier
import com.longlifeio.fineprint.explain.WhatYouCanDo
import com.longlifeio.fineprint.explain.explain
import com.longlifeio.fineprint.explain.whatYouCanDo

/** One app as the mockups show it: the real scan, the real explanation and its icon from this phone. */
data class MockApp(
    val app: InstalledApp,
    val icon: ImageBitmap?,
    val e: Explanation,
    val check: WhatYouCanDo,
    val trackers: Int,
    val developer: String?,
) {
    val tier: Tier? get() = e.tier.tier
    fun flows(bucket: String): List<FlowLine> = e.flows[bucket].orEmpty().filterNot { it.historical }
    /** "3 of 8 flows limited by your settings", or null when nothing goes beyond running the app. */
    val limitedLine: String? get() = check.summary
}

/** The app's own words: its store short description, quoted verbatim, never edited. */
data class Tagline(val text: String, val where: String, val asOf: String)

data class MockupData(val apps: List<MockApp>, val detail: MockApp, val tagline: Tagline, val bundleVersion: String)

const val LIFE360 = "com.life360.android.safetymapd"

/**
 * Life360's description on its Google Play listing (Canada), verbatim from the page's description
 * metadata in the copy saved to pipeline/raw/sources/play-life360-ca on 2026-10-07.
 */
val LIFE360_TAGLINE = Tagline("Family locator & phone tracker: real-time GPS location sharing + SOS", "Google Play listing", "2026-10-07")

/** Everything the mockups show, from the phone's real apps, scans and the downloaded bundle; null until Life360 is scanned. */
fun mockupData(
    apps: List<InstalledApp>,
    results: Map<String, TrackerScanResult>,
    bundle: Bundle,
    signatures: Map<String, TrackerSignature>,
    icon: (String) -> ImageBitmap?,
): MockupData? {
    val feeds = bundle.permissions.mapValues { it.value.feeds }
    val mock = apps.filter { results.containsKey(it.scanKey) }.map { a ->
        val e = explain(a, results[a.scanKey], bundle, signatures)
        val record = bundle.apps[a.packageName]
        val developer = record?.developerCompany?.let { bundle.companies[it]?.name }
        MockApp(a, icon(a.packageName), e, whatYouCanDo(a, e, record, feeds, emptySet()), results[a.scanKey]?.trackers?.size ?: 0, developer)
    }
    val detail = mock.find { it.app.packageName == LIFE360 } ?: return null
    return MockupData(mock.sortedWith(compareBy({ tierOrder(it.tier) }, { it.app.label.lowercase() })), detail, LIFE360_TAGLINE, bundle.version)
}

fun tierOrder(t: Tier?): Int = when (t) { Tier.FLAGGED -> 0; Tier.CAUTION -> 1; Tier.EXPECTED -> 2; null -> 3 }

/** A tier's name and its published definition (the same words as METHOD.md and the app's tooltips). */
fun tierName(t: Tier?): String = t?.label ?: NO_RECORD
fun tierDescriptor(t: Tier?): String = t?.definition ?: NO_RECORD_DEFINITION

/** Numbers each distinct source (document and section) in reading order, so a claim can carry ¹ ² ³ and the sheet can list [1]…[n]. */
class Footnotes {
    private val numbers = LinkedHashMap<String, Int>()
    val sources = ArrayList<Source>()
    fun mark(sources: List<Source>): String = sources.joinToString("") { s ->
        val n = numbers.getOrPut(s.url + "|" + s.title) { this.sources.add(s); this.sources.size }
        superscript(n)
    }
}

private val SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹"
fun superscript(n: Int): String = n.toString().map { SUPERSCRIPTS[it - '0'] }.joinToString("")

/** How many of an app's current lines fall in each bucket. */
fun bucketCounts(a: MockApp): Map<String, Int> = BUCKETS.associateWith { a.flows(it).size }

/** "Precise location → Select business partners: For "their own monetization purposes"". */
fun FlowLine.claim(): String = "${dataLabel()} → $recipient: $purpose"

/** The data's plain name, as the app shows it ("Precise location"). */
fun FlowLine.dataLabel(): String = DATA_LABELS[data] ?: data

/** Up to [n] lines that show the range of evidence: the first of each status (court and lawsuit lines first), then the rest in order. */
fun showcase(lines: List<FlowLine>, n: Int): List<FlowLine> {
    val order = listOf("adjudicated", "alleged", "self_disclosed", "reported")
    val firsts = lines.filter { it.status != null }.groupBy { it.status }.toList()
        .sortedBy { order.indexOf(it.first).let { i -> if (i < 0) 9 else i } }.map { it.second.first() }
    return (firsts + lines).distinct().take(n)
}

/** The source's own date, or when FinePrint read it. */
fun Source.dated(): String = asOf ?: accessed?.let { "read $it" } ?: "undated"

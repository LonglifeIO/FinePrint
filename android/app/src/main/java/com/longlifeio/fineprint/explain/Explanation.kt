package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.AppRecord
import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.bundle.Consequence
import com.longlifeio.fineprint.bundle.ProceduralNote
import com.longlifeio.fineprint.bundle.ReachText
import com.longlifeio.fineprint.bundle.Source
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.egress.TrackerSignature

/** The explanation screen's content: organized by data, with the evidence underneath. */
data class Explanation(
    val summary: String,
    /** "curated" (a reviewed record exists) or "auto" (derived from tracker categories only). */
    val coverage: String,
    /** The record's risk tags in plain words, each with its qualifier when the record gives one. */
    val tags: List<String>,
    /** bucket -> lines, in BUCKETS order; empty buckets are left out. */
    val flows: Map<String, List<FlowLine>>,
    val consequences: List<Consequence>,
    val applies: List<AppliesLine>,
    val reach: List<ReachText>,
    val privacyControls: String?,
    val lastReviewed: String?,
    val stale: Boolean,
    val exodusNote: String?,
)

data class FlowLine(
    val data: String,
    val recipient: String,
    val purpose: String,
    /** null for lines Fine Print derived itself: shown as "auto", with no citation. */
    val status: String?,
    val wording: String?,
    val historical: Boolean,
    /** Primary source first; empty for auto lines. */
    val sources: List<Source>,
    val proceduralNote: ProceduralNote?,
)

data class AppliesLine(val permission: String, val plain: String, val whyItMatters: String)

val BUCKETS = listOf("stays_here", "used_for_more", "goes_elsewhere")

val DATA_LABELS = mapOf(
    "precise_location" to "Precise location",
    "approximate_location" to "Approximate location",
    "movement_and_driving" to "How you move and drive",
    "physical_activity" to "Physical activity",
    "contacts" to "Your contacts",
    "account_identity" to "Your name, email or account",
    "device_identifiers" to "Device and advertising IDs",
    "app_activity" to "What you do in the app",
    "crash_diagnostics" to "Crash and performance data",
    "sensitive_personal_data" to "Sensitive personal data",
)

fun explain(app: InstalledApp, scan: TrackerScanResult?, bundle: Bundle?, signatures: Map<String, TrackerSignature>): Explanation {
    val record = bundle?.apps?.get(app.packageName)
    val detected = scan?.trackers.orEmpty()
    val lines = ArrayList<Pair<String, FlowLine>>()

    record?.dataFlows?.forEach { f ->
        val recipient = f.recipient?.let { bundle.companies[it]?.name } ?: f.recipientLabel ?: "Unnamed recipient"
        lines += f.bucket to FlowLine(f.data, recipient, f.purpose, f.status, f.wording, f.historical, f.sources, f.proceduralNote)
    }
    for (tracker in detected) {
        val trackerRecord = bundle?.trackers?.get(tracker.id)
        if (trackerRecord != null && trackerRecord.dataFlows.isNotEmpty()) {
            val chain = trackerRecord.ownerChain.takeIf { it.isNotEmpty() }?.joinToString(" → ") ?: trackerRecord.owner
            trackerRecord.dataFlows.forEach { f ->
                lines += f.bucket to FlowLine(f.data, chain, f.purpose, f.status, f.wording, f.historical, f.sources, f.proceduralNote)
            }
        } else {
            val categories = signatures[tracker.id]?.categories ?: tracker.categories
            deriveFlows(tracker.name, categories, trackerRecord?.party).forEach { lines += it }
        }
    }
    val flows = BUCKETS.associateWith { b -> lines.filter { it.first == b }.map { it.second }.distinct() }.filterValues { it.isNotEmpty() }

    val shownData = lines.map { it.second.data }.toSet()
    val applies = app.permissions.filter { it.granted }.mapNotNull { p ->
        bundle?.permissions?.get(p.name)?.takeIf { text -> text.feeds.any { it in shownData } }
            ?.let { AppliesLine(p.name, it.plain, it.whyItMatters) }
    }
    val consequences = record?.consequences
        ?: detected.flatMap { bundle?.trackers?.get(it.id)?.consequences.orEmpty() }

    return Explanation(
        summary = record?.summary ?: autoSummary(detected.map { it.name }),
        coverage = if (record != null) "curated" else "auto",
        tags = record?.let { riskTagLabels(it) }.orEmpty(),
        flows = flows,
        consequences = consequences,
        applies = applies,
        reach = app.deviceReach.mapNotNull { bundle?.deviceReach?.get(it) },
        privacyControls = record?.privacyControls,
        lastReviewed = record?.lastReviewed,
        stale = record?.stale == true,
        exodusNote = exodusNote(record?.exodusReport?.trackerCount, record?.trackers.orEmpty(), scan),
    )
}

/**
 * "Exodus lists N trackers; M are adapter references with no code in this app." N comes from the
 * Exodus report pinned in the reviewed record; M counts report trackers this scan saw only as
 * references. Without a record, the count Fine Print can compute on-device is an upper bound.
 */
internal fun exodusNote(reportCount: Int?, reportTrackers: List<String>, scan: TrackerScanResult?): String? {
    if (scan == null) return null
    val found = scan.trackers.count { it.id.startsWith("exodus-") }
    if (reportCount != null) {
        if (reportCount <= found) return null
        val referenced = reportTrackers.count { it in scan.referencedOnly }
        return if (referenced > 0) {
            "Exodus lists $reportCount trackers; $referenced ${if (referenced == 1) "is an adapter reference" else "are adapter references"} with no code in this app."
        } else {
            "Exodus lists $reportCount trackers; Fine Print found code for $found of them in this version."
        }
    }
    val m = scan.referencedOnly.size
    if (m == 0) return null
    return "Exodus may list up to ${found + m}; $m ${if (m == 1) "is an adapter reference" else "are adapter references"} with no code in this app."
}

private val TAG_LABELS = mapOf(
    "location_sale" to "location sale",
    "telematics" to "driving data",
    "ad_profiling" to "ad profiling",
    "cross_app_tracking" to "cross-app tracking",
    "data_broker" to "data brokers",
    "breach" to "data breach",
    "regulatory_action" to "regulatory action",
    "child_data" to "children's data",
)

/**
 * A record's tags in plain words. A qualifier from the record follows its tag, so an action against
 * someone else reads "regulatory action against Allstate/Arity concerning this app's data".
 */
internal fun riskTagLabels(record: AppRecord): List<String> = record.riskTags.map { tag ->
    val label = TAG_LABELS[tag] ?: tag.replace('_', ' ')
    record.riskTagNotes[tag]?.let { "$label $it" } ?: label
}

/**
 * Flows for a tracker with no reviewed record, from its Exodus categories: third-party SDKs that
 * serve the app (crash reports, analytics) "stay here"; advertising, profiling, identification and
 * location go to the SDK's company for its own use ("goes elsewhere"), or stay in the company when
 * the tracker is its own (first party).
 */
internal fun deriveFlows(trackerName: String, categories: List<String>, party: String?): List<Pair<String, FlowLine>> {
    val elsewhere = if (party == "first_party") "used_for_more" else "goes_elsewhere"
    fun line(bucket: String, data: String, purpose: String) =
        bucket to FlowLine(data, trackerName, purpose, null, null, false, emptyList(), null)
    return categories.flatMap { category ->
        when (category.lowercase()) {
            "crash reporting", "crash_reporting" -> listOf(line("stays_here", "crash_diagnostics", "Crash reports for the developer"))
            "analytics" -> listOf(line("stays_here", "app_activity", "Usage statistics for the developer"))
            "advertisement", "advertising" -> listOf(
                line(elsewhere, "device_identifiers", "Advertising"),
                line(elsewhere, "app_activity", "Advertising"),
            )
            "profiling" -> listOf(line(elsewhere, "app_activity", "Building a profile of you"))
            "identification" -> listOf(line(elsewhere, "device_identifiers", "Recognizing you across apps"))
            "location" -> listOf(line(elsewhere, "precise_location", "Location data"))
            else -> emptyList()
        }
    }.distinct()
}

private fun autoSummary(trackerNames: List<String>): String = when (trackerNames.size) {
    0 -> "Fine Print found no tracker code in this app. That doesn't show what the app itself does with your data, and there is no reviewed record for it yet."
    else -> "Fine Print found code from ${trackerNames.size} tracker SDK${if (trackerNames.size == 1) "" else "s"} in this app: " +
        trackerNames.joinToString() + ". There is no reviewed record for this app yet, so what they collect is inferred from each tracker's category."
}

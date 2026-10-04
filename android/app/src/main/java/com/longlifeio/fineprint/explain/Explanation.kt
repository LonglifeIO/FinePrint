package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.AppRecord
import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.bundle.Change
import com.longlifeio.fineprint.bundle.Company
import com.longlifeio.fineprint.bundle.Consequence
import com.longlifeio.fineprint.bundle.DataFlow
import com.longlifeio.fineprint.bundle.ProceduralNote
import com.longlifeio.fineprint.bundle.ReachText
import com.longlifeio.fineprint.bundle.Source
import com.longlifeio.fineprint.bundle.SummaryNote
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.egress.TrackerSignature
import java.time.LocalDate

/** One app's explanation, in the detail screen's section order. */
data class Explanation(
    val appName: String,
    val summary: String,
    /** Sourced lines shown under the summary. */
    val summaryNotes: List<SummaryNote>,
    /** "curated" (a reviewed record exists) or "auto" (inferred from tracker code only). */
    val coverage: String,
    /** Its tier is null for "No record yet", with what the scan found as the line. */
    val tier: TierResult,
    val privacyControls: String?,
    /** Plain labels: data kinds from the flows, then what granted permissions give the app. */
    val collects: List<String>,
    /** bucket -> lines, in BUCKETS order; empty buckets are left out. */
    val flows: Map<String, List<FlowLine>>,
    val applies: List<AppliesLine>,
    /** Actions by regulators and courts (this app's, then the developer's), and what others reported. */
    val onTheRecord: OnTheRecord,
    val reach: List<ReachText>,
    val lastReviewed: String?,
    val stale: Boolean,
    val exodusNote: String?,
    /** Changes to the record, newest first: the first shows under the summary, all under On the record. */
    val changes: List<Change> = emptyList(),
)

data class FlowLine(
    val data: String,
    val bucket: String,
    val recipient: String,
    val purpose: String,
    /** null for lines FinePrint inferred itself ("Auto"). */
    val status: String?,
    /** The attribution phrase; alleged lines always say "not proven in court". */
    val wording: String?,
    val historical: Boolean,
    /** Primary source first; empty for auto lines. */
    val sources: List<Source>,
    val proceduralNote: ProceduralNote?,
    /** The tracker the line comes from; null for lines from the app's own record. */
    val via: String? = null,
    /** The record's flow id, when it has one: how in-app controls say which lines they limit. */
    val id: String? = null,
)

data class AppliesLine(val permission: String, val label: String, val plain: String, val whyItMatters: String)

fun explain(
    app: InstalledApp,
    scan: TrackerScanResult?,
    bundle: Bundle?,
    signatures: Map<String, TrackerSignature>,
    today: LocalDate = LocalDate.now(),
): Explanation {
    val record = bundle?.apps?.get(app.packageName)
    val detected = scan?.trackers.orEmpty()
    val appName = record?.displayName ?: app.label
    val lines = ArrayList<FlowLine>()
    record?.dataFlows?.forEach { f ->
        lines += f.toLine(f.recipient?.let { bundle.companies[it]?.name } ?: f.recipientLabel ?: "Unnamed recipient", via = null)
    }
    lines += trackerLines(detected, bundle, record?.developerCompany, signatures)
    val shown = lines.distinct()
    val granted = app.permissions.filter { it.granted }
    val shownData = shown.map { it.data }.toSet()
    val readable = scan != null && scan.dexFiles > 0
    // With a record, a tracker's legal lines join it only when they name this app; without one, all of them do.
    val fromTrackers = detected.mapNotNull { bundle?.trackers?.get(it.id) }.distinct()
        .flatMap { t -> t.consequences.filter { record == null || it.concernsApp == app.packageName }.map { Said(it, t) } }
    val onRecord = onTheRecord(record, record?.consequences.orEmpty().map { Said(it) } + fromTrackers, bundle, app.packageName)

    return Explanation(
        appName = appName,
        summary = record?.summary ?: autoSummary(detected.map { it.name }),
        summaryNotes = record?.summaryNotes.orEmpty(),
        coverage = if (record != null) "curated" else "auto",
        tier = if (record == null && !readable) TierResult(null, scanFacts(app, scan), "N") else tier(
            curated = record != null,
            appName = appName,
            flows = shown,
            events = onRecord.actions.filter { it.namesThisApp }
                .map { TierEvent(it.status, it.statusKind, true, it.sources, it.label, it.date, it.ongoing, it.dated.takeIf { d -> d != it.date }) },
            reach = app.deviceReach,
            scanFacts = scanFacts(app, scan),
            today = today,
        ),
        privacyControls = record?.privacyControls,
        collects = (shown.map { DATA_LABELS[it.data] ?: it.data } + granted.mapNotNull { permissionLabel(it.name) }).distinct(),
        flows = BUCKETS.associateWith { b -> forDisplay(shown.filter { it.bucket == b }) }.filterValues { it.isNotEmpty() },
        applies = granted.mapNotNull { p ->
            bundle?.permissions?.get(p.name)?.takeIf { text -> text.feeds.any { it in shownData } }
                ?.let { AppliesLine(p.name, permissionLabel(p.name) ?: p.name.substringAfterLast('.'), it.plain, it.whyItMatters) }
        },
        onTheRecord = onRecord,
        reach = app.deviceReach.mapNotNull { bundle?.deviceReach?.get(it) },
        lastReviewed = record?.lastReviewed,
        stale = record?.stale == true,
        exodusNote = exodusNote(record?.exodusReport?.trackerCount, record?.trackers.orEmpty(), scan),
        changes = record?.changes.orEmpty().sortedByDescending { it.date },
    )
}

/**
 * Reviewed lines first, then inferred ones; inferred lines with the same data and purpose become one
 * line naming every tracker ("Amplitude, Branch: Usage statistics for the developer"). Display only:
 * the tier is worked out from the separate lines.
 */
internal fun forDisplay(lines: List<FlowLine>): List<FlowLine> {
    val (inferred, reviewed) = lines.partition { it.status == null }
    return reviewed + inferred.groupBy { it.data to it.purpose }.values.map { group ->
        if (group.size == 1) group.single()
        else group.first().copy(recipient = group.joinToString { it.recipient }, wording = "Inferred from their code in this app", via = null)
    }
}

/** What the scan found, for an app FinePrint can't rate: "No third-party trackers found · 12 permissions". */
internal fun scanFacts(app: InstalledApp, scan: TrackerScanResult?): String {
    val permissions = if (app.permissions.size == 1) "1 permission" else "${app.permissions.size} permissions"
    val trackers = scan?.trackers?.size ?: 0
    return when {
        !app.hasCode -> "No code of its own to check · $permissions"
        scan == null -> "Checking its code…"
        scan.dexFiles == 0 -> "Couldn't read its code · $permissions"
        trackers == 0 -> "No third-party trackers found · $permissions"
        else -> "${if (trackers == 1) "1 tracker" else "$trackers trackers"} found · $permissions"
    }
}

internal fun DataFlow.toLine(recipient: String, via: String?) =
    FlowLine(data, bucket, recipient, purpose, status, attribution(status, wording), historical, sources, proceduralNote, via, id)

/** Alleged lines always say "not proven in court", whatever the record's own wording. */
internal fun attribution(status: String?, wording: String?): String? = when {
    status != "alleged" -> wording
    wording == null -> "Alleged ($NOT_PROVEN)"
    NOT_PROVEN in wording -> wording
    else -> "$wording ($NOT_PROVEN)"
}

/** "Allstate/Arity" when the record gives a short name; otherwise "The Allstate Corporation and its unit Arity". */
internal fun companyName(c: Company): String = when {
    c.shortName != null -> (listOf(c.shortName) + c.subsidiaries).joinToString("/")
    c.subsidiaries.isEmpty() -> c.name
    else -> "${c.name} and its unit${if (c.subsidiaries.size > 1) "s" else ""} ${c.subsidiaries.joinToString(" and ")}"
}

/**
 * "Exodus lists N trackers; M are adapter references with no code in this app." N comes from the
 * Exodus report pinned in the reviewed record; M counts report trackers this scan saw only as
 * references. Without a record, the count FinePrint can compute on-device is an upper bound.
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
            "Exodus lists $reportCount trackers; FinePrint found code for $found of them in this version."
        }
    }
    val m = scan.referencedOnly.size
    if (m == 0) return null
    return "Exodus may list up to ${found + m}; $m ${if (m == 1) "is an adapter reference" else "are adapter references"} with no code in this app."
}

/**
 * Flows for a tracker with no reviewed record, from its Exodus categories: third-party SDKs that
 * serve the app (crash reports, analytics) "stay here"; advertising, profiling, identification and
 * location go to the SDK's company for its own use ("goes elsewhere"), or stay in the company when
 * the tracker is its own (first party).
 */
internal fun deriveFlows(trackerName: String, categories: List<String>, party: String?): List<FlowLine> {
    val elsewhere = if (party == "first_party") USED_FOR_MORE else GOES_ELSEWHERE
    fun line(bucket: String, data: String, purpose: String) = FlowLine(
        data, bucket, trackerName, purpose, null, "Inferred from $trackerName code in this app", false, emptyList(), null, via = trackerName,
    )
    return categories.flatMap { category ->
        when (category.lowercase()) {
            "crash reporting", "crash_reporting" -> listOf(line(STAYS_HERE, "crash_diagnostics", "Crash reports for the developer"))
            "analytics" -> listOf(line(STAYS_HERE, "app_activity", "Usage statistics for the developer"))
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
    0 -> "FinePrint found no tracker code in this app. That doesn't show what the app itself does with your data, and there is no reviewed record for it yet."
    else -> "FinePrint found code from ${trackerNames.size} tracker SDK${if (trackerNames.size == 1) "" else "s"} in this app: " +
        trackerNames.joinToString() + ". There is no reviewed record for this app yet, so what they collect is inferred from each tracker's category."
}

package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.bundle.DataFlow
import com.longlifeio.fineprint.bundle.TrackerRecord
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.TrackerSignature

/**
 * Lines for the trackers found in an app. Trackers that one record covers (Meta's kits) share its
 * lines once. A tracker whose owner company made the app sends nothing elsewhere: each of its lines
 * takes the bucket its record gives for its owner's own apps, and a goes-elsewhere line without one
 * is left out, since the app's own record says what its maker does. [developer] is the app's maker.
 */
internal fun trackerLines(
    detected: List<DetectedTracker>,
    bundle: Bundle?,
    developer: String?,
    signatures: Map<String, TrackerSignature>,
): List<FlowLine> {
    val lines = ArrayList<FlowLine>()
    for ((record, found) in detected.groupBy { bundle?.trackers?.get(it.id) }) {
        val owned = developer != null && record?.ownerCompany?.let { sameCompany(it, developer, bundle) } == true
        if (record != null && record.dataFlows.any { it.government == null }) {
            val chain = record.ownerChain.takeIf { it.isNotEmpty() }?.joinToString(" → ") ?: record.owner
            val via = if (record.covers.isEmpty()) found.first().name else ownerName(record, bundle)
            for (f in record.dataFlows.filter { it.government == null }) {
                val bucket = if (owned) inOwnersApp(f) ?: continue else f.bucket
                lines += f.toLine(chain, via).copy(bucket = bucket, about = record.confidenceNote)
            }
        } else {
            for (t in found) {
                lines += deriveFlows(t.name, categoriesOf(t, record, signatures), if (owned) "first_party" else record?.party)
            }
        }
    }
    return lines
}

/**
 * What a tracker's lines are inferred from: its record's own sourced purpose when it has one, else εxodus'
 * categories (docs/METHOD.md, Where data goes).
 */
internal fun categoriesOf(t: DetectedTracker, record: TrackerRecord?, signatures: Map<String, TrackerSignature>): List<String> =
    record?.purpose?.let { listOf(PURPOSE_CATEGORY[it] ?: it) } ?: signatures[t.id]?.categories ?: t.categories

/** A record's purpose as the category deriveFlows reads; "other" infers no line. */
private val PURPOSE_CATEGORY = mapOf(
    "ads" to "advertising", "analytics" to "analytics", "crash_reporting" to "crash_reporting", "attribution" to "attribution",
    "location_data" to "location", "identity" to "identification", "other" to "other",
)

/** A tracker line's bucket inside an app its owner made: what the record says, or its own unless it goes elsewhere. */
private fun inOwnersApp(f: DataFlow): String? = f.inOwnerApps ?: f.bucket.takeUnless { it == GOES_ELSEWHERE }

/** "Meta", for a record that explains several of a company's kits. */
private fun ownerName(record: TrackerRecord, bundle: Bundle?): String =
    record.ownerCompany?.let { bundle?.companies?.get(it) }?.let { it.shortName ?: it.name } ?: record.owner

/** True when [a] and [b] are the same company, or one owns the other by the records' parent links. */
internal fun sameCompany(a: String, b: String, bundle: Bundle?): Boolean {
    fun owners(id: String) = generateSequence(id) { bundle?.companies?.get(it)?.parent }.take(8).toList()
    return b in owners(a) || a in owners(b)
}

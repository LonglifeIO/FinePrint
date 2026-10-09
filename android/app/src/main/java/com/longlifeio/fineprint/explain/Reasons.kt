package com.longlifeio.fineprint.explain

/*
 * Why an app has its tier (docs/METHOD.md, An app's page): one line under its name, "Why: …", naming what set it, and a
 * marker in the tier's colour on each line or legal item that set it, which the page reads first. A reason is always a
 * fact: what the app's maker or a tracker's company says, a report, a ruling, or a court's or regulator's step. What a
 * claimant alleges is never the reason, and leads nothing (Tier.kt, LineOrder.kt).
 */

const val WHY = "Why"

/** "Flagged for this": the marker on a line or legal item that set the tier. */
fun reasonMarker(tier: Tier): String = "${tier.label} for this"

/**
 * "Why: Location data goes elsewhere — Life360's own policy, and 3 more marked below": the reason, and how many other
 * lines (or legal items, on the record) set the tier too. Null when nothing is rated yet: No record yet says what the
 * scan found instead.
 */
fun whyLine(e: Explanation): String? {
    if (e.tier.tier == null) return null
    val items = e.tier.events.size
    val lines = e.flows.values.sumOf { bucket -> bucket.count { it.setsTier } }
    val more = when {
        items > 1 -> ", and ${items - 1} more on the record"
        lines > 1 -> ", and ${lines - 1} more marked below"
        else -> ""
    }
    return "$WHY: ${e.tier.reason}$more"
}

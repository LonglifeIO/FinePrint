package com.longlifeio.fineprint.explain

/*
 * Why an app has its tier (docs/METHOD.md, An app's page): one line under its name, "Why: …", naming what set it. The
 * lines or legal items that set it come first on the page, under one heading, "Why it's Flagged" or "Why it's
 * Caution", and the rest under "Also". A reason is always a fact: what the app's maker or a tracker's company says, a
 * report, a ruling, or a court's or regulator's step. What a claimant alleges is never the reason, and leads nothing
 * (Tier.kt, LineOrder.kt).
 */

const val WHY = "Why"

/** The heading over the lines, or the items on the record, that set the tier: "Why it's Flagged". */
fun whyItIs(tier: Tier): String = "$WHY it's ${tier.label}"

/** The heading over the rest of the lines, after [whyItIs]. */
const val ALSO = "Also"

/**
 * "Why: Life360 says your location goes to other companies. 3 more reasons below.": the reason, and how many other
 * lines set the tier too, or "2 more reasons on the record" when rulings or cases set it. Null when nothing is rated
 * yet: Not checked yet says what the scan found instead.
 */
fun whyLine(e: Explanation): String? {
    if (e.tier.tier == null) return null
    val items = e.tier.events.size
    val lines = e.flows.values.sumOf { bucket -> bucket.count { it.setsTier } }
    fun more(n: Int, where: String) = " $n more ${if (n == 1) "reason" else "reasons"} $where."
    val rest = when {
        items > 1 -> more(items - 1, "on the record")
        lines > 1 -> more(lines - 1, "below")
        else -> return "$WHY: ${e.tier.reason}"
    }
    return "$WHY: ${e.tier.reason}.$rest"
}

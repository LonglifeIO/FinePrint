package com.longlifeio.fineprint.explain

/*
 * Why an app has its tier (docs/METHOD.md, An app's page): one line under its name, "Why it's Flagged: …", naming
 * what set it. The lines or legal items that set it come first on the page, under one heading, "Why it's Flagged" or
 * "Why it's Caution", and the rest under "Also". A reason is always a fact: what the app's maker or a tracker's company
 * says, a report, a ruling, or a court's or regulator's step. What a claimant alleges is never the reason, and leads
 * nothing (Tier.kt, LineOrder.kt).
 */

const val WHY = "Why"

/** The heading over the lines, or the items on the record, that set the tier: "Why it's Flagged". */
fun whyItIs(tier: Tier): String = "$WHY it's ${tier.label}"

/** The heading over the rest of the lines, after [whyItIs]. */
const val ALSO = "Also"

/** The Why line's label: the tier's own phrase, as the fine print's heading has it ("Why it's Flagged:"). */
fun whyLabel(tier: Tier): String = "${whyItIs(tier)}:"

/**
 * "Why it's Flagged: Life360 says your location goes to other companies. 3 more reasons below.": the label, the reason,
 * and how many other lines set the tier too, or "2 more reasons on the record" when rulings or cases set it. After the
 * label a reason that opens with a common word reads on in lower case ("Why it's Flagged: a court or regulator has
 * ruled …"); a name keeps its capital. Null when nothing is rated yet: Not checked yet has no Why line.
 */
fun whyLine(e: Explanation): String? {
    val tier = e.tier.tier ?: return null
    val items = e.tier.events.size
    val lines = e.flows.values.sumOf { bucket -> bucket.count { it.setsTier } }
    fun more(n: Int, where: String) = " $n more ${if (n == 1) "reason" else "reasons"} $where."
    val rest = when {
        items > 1 -> more(items - 1, "on the record")
        lines > 1 -> more(lines - 1, "below")
        else -> return "${whyLabel(tier)} ${readsOn(e.tier.reason)}"
    }
    return "${whyLabel(tier)} ${readsOn(e.tier.reason)}.$rest"
}

/** The words a reason can open with that aren't names (Tier.kt): after the label, they go to lower case. */
private val COMMON_OPENINGS = setOf("A", "Can", "Nothing")

private fun readsOn(reason: String): String =
    if (reason.substringBefore(' ') in COMMON_OPENINGS) reason.replaceFirstChar { it.lowercase() } else reason

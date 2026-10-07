package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Source

/** One line of "The fine print" under an app's own words: a claim, its status and its sources. */
data class FinePrintLine(val text: String, val status: String?, val sources: List<Source>)

/** "Precise location → Select business partners: For their own monetization purposes". */
fun FlowLine.claim(): String = "${DATA_LABELS[data] ?: data} → $recipient: $purpose"

const val FINE_PRINT_MAX = 4

/**
 * The fine print (docs/METHOD.md, An app's page): at most four of FinePrint's own lines. First the
 * line that set the tier, a flow or the ruling or lawsuit the tier names; then, for each place data
 * goes that has lines, in bucket order, the first line the tier rules would name that isn't already
 * shown (a current practice before a past one; the company's own account, then a ruling, a report, an
 * allegation, then a line inferred from code; sensitive data first; then record order).
 */
fun finePrint(e: Explanation): List<FinePrintLine> {
    val chosen = ArrayList<FlowLine>()
    val out = ArrayList<FinePrintLine>()
    e.tier.flow?.let { set ->
        // The page shows inferred lines merged by data and purpose; find the one the tier's line sits in.
        val shown = e.flows[set.bucket].orEmpty()
        val line = shown.firstOrNull { it == set } ?: shown.firstOrNull { it.status == null && it.data == set.data && it.purpose == set.purpose } ?: set
        chosen += line
        out += FinePrintLine(line.claim(), line.status, line.sources)
    }
    e.tier.event?.line?.let { out += FinePrintLine(it.line, it.status, it.sources) } // its line starts with its date
    for (bucket in BUCKETS) {
        val first = e.flows[bucket].orEmpty().sortedWith(NAMED_FIRST).firstOrNull { it !in chosen } ?: continue
        chosen += first
        out += FinePrintLine(first.claim(), first.status, first.sources)
    }
    return out.take(FINE_PRINT_MAX)
}

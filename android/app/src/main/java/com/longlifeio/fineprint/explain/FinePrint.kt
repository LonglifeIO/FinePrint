package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Source

/** One line of "The fine print" under an app's own words: a claim, its status and its sources. */
data class FinePrintLine(
    val text: String,
    val status: String?,
    val sources: List<Source>,
    val historical: Boolean = false,
    /** "court" or "regulator", for an alleged line's badge. */
    val forum: String = "court",
    /** A setting FinePrint can't see that the line depends on. */
    val conditional: String? = null,
)

/** "Precise location → Select business partners: For their own monetization purposes". */
fun FlowLine.claim(): String = "${DATA_LABELS[data] ?: data} → $recipient: $purpose"

const val FINE_PRINT_MAX = 4

/**
 * The fine print (docs/METHOD.md, An app's page): FinePrint's lines in reading order (LineOrder.kt), at most
 * four, from the first two groups: data that goes to other companies for more than running the app
 * (government lines and trackers whose purpose isn't recorded among them), then the app's own further uses.
 * What the app collects to run itself follows them as one folded line ([alsoCollected]).
 */
fun finePrint(e: Explanation): List<FinePrintLine> =
    readingOrder(e).filter { it.group() != LineGroup.RUNS_THE_APP }.take(FINE_PRINT_MAX).map { it.finePrintLine() }

/** "Also collected to run the app: usage and crash data", and the lines it opens to. */
data class FoldedLines(val text: String, val lines: List<FinePrintLine>)

/** The fine print's last line: everything the app collects to run itself, folded into one; null when there's none. */
fun alsoCollected(e: Explanation): FoldedLines? {
    val lines = readingOrder(e).filter { it.group() == LineGroup.RUNS_THE_APP }
    return if (lines.isEmpty()) null else FoldedLines(alsoCollectedText(lines), lines.map { it.finePrintLine() })
}

private fun FlowLine.finePrintLine() = FinePrintLine(claim(), status, sources, historical, forum, conditional)

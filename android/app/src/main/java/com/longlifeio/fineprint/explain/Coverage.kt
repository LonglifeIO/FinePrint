package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.egress.InstalledApp

/*
 * How far FinePrint has looked at an app (docs/METHOD.md, An app's page): a reviewer checked it, it has a record of
 * the app's own words only, or there's no record yet. Words, never a colour; it never changes a tier.
 */

enum class Coverage { CHECKED, THEIR_WORDS_ONLY, NO_RECORD }

val Explanation.coverageState: Coverage
    get() = when {
        coverage != "curated" -> Coverage.NO_RECORD
        checkedOn != null -> Coverage.CHECKED
        else -> Coverage.THEIR_WORDS_ONLY
    }

const val CHECKED_BY = "Checked by FinePrint"
const val THEIR_WORDS_ONLY = "Their words only"
const val THEIR_WORDS_ONLY_LINE = "$THEIR_WORDS_ONLY — a reviewer hasn't looked at this app yet"
const val NO_RECORD_LINE = "$NO_RECORD — these lines come from the trackers found in its code."

/** The home row's small label: "Checked by FinePrint · 2026-10-04", "Their words only" or "No record yet". */
fun coverageLabel(e: Explanation): String = when (e.coverageState) {
    Coverage.CHECKED -> "$CHECKED_BY · ${e.checkedOn}"
    Coverage.THEIR_WORDS_ONLY -> THEIR_WORDS_ONLY
    Coverage.NO_RECORD -> e.maker?.takeIf { it.inherited }?.let { noRecordFrom(it.name) } ?: NO_RECORD
}

/** The line under an app's header; a preinstalled app's lines also come from its maker's policy. */
fun coverageLine(e: Explanation): String = when (e.coverageState) {
    Coverage.CHECKED -> "$CHECKED_BY on ${e.checkedOn}"
    Coverage.THEIR_WORDS_ONLY -> THEIR_WORDS_ONLY_LINE
    Coverage.NO_RECORD -> e.maker?.takeIf { it.inherited }
        ?.let { "$NO_RECORD — these lines come from ${possessive(it.name)} policy and the trackers found in its code." } ?: NO_RECORD_LINE
}

/** At a glance: "Records: 4 checked, 0 their words only, 6 no record yet." */
fun recordsLine(apps: List<InstalledApp>, explanations: Map<String, Explanation>): String {
    val states = apps.map { explanations[it.packageName]?.coverageState ?: Coverage.NO_RECORD }
    return "Records: ${states.count { it == Coverage.CHECKED }} checked, ${states.count { it == Coverage.THEIR_WORDS_ONLY }} their words only, " +
        "${states.count { it == Coverage.NO_RECORD }} no record yet."
}

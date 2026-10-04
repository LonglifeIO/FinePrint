package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.AppRecord
import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.bundle.ProceduralNote
import com.longlifeio.fineprint.bundle.Source

/** One event from the developer company's history, rendered "Action against <company>". */
data class HistoryItem(
    val subject: String,
    val title: String,
    /** "Arizona Attorney General · settled · $85 million · 2022-10-04". */
    val details: String,
    val notes: String?,
    val status: String,
    val proceduralNote: ProceduralNote?,
    val sources: List<Source>,
)

/**
 * The developer company's legal and regulatory events, newest first: each one the app's own record
 * doesn't already tell (no shared source with one of its legal lines). Breaches aren't regulators or
 * courts; they show under Also reported. An event counts toward the tier only when it names this app.
 */
internal fun companyHistory(record: AppRecord?, bundle: Bundle?, pkg: String): List<HistoryItem> {
    val developer = record?.developerCompany?.let { bundle?.companies?.get(it) } ?: return emptyList()
    val told = record.consequences.filter { it.status == "alleged" || it.status == "adjudicated" }
        .flatMap { c -> c.sources.map { it.url } }.toSet()
    return developer.events
        .filter { e -> e.type != "breach" && e.sources.none { it.url in told } }
        .sortedByDescending { it.date }
        .map { e ->
            val against = e.subjectCompany?.let { bundle?.companies?.get(it) } ?: developer
            HistoryItem(
                subject = "Action against ${companyName(against)}" + if (e.concernsApp == pkg) " concerning this app's data" else "",
                title = e.title,
                details = listOfNotNull(e.body, OUTCOMES[e.statusKind], e.amount, e.date).joinToString(" · ") +
                    if (e.status == "alleged") " ($NOT_PROVEN)" else "",
                notes = e.notes,
                status = e.status,
                proceduralNote = e.proceduralNote,
                sources = e.sources,
            )
        }
}

/** Status kinds in plain words. */
private val OUTCOMES = mapOf(
    "settlement" to "settled",
    "settlement_no_admission" to "settled without admitting wrongdoing",
    "ruling" to "ruled",
    "regulator_finding" to "regulator's finding",
    "consent_order" to "consent order",
    "filed" to "filed",
    "survived_motion_to_dismiss" to "survived a motion to dismiss",
    "dismissed" to "dismissed",
)

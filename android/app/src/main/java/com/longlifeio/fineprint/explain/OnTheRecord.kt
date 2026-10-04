package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.AppRecord
import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.bundle.Company
import com.longlifeio.fineprint.bundle.Consequence
import com.longlifeio.fineprint.bundle.LegalEvent
import com.longlifeio.fineprint.bundle.ProceduralNote
import com.longlifeio.fineprint.bundle.Source

/**
 * One line of On the record: when, who acted, what came of it and its status ("2024-07-30 · Texas
 * Attorney General · settled, no admission · $1.4 billion over five years"). The rest (who it was
 * against, the app record's own words for it, notes, sources) opens in a sheet.
 */
data class RecordLine(
    val date: String,
    val line: String,
    val status: String,
    val title: String,
    /** "Action against Meta concerning this app's data"; null when the record doesn't say. */
    val subject: String?,
    val details: List<String>,
    val proceduralNote: ProceduralNote?,
    val sources: List<Source>,
    /** False for an action against the company that doesn't name this app ("about Meta"). */
    val namesThisApp: Boolean,
)

/** On the record, newest first: actions by regulators and courts, then what others have reported. */
data class OnTheRecord(val actions: List<RecordLine>, val alsoReported: List<RecordLine>) {
    val count: Int get() = actions.size + alsoReported.size
}

/**
 * Built from the companies' dated records: every action naming this app (any company), then the
 * developer's other actions, marked as about that company. A line of the app's own record joins the
 * action it shares a source with; one with none becomes a line of its own, dated by its first source.
 */
internal fun onTheRecord(record: AppRecord?, consequences: List<Consequence>, bundle: Bundle?, pkg: String): OnTheRecord {
    val companies = bundle?.companies?.values.orEmpty()
    val developer = record?.developerCompany?.let { bundle?.companies?.get(it) }
    val legal = consequences.filter { it.status == "alleged" || it.status == "adjudicated" }
    val reported = consequences.filter { it.status == "reported" && !it.historical }
    fun told(e: LegalEvent, c: Consequence) = e.sources.any { s -> c.sources.any { it.url == s.url } }

    val naming = companies.flatMap { c -> c.events.filter { it.concernsApp == pkg && it.type != "breach" }.map { c to it } }
    val others = developer?.events.orEmpty().filter { it.concernsApp != pkg && it.type != "breach" }.map { developer!! to it }
    val actions = (naming + others).map { (company, e) -> eventLine(company, e, legal.filter { told(e, it) }, pkg, bundle, developer) } +
        legal.filter { c -> (naming + others).none { told(it.second, c) } }.map { consequenceLine(it, pkg, bundle, developer) }

    val breaches = developer?.events.orEmpty().filter { it.type == "breach" }
    val alsoReported = reported.map { c ->
        val event = breaches.firstOrNull { told(it, c) }
        consequenceLine(c, pkg, bundle, developer, dated = event?.date, by = event?.body)
    } + breaches.filter { e -> reported.none { told(e, it) } }.map { eventLine(developer!!, it, emptyList(), pkg, bundle, developer) }

    return OnTheRecord(actions.sortedByDescending { it.date }, alsoReported.sortedByDescending { it.date })
}

/**
 * Who an action was against: the app's developer by its short name ("Meta"), since the record doesn't
 * say which of its units each action named; another company with its units ("Allstate/Arity").
 */
private fun againstName(c: Company, developer: Company?) = if (c.id == developer?.id) c.shortName ?: c.name else companyName(c)

private fun eventLine(company: Company, e: LegalEvent, told: List<Consequence>, pkg: String, bundle: Bundle?, developer: Company?): RecordLine {
    val against = e.subjectCompany?.let { bundle?.companies?.get(it) } ?: company
    val names = e.concernsApp == pkg
    val about = if (names) null else "about ${against.shortName ?: against.name}"
    return RecordLine(
        date = e.date,
        line = listOfNotNull(e.date, e.body, outcome(e.statusKind, e.appealPending), e.amount, about).joinToString(" · "),
        status = e.status,
        title = e.title,
        subject = "Action against ${againstName(against, developer)}" + if (names) " concerning this app's data" else "",
        details = told.flatMap { listOfNotNull(it.text, attribution(it.status, it.wording)) }.distinct() +
            listOfNotNull(e.notes) + listOfNotNull(NOT_PROVEN_LINE.takeIf { e.status == "alleged" && told.isEmpty() }),
        proceduralNote = e.proceduralNote ?: told.firstNotNullOfOrNull { it.proceduralNote },
        sources = (e.sources + told.flatMap { it.sources }).distinctBy { it.url to it.quote },
        namesThisApp = names,
    )
}

/** A line from the app record itself, when no dated company record tells it. */
private fun consequenceLine(c: Consequence, pkg: String, bundle: Bundle?, developer: Company?, dated: String? = null, by: String? = null): RecordLine {
    val first = c.sources.firstOrNull()
    val against = c.subjectCompany?.let { bundle?.companies?.get(it) }
    return RecordLine(
        date = dated ?: first?.asOf ?: first?.accessed.orEmpty(),
        line = listOfNotNull(dated ?: first?.asOf ?: first?.accessed, by ?: first?.title, outcome(c.statusKind, c.appealPending)).joinToString(" · "),
        status = c.status,
        title = c.text.substringBefore(". ").take(80),
        subject = against?.let { "Action against ${againstName(it, developer)}" + if (c.concernsApp == null || c.concernsApp == pkg) " concerning this app's data" else " over data collected through its SDK" },
        details = listOfNotNull(c.text, attribution(c.status, c.wording)),
        proceduralNote = c.proceduralNote,
        sources = c.sources,
        namesThisApp = c.concernsApp == null || c.concernsApp == pkg,
    )
}

private const val NOT_PROVEN_LINE = "Alleged: not proven in court."

/**
 * What came of it, in plain words: "settled, no admission", "ruled", "filed, under appeal". Null when
 * the record doesn't say, or for a report, whose badge already says Reported.
 */
internal fun outcome(kind: String?, appealPending: Boolean): String? {
    val words = when (kind) {
        "settlement" -> "settled"
        "settlement_no_admission" -> "settled, no admission"
        "ruling" -> "ruled"
        "regulator_finding" -> "regulator's finding"
        "consent_order" -> "consent order"
        "filed" -> "filed"
        "survived_motion_to_dismiss" -> "survived a motion to dismiss"
        "dismissed" -> "dismissed"
        else -> null
    }
    return if (appealPending) listOfNotNull(words, "under appeal").joinToString(", ") else words
}

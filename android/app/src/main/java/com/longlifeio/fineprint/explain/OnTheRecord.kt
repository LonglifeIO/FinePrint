package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.AppRecord
import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.bundle.Company
import com.longlifeio.fineprint.bundle.Consequence
import com.longlifeio.fineprint.bundle.LegalEvent
import com.longlifeio.fineprint.bundle.ProceduralNote
import com.longlifeio.fineprint.bundle.Source
import com.longlifeio.fineprint.bundle.TrackerRecord

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
    /** In force, pending or under appeal; otherwise the matter has ended (Past). */
    val ongoing: Boolean = false,
    val statusKind: String? = null,
    /** The dated record's title, which a tier reason can name. */
    val label: String? = null,
    /** When it ended, else its date: what the three-year limit on tiers looks at. */
    val dated: String = date,
    /** "court" or "regulator": an alleged line says "not proven in court" or "not yet decided". */
    val forum: String = "court",
)

/** On the record, newest first: actions by regulators and courts, then what others have reported. */
data class OnTheRecord(val actions: List<RecordLine>, val alsoReported: List<RecordLine>) {
    val count: Int get() = actions.size + alsoReported.size
    val ongoing: List<RecordLine> get() = actions.filter { it.ongoing }
    val past: List<RecordLine> get() = actions.filterNot { it.ongoing }
}

/** A legal or reported line from an app's record, or from the record of a tracker in it ([by]). */
data class Said(val consequence: Consequence, val by: TrackerRecord? = null)

/**
 * Built from the companies' dated records: every action naming this app (any company), then the
 * developer's other actions, marked as about that company. A line of the app's own record joins the
 * action it shares a source with; one with none becomes a line of its own, dated by its first source.
 * A tracker's line names this app only when its record says so; otherwise it is about the tracker's owner.
 */
internal fun onTheRecord(record: AppRecord?, said: List<Said>, bundle: Bundle?, pkg: String): OnTheRecord {
    val companies = bundle?.companies?.values.orEmpty()
    val developer = record?.developerCompany?.let { bundle?.companies?.get(it) }
    val legal = said.filter { it.consequence.status == "alleged" || it.consequence.status == "adjudicated" }
    val reported = said.filter { it.consequence.status == "reported" && !it.consequence.historical }
    fun told(e: LegalEvent, s: Said) = e.sources.any { src -> s.consequence.sources.any { it.url == src.url } }

    val naming = companies.flatMap { c -> c.events.filter { it.concernsApp == pkg && it.type != "breach" }.map { c to it } }
    val others = developer?.events.orEmpty().filter { it.concernsApp != pkg && it.type != "breach" }.map { developer!! to it }
    val actions = (naming + others).map { (company, e) -> eventLine(company, e, legal.filter { told(e, it) }, pkg, bundle, developer) } +
        legal.filter { s -> (naming + others).none { told(it.second, s) } }.map { consequenceLine(it, pkg, bundle, developer) }

    val breaches = developer?.events.orEmpty().filter { it.type == "breach" }
    val alsoReported = reported.map { s ->
        val event = breaches.firstOrNull { told(it, s) }
        consequenceLine(s, pkg, bundle, developer, dated = event?.date, by = event?.body)
    } + breaches.filter { e -> reported.none { told(e, it) } }.map { eventLine(developer!!, it, emptyList(), pkg, bundle, developer) }

    return OnTheRecord(actions.sortedByDescending { it.date }, alsoReported.sortedByDescending { it.date })
}

/**
 * Who an action was against: the app's developer by its short name ("Meta"), since the record doesn't
 * say which of its units each action named; another company with its units ("Allstate/Arity").
 */
private fun againstName(c: Company, developer: Company?) = if (c.id == developer?.id) c.shortName ?: c.name else companyName(c)

private fun eventLine(company: Company, e: LegalEvent, told: List<Said>, pkg: String, bundle: Bundle?, developer: Company?): RecordLine {
    val against = e.subjectCompany?.let { bundle?.companies?.get(it) } ?: company
    val names = e.concernsApp == pkg
    val about = if (names) null else "about ${against.shortName ?: against.name}"
    val words = told.map { it.consequence }
    val ongoing = ongoing(e.status, e.statusKind, e.inForce, e.appealPending, e.closedDate) ||
        words.any { ongoing(it.status, it.statusKind, it.inForce, it.appealPending, it.closedDate) }
    return RecordLine(
        date = e.date,
        line = listOfNotNull(
            e.date, e.body, outcome(e.statusKind, e.appealPending, e.inForce), e.amount, e.closedDate?.let { "closed $it" }, about,
        ).joinToString(" · "),
        status = e.status,
        title = e.title,
        subject = "Action against ${againstName(against, developer)}" + if (names) " concerning this app's data" else "",
        details = words.flatMap { listOfNotNull(it.text, attribution(it.status, it.wording, it.forum)) }.distinct() +
            listOfNotNull(e.notes) + listOfNotNull("Alleged: ${undecided(e.forum)}.".takeIf { e.status == "alleged" && told.isEmpty() }),
        proceduralNote = e.proceduralNote ?: words.firstNotNullOfOrNull { it.proceduralNote },
        sources = (e.sources + words.flatMap { it.sources }).distinctBy { it.url to it.quote },
        namesThisApp = names,
        ongoing = ongoing,
        statusKind = e.statusKind,
        label = e.title,
        dated = e.closedDate ?: e.date,
        forum = e.forum,
    )
}

/** A line from the app's or a tracker's record itself, when no dated company record tells it. */
private fun consequenceLine(s: Said, pkg: String, bundle: Bundle?, developer: Company?, dated: String? = null, by: String? = null): RecordLine {
    val c = s.consequence
    val first = c.sources.firstOrNull()
    val against = c.subjectCompany?.let { bundle?.companies?.get(it) }
    val names = if (s.by == null) c.concernsApp == null || c.concernsApp == pkg else c.concernsApp == pkg
    val owner = s.by?.let { t -> t.ownerCompany?.let { bundle?.companies?.get(it) }?.let { it.shortName ?: it.name } ?: t.owner }
    val date = dated ?: first?.asOf ?: first?.accessed.orEmpty()
    return RecordLine(
        date = date,
        line = listOfNotNull(
            date.ifEmpty { null }, by ?: first?.title, outcome(c.statusKind, c.appealPending, c.inForce), c.closedDate?.let { "closed $it" },
            owner?.takeIf { !names }?.let { "about $it" },
        ).joinToString(" · "),
        status = c.status,
        title = c.text.substringBefore(". ").take(80),
        subject = against?.let { "Action against ${againstName(it, developer)}" + if (names) " concerning this app's data" else " over data collected through its SDK" },
        details = listOfNotNull(c.text, attribution(c.status, c.wording, c.forum)),
        proceduralNote = c.proceduralNote,
        sources = c.sources,
        namesThisApp = names,
        ongoing = ongoing(c.status, c.statusKind, c.inForce, c.appealPending, c.closedDate),
        statusKind = c.statusKind,
        dated = c.closedDate ?: date,
        forum = c.forum,
    )
}


/** In force, under appeal, or a lawsuit not yet dismissed or ended: what makes a matter ongoing. */
internal fun ongoing(status: String, kind: String?, inForce: Boolean, appealPending: Boolean, closedDate: String?): Boolean =
    appealPending || inForce || (status == "alleged" && kind != "dismissed" && closedDate == null)

/**
 * What came of it, in plain words: "settled, no admission", "ruled", "consent order, in force",
 * "filed, under appeal". Null when the record doesn't say, or for a report, whose badge says Reported.
 */
internal fun outcome(kind: String?, appealPending: Boolean, inForce: Boolean = false): String? {
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
    return listOfNotNull(words, "in force".takeIf { inForce }, "under appeal".takeIf { appealPending }).joinToString(", ").ifEmpty { null }
}

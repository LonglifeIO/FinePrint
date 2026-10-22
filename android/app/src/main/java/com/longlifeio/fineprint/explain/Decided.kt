package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Bundle

/*
 * A decided case in words (docs/METHOD.md, Tiers): what came of it, by its status_kind, and who acted, by the body on
 * record. One table for every reason a decided case gives: F2 says it of a case about the app's data, F1 of a decided
 * claim that data goes to other companies.
 */

/** Who decided a decided line's case, and when: the legal event in a company's record that cites one of the line's sources. */
data class Decision(val body: String?, val label: String?, val date: String?, val bodyShort: String? = null)

/** This line with the case that decided it, when a company's record cites one of its sources (the join On the record makes). */
internal fun FlowLine.withDecision(bundle: Bundle?): FlowLine {
    if (status != "adjudicated" || bundle == null) return this
    val urls = sources.map { it.url }.toSet()
    val event = bundle.companies.values.asSequence().flatMap { it.events }.firstOrNull { e -> e.sources.any { it.url in urls } } ?: return this
    return copy(decision = Decision(event.body, event.title, event.date, event.bodyShort))
}

private enum class Outcome { RULED, SETTLED }

/** What came of a case: a ruling or a regulator's finding, or a settlement, which a consent order is too. */
private val OUTCOMES = mapOf(
    "ruling" to Outcome.RULED, "regulator_finding" to Outcome.RULED,
    "settlement" to Outcome.SETTLED, "settlement_no_admission" to Outcome.SETTLED, "consent_order" to Outcome.SETTLED,
)

/** Bodies of several regulators, which a reason calls Regulators. */
private val SEVERAL = Regex("""\b(commissioners|attorneys general|regulators|authorities)\b""", RegexOption.IGNORE_CASE)

/** How one reason words a decided case: [ruled] and [settled] after who acted, [approved] for a court's, [acted] without a body or kind. */
internal class CaseWords(val ruled: (String) -> String, val settled: (String) -> String, val approved: String, val acted: String)

/**
 * A decided case as who acted and what came of it ("A regulator settled a case about this app's data"): a court when the
 * body is one, else a regulator (an attorney general counts as one), or regulators when several acted together. A ruling
 * or a regulator's finding is ruled on or found; a settlement or consent order is settled, and a court approved one.
 * Without a body or a known kind, the definition's own words.
 */
internal fun verdict(kind: String?, body: String?, words: CaseWords): String {
    val outcome = OUTCOMES[kind] ?: return words.acted
    if (body == null) return words.acted
    val court = body.contains("court", ignoreCase = true)
    val who = when {
        court -> "A court"
        SEVERAL.containsMatchIn(body) -> "Regulators"
        else -> "A regulator"
    }
    return when (outcome) {
        Outcome.RULED -> words.ruled(who)
        Outcome.SETTLED -> if (court) words.approved else words.settled(who)
    }
}

/** F2's: a case about this app's data. */
internal val ABOUT_THIS_APP = CaseWords(
    ruled = { "$it ruled on a case about this app's data" },
    settled = { "$it settled a case about this app's data" },
    approved = "A court approved a settlement in a case about this app's data",
    acted = ACTED,
)

/**
 * A decided line's, F1's when its data is sensitive: what the line claims, as a finding ("your contact list goes to other
 * companies") and as what the case was over ("your contact list going to other companies").
 */
internal fun overClaim(finding: String, over: String) = CaseWords(
    ruled = { "$it found $finding" },
    settled = { "$it settled a case over $over" },
    approved = "A court approved a settlement over $over",
    acted = "A court or regulator has acted on a case over $over",
)

/** " — US Federal Trade Commission, 2024, about X-Mode": who acted and when, the title when no body is on record, then the tracker it was about. */
internal fun whoAndWhen(body: String?, label: String?, date: String?, about: String? = null): String =
    listOfNotNull(body ?: label, date?.take(4), about?.let { "about $it" }).joinToString(", ").let { if (it.isEmpty()) "" else " — $it" }

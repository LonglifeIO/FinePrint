package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.bundle.Consequence
import com.longlifeio.fineprint.bundle.DataFlow
import com.longlifeio.fineprint.bundle.Law
import com.longlifeio.fineprint.bundle.OwnerChange
import com.longlifeio.fineprint.bundle.ProceduralNote
import com.longlifeio.fineprint.bundle.Source
import java.util.Locale

/** One way a government can get the data: Can compel (a law), Has bought, Has used; with its badge and sources. */
data class GovernmentLine(
    val kind: String,
    val country: String,
    val title: String,
    val text: String?,
    val status: String,
    val wording: String?,
    val sources: List<Source>,
    val note: ProceduralNote? = null,
    /** A law's own review date and stale flag, shown on its line the way a record shows its own. */
    val lastReviewed: String? = null,
    val stale: Boolean = false,
    /** For a law: who it binds, with sources. */
    val scope: ProceduralNote? = null,
    /** For a law: its one plain sentence, shown first, with [text] opening under it. */
    val short: String? = null,
)

/**
 * A company that gets the data, as a country's block shows it: headquartered there, or registered there; with its
 * changes of ownership, which its sheet shows as a dated chain.
 */
data class CompanyPlace(val name: String, val headquarteredHere: Boolean, val sources: List<Source>, val history: List<OwnerChange> = emptyList()) {
    /** Two separate claims, each from the company's record; where its servers are is never claimed. */
    val text: String get() = if (headquarteredHere) "$name: headquartered here and subject to its law" else "$name: subject to its law"
}

/** One country: the companies headquartered there or subject to its law, then its government lines. */
data class CountryBlock(val code: String, val name: String, val companies: List<CompanyPlace>, val lines: List<GovernmentLine>, val lawsReviewed: Boolean)

/**
 * Jurisdictions: where the companies that get this app's data are based (their head office, else
 * where they're registered), and for each country, in alphabetical order with the same wording, the
 * laws that let its government compel the data and what records say it has bought or used.
 * [unplaced] is true when some of the data goes to recipients FinePrint can't place.
 */
data class Governments(val basedIn: List<String>, val blocks: List<CountryBlock>, val unplaced: Boolean) {
    val line: String? get() = if (basedIn.isEmpty()) null else "Your data goes to companies based in: ${basedIn.joinToString()}"
}

val NO_GOVERNMENTS = Governments(emptyList(), emptyList(), unplaced = false)

internal fun governments(companyIds: Collection<String>, unplaced: Boolean, recorded: List<GovernmentLine>, bundle: Bundle?): Governments {
    val companies = companyIds.distinct().mapNotNull { bundle?.companies?.get(it) }.filter { it.jurisdiction != null }
    fun name(code: String) = bundle?.jurisdictions?.get(code)?.name ?: countryName(code)
    val codes = (companies.flatMap { listOfNotNull(it.jurisdiction, it.headquarters) } + recorded.map { it.country }).distinct()
    val blocks = codes.map { code ->
        val place = bundle?.jurisdictions?.get(code)
        val here = companies.filter { code == it.jurisdiction || code == it.headquarters }
        // A country's laws apply to the companies subject to them; with none here, only what's on record shows.
        val laws = if (here.isEmpty()) emptyList() else lawsBinding(code, bundle)
        CountryBlock(
            code = code,
            name = name(code),
            companies = here.map { CompanyPlace(it.name, it.headquarters == code, it.jurisdictionSources, it.ownerHistory) },
            lines = laws.map {
                GovernmentLine(CAN_COMPEL, code, "${it.name} (${it.citation})", it.text, it.status, null, it.sources, it.statusNote, it.lastReviewed, it.stale, it.scope, it.short)
            } +
                recorded.filter { it.country == code },
            lawsReviewed = place != null || here.isEmpty(),
        )
    }
    return Governments(companies.map { name(it.headquarters ?: it.jurisdiction!!) }.distinct().sorted(), blocks.sortedBy { it.name }, unplaced)
}

/** A record's line about a government (a flow or a legal line with recipient_kind government_body). */
internal fun DataFlow.governmentLine(): GovernmentLine? = government?.let {
    GovernmentLine(it.line, it.jurisdiction, recipientLabel ?: it.jurisdiction, purpose, status ?: "reported", attribution(status, wording), sources, proceduralNote)
}

internal fun Consequence.governmentLine(): GovernmentLine? = government?.let {
    GovernmentLine(it.line, it.jurisdiction, text.substringBefore(". ").take(80), text, status, attribution(status, wording), sources, proceduralNote)
}

const val CAN_COMPEL = "can_compel"

/** "United States", "Cayman Islands": the same form for every country without a reviewed entry of its own. */
/**
 * The laws that bind a country: its own entry's, then any union's (an EU regulation) whose applies_to names it. A law
 * with applies_to binds exactly those countries; one without binds the country of its own entry.
 */
internal fun lawsBinding(code: String, bundle: Bundle?): List<Law> {
    val entries = bundle?.jurisdictions.orEmpty()
    val own = entries[code]
    return (listOfNotNull(own) + entries.values.filter { it !== own }).flatMap { j ->
        j.laws.filter { it.appliesTo?.contains(code) ?: (j.id == code) }
    }
}

internal fun countryName(code: String): String = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH).ifEmpty { code }

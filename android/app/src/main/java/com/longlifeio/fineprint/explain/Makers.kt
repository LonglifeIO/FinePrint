package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.AppRecord
import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.bundle.Company
import com.longlifeio.fineprint.bundle.SummaryNote
import com.longlifeio.fineprint.egress.InstalledApp

/**
 * Who made an app, as far as FinePrint can tell: its record's developer or, for a preinstalled app
 * without a record, the company whose package-name prefix it carries (com.google. for Google).
 * [inherited] is true when the app shows that company's default lines because it has no record of
 * its own; [lines] and [notes] are those lines, worded "From Google's privacy policy, which covers
 * this app", and head the maker's group in the system-apps view.
 */
data class Maker(val id: String, val name: String, val inherited: Boolean, val lines: List<FlowLine>, val notes: List<SummaryNote>)

internal fun maker(app: InstalledApp, record: AppRecord?, bundle: Bundle?): Maker? {
    val company = record?.developerCompany?.let { bundle?.companies?.get(it) } ?: preinstalledVendor(app, bundle) ?: return null
    val name = company.shortName ?: company.name
    val wording = fromPolicy(name)
    return Maker(
        id = company.id,
        name = name,
        inherited = record == null && (company.defaultFlows.isNotEmpty() || company.defaultNotes.isNotEmpty()),
        lines = company.defaultFlows.filter { it.government == null }.map { f ->
            f.toLine(f.recipient?.let { bundle?.companies?.get(it)?.name } ?: f.recipientLabel ?: name, via = name).copy(wording = wording)
        },
        notes = company.defaultNotes.map { it.copy(wording = wording) },
    )
}

/**
 * The company whose prefix a preinstalled app with code carries. Only preinstalled apps: one installed
 * later could be a lookalike with a borrowed package name.
 */
internal fun preinstalledVendor(app: InstalledApp, bundle: Bundle?): Company? =
    if (!app.isSystem || !app.hasCode) null
    else bundle?.companies?.values?.firstOrNull { c -> c.packagePrefixes.any { app.packageName.startsWith(it) } }

/** "From Google's privacy policy, which covers this app." */
fun fromPolicy(company: String): String = "From ${possessive(company)} privacy policy, which covers this app."

/** The summary of a preinstalled app that inherits its maker's lines. */
internal fun inheritedSummary(app: InstalledApp, maker: Maker, prefix: String?, trackerNames: List<String>): String =
    "FinePrint hasn't checked this app. It came with your phone and its package name starts with " +
        "${prefix ?: app.packageName}, so the lines below come from ${possessive(maker.name)} privacy policy, which covers it." +
        if (trackerNames.isEmpty()) "" else " FinePrint also found code from ${trackerNames.size} tracker SDK${if (trackerNames.size == 1) "" else "s"} in it: ${trackerNames.joinToString()}."

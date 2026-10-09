package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.review.ReviewStatus

/**
 * The list's filter chips: tiers combine with OR, coverage with OR, and the groups (with Reviewed and
 * System) with AND. System shows only with system apps on, and lists them grouped by maker.
 */
enum class ListFilter(val label: String) {
    FLAGGED("Flagged"),
    CAUTION("Caution"),
    EXPECTED("Expected"),
    CHECKED("Checked"),
    THEIR_WORDS(THEIR_WORDS_ONLY),
    NO_RECORD(NO_RECORD_LABEL),
    REVIEWED(REVIEWED_LABEL),
    SYSTEM(SYSTEM_LABEL),
}

/** A maker's group in the system-apps view: "Google · 14 apps", its policy lines once, then its apps. Null maker: the rest. */
data class SystemGroup(val maker: Maker?, val apps: List<InstalledApp>)

/** Groups apps (already in list order) by maker, makers by name, with the apps of unknown makers last. */
fun systemGroups(ordered: List<InstalledApp>, explanations: Map<String, Explanation>): List<SystemGroup> =
    ordered.groupBy { explanations[it.packageName]?.maker?.id }
        .map { (_, apps) -> SystemGroup(apps.firstNotNullOfOrNull { explanations[it.packageName]?.maker }, apps) }
        .sortedWith(compareBy({ it.maker == null }, { it.maker?.name?.lowercase() }))

/** The wording constants; inside the enum, REVIEWED and NO_RECORD would mean the entries themselves. */
private const val REVIEWED_LABEL = REVIEWED
private const val NO_RECORD_LABEL = NO_RECORD
private val SYSTEM_LABEL = SYSTEM.label

/** The home's search field (docs/METHOD.md, The home), and the button that opens the search with its filters first. */
const val SEARCH_HINT = "Search apps and companies"
const val FILTERS = "Filters"

/** "3 apps", read out as the results change. */
fun matchCount(n: Int): String = when (n) {
    0 -> "No apps match"
    1 -> "1 app"
    else -> "$n apps"
}

/** What a search matches: the app's name and package, its maker, and the companies its lines name ("Google", "Meta"). */
internal fun searchTerms(app: InstalledApp, e: Explanation?): List<String> =
    listOfNotNull(app.label, app.packageName, e?.appName, e?.maker?.name) +
        e?.let { (it.flows.values.flatten() + it.governmentFlows + it.unrecorded).flatMap { line -> listOfNotNull(line.recipient, line.via) } }.orEmpty()

/**
 * Flagged, then Caution, then Expected, then apps with nothing to rate yet (no record and no
 * readable scan). Within a tier, apps you've marked Reviewed come after the rest; one that has
 * changed since counts as not reviewed, so it comes back up. Then by name. [query] matches [searchTerms].
 */
fun listOrder(
    apps: List<InstalledApp>,
    explanations: Map<String, Explanation>,
    reviews: Map<String, ReviewStatus>,
    query: String,
    filters: Set<ListFilter>,
): List<InstalledApp> {
    val tiers = filters.mapNotNull {
        when (it) {
            ListFilter.FLAGGED -> Tier.FLAGGED
            ListFilter.CAUTION -> Tier.CAUTION
            ListFilter.EXPECTED -> Tier.EXPECTED
            else -> null
        }
    }.toSet()
    val coverage = filters.mapNotNull {
        when (it) {
            ListFilter.CHECKED -> Coverage.CHECKED
            ListFilter.THEIR_WORDS -> Coverage.THEIR_WORDS_ONLY
            ListFilter.NO_RECORD -> Coverage.NO_RECORD
            else -> null
        }
    }.toSet()
    val q = query.trim()
    return apps.filter { app ->
        val e = explanations[app.packageName]
        (q.isEmpty() || searchTerms(app, e).any { it.contains(q, ignoreCase = true) }) &&
            (tiers.isEmpty() || e?.tier?.tier in tiers) &&
            (coverage.isEmpty() || (e?.coverageState ?: Coverage.NO_RECORD) in coverage) &&
            (ListFilter.REVIEWED !in filters || reviews[app.packageName] in setOf(ReviewStatus.REVIEWED, ReviewStatus.CHANGED)) &&
            (ListFilter.SYSTEM !in filters || app.isSystem)
    }.sortedWith(
        compareBy(
            { explanations[it.packageName]?.tier?.tier?.ordinal ?: Tier.entries.size },
            { if (reviews[it.packageName] == ReviewStatus.REVIEWED) 1 else 0 },
            { it.label.lowercase() },
        ),
    )
}

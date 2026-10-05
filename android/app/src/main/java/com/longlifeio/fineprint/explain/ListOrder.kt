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
    HAS_RECORD("Has record"),
    NO_RECORD("No record yet"),
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

/** The wording constants; inside the enum, REVIEWED would mean the entry itself. */
private const val REVIEWED_LABEL = REVIEWED
private val SYSTEM_LABEL = SYSTEM.label

/**
 * Flagged, then Caution, then Expected, then apps with nothing to rate yet (no record and no
 * readable scan). Within a tier, apps you've marked Reviewed come after the rest; one that has
 * changed since counts as not reviewed, so it comes back up. Then by name. [query] matches the name.
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
            ListFilter.HAS_RECORD -> "curated"
            ListFilter.NO_RECORD -> "auto"
            else -> null
        }
    }.toSet()
    val q = query.trim()
    return apps.filter { app ->
        val e = explanations[app.packageName]
        (q.isEmpty() || app.label.contains(q, ignoreCase = true) || e?.appName?.contains(q, ignoreCase = true) == true) &&
            (tiers.isEmpty() || e?.tier?.tier in tiers) &&
            (coverage.isEmpty() || (e?.coverage ?: "auto") in coverage) &&
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

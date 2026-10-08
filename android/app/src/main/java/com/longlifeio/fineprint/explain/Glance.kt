package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Change
import com.longlifeio.fineprint.egress.InstalledApp

/** The home's summary of the apps listed: how many have lines in each bucket, and the flows you've limited. */
data class Glance(
    val apps: Int,
    /** Apps with at least one current line in each bucket, in BUCKETS order: the chips. */
    val perBucket: Map<String, Int>,
    val limited: Int,
    val flows: Int,
    /** Apps with a current line in the first group of the reading order (LineOrder.kt), other than Purpose not recorded: the headline. */
    val othersForMore: Int = perBucket[GOES_ELSEWHERE] ?: 0,
)

fun glance(apps: List<InstalledApp>, explanations: Map<String, Explanation>, checks: Map<String, WhatYouCanDo>): Glance {
    fun hasLine(app: InstalledApp, bucket: String) = explanations[app.packageName]?.flows?.get(bucket).orEmpty().any { !it.historical }
    return Glance(
        apps = apps.size,
        perBucket = BUCKETS.associateWith { bucket -> apps.count { hasLine(it, bucket) } },
        limited = apps.sumOf { checks[it.packageName]?.limited ?: 0 },
        flows = apps.sumOf { checks[it.packageName]?.total ?: 0 },
        othersForMore = apps.count { app ->
            explanations[app.packageName]?.let(::readingOrder).orEmpty()
                .any { it.group() == LineGroup.OTHER_COMPANIES && !it.historical && it.data != UNRECORDED_DATA }
        },
    )
}

/**
 * "For 5 of your 9 apps, FinePrint lists data that can go to other companies for more than running the
 * app." It counts apps with a current line in the first group of the reading order; the chips below count
 * each place as before, and the bar's caption says how many flows you've limited. The count includes Auto
 * lines (tracker code found, no record), and code shows where data can go, not that it went: so "can go",
 * never "goes" (docs/METHOD.md, Where data goes). It leaves out a tracker whose purpose isn't recorded: it
 * comes first in the order, but FinePrint can't say its data is used for more than running the app. An app it
 * lists nothing for isn't said to send nothing.
 */
fun glanceHeadline(g: Glance): String {
    val n = g.othersForMore
    return when {
        g.apps == 0 -> "No apps to show yet."
        n == 0 && g.apps == 1 -> "FinePrint doesn't yet list, for your app, data that $FOR_MORE."
        n == 0 -> "FinePrint doesn't yet list, for any of your ${g.apps} apps, data that $FOR_MORE."
        n == g.apps && g.apps == 1 -> "For your app, FinePrint lists data that $FOR_MORE."
        n == g.apps -> "For all ${g.apps} of your apps, FinePrint lists data that $FOR_MORE."
        else -> "For $n of your ${g.apps} apps, FinePrint lists data that $FOR_MORE."
    }
}

private const val FOR_MORE = "can go to other companies for more than running the app"

/** "4 of 19 flows limited by your settings", under the segmented bar. */
fun limitedLine(g: Glance): String = "${g.limited} of ${g.flows} ${if (g.flows == 1) "flow" else "flows"} limited by your settings"

/** The newest change to any of these apps' records, and whose it is; on the same day, the app first by name wins. */
fun latestChange(apps: List<InstalledApp>, explanations: Map<String, Explanation>): Pair<InstalledApp, Change>? =
    apps.mapNotNull { app -> explanations[app.packageName]?.changes?.maxByOrNull { it.date }?.let { app to it } }
        .sortedWith(compareByDescending<Pair<InstalledApp, Change>> { it.second.date }.thenBy { it.first.label.lowercase() })
        .firstOrNull()

/** The home's sections, in list order; null is "No record yet". */
val TIER_SECTIONS: List<Tier?> = listOf(Tier.FLAGGED, Tier.CAUTION, Tier.EXPECTED, null)

/** Before you've opened or closed one: Flagged and Caution start open, Expected and No record yet closed. */
fun openByDefault(tier: Tier?): Boolean = tier == Tier.FLAGGED || tier == Tier.CAUTION

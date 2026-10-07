package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Change
import com.longlifeio.fineprint.egress.InstalledApp

/** The home's summary of the apps listed: how many have lines in each bucket, and the flows you've limited. */
data class Glance(
    val apps: Int,
    /** Apps with at least one current line in each bucket, in BUCKETS order. */
    val perBucket: Map<String, Int>,
    val limited: Int,
    val flows: Int,
) {
    /** Apps with a current line in Goes elsewhere. */
    val sending: Int get() = perBucket[GOES_ELSEWHERE] ?: 0
}

fun glance(apps: List<InstalledApp>, explanations: Map<String, Explanation>, checks: Map<String, WhatYouCanDo>): Glance {
    fun hasLine(app: InstalledApp, bucket: String) = explanations[app.packageName]?.flows?.get(bucket).orEmpty().any { !it.historical }
    return Glance(
        apps = apps.size,
        perBucket = BUCKETS.associateWith { bucket -> apps.count { hasLine(it, bucket) } },
        limited = apps.sumOf { checks[it.packageName]?.limited ?: 0 },
        flows = apps.sumOf { checks[it.packageName]?.total ?: 0 },
    )
}

/**
 * "For 5 of your 9 apps, FinePrint lists data that can go to other companies. You've limited 4 of the
 * 19 flows you can change." The count includes Auto lines (tracker code found, no record), and code
 * shows where data can go, not that it went: so "can go", never "goes" (docs/METHOD.md, Where data goes).
 * An app it lists nothing for isn't said to send nothing.
 */
fun glanceHeadline(g: Glance): String {
    val sending = when {
        g.apps == 0 -> "No apps to show yet."
        g.sending == 0 && g.apps == 1 -> "FinePrint doesn't yet list data that can go to other companies for your app."
        g.sending == 0 -> "FinePrint doesn't yet list data that can go to other companies for any of your ${g.apps} apps."
        g.sending == g.apps && g.apps == 1 -> "For your app, FinePrint lists data that can go to other companies."
        g.sending == g.apps -> "For all ${g.apps} of your apps, FinePrint lists data that can go to other companies."
        else -> "For ${g.sending} of your ${g.apps} apps, FinePrint lists data that can go to other companies."
    }
    val limited = when {
        g.flows == 0 -> null
        g.flows == 1 -> "You've limited ${g.limited} of the 1 flow you can change."
        else -> "You've limited ${g.limited} of the ${g.flows} flows you can change."
    }
    return listOfNotNull(sending, limited).joinToString(" ")
}

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

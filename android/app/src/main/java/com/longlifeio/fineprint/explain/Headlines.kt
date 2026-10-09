package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult

/*
 * The plain headline at the top of each card on an app's page, under the section's name. They state
 * what the card holds, in the same terms as its lines: "goes" only where a sourced line says so, "can
 * go" where only tracker code does.
 */

private fun plural(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

fun summaryHeadline(e: Explanation): String = when {
    e.coverage == "curated" -> "What ${e.appName} does with what it collects"
    e.maker?.inherited == true -> "What ${possessive(e.maker.name)} policy says for its apps"
    else -> "What the tracker code in it suggests"
}

fun collectsHeadline(e: Explanation): String =
    if (e.collects.isEmpty()) "Nothing found" else "${plural(e.collects.size, "kind", "kinds")} of data from this phone"

fun whereHeadline(e: Explanation): String {
    fun current(bucket: String) = e.flows[bucket].orEmpty().filterNot { it.historical || it.conditional != null }
    val elsewhere = current(GOES_ELSEWHERE)
    val more = current(USED_FOR_MORE)
    return when {
        elsewhere.any { it.status != null } -> "Some of it goes to other companies"
        elsewhere.isNotEmpty() || e.unrecorded.isNotEmpty() -> "Some of it can go to other companies"
        more.any { it.status != null } -> "Some of it is used for more than running the app"
        more.isNotEmpty() -> "Some of it can be used for more than running the app"
        current(STAYS_HERE).isNotEmpty() -> "It stays with ${e.appName}"
        else -> "Where the companies that get it are based"
    }
}

fun appliesHeadline(e: Explanation): String =
    "${plural(e.applies.size, "permission", "permissions")} you've granted ${if (e.applies.size == 1) "feeds" else "feed"} this"

fun canDoHeadline(check: WhatYouCanDo): String = check.summary ?: "Settings inside the app"

fun reachHeadline(e: Explanation): String = "${plural(e.reach.size, "extra power", "extra powers")} beyond permissions"

fun recordHeadline(e: Explanation): String {
    val count = e.onTheRecord.count + e.changes.size
    return "${plural(count, "item", "items")} on the record"
}

fun evidenceHeadline(app: InstalledApp, result: TrackerScanResult?): String {
    val trackers = result?.trackers?.size?.let { plural(it, "tracker", "trackers") } ?: "Still checking its code"
    return "$trackers · ${plural(app.permissions.size, "permission", "permissions")}"
}

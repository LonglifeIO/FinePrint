package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Change
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

/*
 * The one-line summaries a closed section shows beside its name, below the fine print (docs/METHOD.md, An app's page):
 * "Where it goes · 7 ways, 3 limited by your settings". Each counts what the section holds when it opens.
 */

/** "7 ways, 3 limited by your settings": the ways it uses or shares data that What you can do counts; else the headline. */
fun whereSummary(e: Explanation, check: WhatYouCanDo): String =
    if (check.total == 0) whereHeadline(e) else "${plural(check.total, "way", "ways")}, ${check.limited} limited by your settings"

/** "3 of 7 limited", the same count; "Settings inside the app" when none of its ways is counted. */
fun canDoSummary(check: WhatYouCanDo): String = if (check.total == 0) canDoHeadline(check) else "${check.limited} of ${check.total} limited"

/** "Based in: Ireland, United States", in the order Jurisdictions lists them; what FinePrint can't say when it places none. */
fun basedInSummary(g: Governments): String = if (g.basedIn.isEmpty()) NONE_PLACED else "Based in: ${g.basedIn.joinToString()}"

/** "4 matters, latest 2025"; with none, the changes its History lists ("1 change in its history"). */
fun recordSummary(record: OnTheRecord, changes: List<Change>): String {
    val dated = (record.actions + record.alsoReported + record.pastReports).map { it.date.take(4) }.filter { it.length == 4 && it.all(Char::isDigit) }
    return when {
        record.count > 0 -> plural(record.count, "matter", "matters") + (dated.maxOrNull()?.let { ", latest $it" } ?: "")
        else -> plural(changes.size, "change", "changes") + " in its history"
    }
}

fun evidenceHeadline(app: InstalledApp, result: TrackerScanResult?): String {
    val trackers = result?.trackers?.size?.let { plural(it, "tracker", "trackers") } ?: "Still checking its code"
    return "$trackers · ${plural(app.permissions.size, "permission", "permissions")}"
}

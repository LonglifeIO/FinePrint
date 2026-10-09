package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.AppRecord
import com.longlifeio.fineprint.bundle.Source
import com.longlifeio.fineprint.egress.InstalledApp

/**
 * One checklist item: an Android setting FinePrint reads ([automatic]: shown as a read-only status),
 * one it can't see (the advertising ID), or a setting inside the app ([inApp]); the last two you tick.
 */
data class CheckItem(
    val id: String,
    val label: String,
    val how: String,
    /** True when FinePrint reads it from what Android reports; then [ticked] means "off". */
    val automatic: Boolean,
    val ticked: Boolean,
    val inApp: Boolean = false,
    /** Data kinds (Android items) or flow ids (in-app items) the item limits. */
    val limitsData: Set<String> = emptySet(),
    val limitsFlows: Set<String> = emptySet(),
    val sources: List<Source> = emptyList(),
    /** In-app items: what turning it off changes, as the app puts it. */
    val effect: String? = null,
    /** What FinePrint infers about the flows it limits, when the sources don't say so outright. */
    val notes: List<String> = emptyList(),
) {
    /** The fixed subtext for this kind of item. */
    val subtext: String get() = when {
        automatic -> if (ticked) CHECK_ANDROID_OFF else CHECK_ANDROID_ON
        inApp -> CHECK_IN_APP
        else -> CHECK_ANDROID_UNSEEN
    }
}

/** The "What you can do" checklist, how many of the app's current flows it limits, and free-text controls for records without structured ones. */
data class WhatYouCanDo(val items: List<CheckItem>, val limited: Int, val total: Int, val inAppText: String?) {
    /** "Your settings limit 3 of the 11 ways it uses or shares data."; null when nothing goes beyond running the app. */
    val summary: String? get() = if (total == 0) null else "Your settings limit $limited of the $total ${if (total == 1) "way" else "ways"} it uses or shares data."
}

private const val AD_ID = "com.google.android.gms.permission.AD_ID"

/** Runtime permissions a person can turn off for one app, and where. */
private val ANDROID_ITEMS = mapOf(
    "android.permission.ACCESS_FINE_LOCATION" to (
        "Turn off precise location" to "Android settings > Apps > this app > Permissions > Location: turn off Use precise location, or choose Don't allow."),
    "android.permission.ACCESS_BACKGROUND_LOCATION" to (
        "Turn off location in the background" to "Android settings > Apps > this app > Permissions > Location: choose Allow only while using the app."),
    "android.permission.ACTIVITY_RECOGNITION" to (
        "Turn off physical activity" to "Android settings > Apps > this app > Permissions > Physical activity: choose Don't allow."),
    "android.permission.READ_CONTACTS" to (
        "Turn off contacts" to "Android settings > Apps > this app > Permissions > Contacts: choose Don't allow."),
)

/**
 * The flows the "N of M" count is about: current lines beyond "Stays here", each counted once.
 * Reviewed lines (the app's record, a tracker's record) count one each; an inferred line counts only
 * when it adds data no reviewed line in its bucket already covers, since it restates the same flow
 * from tracker code.
 */
internal fun countedFlows(e: Explanation): List<FlowLine> {
    val current = e.flows.filterKeys { it != STAYS_HERE }.values.flatten().filterNot { it.historical || it.conditional != null }
    val reviewed = current.filter { it.status != null }
    val covered = reviewed.map { it.bucket to it.data }.toSet()
    return reviewed + current.filter { it.status == null && (it.bucket to it.data) !in covered }.distinctBy { it.bucket to it.data }
}

/**
 * Android items for the permissions the app asks for that feed a line beyond "Stays here" (from the
 * bundle's permission [feeds]); then the record's in-app controls. A counted flow is limited when a
 * ticked item applies to its data kind or names it.
 */
fun whatYouCanDo(app: InstalledApp, e: Explanation, record: AppRecord?, feeds: Map<String, List<String>>, ticked: Set<String>): WhatYouCanDo {
    val current = countedFlows(e)
    val currentData = current.map { it.data }.toSet()
    val items = ArrayList<CheckItem>()
    for (p in app.permissions) {
        val data = feeds[p.name].orEmpty().filter { it in currentData }.toSet()
        if (data.isEmpty()) continue
        if (p.name == AD_ID) {
            items += CheckItem(
                "android:$AD_ID", "Delete your advertising ID",
                "Android settings > Privacy > Ads: Delete advertising ID. It applies to every app.",
                automatic = false, ticked = "android:$AD_ID" in ticked, limitsData = data,
            )
        } else {
            val (label, how) = ANDROID_ITEMS[p.name] ?: continue
            items += CheckItem("android:${p.name}", label, how, automatic = true, ticked = !p.granted, limitsData = data)
        }
    }
    record?.controls?.forEach { c ->
        items += CheckItem(
            c.id, c.label, c.how, automatic = false, ticked = c.id in ticked, inApp = true, limitsFlows = c.limits.map { it.flow }.toSet(),
            sources = c.sources, effect = c.effect.ifBlank { null }, notes = c.limits.filter { it.inferred }.mapNotNull { it.note },
        )
    }
    val limited = current.count { line -> items.any { it.ticked && (line.data in it.limitsData || line.id in it.limitsFlows) } }
    // The record's free-text controls only when it has no structured ones; otherwise they'd repeat the items.
    return WhatYouCanDo(items, limited, current.size, record?.privacyControls?.takeIf { record.controls.isEmpty() })
}

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
    /** An Android item's setting as Android reports it, then the choice that limits it ("Use precise location: On → Off"). */
    val setting: String? = null,
    /** An item you tick: the day you ticked it, when FinePrint kept one. */
    val tickedOn: String? = null,
) {
    /** The fixed subtext for this kind of item. */
    val subtext: String get() = when {
        automatic -> if (ticked) CHECK_ANDROID_OFF else CHECK_ANDROID_ON
        inApp -> CHECK_IN_APP
        else -> CHECK_ANDROID_UNSEEN
    }
}

/**
 * The "What you can do" checklist, how many of the app's current flows it limits, and free-text controls for records
 * without structured ones. [nothing] says why there's nothing to limit, when there's no item and no free text.
 */
data class WhatYouCanDo(val items: List<CheckItem>, val limited: Int, val total: Int, val inAppText: String?, val nothing: String? = null) {
    /** "Your settings limit 3 of the 11 ways it uses or shares data."; null when nothing goes beyond running the app. */
    val summary: String? get() = if (total == 0) null else "Your settings limit $limited of the $total ${if (total == 1) "way" else "ways"} it uses or shares data."
}

private const val AD_ID = "com.google.android.gms.permission.AD_ID"
private const val FINE = "android.permission.ACCESS_FINE_LOCATION"
private const val COARSE = "android.permission.ACCESS_COARSE_LOCATION"
private const val BACKGROUND = "android.permission.ACCESS_BACKGROUND_LOCATION"
private const val ACTIVITY = "android.permission.ACTIVITY_RECOGNITION"
private const val CONTACTS = "android.permission.READ_CONTACTS"

/** Runtime permissions a person can turn off for one app, and where. */
private val ANDROID_ITEMS = mapOf(
    FINE to ("Turn off precise location" to "Android settings > Apps > this app > Permissions > Location: turn off Use precise location, or choose Don't allow."),
    BACKGROUND to ("Turn off location in the background" to "Android settings > Apps > this app > Permissions > Location: choose Allow only while using the app."),
    ACTIVITY to ("Turn off physical activity" to "Android settings > Apps > this app > Permissions > Physical activity: choose Don't allow."),
    CONTACTS to ("Turn off contacts" to "Android settings > Apps > this app > Permissions > Contacts: choose Don't allow."),
)

/**
 * An Android item's setting as Android reports it, then the choice that limits it, in Android's own words: "Use precise
 * location: On → Off". Once it's limited, just how it's set: "Location: Allowed only while in use".
 */
internal fun androidSetting(permission: String, app: InstalledApp): String? {
    fun granted(name: String) = app.permissions.any { it.name == name && it.granted }
    val location = when {
        granted(BACKGROUND) -> "Allowed all the time"
        granted(FINE) || granted(COARSE) -> "Allowed only while in use"
        else -> "Not allowed"
    }
    fun allowed(group: String) = if (granted(permission)) "$group: Allowed → Don't allow" else "$group: Not allowed"
    return when (permission) {
        FINE -> if (granted(FINE)) "Use precise location: On → Off" else if (granted(COARSE)) "Use precise location: Off" else "Location: Not allowed"
        BACKGROUND -> if (granted(BACKGROUND)) "Location: Allowed all the time → Allow only while using the app" else "Location: $location"
        ACTIVITY -> allowed("Physical activity")
        CONTACTS -> allowed("Contacts")
        else -> null
    }
}

/**
 * Why there's nothing to limit (docs/METHOD.md, What you can do): the app asked for none of the permissions FinePrint
 * reads, or none it asked for feeds a way it uses or shares data.
 */
internal fun nothingToLimit(app: InstalledApp, appName: String): String =
    if (app.permissions.none { it.name in ANDROID_ITEMS }) "$appName asked for no permissions FinePrint can check."
    else "No permission FinePrint can check feeds a way $appName uses or shares data."

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
 * ticked item applies to its data kind or names it. [tickedOn]: the day you ticked each item you tick.
 */
fun whatYouCanDo(
    app: InstalledApp, e: Explanation, record: AppRecord?, feeds: Map<String, List<String>>, ticked: Set<String>, tickedOn: Map<String, String> = emptyMap(),
): WhatYouCanDo {
    val current = countedFlows(e)
    val currentData = current.map { it.data }.toSet()
    val items = ArrayList<CheckItem>()
    // In a fixed order, not Android's (granted first): an item stays where it was when you change its setting.
    val order = ANDROID_ITEMS.keys.toList() + AD_ID
    for (p in app.permissions.filter { it.name in order }.sortedBy { order.indexOf(it.name) }) {
        val data = feeds[p.name].orEmpty().filter { it in currentData }.toSet()
        if (data.isEmpty()) continue
        if (p.name == AD_ID) {
            val id = "android:$AD_ID"
            items += CheckItem(
                id, "Delete your advertising ID",
                "Android settings > Privacy > Ads: Delete advertising ID. It applies to every app.",
                automatic = false, ticked = id in ticked, limitsData = data, tickedOn = tickedOn[id]?.takeIf { id in ticked },
            )
        } else {
            val (label, how) = ANDROID_ITEMS[p.name] ?: continue
            items += CheckItem("android:${p.name}", label, how, automatic = true, ticked = !p.granted, limitsData = data, setting = androidSetting(p.name, app))
        }
    }
    record?.controls?.forEach { c ->
        items += CheckItem(
            c.id, c.label, c.how, automatic = false, ticked = c.id in ticked, inApp = true, limitsFlows = c.limits.map { it.flow }.toSet(),
            sources = c.sources, effect = c.effect.ifBlank { null }, notes = c.limits.filter { it.inferred }.mapNotNull { it.note },
            tickedOn = tickedOn[c.id]?.takeIf { c.id in ticked },
        )
    }
    val limited = current.count { line -> items.any { it.ticked && (line.data in it.limitsData || line.id in it.limitsFlows) } }
    // The record's free-text controls only when it has no structured ones; otherwise they'd repeat the items.
    val inAppText = record?.privacyControls?.takeIf { record.controls.isEmpty() }
    return WhatYouCanDo(items, limited, current.size, inAppText, nothingToLimit(app, e.appName).takeIf { items.isEmpty() && inAppText == null })
}

package com.longlifeio.fineprint.explain

/*
 * The words the screens use to explain themselves. docs/METHOD.md must contain every definition here
 * word for word (MethodDocTest checks), so the in-app text and the published method never drift apart.
 */

/** A section's heading and its one-line definition, shown under the heading. */
data class SectionText(val title: String, val subtitle: String)

val SUMMARY_CURATED = SectionText("Summary", "In plain words, from what FinePrint has checked about this app.")
val SUMMARY_INHERITED = SectionText("Summary", "FinePrint hasn't checked this app. This comes from its maker's privacy policy, which covers it, and the tracker code inside it.")
val SUMMARY_AUTO = SectionText("Summary", "FinePrint hasn't checked this app. This is inferred from the tracker code inside it.")
val COLLECTS = SectionText("What it collects", "Data this app takes from your phone, in plain terms.")
val WHERE_IT_GOES = SectionText("Where it goes", "Who gets that data, and whether it's used beyond running the app.")
val APPLIES = SectionText("This applies to you because", "Permissions you've actually granted that feed the above.")
val ON_THE_RECORD = SectionText("On the record", "What regulators and courts have said. FinePrint relays the public record; it doesn't judge.")
val JURISDICTIONS = SectionText("Jurisdictions", "Where the companies that get this data are based, and the laws there that let a government demand it.")
const val UNPLACED = "Some recipients aren't named or have no record, so FinePrint can't say where they're based."
const val NONE_PLACED = "FinePrint can't say where the companies that get this data are based."
const val NO_LAWS_REVIEWED = "FinePrint hasn't checked this country's laws yet."

/** Lines in the order a reader needs them (docs/METHOD.md, Where data goes). */
const val ALSO_COLLECTED = "Also collected to run the app"
const val PURPOSE_NOT_RECORDED = "What it's used for isn't recorded"
val NOT_RECORDED = SectionText("Purpose not recorded", "Tracker code FinePrint hasn't checked yet, so it can't say where its data goes.")

/** The three kinds of government line (docs/METHOD.md, Governments). */
val GOVERNMENT_LINES = mapOf(
    "can_compel" to BadgeText("Can compel", "A company in a country is subject to a law that lets that country's government demand the data. FinePrint cites the law."),
    "has_bought" to BadgeText("Has bought", "A documented government purchase of this kind of data."),
    "has_used" to BadgeText("Has used", "Documented government use of this kind of data, reported by at least two sources."),
)
val RECENT_CHANGES = SectionText("Recent changes", "The latest change to FinePrint's record of this app, and whether it's better or worse for you.")
val HISTORY = SectionText("History", "Every change to FinePrint's record of this app, newest first.")
val ONGOING = SectionText("Ongoing", "Orders still in force, cases still pending, and decisions under appeal.")
val PAST = SectionText("Past", "Matters that have ended. One that ended more than three years ago never changes a tier.")
val ALSO_REPORTED = SectionText("Also reported", "Journalists, researchers or breach trackers found it. No court or regulator has ruled on it.")
val WHAT_YOU_CAN_DO = SectionText(
    "What you can do",
    "Settings that limit where your data goes. FinePrint can't change them. It shows what Android reports, and you can tick what you've changed inside the app.",
)

/** The checklist's subtexts: always the same for each kind of item. */
const val CHECK_ANDROID_OFF = "Checked automatically — Android shows this is off"
const val CHECK_ANDROID_ON = "Checked automatically — Android shows this is still on"
const val CHECK_IN_APP = "Check this yourself — FinePrint can't see settings inside other apps."
const val CHECK_ANDROID_UNSEEN = "Check this yourself — FinePrint can't see this Android setting."
val DEVICE_ACCESS = SectionText("Device access", "Extra powers this app has, beyond its permissions.")
val EVIDENCE = SectionText("Evidence", "The trackers and permissions behind the sections above.")
val SOURCES = SectionText("Sources", "Every number on this page is one of these, in order.")
/** The top of an app's page, when its store description is on record. */
val THEIR_WORDS = SectionText("Their words", "The app's own short description on its Google Play listing, word for word.")
/** The hero's second part, FinePrint's own claims: the first four, then See all. */
const val THE_FINE_PRINT = "The fine print"
const val FINE_PRINT_HEADING = "$THE_FINE_PRINT · what FinePrint found"
/** The home's bar: the product's name, shown on no other screen. */
const val HOME_WORDMARK = "FinePrint"

const val STAYS_HERE = "stays_here"
const val USED_FOR_MORE = "used_for_more"
const val GOES_ELSEWHERE = "goes_elsewhere"

/** The three buckets, always in this order. */
val BUCKETS = listOf(STAYS_HERE, USED_FOR_MORE, GOES_ELSEWHERE)

val BUCKET_TEXT = mapOf(
    STAYS_HERE to SectionText("Stays here", "Used only to run or improve this app."),
    USED_FOR_MORE to SectionText("Used for more", "The same company uses it for ads, profiling, or other products."),
    GOES_ELSEWHERE to SectionText("Goes elsewhere", "Shared with, licensed to, or sold to other companies."),
)

/** A plain gloss after each place's name on an app's page: "Goes elsewhere · to other companies". */
val BUCKET_GLOSS = mapOf(STAYS_HERE to "to run the app", USED_FOR_MORE to "beyond running the app", GOES_ELSEWHERE to "to other companies")

/** A status badge's label and its one-line definition (the tooltip, and the legend in How to read this). */
data class BadgeText(val label: String, val definition: String)

const val AUTO = "auto"
const val HISTORICAL = "historical"

val BADGES = mapOf(
    "self_disclosed" to BadgeText("Their words", "The company says so itself, in its privacy policy, store labels or filings. For a law, the words of the law itself."),
    "reported" to BadgeText("Reported", "Journalists or researchers found it. No court or regulator has ruled on it."),
    // Its badge adds where it stands: "Alleged (not proven in court)", or "(not yet decided)" before a regulator.
    "alleged" to BadgeText("Alleged", "Claimed in a lawsuit or complaint. Not proven in court; before a regulator, not yet decided."),
    "adjudicated" to BadgeText("Decided", "Decided by a court or regulator, or settled."),
    AUTO to BadgeText("Auto", "Inferred by FinePrint from tracker code in the app; no person has checked it."),
    HISTORICAL to BadgeText("Historical", "Describes a past practice, not a current one."),
)

/** How a change to the record moves things for you; build.py works it out from the record's structure. */
val DIRECTIONS = mapOf(
    "improved" to BadgeText("Improved", "Less data is collected or shared, a new way to limit it was added, or a legal matter or evidence lowered the tier."),
    "worsened" to BadgeText("Worsened", "More data is collected or shared, a way to limit it was removed, or a legal matter or evidence raised the tier."),
    "neutral" to BadgeText("Neutral", "Only the words changed, or a legal matter or the evidence changed without moving the tier. What's collected and shared is the same."),
)

/** "Tier: Caution → Flagged", when the change records both. */
fun tierMove(before: String?, after: String?): String? {
    fun label(t: String?) = Tier.entries.firstOrNull { it.name.equals(t, ignoreCase = true) }?.label
    return label(before)?.let { b -> label(after)?.let { a -> "Tier: $b → $a" } }
}

/** A line the app doesn't do unless you act: its label and definition (never shown for "on"). */
val DEFAULTS = mapOf(
    "off" to BadgeText("Off by default", "The app doesn't do this unless you turn a setting on."),
    "opt_in" to BadgeText("Only if you opt in", "The app asks before it does this; it doesn't happen unless you agree."),
)

const val REGION_CAVEAT_END = "Where you live, a different policy may apply."

/** "This page follows TikTok's privacy policy for the United States. Where you live, …" */
fun regionCaveat(appName: String, region: String): String =
    "This page follows ${possessive(appName)} privacy policy for ${withArticle(region)}. $REGION_CAVEAT_END"

/** "the United States", "the European Union", but "Canada". */
private fun withArticle(place: String): String =
    if (place.startsWith("United ") || place.endsWith(" Union") || place in setOf("Netherlands", "Philippines", "Czech Republic")) "the $place" else place

/** For alleged lines whose own wording doesn't already say it: a matter before a court... */
const val NOT_PROVEN = "not proven in court"

/** ...or before a regulator (forum "regulator"). */
const val NOT_YET_DECIDED = "not yet decided"

/** After a conditional line's condition: the setting is the developer's, and invisible from the phone. */
const val CANT_SEE_SETTING = "FinePrint can't see that setting."

/** "If the developer turns on data sharing. FinePrint can't see that setting." */
fun conditionLine(condition: String): String = "${condition.replaceFirstChar { it.uppercase() }}. $CANT_SEE_SETTING"

/** What an alleged line adds about where it stands: "not proven in court", or "not yet decided" before a regulator. */
fun undecided(forum: String?): String = if (forum == "regulator") NOT_YET_DECIDED else NOT_PROVEN

/** An app without a record: FinePrint hasn't checked it. */
const val NO_RECORD = "Not checked yet"
/** "Not checked yet · from Google's policy": a preinstalled app showing its maker's policy lines. */
fun noRecordFrom(maker: String): String = "$NO_RECORD · from ${possessive(maker)} policy"
val SYSTEM = BadgeText("System", "It came with your phone: Android lists it as a system app.")
const val OTHER_PREINSTALLED = "Other preinstalled apps"
const val OTHER_PREINSTALLED_NOTE = "FinePrint can't tell from their package names who made these."
/** "Google · 14 apps", a maker's group in the system-apps view. */
fun groupHeader(name: String, count: Int): String = "$name · $count ${if (count == 1) "app" else "apps"}"
/** "From Google's privacy policy, which covers these apps.": the group's inherited lines, shown once. */
fun fromPolicyForAll(company: String): String = "From ${possessive(company)} privacy policy, which covers these apps."
/** Your own mark, kept on this phone. FinePrint's own work is "checked", never "reviewed": the two never share a word. */
const val REVIEWED = "Reviewed"
const val REVIEWED_DEFINITION = "You've marked this app reviewed. The mark stays on this phone and never changes the tier."
const val CHANGED = "Changed since you reviewed"
const val CHANGED_DEFINITION = "Something about this app has changed since you marked it reviewed. The note beside the mark says what."
const val LIMITED_DEFINITION = "Limited means a setting you've changed applies to it: an Android permission turned off, or an in-app setting ticked. Limited doesn't mean stopped."
const val STALE_DEFINITION = "Last checked more than 180 days ago; it may be out of date."

/** Under an app FinePrint has checked, and under each law ([what]: "this app", "this law"): when, and whether that was too long ago. */
fun lastChecked(what: String, date: String) = "FinePrint last checked $what on $date."
fun staleNote(what: String) = "FinePrint last checked $what more than 180 days ago. It may be out of date."
const val NO_RECORD_DEFINITION = "FinePrint hasn't checked this app. What it shows comes from the trackers built into it. It can be Caution, never Flagged or Expected."

/** Plain names for data kinds: flow headings and "What it collects". */
val DATA_LABELS = mapOf(
    "precise_location" to "Precise location",
    "approximate_location" to "Approximate location",
    "movement_and_driving" to "Driving behaviour and movement",
    "physical_activity" to "Physical activity",
    "contacts" to "Contacts",
    "account_identity" to "Name, email or account",
    "device_identifiers" to "Device and advertising IDs",
    "app_activity" to "What you do in the app",
    "crash_diagnostics" to "Crash and performance data",
    "sensitive_personal_data" to "Sensitive personal data",
    "health" to "Health data",
    "financial" to "Financial data",
    "biometric" to "Biometric data",
    "childrens_data" to "Children's data",
    UNRECORDED_DATA to "Data from this app",
)

/** What a granted permission gives the app, in the same plain terms (no permission names). */
val PERMISSION_LABELS = mapOf(
    "android.permission.ACCESS_FINE_LOCATION" to "Precise location",
    "android.permission.ACCESS_COARSE_LOCATION" to "Approximate location",
    "android.permission.ACCESS_BACKGROUND_LOCATION" to "Location in the background",
    "android.permission.ACTIVITY_RECOGNITION" to "Physical activity",
    "com.google.android.gms.permission.AD_ID" to "Device and advertising IDs",
    "android.permission.READ_CONTACTS" to "Contacts",
    "android.permission.GET_ACCOUNTS" to "Accounts on this phone",
    "android.permission.READ_CALENDAR" to "Calendar",
    "android.permission.CAMERA" to "Camera",
    "android.permission.RECORD_AUDIO" to "Microphone",
    "android.permission.READ_PHONE_STATE" to "Phone status and identity",
    "android.permission.READ_PHONE_NUMBERS" to "Phone number",
    "android.permission.READ_CALL_LOG" to "Call history",
    "android.permission.READ_SMS" to "Text messages",
    "android.permission.RECEIVE_SMS" to "Text messages",
    "android.permission.BODY_SENSORS" to "Body sensors",
    "android.permission.BODY_SENSORS_BACKGROUND" to "Body sensors",
    "android.permission.READ_MEDIA_IMAGES" to "Photos and videos",
    "android.permission.READ_MEDIA_VIDEO" to "Photos and videos",
    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" to "Photos and videos",
    "android.permission.READ_EXTERNAL_STORAGE" to "Files and photos",
    "android.permission.READ_MEDIA_AUDIO" to "Music and audio files",
    "android.permission.BLUETOOTH_SCAN" to "Nearby Bluetooth devices",
    "android.permission.NEARBY_WIFI_DEVICES" to "Nearby Wi-Fi devices",
)

fun permissionLabel(permission: String): String? =
    PERMISSION_LABELS[permission] ?: if (permission.startsWith("android.permission.health.")) "Health data" else null

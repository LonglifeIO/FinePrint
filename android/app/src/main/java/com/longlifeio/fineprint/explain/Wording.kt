package com.longlifeio.fineprint.explain

/*
 * The words the screens use to explain themselves. docs/METHOD.md must contain every definition here
 * word for word (MethodDocTest checks), so the in-app text and the published method never drift apart.
 */

/** A section's heading and its one-line definition, shown under the heading. */
data class SectionText(val title: String, val subtitle: String)

val SUMMARY_CURATED = SectionText("Summary", "In plain words, from FinePrint's reviewed record of this app.")
val SUMMARY_INHERITED = SectionText("Summary", "No reviewed record of this app yet: from its maker's privacy policy, which covers it, and the tracker code found in it.")
val SUMMARY_AUTO = SectionText("Summary", "No reviewed record yet: inferred from the tracker code found in this app.")
val COLLECTS = SectionText("What it collects", "Data this app takes from your phone, in plain terms.")
val WHERE_IT_GOES = SectionText("Where it goes", "Who gets that data, and whether it's used beyond running the app.")
val APPLIES = SectionText("This applies to you because", "Permissions you've actually granted that feed the above.")
val ON_THE_RECORD = SectionText("On the record", "What regulators and courts have said. FinePrint relays the public record; it doesn't judge.")
val JURISDICTIONS = SectionText("Jurisdictions", "Where the companies that get this data are based, and the laws there that let a government demand it.")
const val UNPLACED = "Some recipients aren't named or have no record, so FinePrint can't say where they're based."
const val NONE_PLACED = "FinePrint can't say where the companies that get this data are based."
const val NO_LAWS_REVIEWED = "FinePrint hasn't reviewed this country's laws yet."

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
val ALSO_REPORTED = SectionText("Also reported", "Reported by journalists, researchers or breach trackers; no court or regulator has ruled on it.")
val WHAT_YOU_CAN_DO = SectionText(
    "What you can do",
    "Settings that limit the flows above. FinePrint can't change anything; it shows what Android reports and lets you record what you've changed inside the app.",
)

/** The checklist's subtexts: always the same for each kind of item. */
const val CHECK_ANDROID_OFF = "Checked automatically — Android shows this is off"
const val CHECK_ANDROID_ON = "Checked automatically — Android shows this is still on"
const val CHECK_IN_APP = "Check this yourself — FinePrint can't see settings inside other apps."
const val CHECK_ANDROID_UNSEEN = "Check this yourself — FinePrint can't see this Android setting."
val DEVICE_ACCESS = SectionText("Device access", "Extra powers this app has registered, beyond ordinary permissions.")
val EVIDENCE = SectionText("Evidence", "The trackers and permissions behind the sections above.")
val SOURCES = SectionText("Sources", "Every number on this page is one of these, in order.")
/** The top of an app's page, when its store description is on record. */
val THEIR_WORDS = SectionText("Their words", "The app's own short description on its Google Play listing, word for word.")
val THE_FINE_PRINT = SectionText("The fine print", "At most four of FinePrint's own lines, chosen the same way for every app.")

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

/** A status badge's label and its one-line definition (the tooltip, and the legend in How to read this). */
data class BadgeText(val label: String, val definition: String)

const val AUTO = "auto"
const val HISTORICAL = "historical"

val BADGES = mapOf(
    "self_disclosed" to BadgeText("Self-disclosed", "The company says so itself, in its privacy policy, labels or filings, or a law says so in its own text."),
    "reported" to BadgeText("Reported", "Reported by journalists or researchers; no court or regulator has ruled on it."),
    "alleged" to BadgeText("Alleged", "Claimed in a lawsuit or complaint; not proven in court."),
    "adjudicated" to BadgeText("Adjudicated", "Decided by a court or regulator, or settled."),
    AUTO to BadgeText("Auto", "Inferred by FinePrint from tracker code in the app; no person has reviewed it."),
    HISTORICAL to BadgeText("Historical", "Describes a past practice, not a current one."),
)

/** How a change to the record moves things for you; build.py works it out from the record's structure. */
val DIRECTIONS = mapOf(
    "improved" to BadgeText("Improved", "The record shows less data collected or shared, or a new way to limit it."),
    "worsened" to BadgeText("Worsened", "The record shows more data collected or shared, or a way to limit it removed."),
    "neutral" to BadgeText("Neutral", "The wording changed; what's collected and shared didn't."),
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

/** "This record follows TikTok's privacy policy for one region: United States. Where you live, …" */
fun regionCaveat(appName: String, region: String): String =
    "This record follows ${possessive(appName)} privacy policy for one region: $region. $REGION_CAVEAT_END"

/** For alleged lines whose own wording doesn't already say it. */
const val NOT_PROVEN = "not proven in court"

const val NO_RECORD = "No record yet"
/** "No record yet · from Google's policy": a preinstalled app showing its maker's policy lines. */
fun noRecordFrom(maker: String): String = "$NO_RECORD · from ${possessive(maker)} policy"
val SYSTEM = BadgeText("System", "It came with your phone: Android lists it as a system app.")
const val OTHER_PREINSTALLED = "Other preinstalled apps"
const val OTHER_PREINSTALLED_NOTE = "FinePrint can't tell from their package names who made these."
/** "Google · 14 apps", a maker's group in the system-apps view. */
fun groupHeader(name: String, count: Int): String = "$name · $count ${if (count == 1) "app" else "apps"}"
/** "From Google's privacy policy, which covers these apps.": the group's inherited lines, shown once. */
fun fromPolicyForAll(company: String): String = "From ${possessive(company)} privacy policy, which covers these apps."
/** Your own mark, kept on this phone. FinePrint's record review is a different thing ("No record yet"). */
const val REVIEWED = "Reviewed"
const val REVIEWED_DEFINITION = "You've marked this app reviewed. The mark stays on this phone and never changes the tier."
const val CHANGED = "Changed since you reviewed"
const val CHANGED_DEFINITION = "What FinePrint's record says the app does with your data, the permissions you've granted or the tracker code in the app has changed since you marked it reviewed."
const val LIMITED_DEFINITION = "A flow counts as limited when a setting you've changed applies to it: an Android permission turned off, or an in-app setting ticked. Limited doesn't mean stopped."
const val STALE_DEFINITION = "Last reviewed more than 180 days ago; it may be out of date."

/** Under a reviewed record, and under each law line: when it was last reviewed, and whether that was too long ago. */
fun recordLastReviewed(date: String) = "Record last reviewed $date."
const val STALE_NOTE = "This record is more than 180 days old and may be out of date."
const val NO_RECORD_DEFINITION = "FinePrint hasn't reviewed this app. What it shows is inferred from the tracker code in the app: it can be rated Caution, but never Flagged or Expected."

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

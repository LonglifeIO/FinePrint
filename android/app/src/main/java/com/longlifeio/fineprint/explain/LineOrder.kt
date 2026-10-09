package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.TrackerSignature

/*
 * The order FinePrint's lines are read in (docs/METHOD.md, Where data goes): first the lines that set the tier
 * (docs/METHOD.md, An app's page), and never a line a claimant alleges before one that isn't; then data that goes to other
 * companies for more than running the app (ads, profiling, resale, government access), then what the app's own
 * company uses it for beyond running the app, then what it collects to run the app. A reviewed line's group
 * follows its place (Goes elsewhere, Used for more, Stays here), which its record sets from the stated purpose;
 * an inferred line's place comes from its tracker record's own purpose, else its εxodus category; a tracker
 * whose purpose isn't recorded is in the first group. Display only: the tier and the Reviewed mark read the
 * lines, never their order.
 */

enum class LineGroup { OTHER_COMPANIES, OWN_USE, RUNS_THE_APP }

fun FlowLine.group(): LineGroup = when (bucket) {
    GOES_ELSEWHERE -> LineGroup.OTHER_COMPANIES
    USED_FOR_MORE -> LineGroup.OWN_USE
    else -> LineGroup.RUNS_THE_APP
}

/**
 * The reading order: the lines that set the tier; every line that isn't alleged before any that is; then group; a
 * current practice before a past one; sensitive data first (the tiers' own list); the company's own account, then a
 * ruling, a report, an allegation, a line inferred from code; then the order the record gives (the sort is stable).
 */
val LINE_ORDER: Comparator<FlowLine> = compareBy<FlowLine>(
    { !it.setsTier }, { it.status == "alleged" }, { it.group() }, { it.historical }, { if (it.data in SENSITIVE_DATA) 0 else 1 },
    { NAMING_ORDER.indexOf(it.status) },
)

/** Every line on an app's page in reading order: each place's lines, government lines and trackers with no recorded purpose. */
fun readingOrder(e: Explanation): List<FlowLine> =
    (BUCKETS.flatMap { e.flows[it].orEmpty() } + e.governmentFlows + e.unrecorded).sortedWith(LINE_ORDER)

/** The data kind of a line about trackers with no recorded purpose: FinePrint can't say what data they take. */
const val UNRECORDED_DATA = "unrecorded"

/**
 * One line for the trackers found in an app that FinePrint has no record of and no εxodus category it can
 * read ("Data from this app → Urbanairship: What it's used for isn't recorded"). Trackers the app's own maker
 * owns are left to the app's own record, as their lines are. Display only, outside the three places: neither
 * the tier, the places' counts nor the home's headline reads it.
 */
internal fun unrecordedLines(detected: List<DetectedTracker>, bundle: Bundle?, developer: String?, signatures: Map<String, TrackerSignature>): List<FlowLine> {
    val names = detected.filter { t ->
        val record = bundle?.trackers?.get(t.id)
        val owned = developer != null && record?.ownerCompany?.let { sameCompany(it, developer, bundle) } == true
        val recorded = record != null && record.dataFlows.any { it.government == null }
        !owned && !recorded && record?.purpose == null && deriveFlows(t.name, categoriesOf(t, record, signatures), record?.party).isEmpty()
    }.map { it.name }.distinct()
    if (names.isEmpty()) return emptyList()
    val via = names.joinToString()
    return listOf(FlowLine(UNRECORDED_DATA, GOES_ELSEWHERE, via, PURPOSE_NOT_RECORDED, null,
        "Inferred from ${if (names.size == 1) "its" else "their"} code in this app", false, emptyList(), null, via = via))
}

/** A tracker's group, for the order an app's summary names them in: its lines' first group, or the first group when nothing is recorded. */
internal fun trackerGroup(t: DetectedTracker, bundle: Bundle?, developer: String?, signatures: Map<String, TrackerSignature>): LineGroup =
    trackerLines(listOf(t), bundle, developer, signatures).minOfOrNull { it.group() }
        ?: if (unrecordedLines(listOf(t), bundle, developer, signatures).isNotEmpty()) LineGroup.OTHER_COMPANIES else LineGroup.OWN_USE

/** Short nouns for "Also collected to run the app: usage and crash data", in the order they're named. */
private val RUN_THE_APP_NOUNS = linkedMapOf(
    "precise_location" to "location", "approximate_location" to "location", "movement_and_driving" to "driving",
    "physical_activity" to "activity", "contacts" to "contacts", "account_identity" to "account", "device_identifiers" to "device",
    "app_activity" to "usage", "crash_diagnostics" to "crash", "health" to "health", "financial" to "financial",
    "biometric" to "biometric", "childrens_data" to "children's", "sensitive_personal_data" to "sensitive personal",
)

/** "Also collected to run the app: usage and crash data": the data kinds of the lines it folds, named once each. */
fun alsoCollectedText(lines: List<FlowLine>): String {
    val kinds = lines.map { it.data }.toSet()
    val nouns = RUN_THE_APP_NOUNS.filterKeys { it in kinds }.values.distinct() +
        kinds.filter { it !in RUN_THE_APP_NOUNS }.map { (DATA_LABELS[it] ?: it).lowercase() }
    val named = if (nouns.size <= 1) nouns.joinToString() else nouns.dropLast(1).joinToString() + " and " + nouns.last()
    return "$ALSO_COLLECTED: $named data"
}

package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Source
import java.time.LocalDate

/** How much an app deserves attention. The rules are published in docs/METHOD.md. */
enum class Tier(val label: String, val definition: String) {
    FLAGGED("Flagged", "Sensitive data goes to other companies by the app's own account or a ruling, or a court has ruled on, or let proceed, a case over this app's data."),
    CAUTION("Caution", "Data is used beyond running the app or goes to other companies, a regulator has opened a formal proceeding over this app's data, or the app can reach deep into the phone."),
    EXPECTED("Expected", "FinePrint's reviewed record finds nothing beyond what running the app needs."),
}

/**
 * An app's tier, the one line that explains it, and the rule that set it (F1-F3, C1-C4, E). A null
 * [tier] is "No record yet": an app without a reviewed record that isn't Caution; its line is what
 * the scan found (rule N).
 */
data class TierResult(val tier: Tier?, val reason: String, val rule: String, val capped: Boolean = false) {
    /** The flow that set the tier (F1, C1, C2), when one did; for the fine print, not part of what the tier is. */
    var flow: FlowLine? = null
        internal set

    /** The ruling, lawsuit or regulator's proceeding that set it (F2, F3, C3), when one did. */
    var event: TierEvent? = null
        internal set
}

private fun TierResult.setBy(flow: FlowLine? = null, event: TierEvent? = null) = also { it.flow = flow; it.event = event }

/**
 * A legal or regulatory item, as the tier rules see it. [label] ("$5 billion FTC penalty") and [date]
 * let the reason name the ruling or lawsuit that set the tier; the newest one with a label is named.
 * It counts only while [ongoing] (in force, pending or under appeal), or for three years from when it
 * [closed] (else its [date]); an undated item counts.
 */
data class TierEvent(
    val status: String,
    val statusKind: String?,
    val concernsThisApp: Boolean,
    val sources: List<Source>,
    val label: String? = null,
    val date: String? = null,
    val ongoing: Boolean = false,
    val closed: String? = null,
    /** The On the record line it comes from. */
    val line: RecordLine? = null,
)

/** The stages past filing: a judge let the case go ahead, or a regulator opened a formal proceeding. Filing alone never raises a tier. */
val LET_PROCEED = setOf("survived_motion_to_dismiss", "proceeding_opened")

/** Legal items older than this, once ended, are shown but never change a tier. */
const val SCORED_YEARS = 3L

/** Ongoing, or ended (else dated) within the last three years. */
internal fun counts(e: TierEvent, today: LocalDate): Boolean {
    val dated = e.closed ?: e.date ?: return true
    return e.ongoing || within(dated, SCORED_YEARS, today)
}

/** True when [date] ("2024-03-05", or "2024-03" for the whole month) falls within [years] of [today]. */
internal fun within(date: String, years: Long, today: LocalDate): Boolean =
    (if (date.length == 7) "$date-31" else date) >= today.minusYears(years).toString()

/** The newest labelled event among [events], to name in a reason. */
private fun named(events: List<TierEvent>): TierEvent? = events.filter { it.label != null }.maxByOrNull { it.date.orEmpty() }

/** Location, health, financial, contacts, children's, biometric and precise movement data. */
val SENSITIVE_DATA = listOf(
    "precise_location", "approximate_location", "movement_and_driving", "health", "financial", "biometric",
    "childrens_data", "contacts", "sensitive_personal_data",
)

private val DEEP_REACH = mapOf(
    "accessibility_service" to "Can act as an accessibility service",
    "notification_listener" to "Can read your notifications",
    "device_admin" to "Can be a device administrator",
    "vpn_service" to "Can run a VPN",
)

/** Singular nouns, so a reason reads "Location data goes elsewhere". */
private val REASON_DATA = mapOf(
    "precise_location" to "Location data",
    "approximate_location" to "Location data",
    "movement_and_driving" to "Driving data",
    "physical_activity" to "Activity data",
    "contacts" to "Your contact list",
    "account_identity" to "Account data",
    "device_identifiers" to "Your advertising ID",
    "app_activity" to "In-app activity",
    "crash_diagnostics" to "Crash data",
    "sensitive_personal_data" to "Sensitive personal data",
    "health" to "Health data",
    "financial" to "Financial data",
    "biometric" to "Biometric data",
    "childrens_data" to "Children's data",
)

/**
 * Which flow a reason names when several qualify: a current one before a past practice, then what
 * the app's maker says itself before a ruling, then reports, allegations and inferred lines;
 * sensitive data first.
 */
internal val NAMED_FIRST = compareBy<FlowLine>({ it.historical }, { NAMING_ORDER.indexOf(it.status) }, { if (it.data in SENSITIVE_DATA) 0 else 1 })

/** Self-disclosed first: a current flow the maker discloses is named before a ruling (null is auto). */
internal val NAMING_ORDER = listOf("self_disclosed", "adjudicated", "reported", "alleged", null)

/** Copies (derives_from) and claims that rest on one investigation (single_source) count once. */
internal fun independentSources(sources: List<Source>): Int =
    if (sources.any { it.singleSource }) 1 else sources.count { it.derivesFrom == null }

/** "reported" with a single independent source never raises a tier, nor does an alleged line whose case is only filed. */
private fun canRaise(line: FlowLine) =
    (line.status != "reported" || independentSources(line.sources) >= 2) && (line.status != "alleged" || line.statusKind in LET_PROCEED)

/**
 * The tier formula (docs/METHOD.md, "Tiers"), checked strongest first so the reason names what set
 * the tier. [curated] is false for apps without a reviewed record: those are never Flagged or
 * Expected, so they get Caution at most, or "No record yet" with [scanFacts] as their line.
 */
fun tier(
    curated: Boolean,
    appName: String,
    flows: List<FlowLine>,
    events: List<TierEvent>,
    reach: List<String>,
    scanFacts: String = NO_RECORD,
    today: LocalDate = LocalDate.now(),
): TierResult {
    // A flow that's off by default, with a setting in the app that controls it, doesn't count.
    val raising = flows.filter { canRaise(it) && !(it.default == "off" && it.controlled) }
    val legal = events.filter { it.concernsThisApp && counts(it, today) }
    fun flagged(reason: String, rule: String) =
        if (curated) TierResult(Tier.FLAGGED, reason, rule) else TierResult(Tier.CAUTION, reason, rule, capped = true)

    // F1: sensitive data goes elsewhere, by the app's own account or a ruling; checked first, so a
    // current flow the maker discloses is named before a ruling.
    raising.filter { it.bucket == GOES_ELSEWHERE && it.data in SENSITIVE_DATA && it.status in setOf("self_disclosed", "adjudicated") }
        .minWithOrNull(NAMED_FIRST.thenBy { SENSITIVE_DATA.indexOf(it.data) })
        ?.let { return flagged(reason(it, appName), "F1").setBy(flow = it) }
    // F2: a ruling, settlement or order concerning this app's data, named so a Flagged badge is never unexplained.
    legal.filter { it.status == "adjudicated" }.takeIf { it.isNotEmpty() }?.let { rulings ->
        val ruling = named(rulings) ?: return flagged("A court or regulator has ruled on this app's data", "F2").setBy(event = rulings.first())
        return flagged("A ${ruling.date?.take(4)?.let { "$it " }.orEmpty()}ruling on this app's data: ${ruling.label}", "F2").setBy(event = ruling)
    }
    // F3: a lawsuit over this app's data has survived a motion to dismiss.
    legal.filter { it.status == "alleged" && it.statusKind == "survived_motion_to_dismiss" }.takeIf { it.isNotEmpty() }?.let { suits ->
        return flagged(
            named(suits)?.let { "A lawsuit over this app's data survived a motion to dismiss: ${it.label} ($NOT_PROVEN)" }
                ?: "A lawsuit over this app's data has survived a motion to dismiss ($NOT_PROVEN)",
            "F3",
        ).setBy(event = named(suits) ?: suits.first())
    }
    // C1: used for more, by the app's own account, two independent reports, or a ruling.
    raising.filter { it.bucket == USED_FOR_MORE && it.status in setOf("self_disclosed", "reported", "adjudicated") }
        .minWithOrNull(NAMED_FIRST)
        ?.let { return TierResult(Tier.CAUTION, reason(it, appName), "C1").setBy(flow = it) }
    // C2: anything else that goes elsewhere, including sensitive data with weaker evidence than F1 needs.
    raising.filter { it.bucket == GOES_ELSEWHERE }
        .minWithOrNull(NAMED_FIRST)
        ?.let { return TierResult(Tier.CAUTION, reason(it, appName), "C2").setBy(flow = it) }
    // C3: a regulator has opened a formal proceeding over this app's data (alleged, not yet decided). Filing alone, a
    // lawsuit's or a complaint's, never counts: a lawsuit counts once a judge lets it go ahead (F3).
    legal.filter { it.status == "alleged" && it.statusKind == "proceeding_opened" }.takeIf { it.isNotEmpty() }?.let { proceedings ->
        val what = "A regulator has opened a formal proceeding over this app's data"
        return TierResult(
            Tier.CAUTION,
            named(proceedings)?.let { "$what: ${it.label} ($NOT_YET_DECIDED)" } ?: "$what ($NOT_YET_DECIDED)",
            "C3",
        ).setBy(event = named(proceedings) ?: proceedings.first())
    }
    // C4: access that reaches into the rest of the phone.
    reach.firstNotNullOfOrNull { DEEP_REACH[it] }?.let { return TierResult(Tier.CAUTION, it, "C4") }

    // Expected needs a reviewed record: without one, FinePrint says only what its scan found.
    if (!curated) return TierResult(null, scanFacts, "N")
    return TierResult(Tier.EXPECTED, "Nothing found beyond running the app", "E")
}

/** "Life360's", but "Google Maps'". */
internal fun possessive(name: String): String = if (name.endsWith("s")) "$name'" else "$name's"

/** "Location data goes elsewhere — Life360's own policy". */
private fun reason(line: FlowLine, appName: String): String {
    val what = REASON_DATA[line.data] ?: "Data"
    val verb = if (line.bucket == USED_FOR_MORE) "is used for more" else "goes elsewhere"
    val basis = when (line.status) {
        "self_disclosed" -> if (line.via == null) "${possessive(appName)} own policy" else "${possessive(line.via)} own disclosure"
        "adjudicated" -> "a court or regulator's decision"
        "reported" -> "reported by two or more sources"
        "alleged" -> "alleged, ${undecided(line.forum)}"
        else -> "${line.via ?: line.recipient} code in this app"
    }
    return "$what $verb — $basis"
}

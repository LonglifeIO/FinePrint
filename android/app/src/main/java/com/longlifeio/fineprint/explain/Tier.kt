package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Source
import java.time.LocalDate

/** How much an app deserves attention. The rules are published in docs/METHOD.md. */
enum class Tier(val label: String, val definition: String) {
    // The short lines (the home's section headers, the tier chip); How to read gives each in full, as a list.
    FLAGGED("Flagged", "Sensitive data goes to other companies, or a court has acted on a case about this app's data."),
    CAUTION("Caution", "Data use, sharing or device reach goes beyond running the app, or a regulator has opened a proceeding."),
    EXPECTED("Expected", "FinePrint checked this app and found nothing beyond what it needs to work."),
}

/**
 * An app's tier, the one line that explains it, and the rule that set it (F1-F3, C1-C4, E). A null
 * [tier] is "Not checked yet": an app without a record that isn't Caution; its line is what
 * the scan found (rule N).
 */
data class TierResult(val tier: Tier?, val reason: String, val rule: String, val capped: Boolean = false) {
    /**
     * The lines that set the tier (F1, C1, C2): each would set it alone. The reason names the first; the page marks them
     * all and reads them first. Never an alleged line: what a claimant alleges never leads. Not part of what the tier is.
     */
    var reasons: List<FlowLine> = emptyList()
        internal set

    /** The rulings, lawsuits or regulators' proceedings that set it (F2, F3, C3), the one the reason names first. */
    var events: List<TierEvent> = emptyList()
        internal set

    /** The line the reason names, when a line set the tier. */
    val flow: FlowLine? get() = reasons.firstOrNull()

    /** The legal item the reason names, when one set the tier. */
    val event: TierEvent? get() = events.firstOrNull()
}

private fun TierResult.setBy(flows: List<FlowLine> = emptyList(), events: List<TierEvent> = emptyList()) = also { it.reasons = flows; it.events = events }

/** [named] first, then the rest in their order. */
private fun <T> namedFirst(named: T, all: List<T>): List<T> = listOf(named) + (all - named)

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

/** Singular nouns, so a reason reads "Life360 says your location goes to other companies". */
private val REASON_DATA = mapOf(
    "precise_location" to "your location",
    "approximate_location" to "your location",
    "movement_and_driving" to "your driving data",
    "physical_activity" to "your activity data",
    "contacts" to "your contact list",
    "account_identity" to "your account data",
    "device_identifiers" to "your advertising ID",
    "app_activity" to "what you do in the app",
    "crash_diagnostics" to "crash data",
    "sensitive_personal_data" to "sensitive personal data",
    "health" to "your health data",
    "financial" to "your financial data",
    "biometric" to "your biometric data",
    "childrens_data" to "children's data",
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
 * the tier. [curated] is false for apps without a record: those are never Flagged or
 * Expected, so they get Caution at most, or "Not checked yet" with [scanFacts] as their line.
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
        .sortedWith(NAMED_FIRST.thenBy { SENSITIVE_DATA.indexOf(it.data) }).takeIf { it.isNotEmpty() }
        ?.let { return flagged(reason(it.first(), appName), "F1").setBy(flows = it) }
    // F2: a ruling, settlement or order concerning this app's data, named so a Flagged badge is never unexplained.
    legal.filter { it.status == "adjudicated" }.takeIf { it.isNotEmpty() }?.let { rulings ->
        val ruling = named(rulings) ?: return flagged("A court or regulator has ruled on this app's data", "F2").setBy(events = rulings)
        return flagged("A ${ruling.date?.take(4)?.let { "$it " }.orEmpty()}ruling on this app's data: ${ruling.label}", "F2").setBy(events = namedFirst(ruling, rulings))
    }
    // F3: a lawsuit over this app's data has survived a motion to dismiss. Said as the court's step, a fact; the claim
    // itself stays in On the record.
    legal.filter { it.status == "alleged" && it.statusKind == "survived_motion_to_dismiss" }.takeIf { it.isNotEmpty() }?.let { suits ->
        val what = "A court has let a case about this app's data go ahead"
        val suit = named(suits) ?: suits.first()
        return flagged(named(suits)?.let { "$what: ${it.label} ($NOT_PROVEN)" } ?: "$what ($NOT_PROVEN)", "F3").setBy(events = namedFirst(suit, suits))
    }
    // C1: used for more, by the app's own account, two independent reports, or a ruling.
    raising.filter { it.bucket == USED_FOR_MORE && it.status in setOf("self_disclosed", "reported", "adjudicated") }
        .sortedWith(NAMED_FIRST).takeIf { it.isNotEmpty() }
        ?.let { return TierResult(Tier.CAUTION, reason(it.first(), appName), "C1").setBy(flows = it) }
    // C2: anything else that goes elsewhere, including sensitive data with weaker evidence than F1 needs. A line a
    // claimant alleges is never the reason given: when only such lines count, the reason is the court's or regulator's
    // step that lets them count, and no line is marked.
    raising.filter { it.bucket == GOES_ELSEWHERE }.takeIf { it.isNotEmpty() }?.let { elsewhere ->
        val facts = elsewhere.filterNot { it.status == "alleged" }.sortedWith(NAMED_FIRST)
        if (facts.isNotEmpty()) return TierResult(Tier.CAUTION, reason(facts.first(), appName), "C2").setBy(flows = facts)
        val step = if (elsewhere.any { it.statusKind == "survived_motion_to_dismiss" }) {
            "A court has let a case go ahead over where this app's data goes ($NOT_PROVEN)"
        } else {
            "A regulator has opened a formal proceeding over where this app's data goes ($NOT_YET_DECIDED)"
        }
        return TierResult(Tier.CAUTION, step, "C2")
    }
    // C3: a regulator has opened a formal proceeding over this app's data (alleged, not yet decided). Filing alone, a
    // lawsuit's or a complaint's, never counts: a lawsuit counts once a judge lets it go ahead (F3).
    legal.filter { it.status == "alleged" && it.statusKind == "proceeding_opened" }.takeIf { it.isNotEmpty() }?.let { proceedings ->
        val what = "A regulator has opened a formal proceeding over this app's data"
        return TierResult(
            Tier.CAUTION,
            named(proceedings)?.let { "$what: ${it.label} ($NOT_YET_DECIDED)" } ?: "$what ($NOT_YET_DECIDED)",
            "C3",
        ).setBy(events = namedFirst(named(proceedings) ?: proceedings.first(), proceedings))
    }
    // C4: access that reaches into the rest of the phone.
    reach.firstNotNullOfOrNull { DEEP_REACH[it] }?.let { return TierResult(Tier.CAUTION, it, "C4") }

    // Expected needs a record FinePrint has checked: without one, FinePrint says only what its scan found.
    if (!curated) return TierResult(null, scanFacts, "N")
    return TierResult(Tier.EXPECTED, "Nothing found beyond running the app", "E")
}

/** "Life360's", but "Google Maps'". */
internal fun possessive(name: String): String = if (name.endsWith("s")) "$name'" else "$name's"

/**
 * "Life360 says your location goes to other companies": who says so, then what happens to the data. The app or a
 * tracker's company says; a court or regulator found; reporters found (named when there are two outlets, else counted,
 * with the Sources sheet listing them); or tracker code in the app can send it.
 */
private fun reason(line: FlowLine, appName: String): String {
    val data = REASON_DATA[line.data] ?: "your data"
    val more = line.bucket == USED_FOR_MORE
    fun found(who: String) = if (more) "$who found $appName uses $data for more than running the app" else "$who found $data goes to other companies"
    return when (line.status) {
        "self_disclosed" -> (line.via ?: appName).let { who -> if (more) "$who says it uses $data for more than running the app" else "$who says $data goes to other companies" }
        "adjudicated" -> found("A court or regulator")
        "reported" -> found(reporters(line.sources))
        // Never the reason given (tier() leaves alleged lines out); said as a claim if it ever were.
        "alleged" -> "It's alleged that $data ${if (more) "is used for more than running the app" else "goes to other companies"} (${undecided(line.forum)})"
        else -> "${line.via ?: line.recipient} code in this app " + if (more) "can use $data for more than running the app" else "can send $data to other companies"
    }
}

/** "The Markup and Reuters" when two outlets reported it independently; otherwise "3 independent reports". */
internal fun reporters(sources: List<Source>): String {
    val outlets = sources.filter { it.derivesFrom == null }.map { outlet(it.title) }.distinct()
    return if (outlets.size == 2) outlets.joinToString(" and ") else "${independentSources(sources)} independent reports"
}

/** An outlet's name from its source's title: "The Markup (follow-up)" and "Consumer Reports, “Who Shares …”" give "The Markup" and "Consumer Reports". */
private fun outlet(title: String): String = title.substringBefore(" (").substringBefore(", ").substringBefore(": ").trim()

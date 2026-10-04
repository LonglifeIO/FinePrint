package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Source

/** How much an app deserves attention. The rules are published in docs/METHOD.md. */
enum class Tier(val label: String, val definition: String) {
    FLAGGED("Flagged", "Sensitive data goes to other companies by the app's own account or a ruling, or a court has ruled on, or let proceed, a case over this app's data."),
    CAUTION("Caution", "Data is used beyond running the app or goes to other companies, a lawsuit over this app's data has been filed, or the app can reach deep into the phone."),
    EXPECTED("Expected", "FinePrint's reviewed record finds nothing beyond what running the app needs."),
}

/**
 * An app's tier, the one line that explains it, and the rule that set it (F1-F3, C1-C4, E). A null
 * [tier] is "No record yet": an app without a reviewed record that isn't Caution; its line is what
 * the scan found (rule N).
 */
data class TierResult(val tier: Tier?, val reason: String, val rule: String, val capped: Boolean = false)

/** A legal or regulatory item, as the tier rules see it. */
data class TierEvent(val status: String, val statusKind: String?, val concernsThisApp: Boolean, val sources: List<Source>)

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

/** Copies (derives_from) and claims that rest on one investigation (single_source) count once. */
internal fun independentSources(sources: List<Source>): Int =
    if (sources.any { it.singleSource }) 1 else sources.count { it.derivesFrom == null }

/** "reported" with a single independent source never raises a tier. */
private fun canRaise(status: String?, sources: List<Source>) = status != "reported" || independentSources(sources) >= 2

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
): TierResult {
    val raising = flows.filter { canRaise(it.status, it.sources) }
    val legal = events.filter { it.concernsThisApp }
    fun flagged(reason: String, rule: String) =
        if (curated) TierResult(Tier.FLAGGED, reason, rule) else TierResult(Tier.CAUTION, reason, rule, capped = true)

    // F2: a ruling, settlement or order concerning this app's data.
    if (legal.any { it.status == "adjudicated" }) return flagged("A court or regulator has ruled on this app's data", "F2")
    // F1: sensitive data goes elsewhere, by the app's own account or a ruling.
    raising.filter { it.bucket == GOES_ELSEWHERE && it.data in SENSITIVE_DATA && it.status in setOf("self_disclosed", "adjudicated") }
        .minWithOrNull(compareBy({ if (it.status == "adjudicated") 0 else 1 }, { SENSITIVE_DATA.indexOf(it.data) }))
        ?.let { return flagged(reason(it, appName), "F1") }
    // F3: a lawsuit over this app's data has survived a motion to dismiss.
    if (legal.any { it.status == "alleged" && it.statusKind == "survived_motion_to_dismiss" }) {
        return flagged("A lawsuit over this app's data has survived a motion to dismiss (not proven in court)", "F3")
    }
    // C1: used for more, by the app's own account, two independent reports, or a ruling.
    raising.firstOrNull { it.bucket == USED_FOR_MORE && it.status in setOf("self_disclosed", "reported", "adjudicated") }
        ?.let { return TierResult(Tier.CAUTION, reason(it, appName), "C1") }
    // C2: anything else that goes elsewhere, including sensitive data with weaker evidence than F1 needs.
    raising.filter { it.bucket == GOES_ELSEWHERE }
        .minWithOrNull(compareBy({ STRENGTH.indexOf(it.status) }, { if (it.data in SENSITIVE_DATA) 0 else 1 }))
        ?.let { return TierResult(Tier.CAUTION, reason(it, appName), "C2") }
    // C3: a lawsuit over this app's data has been filed (alleged, not yet past a motion to dismiss).
    if (legal.any { it.status == "alleged" && (it.statusKind == null || it.statusKind == "filed") }) {
        return TierResult(Tier.CAUTION, "A lawsuit over this app's data has been filed (not proven in court)", "C3")
    }
    // C4: access that reaches into the rest of the phone.
    reach.firstNotNullOfOrNull { DEEP_REACH[it] }?.let { return TierResult(Tier.CAUTION, it, "C4") }

    // Expected needs a reviewed record: without one, FinePrint says only what its scan found.
    if (!curated) return TierResult(null, scanFacts, "N")
    return TierResult(Tier.EXPECTED, "Nothing found beyond running the app", "E")
}

/** Strongest evidence first; null (auto) last. */
private val STRENGTH = listOf("adjudicated", "self_disclosed", "reported", "alleged", null)

/** "Location data goes elsewhere — Life360's own policy". */
private fun reason(line: FlowLine, appName: String): String {
    val what = REASON_DATA[line.data] ?: "Data"
    val verb = if (line.bucket == USED_FOR_MORE) "is used for more" else "goes elsewhere"
    val basis = when (line.status) {
        "self_disclosed" -> if (line.via == null) "$appName's own policy" else "${line.via}'s own disclosure"
        "adjudicated" -> "a court or regulator's decision"
        "reported" -> "reported by two or more sources"
        "alleged" -> "alleged, $NOT_PROVEN"
        else -> "${line.via ?: line.recipient} code in this app"
    }
    return "$what $verb — $basis"
}

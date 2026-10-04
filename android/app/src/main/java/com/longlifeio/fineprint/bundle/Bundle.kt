package com.longlifeio.fineprint.bundle

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** bundle.json (CC BY 4.0): FinePrint's reviewed records, joined to trackers.json (ODbL) by tracker id. */
class Bundle(
    val version: String,
    val generatedAt: String,
    val apps: Map<String, AppRecord>,
    val trackers: Map<String, TrackerRecord>,
    val companies: Map<String, Company>,
    val permissions: Map<String, PermissionText>,
    val deviceReach: Map<String, ReachText>,
)

/** One cited source. [asOf] is its own date; undated pages carry only [accessed], the day FinePrint read them. */
data class Source(
    val url: String,
    val title: String,
    val type: String,
    val status: String,
    val asOf: String?,
    val accessed: String?,
    val quote: String,
    val id: String? = null,
    /** Id of the source this one re-reports: such copies don't count as independent. */
    val derivesFrom: String? = null,
    /** The claim rests on one original investigation or study. */
    val singleSource: Boolean = false,
)

/** Where a legal matter stands (a dismissal, an appeal), sourced separately; never changes the status. */
data class ProceduralNote(val text: String, val sources: List<Source>)

/** One "what goes where" line. [status] is null only for flows FinePrint derived itself ("auto"). */
data class DataFlow(
    /** Set when a control names this flow ("flow-…"). */
    val id: String?,
    val data: String,
    val recipient: String?,
    val recipientLabel: String?,
    val purpose: String,
    val bucket: String,
    val status: String?,
    val wording: String?,
    val historical: Boolean,
    val proceduralNote: ProceduralNote?,
    /** The first source is the primary one. */
    val sources: List<Source>,
)

data class Consequence(
    val text: String,
    val status: String,
    val wording: String?,
    val historical: Boolean,
    val proceduralNote: ProceduralNote?,
    val sources: List<Source>,
    /** filed, survived_motion_to_dismiss, dismissed (alleged); ruling, settlement, ... (adjudicated). */
    val statusKind: String? = null,
    /** Who the action is against, when that isn't the app's developer. */
    val subjectCompany: String? = null,
    /** The package whose data the action concerns. */
    val concernsApp: String? = null,
    val appealPending: Boolean = false,
)

/** A company's regulatory or legal history entry; [date] may be year and month only ("2024-03"). */
data class LegalEvent(
    val date: String,
    val title: String,
    /** The court or regulator. */
    val body: String?,
    val type: String,
    val amount: String?,
    val status: String,
    val statusKind: String?,
    val subjectCompany: String?,
    val concernsApp: String?,
    val notes: String?,
    val proceduralNote: ProceduralNote?,
    val sources: List<Source>,
    val appealPending: Boolean = false,
)

data class ExodusReport(val id: Int, val appVersion: String, val created: String, val trackerCount: Int)

/** A flow a control limits; [inferred] when the sources don't say so in as many words, with a [note] saying what is inferred. */
data class Limit(val flow: String, val inferred: Boolean = false, val note: String? = null)

/**
 * An in-app setting that limits some of the app's flows (by flow id); the user ticks it when done.
 * [effect] is what turning it off changes, in the app's own quoted words, or that the app doesn't say.
 */
data class Control(val id: String, val label: String, val how: String, val effect: String, val limits: List<Limit>, val sources: List<Source>)

/** A short sourced line shown under the summary, such as what the app's policy says it doesn't do. */
data class SummaryNote(val text: String, val status: String, val wording: String?, val sources: List<Source>)

data class AppRecord(
    /** SHA-256 of the record's JSON (without the build-computed stale flag): changes only when the record does. */
    val hash: String,
    val packageId: String,
    val displayName: String,
    val developerCompany: String?,
    val summary: String,
    val summaryNotes: List<SummaryNote>,
    val trackers: List<String>,
    val exodusReport: ExodusReport?,
    val dataFlows: List<DataFlow>,
    val consequences: List<Consequence>,
    val privacyControls: String?,
    val riskTags: List<String>,
    /** Qualifier shown with a tag, e.g. regulatory_action -> "against Allstate/Arity concerning this app's data". */
    val riskTagNotes: Map<String, String>,
    val controls: List<Control>,
    val stale: Boolean,
    val lastReviewed: String,
)

data class TrackerRecord(
    val hash: String,
    val id: String,
    val owner: String,
    val ownerChain: List<String>,
    val party: String?,
    val categories: List<String>,
    val dataFlows: List<DataFlow>,
    val consequences: List<Consequence>,
    val lastReviewed: String,
)

data class Company(
    val hash: String,
    val id: String,
    val name: String,
    /** For running text ("Allstate"); defaults to [name]. */
    val shortName: String?,
    val subsidiaries: List<String>,
    val events: List<LegalEvent> = emptyList(),
)

data class PermissionText(val id: String, val plain: String, val whyItMatters: String, val feeds: List<String>)

data class ReachText(val id: String, val plain: String, val whyItMatters: String)

fun parseBundle(json: String): Bundle {
    val root = JSONObject(json)
    require(root.getInt("schema_version") == 1) { "unsupported bundle schema ${root.get("schema_version")}" }
    return Bundle(
        version = root.getString("bundle_version"),
        generatedAt = root.getString("generated_at"),
        apps = root.objects("apps").associate { it.getString("package_id") to it.toAppRecord() },
        trackers = root.objects("trackers").associate { it.getString("id") to it.toTrackerRecord() },
        companies = root.objects("companies").associate { it.getString("id") to it.toCompany() },
        permissions = root.objects("permissions").associate {
            it.getString("id") to PermissionText(it.getString("id"), it.getString("plain"), it.getString("why_it_matters"), it.strings("feeds"))
        },
        deviceReach = root.objects("device_reach").associate {
            it.getString("id") to ReachText(it.getString("id"), it.getString("plain"), it.getString("why_it_matters"))
        },
    )
}

private fun JSONObject.toAppRecord() = AppRecord(
    hash = contentHash(),
    packageId = getString("package_id"),
    displayName = getString("display_name"),
    developerCompany = text("developer_company"),
    summary = getString("summary"),
    summaryNotes = objects("summary_notes").map {
        SummaryNote(it.getString("text"), it.getString("status"), it.text("wording"), it.objects("sources").map { s -> s.toSource() })
    },
    trackers = strings("trackers"),
    exodusReport = optJSONObject("exodus_report")?.let {
        ExodusReport(it.getInt("id"), it.getString("app_version"), it.getString("created"), it.getInt("tracker_count"))
    },
    dataFlows = objects("data_flows").map { it.toDataFlow() },
    consequences = objects("consequences").map { it.toConsequence() },
    privacyControls = text("privacy_controls"),
    riskTags = strings("risk_tags"),
    riskTagNotes = optJSONObject("risk_tag_notes")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty(),
    controls = objects("controls").map {
        Control(
            it.getString("id"), it.getString("label"), it.getString("how"), it.text("effect").orEmpty(), it.limits(),
            it.objects("sources").map { s -> s.toSource() },
        )
    },
    stale = optBoolean("stale"),
    lastReviewed = getString("last_reviewed"),
)

private fun JSONObject.toTrackerRecord() = TrackerRecord(
    hash = contentHash(),
    id = getString("id"),
    owner = getString("owner"),
    ownerChain = strings("owner_chain"),
    party = text("party"),
    categories = strings("categories"),
    dataFlows = objects("data_flows").map { it.toDataFlow() },
    consequences = objects("consequences").map { it.toConsequence() },
    lastReviewed = getString("last_reviewed"),
)

private fun JSONObject.toDataFlow() = DataFlow(
    id = text("id"),
    data = getString("data"),
    recipient = text("recipient"),
    recipientLabel = text("recipient_label"),
    purpose = getString("purpose"),
    bucket = getString("bucket"),
    status = getString("status"),
    wording = text("wording"),
    historical = optBoolean("historical"),
    proceduralNote = optJSONObject("procedural_note")?.toProceduralNote(),
    sources = objects("sources").map { it.toSource() },
)

private fun JSONObject.toConsequence() = Consequence(
    text = getString("text"),
    status = getString("status"),
    wording = text("wording"),
    historical = optBoolean("historical"),
    proceduralNote = optJSONObject("procedural_note")?.toProceduralNote(),
    sources = objects("sources").map { it.toSource() },
    statusKind = text("status_kind"),
    subjectCompany = text("subject_company"),
    concernsApp = text("concerns_app"),
    appealPending = optBoolean("appeal_pending"),
)

private fun JSONObject.toCompany() = Company(
    hash = contentHash(),
    id = getString("id"),
    name = getString("name"),
    shortName = text("short_name"),
    subsidiaries = strings("subsidiaries"),
    events = objects("regulatory_history").map {
        LegalEvent(
            date = it.getString("date"),
            title = it.getString("title"),
            body = it.text("body"),
            type = it.getString("type"),
            amount = it.text("amount"),
            status = it.getString("status"),
            statusKind = it.text("status_kind"),
            subjectCompany = it.text("subject_company"),
            concernsApp = it.text("concerns_app"),
            notes = it.text("notes"),
            proceduralNote = it.optJSONObject("procedural_note")?.toProceduralNote(),
            sources = it.objects("sources").map { s -> s.toSource() },
            appealPending = it.optBoolean("appeal_pending"),
        )
    },
)

private fun JSONObject.toProceduralNote() = ProceduralNote(getString("text"), objects("sources").map { it.toSource() })

private fun JSONObject.toSource() = Source(
    url = getString("url"),
    title = text("title") ?: getString("url"),
    type = getString("type"),
    status = getString("status"),
    asOf = text("as_of"),
    accessed = text("accessed"),
    quote = text("quote").orEmpty(),
    id = text("id"),
    derivesFrom = text("derives_from"),
    singleSource = optBoolean("single_source"),
)

/** A control's limits: plain flow ids, or {flow, inferred, note} objects. */
private fun JSONObject.limits(): List<Limit> {
    val a = optJSONArray("limits") ?: return emptyList()
    return List(a.length()) { i ->
        when (val v = a.get(i)) {
            is JSONObject -> Limit(v.getString("flow"), v.optBoolean("inferred"), v.text("note"))
            else -> Limit(v.toString())
        }
    }
}

/** SHA-256 of this record's JSON, leaving out "stale", which build.py recomputes on every build. */
private fun JSONObject.contentHash(): String {
    val text = JSONObject(toString()).apply { remove("stale") }.toString()
    return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

private fun JSONObject.objects(key: String): List<JSONObject> =
    optJSONArray(key)?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: emptyList()

private fun JSONObject.strings(key: String): List<String> =
    optJSONArray(key)?.let { a: JSONArray -> List(a.length()) { a.getString(it) } } ?: emptyList()

/** A present, non-null string (optString would turn JSON null into "null"). */
private fun JSONObject.text(key: String): String? = if (isNull(key)) null else getString(key)

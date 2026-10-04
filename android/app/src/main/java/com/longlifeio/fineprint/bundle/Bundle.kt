package com.longlifeio.fineprint.bundle

import org.json.JSONArray
import org.json.JSONObject

/** bundle.json (CC BY 4.0): Fine Print's reviewed records, joined to trackers.json (ODbL) by tracker id. */
class Bundle(
    val version: String,
    val generatedAt: String,
    val apps: Map<String, AppRecord>,
    val trackers: Map<String, TrackerRecord>,
    val companies: Map<String, Company>,
    val permissions: Map<String, PermissionText>,
    val deviceReach: Map<String, ReachText>,
)

/** [asOf] is the source's own date; undated pages carry only [accessed], the day Fine Print read them. */
data class Source(val url: String, val title: String, val asOf: String?, val accessed: String?, val quote: String)

/** Where a legal matter stands (a dismissal, an appeal), sourced separately; never changes the status. */
data class ProceduralNote(val text: String, val sources: List<Source>)

/** One "what goes where" line. [status] is null only for flows Fine Print derived itself ("auto"). */
data class DataFlow(
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
)

data class ExodusReport(val id: Int, val appVersion: String, val created: String, val trackerCount: Int)

data class AppRecord(
    val packageId: String,
    val summary: String,
    val trackers: List<String>,
    val exodusReport: ExodusReport?,
    val dataFlows: List<DataFlow>,
    val consequences: List<Consequence>,
    val privacyControls: String?,
    val riskTags: List<String>,
    /** Qualifier shown with a tag, e.g. regulatory_action -> "against Allstate/Arity concerning this app's data". */
    val riskTagNotes: Map<String, String>,
    val stale: Boolean,
    val lastReviewed: String,
)

data class TrackerRecord(
    val id: String,
    val owner: String,
    val ownerChain: List<String>,
    val party: String?,
    val categories: List<String>,
    val dataFlows: List<DataFlow>,
    val consequences: List<Consequence>,
    val lastReviewed: String,
)

data class Company(val id: String, val name: String, val subsidiaries: List<String>)

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
        companies = root.objects("companies").associate {
            it.getString("id") to Company(it.getString("id"), it.getString("name"), it.strings("subsidiaries"))
        },
        permissions = root.objects("permissions").associate {
            it.getString("id") to PermissionText(it.getString("id"), it.getString("plain"), it.getString("why_it_matters"), it.strings("feeds"))
        },
        deviceReach = root.objects("device_reach").associate {
            it.getString("id") to ReachText(it.getString("id"), it.getString("plain"), it.getString("why_it_matters"))
        },
    )
}

private fun JSONObject.toAppRecord() = AppRecord(
    packageId = getString("package_id"),
    summary = getString("summary"),
    trackers = strings("trackers"),
    exodusReport = optJSONObject("exodus_report")?.let {
        ExodusReport(it.getInt("id"), it.getString("app_version"), it.getString("created"), it.getInt("tracker_count"))
    },
    dataFlows = objects("data_flows").map { it.toDataFlow() },
    consequences = objects("consequences").map { it.toConsequence() },
    privacyControls = text("privacy_controls"),
    riskTags = strings("risk_tags"),
    riskTagNotes = optJSONObject("risk_tag_notes")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty(),
    stale = optBoolean("stale"),
    lastReviewed = getString("last_reviewed"),
)

private fun JSONObject.toTrackerRecord() = TrackerRecord(
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
)

private fun JSONObject.toProceduralNote() = ProceduralNote(getString("text"), objects("sources").map { it.toSource() })

private fun JSONObject.toSource() = Source(
    url = getString("url"),
    title = text("title") ?: getString("url"),
    asOf = text("as_of"),
    accessed = text("accessed"),
    quote = text("quote").orEmpty(),
)

private fun JSONObject.objects(key: String): List<JSONObject> =
    optJSONArray(key)?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: emptyList()

private fun JSONObject.strings(key: String): List<String> =
    optJSONArray(key)?.let { a: JSONArray -> List(a.length()) { a.getString(it) } } ?: emptyList()

/** A present, non-null string (optString would turn JSON null into "null"). */
private fun JSONObject.text(key: String): String? = if (isNull(key)) null else getString(key)

package com.longlifeio.fineprint.bundle

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** bundle.json (CC BY 4.0): FinePrint's reviewed records, joined to trackers.json (ODbL) by tracker id. */
class Bundle(
    val version: String,
    val generatedAt: String,
    val apps: Map<String, AppRecord>,
    /** By tracker id; a record that covers several ids is under each of them, and under its own. */
    val trackers: Map<String, TrackerRecord>,
    val companies: Map<String, Company>,
    val permissions: Map<String, PermissionText>,
    val deviceReach: Map<String, ReachText>,
    /** From jurisdictions.json: each country's laws that let its government compel data. */
    val jurisdictions: Map<String, Jurisdiction> = emptyMap(),
)

/** bundle.json, joined to jurisdictions.json when given. */
fun parseBundle(json: String, jurisdictions: String? = null): Bundle {
    val root = JSONObject(json)
    require(root.getInt("schema_version") == 1) { "unsupported bundle schema ${root.get("schema_version")}" }
    return Bundle(
        version = root.getString("bundle_version"),
        generatedAt = root.getString("generated_at"),
        apps = root.objects("apps").associate { it.getString("package_id") to it.toAppRecord() },
        trackers = root.objects("trackers").map { it.toTrackerRecord() }
            .flatMap { t -> (listOf(t.id) + t.covers).map { it to t } }.toMap(),
        companies = root.objects("companies").associate { it.getString("id") to it.toCompany() },
        permissions = root.objects("permissions").associate {
            it.getString("id") to PermissionText(it.getString("id"), it.getString("plain"), it.getString("why_it_matters"), it.strings("feeds"))
        },
        deviceReach = root.objects("device_reach").associate {
            it.getString("id") to ReachText(it.getString("id"), it.getString("plain"), it.getString("why_it_matters"))
        },
        jurisdictions = jurisdictions?.let(::parseJurisdictions).orEmpty(),
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
    changes = objects("changes").map {
        Change(
            it.getString("date"), it.getString("text"), it.getString("direction"), it.objects("sources").map { s -> s.toSource() },
            it.text("tier_before"), it.text("tier_after"),
        )
    },
)

private fun JSONObject.toTrackerRecord() = TrackerRecord(
    hash = contentHash(),
    id = getString("id"),
    owner = getString("owner"),
    ownerCompany = text("owner_company"),
    covers = strings("covers"),
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
    inOwnerApps = text("in_owner_apps"),
    government = government(),
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
    inForce = optBoolean("in_force"),
    closedDate = text("closed_date"),
    government = government(),
)

private fun JSONObject.toCompany() = Company(
    hash = contentHash(),
    id = getString("id"),
    name = getString("name"),
    shortName = text("short_name"),
    subsidiaries = strings("subsidiaries"),
    parent = text("parent"),
    jurisdiction = text("jurisdiction"),
    headquarters = text("headquarters"),
    jurisdictionSources = objects("jurisdiction_sources").map { it.toSource() },
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
            inForce = it.optBoolean("in_force"),
            closedDate = it.text("closed_date"),
        )
    },
)

private fun JSONObject.government(): GovernmentRef? =
    if (text("recipient_kind") != "government_body") null else GovernmentRef(getString("government_line"), getString("jurisdiction"))

internal fun JSONObject.toProceduralNote() = ProceduralNote(getString("text"), objects("sources").map { it.toSource() })

internal fun JSONObject.toSource() = Source(
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

internal fun JSONObject.objects(key: String): List<JSONObject> =
    optJSONArray(key)?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: emptyList()

private fun JSONObject.strings(key: String): List<String> =
    optJSONArray(key)?.let { a: JSONArray -> List(a.length()) { a.getString(it) } } ?: emptyList()

/** A present, non-null string (optString would turn JSON null into "null"). */
internal fun JSONObject.text(key: String): String? = if (isNull(key)) null else getString(key)

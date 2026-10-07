package com.longlifeio.fineprint.bundle

import org.json.JSONObject

/**
 * jurisdictions.json (CC BY 4.0): for each country, and for a union of countries such as the EU, the laws that let a
 * government compel a company to hand over data.
 */
data class Jurisdiction(val id: String, val name: String, val laws: List<Law>, val lastReviewed: String)

/** A law in force, quoted from its own text: a Can compel line. */
data class Law(
    val id: String,
    val name: String,
    val citation: String,
    val text: String,
    val status: String,
    val statusNote: ProceduralNote?,
    /** The first is the law's own text. */
    val sources: List<Source>,
    /** The countries a union's law binds (an EU regulation); null: it binds the country of its own entry. */
    val appliesTo: List<String>? = null,
    /** When the owner last reviewed this entry; stale, from build.py, once that's more than 180 days before the build. */
    val lastReviewed: String? = null,
    val stale: Boolean = false,
)

fun parseJurisdictions(json: String): Map<String, Jurisdiction> {
    val root = JSONObject(json)
    require(root.getInt("schema_version") == 1) { "unsupported jurisdictions schema ${root.get("schema_version")}" }
    return root.objects("jurisdictions").associate { j ->
        j.getString("id") to Jurisdiction(
            id = j.getString("id"),
            name = j.getString("name"),
            laws = j.objects("laws").map {
                Law(
                    it.getString("id"), it.getString("name"), it.getString("citation"), it.getString("text"), it.getString("status"),
                    it.optJSONObject("status_note")?.toProceduralNote(), it.objects("sources").map { s -> s.toSource() },
                    it.optJSONArray("applies_to")?.let { a -> List(a.length()) { i -> a.getString(i) } },
                    it.text("last_reviewed"), it.optBoolean("stale"),
                )
            },
            lastReviewed = j.getString("last_reviewed"),
        )
    }
}

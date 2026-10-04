package com.longlifeio.fineprint.egress

import android.content.res.AssetManager
import org.json.JSONObject

/**
 * One tracker from the bundled signature list: Exodus Privacy's trackers (id "exodus-<n>") plus any
 * FinePrint additions (id "fp-<slug>"). Same id form as bundle/schema.json, so G2 can join on it.
 */
data class TrackerSignature(
    val id: String,
    val name: String,
    /** Exodus regex such as "com.appsflyer.|com.kochava.base."; '.' matches any character. */
    val codeSignature: String,
    val categories: List<String>,
)

/** assets/trackers.json, as written by pipeline/fetch_trackers.py. */
class TrackerSignatures(
    val source: String,
    /** When the pipeline fetched the list (ISO 8601, America/Halifax). */
    val fetchedAt: String,
    /** The εxodus data is ODbL 1.0; this notice must be shown wherever its tracker names are. */
    val attribution: String,
    val trackers: List<TrackerSignature>,
)

const val TRACKERS_ASSET = "trackers.json"

fun loadTrackerSignatures(assets: AssetManager): TrackerSignatures =
    assets.open(TRACKERS_ASSET).bufferedReader().use { parseTrackerSignatures(it.readText()) }

fun parseTrackerSignatures(json: String): TrackerSignatures {
    val root = JSONObject(json)
    val list = root.getJSONArray("trackers")
    val trackers = List(list.length()) { i ->
        val t = list.getJSONObject(i)
        val categories = t.optJSONArray("categories")
        TrackerSignature(
            id = t.getString("id"),
            name = t.getString("name"),
            // isNull() also covers JSON null, which optString() would turn into the text "null".
            codeSignature = if (t.isNull("code_signature")) "" else t.getString("code_signature"),
            categories = if (categories == null) emptyList() else List(categories.length()) { categories.getString(it) },
        )
    }
    return TrackerSignatures(
        source = root.optString("source"),
        fetchedAt = root.optString("fetched_at"),
        attribution = root.optString("attribution"),
        trackers = trackers,
    )
}

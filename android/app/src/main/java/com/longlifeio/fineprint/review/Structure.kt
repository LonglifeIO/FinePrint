package com.longlifeio.fineprint.review

import com.longlifeio.fineprint.bundle.AppRecord
import com.longlifeio.fineprint.bundle.DataFlow
import com.longlifeio.fineprint.bundle.TrackerRecord
import org.json.JSONArray
import org.json.JSONObject

/*
 * What a Reviewed mark compares (docs/METHOD.md, Your Reviewed marks): only the fields of FinePrint's
 * records that pipeline/build.py's structural diff compares to say whether a record got better or
 * worse. Wording, purposes, sources, legal lines and the store description aren't among them, so an
 * edit to those never marks an app changed. ReviewTest keeps this port and build.py in step.
 */

/** One current flow: its identity (id, or data and recipient), whether a company is named, its bucket, and whether it's on by default. */
data class FlowShape(val key: String, val data: String, val named: Boolean, val bucket: String, val on: Boolean)

data class Shape(val flows: List<FlowShape>, val trackers: List<String>, val controls: List<String>) {
    fun encode(): String = JSONObject()
        .put("flows", JSONArray(flows.map { JSONObject().put("key", it.key).put("data", it.data).put("named", it.named).put("bucket", it.bucket).put("on", it.on) }))
        .put("trackers", JSONArray(trackers)).put("controls", JSONArray(controls)).toString()

    companion object {
        /** Null for a mark set before shapes were kept (a bare hash): nothing is known about its record. */
        fun decode(text: String): Shape? = runCatching {
            val o = JSONObject(text)
            val flows = o.getJSONArray("flows")
            Shape(
                List(flows.length()) { i ->
                    flows.getJSONObject(i).let { FlowShape(it.getString("key"), it.getString("data"), it.getBoolean("named"), it.getString("bucket"), it.getBoolean("on")) }
                },
                o.getJSONArray("trackers").let { a -> List(a.length()) { a.getString(it) } },
                o.getJSONArray("controls").let { a -> List(a.length()) { a.getString(it) } },
            )
        }.getOrNull()
    }
}

private fun DataFlow.shape(prefix: String) = FlowShape(
    key = prefix + (id ?: "$data|${recipient ?: recipientLabel}"),
    data = data,
    named = recipient != null,
    bucket = bucket,
    on = default == "on",
)

/** The app's record and the records of the trackers found in it, as the structural diff sees them. */
fun shape(record: AppRecord?, trackers: List<Pair<String, TrackerRecord>>): Shape = Shape(
    flows = record?.dataFlows.orEmpty().filterNot { it.historical }.map { it.shape("") } +
        trackers.flatMap { (id, t) -> t.dataFlows.filterNot { it.historical }.map { it.shape("$id:") } },
    trackers = record?.trackers.orEmpty().distinct().sorted(),
    controls = record?.controls.orEmpty().map { it.id }.distinct().sorted(),
)

private val RANK = mapOf("stays_here" to 0, "used_for_more" to 1, "goes_elsewhere" to 2)

/**
 * build.py's structural_diff on shapes: flows added or removed (outside Stays here), moved between
 * buckets or turned on or off by default; data kinds, trackers and controls added or removed. Flows
 * pair by key, in order when a record has two with the same one (Facebook's two flows of app activity
 * to Meta); a reworded unnamed recipient pairs with the flow of the same data and bucket.
 */
fun structuralDiff(old: Shape, new: Shape): List<String> {
    val left = LinkedHashMap<String, ArrayDeque<FlowShape>>().apply { old.flows.forEach { getOrPut(it.key) { ArrayDeque() }.add(it) } }
    val pairs = ArrayList<Pair<FlowShape, FlowShape>>()
    val added = ArrayList<FlowShape>()
    for (f in new.flows) left[f.key]?.removeFirstOrNull()?.let { pairs += it to f } ?: added.add(f)
    val removed = left.values.flatten().toMutableList()
    for (f in added.toList()) {
        val twin = removed.firstOrNull { !it.named && !f.named && it.data == f.data && it.bucket == f.bucket } ?: continue
        pairs += twin to f
        removed.remove(twin)
        added.remove(f)
    }
    val diff = ArrayList<String>()
    diff += added.filter { it.bucket != "stays_here" }.map { "flow added: ${it.key} (${it.bucket})" }
    diff += removed.filter { it.bucket != "stays_here" }.map { "flow removed: ${it.key} (${it.bucket})" }
    for ((a, b) in pairs) {
        if (RANK[a.bucket] != RANK[b.bucket]) diff += "moved: ${b.key} (${a.bucket} to ${b.bucket})"
        if (a.on != b.on) diff += "now ${if (b.on) "on" else "off"} by default: ${b.key}"
    }
    fun sets(what: String, before: Set<String>, after: Set<String>) {
        diff += (after - before).sorted().map { "$what added: $it" }
        diff += (before - after).sorted().map { "$what removed: $it" }
    }
    sets("data kind", old.flows.map { it.data }.toSet(), new.flows.map { it.data }.toSet())
    sets("tracker", old.trackers.toSet(), new.trackers.toSet())
    sets("control", old.controls.toSet(), new.controls.toSet())
    return diff
}

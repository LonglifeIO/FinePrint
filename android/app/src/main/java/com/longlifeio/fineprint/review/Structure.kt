package com.longlifeio.fineprint.review

import com.longlifeio.fineprint.bundle.AppRecord
import com.longlifeio.fineprint.bundle.Company
import com.longlifeio.fineprint.bundle.Consequence
import com.longlifeio.fineprint.bundle.DataFlow
import com.longlifeio.fineprint.bundle.TrackerRecord
import com.longlifeio.fineprint.explain.independentSources
import org.json.JSONArray
import org.json.JSONObject

/*
 * What a Reviewed mark compares (docs/METHOD.md, Your Reviewed marks): the fields of FinePrint's
 * records that feed the tier rules or the controls, the same ones pipeline/build.py's structural diff
 * compares to say whether a record got better or worse: flows (place, default, evidence), data kinds,
 * trackers, controls, and the legal items naming the app. Wording, purposes and the store description
 * aren't among them, so an edit to those never marks an app changed. ReviewTest keeps this port and
 * build.py in step.
 */

/** One current flow: its identity (its id), whether a company is named, its bucket, whether it's on by default, and its evidence. */
data class FlowShape(val key: String, val data: String, val named: Boolean, val bucket: String, val on: Boolean, val evidence: String)

/** [legal] is each legal item naming the app, with its standing (legalItems). */
data class Shape(val flows: List<FlowShape>, val trackers: List<String>, val controls: List<String>, val legal: Map<String, String> = emptyMap()) {
    fun encode(): String = JSONObject()
        .put("flows", JSONArray(flows.map { JSONObject().put("key", it.key).put("data", it.data).put("named", it.named).put("bucket", it.bucket).put("on", it.on).put("evidence", it.evidence) }))
        .put("trackers", JSONArray(trackers)).put("controls", JSONArray(controls))
        .put("legal", JSONArray(legal.map { (key, standing) -> JSONObject().put("key", key).put("standing", standing) })).toString()

    companion object {
        /** Null for a mark set before shapes held all of this (a bare hash, or no legal items): nothing is known about its record. */
        fun decode(text: String): Shape? = runCatching {
            val o = JSONObject(text)
            val flows = o.getJSONArray("flows")
            val legal = o.getJSONArray("legal")
            Shape(
                List(flows.length()) { i ->
                    flows.getJSONObject(i).let {
                        FlowShape(it.getString("key"), it.getString("data"), it.getBoolean("named"), it.getString("bucket"), it.getBoolean("on"), it.getString("evidence"))
                    }
                },
                o.getJSONArray("trackers").let { a -> List(a.length()) { a.getString(it) } },
                o.getJSONArray("controls").let { a -> List(a.length()) { a.getString(it) } },
                LinkedHashMap<String, String>().apply { for (i in 0 until legal.length()) legal.getJSONObject(i).let { put(it.getString("key"), it.getString("standing")) } },
            )
        }.getOrNull()
    }
}

/** What the tier rules read of a flow's evidence: its status, and whether a report has the second independent source it needs to raise a tier. */
private fun DataFlow.evidence(): String =
    if (status == "reported" && independentSources(sources) < 2) "reported, one source" else status ?: "auto"

private fun DataFlow.shape(prefix: String) = FlowShape(
    key = prefix + (id ?: "$data|${recipient ?: recipientLabel}"),
    data = data,
    named = recipient != null,
    bucket = bucket,
    on = default == "on",
    evidence = evidence(),
)

/** The app's record, the records of the trackers found in it and the companies' actions, as the structural diff sees them. */
fun shape(pkg: String, record: AppRecord?, trackers: List<TrackerRecord>, companies: Collection<Company>): Shape = Shape(
    flows = record?.dataFlows.orEmpty().filterNot { it.historical }.map { it.shape("") } +
        trackers.flatMap { t -> t.dataFlows.filterNot { it.historical }.map { it.shape("${t.id}:") } },
    trackers = record?.trackers.orEmpty().distinct().sorted(),
    controls = record?.controls.orEmpty().map { it.id }.distinct().sorted(),
    legal = legalItems(pkg, record, trackers, companies),
)

private val LEGAL = setOf("alleged", "adjudicated")

/** What the tier rules read of a legal item: "adjudicated/ruling, in force, closed 2026-08-24". */
private fun standing(status: String, kind: String?, inForce: Boolean, appealPending: Boolean, closed: String?) = listOfNotNull(
    status + kind?.let { "/$it" }.orEmpty(), "in force".takeIf { inForce }, "appeal pending".takeIf { appealPending }, closed?.let { "closed $it" },
).joinToString(", ")

/**
 * build.py's legal_items: every company's actions naming the app (breaches aside), by company, date
 * and type; the app's own alleged or adjudicated lines, and those of its trackers that name the app,
 * by owner and first source. Each with its standing.
 */
fun legalItems(pkg: String, record: AppRecord?, trackers: List<TrackerRecord>, companies: Collection<Company>): Map<String, String> {
    val found = LinkedHashMap<String, String>()
    fun put(key: String, standing: String) {
        var k = key
        var n = 2
        while (k in found) k = "$key #${n++}"
        found[k] = standing
    }
    for (c in companies) for (e in c.events) {
        if (e.concernsApp == pkg && e.type != "breach") put("${c.id} ${e.date} ${e.type}", standing(e.status, e.statusKind, e.inForce, e.appealPending, e.closedDate))
    }
    fun lines(owner: String, said: List<Consequence>, names: (Consequence) -> Boolean) = said.filter { it.status in LEGAL && names(it) }.forEach {
        put("$owner ${it.sources.firstOrNull()?.url.orEmpty()}", standing(it.status, it.statusKind, it.inForce, it.appealPending, it.closedDate))
    }
    lines(pkg, record?.consequences.orEmpty()) { it.concernsApp == null || it.concernsApp == pkg }
    for (t in trackers) lines(t.id, t.consequences) { it.concernsApp == pkg }
    return found
}

/** build.py's legal_diff: a line reworded or cited to another first source pairs with a removed line of the same owner and standing. */
private fun legalDiff(old: Map<String, String>, new: Map<String, String>): List<String> {
    val came = new.filterKeys { it !in old }.toMutableMap()
    val gone = old.filterKeys { it !in new }.toMutableMap()
    for ((k, v) in came.toMap()) {
        val twin = gone.entries.firstOrNull { (g, w) -> "://" in g && "://" in k && g.substringBefore(' ') == k.substringBefore(' ') && w == v }?.key ?: continue
        came.remove(k)
        gone.remove(twin)
    }
    return came.map { (k, v) -> "event added: $k ($v)" } + gone.map { (k, v) -> "event removed: $k ($v)" } +
        new.filter { (k, v) -> k in old && old[k] != v }.map { (k, v) -> "event status: $k (${old[k]} to $v)" }
}

private val RANK = mapOf("stays_here" to 0, "used_for_more" to 1, "goes_elsewhere" to 2)

/**
 * build.py's structural_diff on shapes: flows added or removed (outside Stays here), moved between
 * buckets, turned on or off by default or with other evidence; data kinds, trackers and controls added
 * or removed; legal items added, removed or changed in standing. Flows
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
        if (a.evidence != b.evidence) diff += "flow status: ${b.key} (${a.evidence} to ${b.evidence})"
    }
    fun sets(what: String, before: Set<String>, after: Set<String>) {
        diff += (after - before).sorted().map { "$what added: $it" }
        diff += (before - after).sorted().map { "$what removed: $it" }
    }
    sets("data kind", old.flows.map { it.data }.toSet(), new.flows.map { it.data }.toSet())
    sets("tracker", old.trackers.toSet(), new.trackers.toSet())
    sets("control", old.controls.toSet(), new.controls.toSet())
    return diff + legalDiff(old.legal, new.legal)
}

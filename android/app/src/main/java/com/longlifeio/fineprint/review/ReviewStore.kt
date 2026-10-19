package com.longlifeio.fineprint.review

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One app's on-device state: its Reviewed mark, and the checklist items you've ticked, with the day you ticked each. */
data class ReviewEntry(val mark: ReviewMark? = null, val ticked: Set<String> = emptySet(), val tickedOn: Map<String, String> = emptyMap()) {
    val isEmpty: Boolean get() = mark == null && ticked.isEmpty()
}

/**
 * Reviewed marks and ticks, in one small JSON file in the app's no-backup directory. It never goes in
 * the bundle and never leaves the phone: the manifest also turns off backup and device transfer.
 */
class ReviewStore(private val file: File, private val io: Executor = Executors.newSingleThreadExecutor()) {
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<Map<String, ReviewEntry>> = _entries.asStateFlow()

    fun markReviewed(packageName: String, fingerprint: Fingerprint, at: String) =
        edit(packageName) { it.copy(mark = ReviewMark(at, fingerprint)) }

    fun clearMark(packageName: String) = edit(packageName) { it.copy(mark = null) }

    /** [on]: the day you ticked it ("2026-10-19"), shown beside the tick; a tick saved before days were kept has none. */
    fun setTicked(packageName: String, itemId: String, ticked: Boolean, on: String? = null) = edit(packageName) {
        it.copy(
            ticked = if (ticked) it.ticked + itemId else it.ticked - itemId,
            tickedOn = if (ticked && on != null) it.tickedOn + (itemId to on) else it.tickedOn - itemId,
        )
    }

    private fun edit(packageName: String, change: (ReviewEntry) -> ReviewEntry) {
        _entries.update { all ->
            val entry = change(all[packageName] ?: ReviewEntry())
            if (entry.isEmpty) all - packageName else all + (packageName to entry)
        }
        val snapshot = _entries.value
        io.execute { write(snapshot) }
    }

    private fun load(): Map<String, ReviewEntry> = try {
        if (!file.exists()) emptyMap() else decode(JSONObject(file.readText()))
    } catch (e: Exception) {
        emptyMap() // an unreadable file only loses marks; it must not stop the app
    }

    private fun write(entries: Map<String, ReviewEntry>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(encode(entries).toString())
        if (!tmp.renameTo(file)) file.writeText(tmp.readText()).also { tmp.delete() }
    }

    companion object {
        fun encode(entries: Map<String, ReviewEntry>): JSONObject = JSONObject().put("version", 1).put(
            "apps",
            JSONObject().apply {
                for ((pkg, e) in entries.toSortedMap()) {
                    put(pkg, JSONObject().apply {
                        e.mark?.let { m ->
                            put("reviewed_at", m.reviewedAt)
                            put("record", m.fingerprint.record)
                            put("granted", JSONArray(m.fingerprint.granted))
                            put("trackers", JSONArray(m.fingerprint.trackers))
                            m.fingerprint.reach?.let { put("reach", JSONArray(it)) }
                        }
                        if (e.ticked.isNotEmpty()) put("ticked", JSONArray(e.ticked.sorted()))
                        if (e.tickedOn.isNotEmpty()) put("ticked_on", JSONObject(e.tickedOn.toSortedMap()))
                    })
                }
            },
        )

        fun decode(root: JSONObject): Map<String, ReviewEntry> {
            val apps = root.optJSONObject("apps") ?: return emptyMap()
            return apps.keys().asSequence().associateWith { pkg ->
                val o = apps.getJSONObject(pkg)
                val mark = if (o.has("reviewed_at")) {
                    val fingerprint = Fingerprint(o.getString("record"), o.getJSONArray("granted").strings(), o.getJSONArray("trackers").strings(), o.optJSONArray("reach")?.strings())
                    ReviewMark(o.getString("reviewed_at"), fingerprint)
                } else null
                val days = o.optJSONObject("ticked_on")
                ReviewEntry(mark, o.optJSONArray("ticked")?.strings()?.toSet().orEmpty(), days?.keys()?.asSequence()?.associateWith { days.getString(it) }.orEmpty())
            }
        }

        private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
    }
}

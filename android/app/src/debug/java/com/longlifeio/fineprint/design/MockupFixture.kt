package com.longlifeio.fineprint.design

import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
import org.json.JSONArray
import org.json.JSONObject

/*
 * The scan fixture the @Previews draw from: the test emulator's installed apps and their scans as
 * MockupActivity saw them, saved as scan-fixture.json and copied into the debug assets. It keeps package
 * names, labels, versions, permissions, manifest capabilities, tracker ids, matched class names and how many
 * APK files each has. APK paths, install times, scan timings and problem details (which can hold paths) are left out; nothing
 * in an explanation depends on them.
 */

const val FIXTURE_ASSET = "scan-fixture.json"

fun fixtureJson(apps: List<InstalledApp>, results: Map<String, TrackerScanResult>): String = JSONObject().put("apps", JSONArray(apps.map { a ->
    val r = results.getValue(a.scanKey)
    JSONObject()
        .put("package", a.packageName).put("label", a.label).put("version_name", a.versionName ?: JSONObject.NULL)
        .put("version_code", a.versionCode).put("has_code", a.hasCode).put("apk_count", a.apkPaths.size)
        .put("permissions", JSONArray(a.permissions.map { JSONObject().put("name", it.name).put("granted", it.granted).put("dangerous", it.dangerous) }))
        .put("device_reach", JSONArray(a.deviceReach))
        .put("trackers", JSONArray(r.trackers.map {
            JSONObject().put("id", it.id).put("name", it.name).put("categories", JSONArray(it.categories)).put("matched_class", it.matchedClass)
        }))
        .put("referenced_only", JSONArray(r.referencedOnly)).put("problems", r.problems.size).put("out_of_memory", r.outOfMemory)
        .put("dex_files", r.dexFiles).put("classes", r.classes)
})).toString(1)

/** The fixture back as apps and their scans, keyed by scan key as ScanSession keys them. */
fun parseFixture(json: String): Pair<List<InstalledApp>, Map<String, TrackerScanResult>> {
    val apps = JSONObject(json).getJSONArray("apps").objects().map { o ->
        val app = InstalledApp(
            packageName = o.getString("package"), label = o.getString("label"),
            versionName = if (o.isNull("version_name")) null else o.getString("version_name"),
            versionCode = o.getLong("version_code"), lastUpdateTime = 0,
            isSystem = false, hasCode = o.getBoolean("has_code"),
            // How many APK files, not where they are: the paths stay on the phone.
            apkPaths = List(o.optInt("apk_count", 0)) { "apk-${it + 1}" },
            permissions = o.getJSONArray("permissions").objects().map { RequestedPermission(it.getString("name"), it.getBoolean("granted"), it.getBoolean("dangerous")) },
            deviceReach = o.getJSONArray("device_reach").strings(),
        )
        app to TrackerScanResult(
            trackers = o.getJSONArray("trackers").objects().map {
                DetectedTracker(it.getString("id"), it.getString("name"), it.getJSONArray("categories").strings(), it.getString("matched_class"))
            },
            dexFiles = o.getInt("dex_files"), classes = o.getInt("classes"), durationMs = 0,
            problems = List(o.getInt("problems")) { "an unreadable entry (details not kept in the fixture)" }, outOfMemory = o.getBoolean("out_of_memory"),
            referencedOnly = o.getJSONArray("referenced_only").strings(),
        )
    }
    return apps.map { it.first } to apps.associate { it.first.scanKey to it.second }
}

private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

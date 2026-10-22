package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.PathStep
import com.longlifeio.fineprint.bundle.VerifiedOn
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.review.shape
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val LIFE360 = "com.life360.android.safetymapd"
private const val HELP = "https://support.life360.com/hc/en-us/articles/23053465742743-Privacy-Center"

/**
 * Schema 1.8 in the app, from the real bundle, with its 1.8 fields taken out or put in: a control's guided path, its help
 * page's date and, when a person walked it, when; a path never changes what a Reviewed mark compares; a short body name
 * in the Why line, never under On the record.
 */
class Schema18Test {

    private val text = File("../../bundle/bundle.json").readText()

    private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
    private fun JSONObject.find(array: String, key: String, value: String) = getJSONArray(array).objects().single { it.getString(key) == value }

    /** The real bundle without any 1.8 field: what a 1.7 bundle holds. */
    private fun without18(): JSONObject {
        val doc = JSONObject(text)
        doc.getJSONArray("apps").objects().flatMap { it.optJSONArray("controls")?.objects().orEmpty() }
            .forEach { c -> listOf("path", "help_url", "verified_on").forEach(c::remove) }
        doc.getJSONArray("companies").objects().flatMap { it.optJSONArray("regulatory_history")?.objects().orEmpty() }.forEach { it.remove("body_short") }
        return doc
    }

    /** The real bundle, with Life360's Your Privacy Choices given a path, walked on [walked] if at all; [stale] as build.py marks it. */
    private fun withPath(walked: String? = "2026-10-20", stale: Boolean? = null, url: String? = null): String {
        val doc = JSONObject(text)
        val control = doc.find("apps", "package_id", LIFE360).find("controls", "id", "ctl-l360-privacy-choices")
        val steps = JSONArray()
            .put(JSONObject().put("screen", "Life360").put("tap", "Settings").put("note", "In the upper left corner."))
            .put(JSONObject().put("screen", "Settings").put("tap", "Privacy Center"))
        val end = if (url == null) JSONObject().put("kind", "open_app") else JSONObject().put("kind", "url").put("url", url)
        val source = JSONObject().put("url", HELP).put("type", "company_site").put("status", "self_disclosed").put("as_of", "2026-05-11").put("quote", "Tap on Privacy Center.")
        control.put("path", JSONObject().put("steps", steps).put("ends_in", end).put("sources", JSONArray().put(source)).apply { stale?.let { put("stale", it) } })
        control.put("help_url", HELP)
        control.remove("verified_on")
        walked?.let { control.put("verified_on", JSONObject().put("app_version", "26.37.0").put("date", it)) }
        return doc.toString()
    }

    @Test
    fun aControlsPathIsReadWithItsStepsWhatItOpensAndWhenItWasWalked() {
        val controls = parseBundle(withPath(stale = false)).apps.getValue(LIFE360).controls.associateBy { it.id }
        val choices = controls.getValue("ctl-l360-privacy-choices")
        val path = choices.path!!
        assertEquals(listOf(PathStep("Life360", "Settings", "In the upper left corner."), PathStep("Settings", "Privacy Center")), path.steps)
        assertNull("the app itself", path.url)
        assertEquals(listOf("Tap on Privacy Center."), path.sources.map { it.quote })
        assertEquals(HELP, choices.helpUrl)
        assertEquals("2026-05-11", path.helpDate)
        assertEquals(false, path.stale)
        assertEquals(VerifiedOn("26.37.0", "2026-10-20"), choices.verifiedOn)
        fun yourPrivacyChoices(json: String) = parseBundle(json).apps.getValue(LIFE360).controls.single { it.id == "ctl-l360-privacy-choices" }
        assertEquals(true, yourPrivacyChoices(withPath(stale = true)).path?.stale)
        assertNull("nobody has walked it", yourPrivacyChoices(withPath(walked = null)).verifiedOn)
        assertEquals("https://example.com/settings", yourPrivacyChoices(withPath(url = "https://example.com/settings")).path?.url)
        // A 1.7 bundle reads as before.
        assertTrue(parseBundle(without18().toString()).apps.getValue(LIFE360).controls.all { it.path == null && it.helpUrl == null && it.verifiedOn == null })
    }

    /** The bundle as built: Life360's three settings each have a path from its help page, dated 11 May 2026. */
    @Test
    fun life360sThreeSettingsHaveTheirPathsInTheBundle() {
        val controls = parseBundle(text).apps.getValue(LIFE360).controls
        assertEquals(listOf("ctl-l360-privacy-choices", "ctl-l360-trusted-partners", "ctl-l360-traffic-insights"), controls.filter { it.path != null }.map { it.id })
        assertTrue(controls.all { it.helpUrl == HELP && it.path?.helpDate == "2026-05-11" && it.path.url == null })
        assertEquals(listOf(4, 5, 5), controls.map { it.path!!.steps.size })
        // Whatever the help page's age, a path nobody has walked is never stale; only a walk goes stale.
        assertTrue(controls.filter { it.verifiedOn == null }.all { it.path?.stale == false })
    }

    @Test
    fun aPathNeverChangesWhatAReviewedMarkComparesAndItsStaleFlagNeverChangesTheRecord() {
        fun life360(json: String) = parseBundle(json).apps.getValue(LIFE360)
        val before = shape(LIFE360, life360(without18().toString()), emptyList(), emptyList())
        assertEquals("a path is how to reach a setting, not a change to it", before, shape(LIFE360, life360(text), emptyList(), emptyList()))
        assertEquals(before, shape(LIFE360, life360(withPath()), emptyList(), emptyList()))
        // build.py's stale flag is left out of the record's hash, as a record's own is; a new walk is a change to the record.
        assertEquals(life360(withPath(stale = false)).hash, life360(withPath(stale = true)).hash)
        assertNotEquals(life360(withPath()).hash, life360(withPath(walked = "2026-10-21")).hash)
    }

    @Test
    fun theWhyLineUsesABodysShortNameAndOnTheRecordKeepsItsFullName() {
        val full = "Privacy commissioners of Canada, Quebec, British Columbia and Alberta"
        fun tikTok(json: String): Explanation {
            val app = InstalledApp("com.zhiliaoapp.musically", "TikTok", "1", 1, 0, isSystem = false, hasCode = true, apkPaths = emptyList(), permissions = emptyList())
            return explain(app, TrackerScanResult(emptyList(), 1, 1, 1, emptyList()), parseBundle(json), emptyMap())
        }
        val before = tikTok(without18().toString())
        val after = tikTok(text) // the bundle as built: the event has body_short
        assertEquals("Regulators ruled on a case about this app's data — $full, 2025", before.tier.reason)
        assertEquals("Regulators ruled on a case about this app's data — four Canadian privacy commissioners, 2025", after.tier.reason)
        assertEquals("the tier never moves", before.tier.tier, after.tier.tier)
        // Who acted still reads from the full name (several commissioners: Regulators); the event's own line keeps it.
        val line = after.onTheRecord.actions.single { it.date == "2025-09-23" }
        assertTrue(line.line, line.line.contains(full))
        assertEquals(before.onTheRecord.actions.map { it.line }, after.onTheRecord.actions.map { it.line })
    }
}

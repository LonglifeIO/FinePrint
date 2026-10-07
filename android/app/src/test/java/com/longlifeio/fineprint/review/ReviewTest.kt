package com.longlifeio.fineprint.review

import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor

class ReviewTest {

    private val fixture = File("src/test/resources/bundle-fixture.json").readText()
    private val bundle = parseBundle(fixture)

    private fun app(vararg granted: String) = InstalledApp(
        packageName = "com.example.family", label = "Example Family", versionName = "1", versionCode = 1, lastUpdateTime = 0,
        isSystem = false, hasCode = true, apkPaths = emptyList(),
        permissions = granted.map { RequestedPermission(it, granted = true, dangerous = true) } +
            RequestedPermission("android.permission.CAMERA", granted = false, dangerous = true),
    )

    private fun scan(vararg ids: String) =
        TrackerScanResult(ids.map { DetectedTracker(it, "Name of $it", emptyList(), "x.Y") }, dexFiles = 1, classes = 1, durationMs = 1, problems = emptyList())

    private val names = mapOf("exodus-312" to "Google AdMob")
    private val location = "android.permission.ACCESS_FINE_LOCATION"

    @Test
    fun notMarkedThenReviewedThenChanged() {
        val then = fingerprint(app(location), scan("fp-arity"), bundle)!!
        assertEquals(ReviewStatus.NOT_REVIEWED, reviewView(null, then, names).status)
        val mark = ReviewMark("2026-10-04T15:00:00-03:00", then)
        assertEquals(ReviewView(ReviewStatus.REVIEWED, "2026-10-04"), reviewView(mark, then, names))
        // Until the scan finishes nothing is known to have changed.
        assertEquals(ReviewStatus.REVIEWED, reviewView(mark, null, names).status)

        val now = fingerprint(app(), scan("fp-arity", "exodus-312"), bundle)!!
        val changed = reviewView(mark, now, names)
        assertEquals(ReviewStatus.CHANGED, changed.status)
        assertEquals("2026-10-04", changed.reviewedOn) // the old date is kept
        assertEquals("new tracker code: Google AdMob; no longer allowed: Precise location", changed.note)
    }

    private fun bundleWith(replace: String, with: String): com.longlifeio.fineprint.bundle.Bundle {
        check(fixture.contains(replace)) { replace }
        return parseBundle(fixture.replace(replace, with))
    }

    private fun changedBy(edited: com.longlifeio.fineprint.bundle.Bundle): List<String> {
        val before = fingerprint(app(location), scan("fp-arity"), bundle)!!
        return changes(before, fingerprint(app(location), scan("fp-arity"), edited)!!, names)
    }

    /** docs/METHOD.md, Your Reviewed marks: wording, sources, the stale flag and a store tagline don't count. */
    @Test
    fun wordingSourcesAndAStoreTaglineLeaveAMarkAlone() {
        assertEquals(emptyList<String>(), changedBy(bundleWith("\"stale\": true", "\"stale\": false")))
        assertEquals(emptyList<String>(), changedBy(bundleWith("Synthetic test record.", "Edited test record.")))
        assertEquals(emptyList<String>(), changedBy(bundleWith("\"purpose\": \"Their own use\"", "\"purpose\": \"Their own commercial use\"")))
        assertEquals(emptyList<String>(), changedBy(bundleWith("\"quote\": \"partners for their own use\"", "\"quote\": \"partners for their use\"")))
        val tagline = bundleWith(
            "\"summary\": \"Synthetic test record.\",",
            "\"summary\": \"Synthetic test record.\", \"store_tagline\": {\"text\": \"Find your family\", \"source_url\": \"https://play.google.com/store/apps/details?id=com.example.family\", \"as_of\": \"2026-10-07\"},",
        )
        assertEquals("Find your family", tagline.apps.getValue("com.example.family").storeTagline?.text)
        val mark = ReviewMark("2026-10-04T15:00:00-03:00", fingerprint(app(location), scan("fp-arity"), bundle)!!)
        assertEquals(ReviewStatus.REVIEWED, reviewView(mark, fingerprint(app(location), scan("fp-arity"), tagline), names).status)
    }

    @Test
    fun aChangeToWhatTheAppDoesIsAChange() {
        val updated = listOf("FinePrint's record was updated")
        // Moved between buckets, turned off by default, a control removed, a tracker removed from the record.
        val flow = "\"id\": \"flow-partners\", \"data\": \"precise_location\", \"recipient_label\": \"Partners\", \"purpose\": \"Their own use\", \"bucket\": \"goes_elsewhere\""
        assertEquals(updated, changedBy(bundleWith(flow, flow.replace("goes_elsewhere", "used_for_more"))))
        assertEquals(updated, changedBy(bundleWith(flow, "$flow, \"default\": \"off\"")))
        assertEquals(updated, changedBy(bundleWith("\"id\": \"ctl-partners\"", "\"id\": \"ctl-other\"")))
        assertEquals(updated, changedBy(bundleWith("[\"exodus-12\", \"exodus-65\", \"exodus-72\", \"fp-arity\"]", "[\"exodus-12\", \"exodus-65\", \"fp-arity\"]")))
    }

    @Test
    fun aMarkKeptAsABareHashCantTellSoItsRecordNeverCountsAsChanged() {
        val now = fingerprint(app(location), scan("fp-arity"), bundle)!!
        val legacy = now.copy(record = "4f86365a9e3d0c2b7b1d5e0a8c6f4e2d1b0a9c8e7f6d5c4b3a291807f6e5d4c3")
        assertEquals(emptyList<String>(), changes(legacy, now, names))
        // A shape kept before legal items were part of it can't tell either.
        assertNull(Shape.decode(JSONObject(now.record).apply { remove("legal") }.toString()))
    }

    private fun flow(key: String, data: String = "precise_location", bucket: String = "goes_elsewhere", named: Boolean = true, on: Boolean = true, evidence: String = "self_disclosed") =
        FlowShape(key, data, named, bucket, on, evidence)

    /** The port follows pipeline/build.py's structural_diff (test_build.py has the same cases). */
    @Test
    fun theStructuralDiffMatchesBuildPy() {
        val base = Shape(listOf(flow("f1"), flow("stays", "app_activity", "stays_here")), listOf("exodus-12"), listOf("ctl-a"))
        assertEquals(emptyList<String>(), structuralDiff(base, base))
        // A new flow under Stays here isn't a change unless its kind of data is new.
        assertEquals(emptyList<String>(), structuralDiff(base, base.copy(flows = base.flows + flow("stays2", "app_activity", "stays_here"))))
        assertEquals(listOf("data kind added: crash_diagnostics"), structuralDiff(base, base.copy(flows = base.flows + flow("stays3", "crash_diagnostics", "stays_here"))))
        // A reworded unnamed recipient pairs with the flow of the same data and bucket.
        val unnamed = Shape(listOf(flow("precise_location|Partners", named = false)), emptyList(), emptyList())
        assertEquals(emptyList<String>(), structuralDiff(unnamed, Shape(listOf(flow("precise_location|Business partners", named = false)), emptyList(), emptyList())))
        assertEquals(listOf("flow added: f2 (used_for_more)"), structuralDiff(base, base.copy(flows = base.flows + flow("f2", bucket = "used_for_more"))))
        assertEquals(listOf("now off by default: f1"), structuralDiff(base, base.copy(flows = listOf(flow("f1", on = false), base.flows[1]))))
        // A flow's evidence, and the legal items naming the app, by presence and standing.
        assertEquals(
            listOf("flow status: f1 (reported, one source to reported)"),
            structuralDiff(base.copy(flows = listOf(flow("f1", evidence = "reported, one source"), base.flows[1])), base.copy(flows = listOf(flow("f1", evidence = "reported"), base.flows[1]))),
        )
        val ruled = base.copy(legal = mapOf("co-x 2026-09-01 fine" to "adjudicated/ruling"))
        assertEquals(listOf("event added: co-x 2026-09-01 fine (adjudicated/ruling)"), structuralDiff(base, ruled))
        assertEquals(listOf("event removed: co-x 2026-09-01 fine (adjudicated/ruling)"), structuralDiff(ruled, base))
        assertEquals(
            listOf("event status: co-x 2026-09-01 fine (adjudicated/ruling to adjudicated/ruling, closed 2026-10-01)"),
            structuralDiff(ruled, base.copy(legal = mapOf("co-x 2026-09-01 fine" to "adjudicated/ruling, closed 2026-10-01"))),
        )
        // A line cited to another first source pairs with the old one of the same owner and standing.
        val line = base.copy(legal = mapOf("com.example https://example.org/a" to "adjudicated/ruling"))
        assertEquals(emptyList<String>(), structuralDiff(line, base.copy(legal = mapOf("com.example https://example.org/b" to "adjudicated/ruling"))))
    }

    private fun edited(vararg edits: Pair<String, String>) =
        parseBundle(edits.fold(fixture) { text, (from, to) -> check(text.contains(from)) { from }; text.replace(from, to) })

    private fun between(a: com.longlifeio.fineprint.bundle.Bundle, b: com.longlifeio.fineprint.bundle.Bundle) =
        changes(fingerprint(app(location), scan("fp-arity"), a)!!, fingerprint(app(location), scan("fp-arity"), b)!!, names)

    /** docs/METHOD.md, Your Reviewed marks: a ruling or lawsuit naming the app, or a change in its standing, counts; rewording it doesn't. */
    @Test
    fun aRulingOrLawsuitNamingTheAppIsAChange() {
        val updated = listOf("FinePrint's record was updated")
        val leak = "\"text\": \"Emails leaked.\", \"status\": \"reported\""
        val filed = "\"text\": \"Emails leaked.\", \"status\": \"alleged\", \"status_kind\": \"filed\""
        val lawsuit = edited(leak to filed)
        assertEquals(updated, between(bundle, lawsuit))
        assertEquals(updated, between(lawsuit, edited(leak to filed.replace("filed", "survived_motion_to_dismiss"))))
        assertEquals(updated, between(lawsuit, edited(leak to "$filed, \"closed_date\": \"2026-09-01\"")))
        assertEquals(emptyList<String>(), between(lawsuit, edited(leak to filed.replace("Emails leaked.", "Emails were leaked."))))
        // A company's action that names the app.
        assertEquals(updated, changedBy(bundleWith("\"concerns_app\": \"com.example.settled\"", "\"concerns_app\": \"com.example.family\"")))
    }

    @Test
    fun aFlowsEvidenceIsAChange() {
        val wording = "\"wording\": \"According to its privacy policy dated 2026-01-01.\""
        assertEquals(listOf("FinePrint's record was updated"), changedBy(bundleWith("\"status\": \"self_disclosed\", $wording", "\"status\": \"reported\", $wording")))
    }

    @Test
    fun newDeviceReachIsAChange() {
        val then = fingerprint(app(location), scan("fp-arity"), bundle)!!
        val now = fingerprint(app(location).copy(deviceReach = listOf("autostart")), scan("fp-arity"), bundle)!!
        assertEquals(listOf("now: starts itself"), changes(then, now, names, reachNames(bundle)))
        assertEquals(listOf("no longer: starts itself"), changes(now, then, names, reachNames(bundle)))
        // A mark kept before device reach was can't tell.
        assertEquals(emptyList<String>(), changes(then.copy(reach = null), now, names))
    }

    /** Facebook's record has two flows of the same data to Meta: they pair in order, so a mark never flips on an unchanged record. */
    @Test
    fun aRecordComparedWithItselfHasNoChanges() {
        val meta = flow("app_activity|co-meta", "app_activity", "used_for_more")
        val twice = Shape(listOf(meta, meta), emptyList(), emptyList())
        assertEquals(emptyList<String>(), structuralDiff(twice, twice))
        assertEquals(listOf("flow removed: app_activity|co-meta (used_for_more)"), structuralDiff(twice, twice.copy(flows = listOf(meta))))
        // Every app in the real bundle, as a mark stores its shape and reads it back.
        val real = parseBundle(File("../../bundle/bundle.json").readText())
        fun trackersOf(pkg: String) = real.apps.getValue(pkg).trackers.mapNotNull { real.trackers[it] }.distinctBy { it.id }
        real.apps.forEach { (pkg, record) ->
            val now = shape(pkg, record, trackersOf(pkg), real.companies.values)
            assertEquals(pkg, emptyList<String>(), structuralDiff(Shape.decode(now.encode())!!, now))
        }
        // As in test_build.py: Meta's eight actions naming Facebook, and four of its own lines.
        val facebook = "com.facebook.katana"
        assertEquals(12, legalItems(facebook, real.apps[facebook], trackersOf(facebook), real.companies.values).size)
    }

    @Test
    fun noFingerprintUntilTheCodeIsScanned() {
        assertNull(fingerprint(app(location), null, bundle))
    }

    @Test
    fun theStoreKeepsMarksAndTicksAcrossRestarts() {
        val dir = Files.createTempDirectory("reviews").toFile()
        val file = File(dir, "reviews.json")
        val now = Executor { it.run() }
        val fp = fingerprint(app(location), scan("fp-arity"), bundle)!!
        ReviewStore(file, now).apply {
            markReviewed("com.example.family", fp, "2026-10-04T15:00:00-03:00")
            setTicked("com.example.family", "ctl-partners", true)
            setTicked("org.other", "ctl-x", true)
            setTicked("org.other", "ctl-x", false) // an entry with nothing left in it is dropped
        }
        val reloaded = ReviewStore(file, now).entries.value
        assertEquals(setOf("com.example.family"), reloaded.keys)
        assertEquals(ReviewEntry(ReviewMark("2026-10-04T15:00:00-03:00", fp), setOf("ctl-partners")), reloaded["com.example.family"])
        ReviewStore(file, now).clearMark("com.example.family")
        assertEquals(ReviewEntry(null, setOf("ctl-partners")), ReviewStore(file, now).entries.value["com.example.family"])
        dir.deleteRecursively()
    }
}

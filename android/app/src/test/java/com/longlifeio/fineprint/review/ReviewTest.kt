package com.longlifeio.fineprint.review

import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
    }

    private fun flow(key: String, data: String = "precise_location", bucket: String = "goes_elsewhere", named: Boolean = true, on: Boolean = true) =
        FlowShape(key, data, named, bucket, on)

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

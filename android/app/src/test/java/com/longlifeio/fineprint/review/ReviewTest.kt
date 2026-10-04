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

    @Test
    fun aRecordUpdateIsAChangeButItsStaleFlagIsNot() {
        val before = fingerprint(app(location), scan(), bundle)!!
        val stale = parseBundle(fixture.replace("\"stale\": true", "\"stale\": false"))
        assertEquals(before, fingerprint(app(location), scan(), stale))
        val edited = parseBundle(fixture.replace("Synthetic test record.", "Edited test record."))
        val now = fingerprint(app(location), scan(), edited)!!
        assertNotEquals(before.record, now.record)
        assertEquals(listOf("FinePrint's record was updated"), changes(before, now, names))
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

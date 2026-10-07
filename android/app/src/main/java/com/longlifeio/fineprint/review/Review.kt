package com.longlifeio.fineprint.review

import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.permissionLabel

/**
 * What a Reviewed mark remembers about an app: the shape of FinePrint's records for it (Structure.kt:
 * only what the structural diff compares), the permissions granted and the trackers found. Kept on
 * this phone only (see ReviewStore).
 */
data class Fingerprint(val record: String, val granted: List<String>, val trackers: List<String>)

/** Null until the app's code has been scanned: the tracker set isn't known before that. */
fun fingerprint(app: InstalledApp, scan: TrackerScanResult?, bundle: Bundle?): Fingerprint? {
    if (scan == null) return null
    val trackerRecords = scan.trackers.map { it.id }.distinct().sorted().mapNotNull { id -> bundle?.trackers?.get(id)?.let { id to it } }
    return Fingerprint(
        record = shape(bundle?.apps?.get(app.packageName), trackerRecords).encode(),
        granted = app.permissions.filter { it.granted }.map { it.name }.distinct().sorted(),
        trackers = scan.trackers.map { it.id }.distinct().sorted(),
    )
}

data class ReviewMark(val reviewedAt: String, val fingerprint: Fingerprint)

enum class ReviewStatus { NOT_REVIEWED, REVIEWED, CHANGED }

/** [changes] says what's different since the mark was set; the mark and its date are kept either way. */
data class ReviewView(val status: ReviewStatus, val reviewedOn: String? = null, val changes: List<String> = emptyList()) {
    val note: String? get() = changes.takeIf { it.isNotEmpty() }?.joinToString("; ")
}

/**
 * Until the scan finishes, a marked app counts as reviewed: nothing is known to have changed. After
 * that it's changed only when [changes] finds something, so a reworded record never flips a mark.
 */
fun reviewView(mark: ReviewMark?, now: Fingerprint?, trackerNames: Map<String, String>): ReviewView {
    if (mark == null) return ReviewView(ReviewStatus.NOT_REVIEWED)
    val changed = now?.let { changes(mark.fingerprint, it, trackerNames) }.orEmpty()
    return if (changed.isEmpty()) ReviewView(ReviewStatus.REVIEWED, mark.reviewedAt.take(10)) else ReviewView(ReviewStatus.CHANGED, mark.reviewedAt.take(10), changed)
}

/** True when the record's structure changed; a mark kept before shapes were (a bare hash) can't tell, so it isn't. */
private fun recordChanged(old: String, now: String): Boolean {
    val before = Shape.decode(old) ?: return false
    val after = Shape.decode(now) ?: return false
    return structuralDiff(before, after).isNotEmpty()
}

/** One phrase per kind of change, in plain words. */
internal fun changes(old: Fingerprint, now: Fingerprint, trackerNames: Map<String, String>): List<String> {
    fun names(ids: Collection<String>) = ids.joinToString { trackerNames[it] ?: it }
    fun labels(permissions: Collection<String>) = permissions.map { permissionLabel(it) ?: it.substringAfterLast('.') }.distinct().joinToString()
    val added = now.trackers - old.trackers.toSet()
    val gone = old.trackers - now.trackers.toSet()
    val allowed = now.granted - old.granted.toSet()
    val revoked = old.granted - now.granted.toSet()
    return listOfNotNull(
        "FinePrint's record was updated".takeIf { recordChanged(old.record, now.record) },
        added.takeIf { it.isNotEmpty() }?.let { "new tracker code: ${names(it)}" },
        gone.takeIf { it.isNotEmpty() }?.let { "tracker code removed: ${names(it)}" },
        allowed.takeIf { it.isNotEmpty() }?.let { "now allowed: ${labels(it)}" },
        revoked.takeIf { it.isNotEmpty() }?.let { "no longer allowed: ${labels(it)}" },
    )
}

package com.longlifeio.fineprint.review

import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.explain.permissionLabel
import java.security.MessageDigest

/**
 * What a Reviewed mark remembers about an app: a hash of FinePrint's records for it, the permissions
 * granted and the trackers found. Kept on this phone only (see ReviewStore).
 */
data class Fingerprint(val record: String, val granted: List<String>, val trackers: List<String>) {
    val digest: String get() = sha256("$record\n${granted.joinToString(",")}\n${trackers.joinToString(",")}")
}

/** Null until the app's code has been scanned: the tracker set isn't known before that. */
fun fingerprint(app: InstalledApp, scan: TrackerScanResult?, bundle: Bundle?): Fingerprint? {
    if (scan == null) return null
    val pkg = app.packageName
    val records = listOfNotNull(bundle?.apps?.get(pkg)?.hash) +
        scan.trackers.mapNotNull { bundle?.trackers?.get(it.id)?.hash } +
        bundle?.companies?.values.orEmpty().filter { c -> c.events.any { it.concernsApp == pkg } }.map { it.hash }
    return Fingerprint(
        record = sha256(records.joinToString("|")),
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

/** Until the scan finishes, a marked app counts as reviewed: nothing is known to have changed. */
fun reviewView(mark: ReviewMark?, now: Fingerprint?, trackerNames: Map<String, String>): ReviewView = when {
    mark == null -> ReviewView(ReviewStatus.NOT_REVIEWED)
    now == null || now.digest == mark.fingerprint.digest -> ReviewView(ReviewStatus.REVIEWED, mark.reviewedAt.take(10))
    else -> ReviewView(ReviewStatus.CHANGED, mark.reviewedAt.take(10), changes(mark.fingerprint, now, trackerNames))
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
        "FinePrint's record was updated".takeIf { old.record != now.record },
        added.takeIf { it.isNotEmpty() }?.let { "new tracker code: ${names(it)}" },
        gone.takeIf { it.isNotEmpty() }?.let { "tracker code removed: ${names(it)}" },
        allowed.takeIf { it.isNotEmpty() }?.let { "now allowed: ${labels(it)}" },
        revoked.takeIf { it.isNotEmpty() }?.let { "no longer allowed: ${labels(it)}" },
    )
}

private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

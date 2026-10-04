package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.review.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ListOrderTest {

    private fun app(label: String) = InstalledApp(
        packageName = "p.$label", label = label, versionName = "1", versionCode = 1, lastUpdateTime = 0, isSystem = false,
        hasCode = true, apkPaths = emptyList(), permissions = emptyList(),
    )

    private fun explanation(tier: Tier?, coverage: String) = Explanation(
        appName = "", summary = "", summaryNotes = emptyList(), coverage = coverage, tier = TierResult(tier, "r", "x"),
        privacyControls = null, collects = emptyList(), flows = emptyMap(), applies = emptyList(), onTheRecord = OnTheRecord(emptyList(), emptyList()),
        reach = emptyList(), lastReviewed = null, stale = false, exodusNote = null,
    )

    private val apps = listOf(app("zebra"), app("Bravo"), app("alpha"), app("delta"), app("Echo"))
    private val explanations = mapOf(
        "p.zebra" to explanation(Tier.FLAGGED, "curated"),
        "p.Bravo" to explanation(Tier.CAUTION, "auto"),
        "p.alpha" to explanation(null, "auto"),
        "p.delta" to explanation(Tier.EXPECTED, "curated"),
        "p.Echo" to explanation(Tier.CAUTION, "curated"),
    )

    private var reviews = emptyMap<String, ReviewStatus>()

    private fun order(query: String = "", vararg filters: ListFilter) = listOrder(apps, explanations, reviews, query, filters.toSet()).map { it.label }

    @Test
    fun flaggedThenCautionThenExpectedThenNothingToRateThenByName() {
        assertEquals(listOf("zebra", "Bravo", "Echo", "delta", "alpha"), order())
    }

    @Test
    fun tierChipsCombineWithOrAndCoverageChipsWithAnd() {
        assertEquals(listOf("zebra"), order(filters = arrayOf(ListFilter.FLAGGED)))
        assertEquals(listOf("zebra", "Bravo", "Echo"), order("", ListFilter.FLAGGED, ListFilter.CAUTION))
        assertEquals(listOf("zebra", "Echo", "delta"), order("", ListFilter.HAS_RECORD))
        assertEquals(listOf("Bravo", "alpha"), order("", ListFilter.NO_RECORD))
        assertEquals(listOf("Echo"), order("", ListFilter.CAUTION, ListFilter.HAS_RECORD))
    }

    @Test
    fun reviewedAppsSinkWithinTheirTierAndChangedOnesComeBackUp() {
        reviews = mapOf("p.Bravo" to ReviewStatus.REVIEWED, "p.zebra" to ReviewStatus.REVIEWED)
        // Bravo drops below Echo inside Caution; zebra stays the only Flagged app: tiers never move.
        assertEquals(listOf("zebra", "Echo", "Bravo", "delta", "alpha"), order())
        reviews = mapOf("p.Bravo" to ReviewStatus.CHANGED)
        assertEquals(listOf("zebra", "Bravo", "Echo", "delta", "alpha"), order())
        reviews = mapOf("p.Bravo" to ReviewStatus.CHANGED, "p.delta" to ReviewStatus.REVIEWED)
        assertEquals(listOf("Bravo", "delta"), order("", ListFilter.REVIEWED))
    }

    @Test
    fun searchMatchesTheNameIgnoringCase() {
        assertEquals(listOf("Echo"), order("ECH"))
        assertEquals(listOf("zebra", "Bravo", "Echo", "delta", "alpha"), order("  "))
    }
}

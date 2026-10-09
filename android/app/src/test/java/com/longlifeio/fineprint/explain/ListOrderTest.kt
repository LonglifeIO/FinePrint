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

    private fun explanation(tier: Tier?, coverage: String, checkedOn: String? = null) = Explanation(
        appName = "", summary = "", summaryNotes = emptyList(), coverage = coverage, tier = TierResult(tier, "r", "x"),
        privacyControls = null, collects = emptyList(), flows = emptyMap(), applies = emptyList(), onTheRecord = OnTheRecord(emptyList(), emptyList()),
        reach = emptyList(), lastReviewed = null, stale = false, exodusNote = null, checkedOn = checkedOn,
    )

    private val apps = listOf(app("zebra"), app("Bravo"), app("alpha"), app("delta"), app("Echo"))
    private val explanations = mapOf(
        "p.zebra" to explanation(Tier.FLAGGED, "curated", checkedOn = "2026-10-04"),
        "p.Bravo" to explanation(Tier.CAUTION, "auto"),
        "p.alpha" to explanation(null, "auto"),
        "p.delta" to explanation(Tier.EXPECTED, "curated", checkedOn = "2026-10-04"),
        "p.Echo" to explanation(Tier.CAUTION, "curated"),
    )

    private var reviews = emptyMap<String, ReviewStatus>()

    private fun order(query: String = "", vararg filters: ListFilter) = listOrder(apps, explanations, reviews, query, filters.toSet()).map { it.label }

    /** docs/METHOD.md, The home: the app's name and package, its maker, and the companies its lines name; in any case. */
    @Test
    fun aSearchMatchesThePackageTheMakerAndTheCompaniesAnAppsLinesName() {
        val line = FlowLine("precise_location", GOES_ELSEWHERE, "Allstate/Arity", "Insurance pricing", "self_disclosed", null, false, emptyList(), null, via = "Arity")
        val e = explanation(Tier.FLAGGED, "curated").copy(
            maker = Maker("co-bytedance", "ByteDance", inherited = false, lines = emptyList(), notes = emptyList()),
            flows = mapOf(GOES_ELSEWHERE to listOf(line)),
        )
        val video = app("Video").copy(packageName = "com.zhiliaoapp.musically")
        fun found(q: String) = listOrder(listOf(video), mapOf(video.packageName to e), emptyMap(), q, emptySet()).size
        assertEquals(listOf(1, 1, 1, 1, 1, 0), listOf("video", "zhiliaoapp", "ByteDance", "Allstate", "arity", "Meta").map(::found))
    }

    @Test
    fun flaggedThenCautionThenExpectedThenNothingToRateThenByName() {
        assertEquals(listOf("zebra", "Bravo", "Echo", "delta", "alpha"), order())
    }

    @Test
    fun tierChipsCombineWithOrAndCoverageChipsWithAnd() {
        assertEquals(listOf("zebra"), order(filters = arrayOf(ListFilter.FLAGGED)))
        assertEquals(listOf("zebra", "Bravo", "Echo"), order("", ListFilter.FLAGGED, ListFilter.CAUTION))
        assertEquals(listOf("zebra", "delta"), order("", ListFilter.CHECKED))
        assertEquals(listOf("Echo"), order("", ListFilter.THEIR_WORDS)) // a record no reviewer has checked
        assertEquals(listOf("Bravo", "alpha"), order("", ListFilter.NO_RECORD))
        assertEquals(listOf("zebra", "Echo", "delta"), order("", ListFilter.CHECKED, ListFilter.THEIR_WORDS))
        assertEquals(listOf("Echo"), order("", ListFilter.CAUTION, ListFilter.THEIR_WORDS))
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
    fun systemAppsGroupByMakerWithUnknownMakersLast() {
        val google = Maker("co-google", "Google", inherited = true, emptyList(), emptyList())
        val kit = Maker("co-kit", "Kit", inherited = false, emptyList(), emptyList())
        val made = explanations + mapOf("p.Bravo" to explanation(Tier.CAUTION, "auto").copy(maker = google),
            "p.Echo" to explanation(Tier.CAUTION, "curated").copy(maker = google), "p.delta" to explanation(Tier.EXPECTED, "curated").copy(maker = kit))
        val groups = systemGroups(listOrder(apps, made, reviews, "", emptySet()), made)
        assertEquals(listOf("Google", "Kit", null), groups.map { it.maker?.name })
        assertEquals(listOf(listOf("Bravo", "Echo"), listOf("delta"), listOf("zebra", "alpha")), groups.map { g -> g.apps.map { it.label } })
        assertEquals("Google · 2 apps", groupHeader("Google", 2))
    }

    @Test
    fun theSystemChipKeepsOnlyPreinstalledApps() {
        val all = apps + app("Clock").copy(isSystem = true)
        assertEquals(listOf("Clock"), listOrder(all, explanations, reviews, "", setOf(ListFilter.SYSTEM)).map { it.label })
    }

    @Test
    fun searchMatchesTheNameIgnoringCase() {
        assertEquals(listOf("Echo"), order("ECH"))
        assertEquals(listOf("zebra", "Bravo", "Echo", "delta", "alpha"), order("  "))
    }
}

package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Company history from the real records in bundle/bundle.json. */
class CompanyHistoryTest {

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText())

    private fun explainApp(pkg: String, label: String): Explanation {
        val app = InstalledApp(pkg, label, "1", 1, 0, isSystem = false, hasCode = true, apkPaths = emptyList(), permissions = emptyList())
        return explain(app, TrackerScanResult(emptyList(), 1, 1, 1, emptyList()), bundle, emptyMap())
    }

    @Test
    fun mapsSummarySentenceHasGooglesSettlementsBehindIt() {
        val maps = explainApp("com.google.android.apps.maps", "Maps")
        assertEquals(
            listOf("Texas settlement over geolocation, Incognito and biometric data", "40-state location-tracking settlement", "Arizona location-tracking settlement"),
            maps.companyHistory.map { it.title },
        )
        val arizona = maps.companyHistory.last()
        assertEquals("Action against Google", arizona.subject) // co-google's short name; none of them names Maps
        assertEquals("Arizona Attorney General · settled · \$85 million · 2022-10-04", arizona.details)
        assertTrue(maps.onTheRecord.isEmpty())
        // Shown, but none names Maps, so the tier stays Caution, set by Google's own label.
        assertEquals(TierResult(Tier.CAUTION, "Location data is used for more — Google Maps' own policy", "C1"), maps.tier)
    }

    @Test
    fun eventsTheAppsOwnRecordAlreadyTellsAreNotRepeated() {
        val facebook = explainApp("com.facebook.katana", "Facebook")
        val told = facebook.onTheRecord.flatMap { r -> r.sources.map { it.url } }.toSet()
        assertTrue(facebook.companyHistory.isNotEmpty())
        assertTrue(facebook.companyHistory.all { h -> h.sources.none { it.url in told } })
        assertEquals(facebook.companyHistory.map { it.title }, facebook.companyHistory.sortedByDescending { it.details.substringAfterLast(" · ") }.map { it.title })
    }

    @Test
    fun breachesStayUnderAlsoReported() {
        assertTrue(explainApp("com.life360.android.safetymapd", "Life360").companyHistory.isEmpty())
    }

    @Test
    fun possessives() {
        assertEquals("Life360's", possessive("Life360"))
        assertEquals("Google Maps'", possessive("Google Maps"))
    }
}

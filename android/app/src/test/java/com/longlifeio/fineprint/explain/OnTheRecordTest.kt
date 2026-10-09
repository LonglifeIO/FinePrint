package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** On the record from the real records in bundle/bundle.json. */
class OnTheRecordTest {

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText())

    private fun explainApp(pkg: String, label: String): Explanation {
        val app = InstalledApp(pkg, label, "1", 1, 0, isSystem = false, hasCode = true, apkPaths = emptyList(), permissions = emptyList())
        return explain(app, TrackerScanResult(emptyList(), 1, 1, 1, emptyList()), bundle, emptyMap())
    }

    @Test
    fun mapsShowsGooglesSettlementsMarkedAboutGoogle() {
        val maps = explainApp("com.google.android.apps.maps", "Maps")
        val actions = maps.onTheRecord.actions
        assertEquals(
            listOf(
                "2025-05-09 · Texas Attorney General · settled · \$1.375 billion · closed 2025-10-31 · about Google",
                "2022-11-14 · Attorneys general of 40 US states · settled, in force · \$391.5 million · about Google",
                "2022-10-04 · Arizona Attorney General · settled · \$85 million · about Google",
            ),
            actions.map { it.line },
        )
        // The 40-state settlement's terms run to mid-2028, so it's ongoing; the other two have ended.
        assertEquals(listOf("2022-11-14"), maps.onTheRecord.ongoing.map { it.date })
        assertTrue(actions.none { it.namesThisApp }) // so the page says no action names this app
        assertEquals("Action against Google", actions.last().subject)
        // Shown, but none names Maps, so the tier stays Caution, set by Google's own label.
        assertEquals(TierResult(Tier.CAUTION, "Google Maps says it uses your location for more than running the app", "C1"), maps.tier)
    }

    @Test
    fun facebookListsEachActionOnceNewestFirst() {
        val facebook = explainApp("com.facebook.katana", "Facebook")
        val record = facebook.onTheRecord
        assertEquals(10, record.count)
        assertEquals(9, record.actions.size)
        assertEquals(record.actions.map { it.date }.sortedDescending(), record.actions.map { it.date })
        // The app record's own words join the action they share a source with, instead of a second line.
        assertTrue(record.actions.single { it.date == "2019-07-24" }.details.any { it.startsWith("In 2019 Meta") })
        assertTrue(record.actions.single { it.date == "2024-07-30" }.line.contains("settled, no admission"))
        assertEquals(listOf("2024-09-27"), record.actions.filterNot { it.namesThisApp }.map { it.date })
        // The developer by its short name: the record doesn't say which of Meta's units each action named.
        assertEquals("Action against Meta concerning this app's data", record.actions.first().subject)
        assertTrue(record.actions.single { it.date == "2024-09-27" }.line.endsWith("about Meta"))
        assertEquals("2024-01-17", record.alsoReported.single().date)
        // Orders in force and decisions under appeal are ongoing; only the 2021 Illinois settlement has ended.
        assertEquals(8, record.ongoing.size)
        assertEquals(listOf("2021-02"), record.past.map { it.date })
        assertTrue(record.ongoing.single { it.date == "2019-07-24" }.line.contains("consent order, in force"))
        // A Flagged badge is never unexplained: the reason names the newest ruling on the app's data.
        assertEquals("A 2024 ruling on this app's data: Irish DPC fines over the 2018 Facebook token breach", facebook.tier.reason)
    }

    @Test
    fun tiktoksChildrensPrivacyCaseEndedInASettlement() {
        val tiktok = explainApp("com.zhiliaoapp.musically", "TikTok").onTheRecord
        val case2024 = tiktok.past.single { it.date == "2024-08-02" }
        assertEquals("2024-08-02 · US District Court, Central District of California · settled, no admission · \$300 million (+\$100 million if the 2019 order is lifted) · closed 2026-08-24", case2024.line)
        assertTrue(case2024.details.none { "not proven" in it }) // settled, so no longer only alleged
        // The 2019 Musical.ly order is still in force: the court refused, for now, to lift it.
        assertTrue(tiktok.ongoing.single { it.date == "2019-02-27" }.line.contains("consent order, in force"))
    }

    @Test
    fun life360sPendingCasesAreOngoing() {
        val life360 = explainApp("com.life360.android.safetymapd", "Life360")
        assertEquals(listOf("2025-04-10", "2025-01-13"), life360.onTheRecord.ongoing.map { it.date })
        assertTrue(life360.onTheRecord.past.isEmpty())
    }

    @Test
    fun outcomesInPlainWords() {
        assertEquals("settled, no admission", outcome("settlement_no_admission", false))
        assertEquals("dismissed, under appeal", outcome("dismissed", true))
        assertEquals("formal proceeding opened", outcome("proceeding_opened", false))
        assertEquals("under appeal", outcome(null, true))
        assertEquals("consent order, in force", outcome("consent_order", false, inForce = true))
        assertEquals(null, outcome(null, false)) // a report: its badge says Reported
    }

    @Test
    fun whatMakesAMatterOngoing() {
        assertTrue(ongoing("alleged", "filed", inForce = false, appealPending = false, closedDate = null)) // pending
        assertTrue(ongoing("alleged", "proceeding_opened", inForce = false, appealPending = false, closedDate = null))
        assertTrue(!ongoing("alleged", "dismissed", inForce = false, appealPending = false, closedDate = null))
        assertTrue(ongoing("alleged", "dismissed", inForce = false, appealPending = true, closedDate = null))
        assertTrue(!ongoing("adjudicated", "settlement", inForce = false, appealPending = false, closedDate = null))
        assertTrue(ongoing("adjudicated", "consent_order", inForce = true, appealPending = false, closedDate = null))
    }

    @Test
    fun possessives() {
        assertEquals("Life360's", possessive("Life360"))
        assertEquals("Google Maps'", possessive("Google Maps"))
    }
}

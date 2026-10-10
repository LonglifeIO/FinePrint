package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Change
import org.junit.Assert.assertEquals
import org.junit.Test

/** The one line a closed section shows beside its name (docs/METHOD.md, An app's page), as it counts and words it. */
class SectionSummariesTest {

    private fun check(limited: Int, total: Int) = WhatYouCanDo(emptyList(), limited, total, null)

    private val staysHere = Explanation(
        appName = "Notes", summary = "", summaryNotes = emptyList(), coverage = "curated", tier = TierResult(Tier.EXPECTED, "r", "E"),
        privacyControls = null, collects = emptyList(),
        flows = mapOf(STAYS_HERE to listOf(FlowLine("app_activity", STAYS_HERE, "Notes", "running it", "self_disclosed", null, false, emptyList(), null))),
        applies = emptyList(), onTheRecord = OnTheRecord(emptyList(), emptyList()), reach = emptyList(), lastReviewed = null, stale = false, exodusNote = null,
    )

    private fun matter(date: String) = RecordLine(date, "$date · A regulator", "adjudicated", "A matter", null, emptyList(), null, emptyList(), namesThisApp = true)

    @Test
    fun whereItGoesCountsTheWaysWhatYouCanDoCountsAndHowManyAreLimited() {
        assertEquals("7 ways, 3 limited by your settings", whereSummary(staysHere, check(3, 7)))
        assertEquals("1 way, 0 limited by your settings", whereSummary(staysHere, check(0, 1)))
        // None counted, since its data stays with the app: the headline says so instead.
        assertEquals("It stays with Notes", whereSummary(staysHere, check(0, 0)))
        assertEquals("3 of 7 limited", canDoSummary(check(3, 7)))
        assertEquals("Settings inside the app", canDoSummary(check(0, 0)))
    }

    @Test
    fun jurisdictionsSayWhereTheCompaniesAreBased() {
        assertEquals("Based in: Ireland, United States", basedInSummary(Governments(listOf("Ireland", "United States"), emptyList(), unplaced = false)))
        assertEquals(NONE_PLACED, basedInSummary(Governments(emptyList(), emptyList(), unplaced = true)))
    }

    @Test
    fun onTheRecordCountsItsMattersAndTheLatestYear() {
        val change = Change("2026-10-01", "A line was added.", "neutral", emptyList())
        assertEquals("3 matters, latest 2025", recordSummary(OnTheRecord(listOf(matter("2023-05-22"), matter("2025-01")), listOf(matter("2024-03-01"))), listOf(change)))
        assertEquals("1 matter, latest 2019", recordSummary(OnTheRecord(listOf(matter("2019-07-24")), emptyList()), emptyList()))
        // An undated report is counted, and no year is made up for it.
        assertEquals("1 matter", recordSummary(OnTheRecord(emptyList(), listOf(matter(""))), emptyList()))
        assertEquals("1 change in its history", recordSummary(OnTheRecord(emptyList(), emptyList()), listOf(change)))
    }
}

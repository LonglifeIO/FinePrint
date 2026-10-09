package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Change
import com.longlifeio.fineprint.egress.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlanceTest {

    private fun app(label: String) = InstalledApp(
        packageName = "p.$label", label = label, versionName = "1", versionCode = 1, lastUpdateTime = 0, isSystem = false,
        hasCode = true, apkPaths = emptyList(), permissions = emptyList(),
    )

    private fun line(bucket: String, historical: Boolean = false, status: String? = "self_disclosed") = FlowLine(
        data = "precise_location", bucket = bucket, recipient = "Partners", purpose = "ads", status = status,
        wording = null, historical = historical, sources = emptyList(), proceduralNote = null,
    )

    private fun explanation(vararg lines: FlowLine, changes: List<Change> = emptyList()) = Explanation(
        appName = "", summary = "", summaryNotes = emptyList(), coverage = "curated", tier = TierResult(Tier.CAUTION, "r", "x"),
        privacyControls = null, collects = emptyList(), flows = lines.groupBy { it.bucket }, applies = emptyList(),
        onTheRecord = OnTheRecord(emptyList(), emptyList()), reach = emptyList(), lastReviewed = null, stale = false, exodusNote = null,
        changes = changes,
    )

    private fun check(limited: Int, total: Int) = WhatYouCanDo(emptyList(), limited, total, null)

    private fun change(date: String, text: String = "changed on $date") = Change(date, text, "neutral", emptyList())

    @Test
    fun countsAppsWithACurrentLineInEachBucketAndAddsUpTheFlows() {
        val a = app("a"); val b = app("b"); val c = app("c")
        val g = glance(
            listOf(a, b, c),
            mapOf(
                a.packageName to explanation(line(GOES_ELSEWHERE), line(GOES_ELSEWHERE), line(STAYS_HERE)),
                b.packageName to explanation(line(GOES_ELSEWHERE, historical = true), line(USED_FOR_MORE)), // a past practice isn't counted
                c.packageName to explanation(),
            ),
            mapOf(a.packageName to check(1, 4), b.packageName to check(2, 3)),
        )
        assertEquals(mapOf(STAYS_HERE to 1, USED_FOR_MORE to 1, GOES_ELSEWHERE to 1), g.perBucket)
        assertEquals(listOf(STAYS_HERE, USED_FOR_MORE, GOES_ELSEWHERE), g.perBucket.keys.toList())
        assertEquals(Glance(3, g.perBucket, limited = 3, flows = 7), g)
        assertEquals(1, g.othersForMore) // the headline counts the first group of the reading order
    }

    /** METHOD.md, Where data goes: an Auto line (tracker code, no record) counts, which is why the headline says "can go". */
    @Test
    fun autoLinesFromTrackerCodeCount() {
        val a = app("a")
        val g = glance(listOf(a), mapOf(a.packageName to explanation(line(GOES_ELSEWHERE, status = null))), emptyMap())
        assertEquals(1, g.othersForMore)
        assertEquals("For your app, FinePrint lists data that can go to other companies for more than running the app.", glanceHeadline(g))
    }

    private fun g(apps: Int, sending: Int, limited: Int = 0, flows: Int = 0) =
        Glance(apps, mapOf(STAYS_HERE to 0, USED_FOR_MORE to 0, GOES_ELSEWHERE to sending), limited, flows)

    @Test
    fun theHeadlineSaysWhatFinePrintListsCanGoNeverThatAnAppSendsNothing() {
        assertEquals(
            "For 5 of your 9 apps, FinePrint lists data that can go to other companies for more than running the app.",
            glanceHeadline(g(9, 5, 4, 19)),
        )
        assertEquals("For all 9 of your apps, FinePrint lists data that can go to other companies for more than running the app.", glanceHeadline(g(9, 9)))
        assertEquals("FinePrint doesn't yet list, for any of your 9 apps, data that can go to other companies for more than running the app.", glanceHeadline(g(9, 0)))
        assertEquals(
            "For your app, FinePrint lists data that can go to other companies for more than running the app.",
            glanceHeadline(g(1, 1, 0, 1)),
        )
        assertEquals("FinePrint doesn't yet list, for your app, data that can go to other companies for more than running the app.", glanceHeadline(g(1, 0)))
        assertEquals("No apps to show yet.", glanceHeadline(g(0, 0)))
        assertEquals("4 of 19 data flows limited by your settings", limitedLine(g(9, 5, 4, 19)))
        assertEquals("0 of 1 data flow limited by your settings", limitedLine(g(1, 1, 0, 1)))
    }

    @Test
    fun whatChangedIsTheNewestChangeAcrossTheAppsAndTiesGoToTheFirstByName() {
        val zed = app("Zed"); val amy = app("amy"); val bob = app("Bob"); val none = app("None")
        val explanations = mapOf(
            zed.packageName to explanation(changes = listOf(change("2026-10-04"), change("2025-01-01"))),
            amy.packageName to explanation(changes = listOf(change("2026-09-30"))),
            bob.packageName to explanation(changes = listOf(change("2026-10-04", "Bob's"))),
            none.packageName to explanation(),
        )
        val (who, what) = latestChange(listOf(zed, amy, bob, none), explanations)!!
        assertEquals("Bob", who.label) // Zed changed the same day; Bob comes first by name
        assertEquals("Bob's", what.text)
        assertEquals("amy" to "2026-09-30", latestChange(listOf(amy, none), explanations)!!.let { it.first.label to it.second.date })
        assertNull(latestChange(listOf(none), explanations))
        assertNull(latestChange(emptyList(), explanations))
    }

    @Test
    fun flaggedAndCautionStartOpenAndTheSectionsRunInTierOrder() {
        assertTrue(openByDefault(Tier.FLAGGED))
        assertTrue(openByDefault(Tier.CAUTION))
        assertFalse(openByDefault(Tier.EXPECTED))
        assertFalse(openByDefault(null))
        assertEquals(listOf(Tier.FLAGGED, Tier.CAUTION, Tier.EXPECTED, null), TIER_SECTIONS)
    }
}

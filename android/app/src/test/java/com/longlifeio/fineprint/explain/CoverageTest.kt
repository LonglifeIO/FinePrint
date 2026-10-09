package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * How far FinePrint has looked at an app (docs/METHOD.md, An app's page): Checked by FinePrint, Their words only, or
 * No record yet, on the schema 1.6 fixture: one app checked, one with a record nobody has checked, one with none.
 */
class CoverageTest {

    private val bundle = parseBundle(File("src/test/resources/schema16-fixture.json").readText(), File("src/test/resources/jurisdictions-fixture.json").readText())
    private fun app(pkg: String) = InstalledApp(pkg, pkg, "1.0", 1, 0, false, true, emptyList(), emptyList())
    private val apps = listOf(app("com.example.conditional"), app("com.example.regulator"), app("org.example.none"))
    private val scan = TrackerScanResult(emptyList(), 1, 10, 1, emptyList())
    private val explanations = apps.associate { it.packageName to explain(it, scan, bundle, emptyMap(), LocalDate.of(2026, 10, 8)) }
    private fun e(pkg: String) = explanations.getValue(pkg)

    @Test
    fun eachStateHasItsLabelAndItsLine() {
        assertEquals(listOf(Coverage.CHECKED, Coverage.THEIR_WORDS_ONLY, Coverage.NO_RECORD), apps.map { e(it.packageName).coverageState })
        assertEquals(
            listOf("Checked by FinePrint · 2026-10-08", "Their words only", "No record yet"),
            apps.map { coverageLabel(e(it.packageName)) },
        )
        assertEquals(
            listOf(
                "Checked by FinePrint on 2026-10-08",
                "Their words only — a reviewer hasn't looked at this app yet",
                "No record yet — these lines come from the trackers found in its code.",
            ),
            apps.map { coverageLine(e(it.packageName)) },
        )
    }

    @Test
    fun aPreinstalledAppsLineNamesItsMakersPolicy() {
        val inherited = e("org.example.none").copy(maker = Maker("co-google", "Google", inherited = true, lines = emptyList(), notes = emptyList()))
        assertEquals("No record yet · from Google's policy", coverageLabel(inherited))
        assertEquals("No record yet — these lines come from Google's policy and the trackers found in its code.", coverageLine(inherited))
    }

    @Test
    fun atAGlanceCountsThemAndTheFiltersPickThemOut() {
        assertEquals("Records: 1 checked, 1 their words only, 1 no record yet.", recordsLine(apps, explanations))
        fun only(f: ListFilter) = listOrder(apps, explanations, emptyMap(), "", setOf(f)).map { it.packageName }
        assertEquals(listOf("com.example.conditional"), only(ListFilter.CHECKED))
        assertEquals(listOf("com.example.regulator"), only(ListFilter.THEIR_WORDS))
        assertEquals(listOf("org.example.none"), only(ListFilter.NO_RECORD))
        assertEquals(listOf("Checked", "Their words only", "No record yet"), listOf(ListFilter.CHECKED, ListFilter.THEIR_WORDS, ListFilter.NO_RECORD).map { it.label })
    }

    @Test
    fun aCheckNeverChangesATier() {
        val record = bundle.apps.getValue("com.example.regulator")
        val checked = parseBundle(File("src/test/resources/schema16-fixture.json").readText().replace("\"package_id\": \"com.example.regulator\",", "\"package_id\": \"com.example.regulator\", \"checked_on\": \"2026-10-08\","))
        assertEquals(null, record.checkedOn)
        assertEquals("2026-10-08", checked.apps.getValue("com.example.regulator").checkedOn)
        val after = explain(app("com.example.regulator"), scan, checked, emptyMap(), LocalDate.of(2026, 10, 8))
        assertEquals(e("com.example.regulator").tier, after.tier)
        assertEquals(Coverage.CHECKED, after.coverageState)
    }
}

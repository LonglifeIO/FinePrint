package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.review.shape
import com.longlifeio.fineprint.ui.statusWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Schema 1.6 in the app (docs/METHOD.md): a conditional line is shown but never scored, an alleged matter before a
 * regulator is "not yet decided", and a tracker record's own purpose comes before its εxodus category.
 */
class Schema16Test {

    private val bundle = parseBundle(File("src/test/resources/schema16-fixture.json").readText(), File("src/test/resources/jurisdictions-fixture.json").readText())
    private val today = LocalDate.of(2026, 10, 8)

    private fun app(pkg: String) = InstalledApp(pkg, pkg, "1.0", 1, 0, false, true, emptyList(),
        listOf(RequestedPermission("android.permission.ACCESS_FINE_LOCATION", granted = true, dangerous = true)))

    private fun scan(vararg trackers: DetectedTracker) = TrackerScanResult(trackers.toList(), 1, 10, 1, emptyList())

    private val conditional = app("com.example.conditional")
    private val withCondition = explain(conditional, scan(), bundle, emptyMap(), today)

    @Test
    fun aConditionalLineIsShownWithItsConditionAndNeverScored() {
        val line = withCondition.flows.getValue(GOES_ELSEWHERE).single()
        assertEquals("if the developer turns on data sharing", line.conditional)
        assertEquals("If the developer turns on data sharing. FinePrint can't see that setting.", conditionLine(line.conditional!!))
        assertEquals(line.claim(), finePrint(withCondition).first().text) // in the reading order by its purpose
        // Scored as if it weren't there: the tier, Where it goes' headline, the home's chips and headline, the flows you
        // can limit, What it collects, and the Reviewed fingerprint.
        assertEquals(Tier.EXPECTED, withCondition.tier.tier)
        assertEquals("It stays with Example Conditional", whereHeadline(withCondition))
        val g = glance(listOf(conditional), mapOf(conditional.packageName to withCondition), emptyMap())
        assertEquals(mapOf(STAYS_HERE to 1, USED_FOR_MORE to 0, GOES_ELSEWHERE to 0), g.perBucket)
        assertEquals(0, g.othersForMore)
        assertTrue(countedFlows(withCondition).none { it.conditional != null })
        assertTrue(withCondition.collects.toString(), "Device and advertising IDs" !in withCondition.collects) // only the conditional line has them
        val record = bundle.apps.getValue(conditional.packageName)
        val without = record.copy(dataFlows = record.dataFlows.filter { it.conditional == null })
        assertEquals(shape(conditional.packageName, without, emptyList(), bundle.companies.values), shape(conditional.packageName, record, emptyList(), bundle.companies.values))
    }

    @Test
    fun anAllegedMatterBeforeARegulatorIsNotYetDecided() {
        val e = explain(app("com.example.regulator"), scan(), bundle, emptyMap(), today)
        val line = e.onTheRecord.actions.single()
        assertEquals("regulator", line.forum)
        assertTrue(line.details.toString(), "Alleged in a complaint filed with a data protection authority on 2025-12-17 (not yet decided)" in line.details)
        assertTrue(line.details.none { "not proven in court" in it })
        assertEquals("Alleged (not yet decided)", statusWord("alleged", "regulator"))
        assertEquals("Alleged (not proven in court)", statusWord("alleged", "court"))
        assertEquals("Alleged (not proven in court)", statusWord("alleged"))
        // The tier formula is the same (C3: filed); its reason says what the matter is.
        assertEquals("C3", e.tier.rule)
        assertEquals("A complaint to a regulator about this app's data has been filed (not yet decided)", e.tier.reason)
    }

    @Test
    fun aTrackerRecordsOwnPurposeComesBeforeItsExodusCategory() {
        // εxodus calls it advertising; its record says crash reporting, with a source.
        val found = DetectedTracker("exodus-3", "Some SDK", listOf("Advertisement"), "x.Y")
        val e = explain(app("org.example.purpose"), scan(found), bundle, emptyMap(), today)
        assertEquals(listOf("crash_diagnostics"), readingOrder(e).map { it.data })
        assertEquals("Also collected to run the app: crash data", alsoCollected(e)?.text)
        assertTrue(e.unrecorded.isEmpty())
        // Without the record, the category decides: advertising goes to other companies.
        val byCategory = explain(app("org.example.purpose"), scan(found), null, emptyMap(), today)
        assertEquals(LineGroup.OTHER_COMPANIES, readingOrder(byCategory).first().group())
        assertNull(alsoCollected(byCategory))
    }
}

package com.longlifeio.fineprint.ui

import com.longlifeio.fineprint.explain.Tier
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenSectionsTest {

    private val kept = mutableMapOf<String, Boolean>()
    private fun sections() = OpenSections({ key, default -> kept[key] ?: default }, { key, value -> kept[key] = value })

    @Test
    fun flaggedAndCautionStartOpenAndAChoiceIsKeptForNextTime() {
        val first = sections()
        assertEquals(listOf(true, true, false, false), listOf(Tier.FLAGGED, Tier.CAUTION, Tier.EXPECTED, null).map(first::isOpen))
        first.toggle(Tier.FLAGGED)
        first.toggle(null)
        assertEquals(mapOf("open:FLAGGED" to false, "open:NO_RECORD" to true), kept)
        // A later launch reads what was kept.
        val next = sections()
        assertEquals(listOf(false, true, false, true), listOf(Tier.FLAGGED, Tier.CAUTION, Tier.EXPECTED, null).map(next::isOpen))
    }

    @Test
    fun allOpenIsForTestsAndScreenshotsAndKeepsNothing() {
        val all = OpenSections.allOpen()
        assertEquals(listOf(true, true, true, true), listOf(Tier.FLAGGED, Tier.CAUTION, Tier.EXPECTED, null).map(all::isOpen))
    }
}

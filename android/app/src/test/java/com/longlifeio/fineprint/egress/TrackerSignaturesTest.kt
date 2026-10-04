package com.longlifeio.fineprint.egress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bundled asset keeps the promises the app and bundle/schema.json rely on. */
class TrackerSignaturesTest {

    private val trackers = bundledSignatures.trackers

    @Test
    fun idsAreUniqueAndInTheSchemaForm() {
        val ids = trackers.map { it.id }
        assertEquals("duplicate ids", ids.size, ids.toSet().size) // the detail screen keys rows by id
        val schemaForm = Regex("^exodus-[0-9]+$|^fp-[a-z0-9-]+$") // bundle/schema.json, tracker.id
        assertEquals(emptyList<String>(), ids.filterNot { schemaForm.matches(it) })
    }

    @Test
    fun mostTrackersHaveAUsableSignature() {
        val usable = trackers.count { it.codeSignature.length > 3 }
        assertTrue("only $usable usable signatures", usable > 400)
    }

    @Test
    fun carriesTheOdblNotice() {
        assertTrue(bundledSignatures.attribution.contains("Open Database License"))
    }

    @Test
    fun parsesMissingAndNullFields() {
        val parsed = parseTrackerSignatures(
            """{"trackers":[{"id":"fp-x","name":"X","code_signature":null},{"id":"fp-y","name":"Y"}]}""",
        )
        assertEquals(listOf("", ""), parsed.trackers.map { it.codeSignature })
        assertEquals(listOf(emptyList<String>(), emptyList()), parsed.trackers.map { it.categories })
    }
}

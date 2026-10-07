package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Bundle
import com.longlifeio.fineprint.bundle.Company
import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.bundle.parseJurisdictions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A law of a union of countries (an EU regulation) binds the countries its applies_to names, and only those. */
class UnionLawsTest {

    private val source = """{ "url": "https://example.org/law", "type": "statute", "title": "Law", "as_of": "2026-01-01", "status": "self_disclosed", "quote": "shall" }"""

    private fun law(id: String, name: String, appliesTo: String = "") =
        """{ "id": "$id", "name": "$name", "citation": "c", "text": "t", "status": "self_disclosed", $appliesTo "sources": [$source] }"""

    private val fixture = parseBundle(File("src/test/resources/bundle-fixture.json").readText())

    private fun company(id: String, country: String) =
        Company(hash = id, id = id, name = id, shortName = null, subsidiaries = emptyList(), jurisdiction = country)

    private val bundle = Bundle(
        fixture.version, fixture.generatedAt, fixture.apps, fixture.trackers,
        companies = listOf(company("co-de", "DE"), company("co-dk", "DK"), company("co-fr", "FR")).associateBy { it.id },
        permissions = fixture.permissions, deviceReach = fixture.deviceReach,
        jurisdictions = parseJurisdictions(
            """{ "schema_version": 1, "jurisdictions": [
                 { "id": "EU", "name": "European Union", "last_reviewed": "2026-10-07",
                   "laws": [${law("law-eu-x", "X Regulation", """ "applies_to": ["DE", "FR"], """)}] },
                 { "id": "FR", "name": "France", "last_reviewed": "2026-10-07", "laws": [${law("law-fr-y", "Y Act")}] } ] }""",
        ),
    )

    @Test
    fun aUnionLawBindsTheCountriesItNamesAfterTheirOwnLaws() {
        val blocks = governments(listOf("co-de", "co-dk", "co-fr"), unplaced = false, recorded = emptyList(), bundle = bundle).blocks
        assertEquals(listOf("Denmark", "France", "Germany"), blocks.map { it.name })
        val (denmark, france, germany) = blocks
        // Germany has no entry of its own: the union's law shows, with the note that its own laws aren't reviewed.
        assertEquals(listOf("X Regulation (c)"), germany.lines.map { it.title })
        assertTrue(!germany.lawsReviewed)
        // France's own law comes first, then the union's.
        assertEquals(listOf("Y Act (c)", "X Regulation (c)"), france.lines.map { it.title })
        assertTrue(france.lawsReviewed)
        // Denmark isn't named, so the union's law doesn't reach it.
        assertTrue(denmark.lines.isEmpty() && !denmark.lawsReviewed)
        // No company is based in the union itself.
        assertTrue(blocks.none { it.code == "EU" })
    }
}

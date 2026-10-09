package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Each law's short line (schema 1.7), read from the real jurisdictions.json and carried to its line on an app's page. */
class LawShortTest {

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())

    @Test
    fun everyLawHasOneShortSentenceThatIsntItsText() {
        val laws = bundle.jurisdictions.values.flatMap { it.laws }
        assertEquals(14, laws.size)
        for (law in laws) {
            val short = law.short ?: error("${law.id} has no short line")
            assertTrue(law.id, short.split(" ").size <= 25 && short.endsWith(".") && short != law.text)
        }
        assertEquals(
            "Section 702 was repealed on 2026-06-12, but directives already issued to providers stay in force until they expire.",
            laws.single { it.id == "law-us-fisa-702" }.short,
        )
    }

    @Test
    fun aCanCompelLineCarriesItsLawsShortLineAndItsText() {
        // Life360 with Arity (Allstate, a US company), so the US laws show under Jurisdictions.
        val app = InstalledApp("com.life360.android.safetymapd", "Life360", "1.0", 1, 0, false, true, emptyList(), emptyList())
        val scan = TrackerScanResult(listOf(DetectedTracker("fp-arity", "Arity", listOf("Location"), "com.arity.x")), 1, 10, 1, emptyList())
        val us = explain(app, scan, bundle, emptyMap()).governments.blocks.single { it.code == "US" }
        val cloud = us.lines.single { it.kind == CAN_COMPEL && it.title.startsWith("CLOUD Act") }
        val law = bundle.jurisdictions.getValue("US").laws.single { it.id == "law-us-cloud-act" }
        assertEquals(law.short, cloud.short)
        assertEquals(law.text, cloud.text)
    }
}

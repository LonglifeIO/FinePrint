package com.longlifeio.fineprint.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Both Field notes palettes, against the brief's rules and the owner's: readable text, chips that hold
 * their shape on the page and the cards, and chip colours that stay apart for a red-green colour-blind
 * reader. The build fails if any two semantic colours collapse.
 */
class PaletteContrastTest {

    private fun name(p: Palette) = if (p.dark) "dark" else "light"

    private fun chips(p: Palette) = mapOf("Stays here / Expected" to p.stays, "Used for more / Caution" to p.more, "Goes elsewhere / Flagged" to p.elsewhere, "No record yet" to p.noRecord)

    private fun contrastFailures(p: Palette): List<String> = buildList {
        fun need(what: String, fg: Color, bg: Color, min: Double) {
            val r = contrast(fg, bg)
            if (r < min) add("${name(p)} $what: ${"%.2f".format(r)} < $min")
        }
        for ((where, bg) in listOf("surface" to p.surface, "card" to p.card, "raised" to p.raised)) {
            need("ink on $where", p.ink, bg, 4.5)
            need("muted on $where", p.muted, bg, 4.5)
        }
        need("outline on surface", p.outline, p.surface, 3.0) // the search field's border, a control boundary
        for ((chip, tone) in chips(p)) {
            need("$chip ink on its fill", tone.content, tone.container, 4.5)
            // The chip's edge: its fill for the three hues, the dashed outline (drawn in its ink) for grey.
            val edge = if (tone == p.noRecord) tone.content else tone.container
            need("$chip edge on surface", edge, p.surface, 3.0)
            need("$chip edge on card", edge, p.card, 3.0)
        }
    }

    private fun collapses(p: Palette): List<String> = buildList {
        val fills = chips(p).mapValues { it.value.container }.toList()
        for (vision in Vision.entries) {
            for (i in fills.indices) for (j in i + 1 until fills.size) {
                val d = difference(fills[i].second, fills[j].second, vision)
                if (d < MIN_DIFFERENCE) add("${name(p)} ${vision.name.lowercase()}: ${fills[i].first} vs ${fills[j].first} ΔE2000 ${"%.1f".format(d)} < $MIN_DIFFERENCE")
            }
        }
    }

    @Test
    fun everyTextAndChipEdgeMeetsWcag() {
        assertEquals(emptyList<String>(), contrastFailures(FieldNotesLight) + contrastFailures(FieldNotesDark))
    }

    @Test
    fun noTwoChipColoursCollapseForRedGreenColourBlindness() {
        assertEquals(emptyList<String>(), collapses(FieldNotesLight) + collapses(FieldNotesDark))
    }

    /** The simulation itself: red and green, far apart in normal vision, become near twins without red-green vision. */
    @Test
    fun theSimulationCollapsesATrafficLight() {
        val red = Color(0xFFD62728)
        val green = Color(0xFF2CA02C)
        assert(difference(red, green, Vision.NORMAL) > 60)
        assert(difference(red, green, Vision.DEUTERANOPIA) < MIN_DIFFERENCE)
        assert(difference(red, green, Vision.PROTANOPIA) < MIN_DIFFERENCE)
    }

    private companion object {
        /** Clearly different at a glance, in CIEDE2000 units (10 is a noticeable difference). */
        const val MIN_DIFFERENCE = 25.0
    }
}

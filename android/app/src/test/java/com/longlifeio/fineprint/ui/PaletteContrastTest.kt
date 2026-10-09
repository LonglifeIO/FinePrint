package com.longlifeio.fineprint.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Both Field notes palettes against the brief's rules and the owner's: readable text, chips whose icon
 * and word read on their tint and around it, borders all or none in a theme (all wherever any tint
 * barely shows), colour on chips only, and chip inks that stay apart for a red-green colour-blind
 * reader. The build fails if any two semantic colours collapse. Cards follow the borders' rule too: a
 * hairline on every card in dark mode, where the fill barely parts from the page, and on none in light.
 */
class PaletteContrastTest {

    private val palettes = listOf("light" to FieldNotesLight, "dark" to FieldNotesDark)

    private fun hues(p: Palette) = mapOf("Stays here / Expected" to p.stays, "Used for more / Caution" to p.more, "Goes elsewhere / Flagged" to p.elsewhere)

    private fun contrastFailures(name: String, p: Palette): List<String> = buildList {
        fun need(what: String, fg: Color, bg: Color, min: Double) {
            val r = contrast(fg, bg)
            if (r < min) add("$name $what: ${"%.2f".format(r)} < $min")
        }
        for ((where, bg) in listOf("surface" to p.surface, "card" to p.card, "raised" to p.raised)) {
            need("ink on $where", p.ink, bg, 4.5)
            need("muted on $where", p.muted, bg, 4.5)
        }
        need("outline on surface", p.outline, p.surface, 3.0) // the search field's border, a control boundary
        // A row never mixes bordered and unbordered chips: if any tint barely shows, all three are bordered.
        val faint = hues(p).values.any { minOf(contrast(it.container, p.card), contrast(it.container, p.surface)) < FAINT_TINT }
        for ((chip, tone) in hues(p)) {
            need("$chip ink on its tint", tone.content, tone.container, 4.5)
            // Where the tint barely shows, the icon and word still read on the card and the page.
            need("$chip ink on card", tone.content, p.card, 4.5)
            need("$chip ink on surface", tone.content, p.surface, 4.5)
            if (faint != (tone.border != null)) add("$name $chip: ${if (faint) "a tint in this theme is under $FAINT_TINT:1, so every chip needs" else "no tint is faint, so no chip needs"} a border")
        }
        // Not checked yet: grey, its dashed edge drawn in its ink.
        need("Not checked yet ink on its fill", p.noRecord.content, p.noRecord.container, 4.5)
        need("Not checked yet edge on surface", p.noRecord.content, p.surface, 3.0)
        need("Not checked yet edge on card", p.noRecord.content, p.card, 3.0)
    }

    private fun collapses(name: String, p: Palette): List<String> = buildList {
        val inks = hues(p).mapValues { it.value.content }.toList()
        for (vision in Vision.entries) {
            for (i in inks.indices) for (j in i + 1 until inks.size) {
                val d = difference(inks[i].second, inks[j].second, vision)
                if (d < MIN_DIFFERENCE) add("$name ${vision.name.lowercase()}: ${inks[i].first} vs ${inks[j].first} ΔE2000 ${"%.1f".format(d)} < $MIN_DIFFERENCE")
            }
        }
    }

    @Test
    fun everyTextAndChipMeetsWcag() {
        assertEquals(emptyList<String>(), palettes.flatMap { (name, p) -> contrastFailures(name, p) })
    }

    @Test
    fun noTwoChipInksCollapseForRedGreenColourBlindness() {
        assertEquals(emptyList<String>(), palettes.flatMap { (name, p) -> collapses(name, p) })
    }

    /** Colour on chips only: every other colour of the palette is a grey, warm or cool. */
    @Test
    fun nothingOutsideTheChipsCarriesHue() {
        val hued = palettes.flatMap { (name, p) ->
            (listOf("surface" to p.surface, "ink" to p.ink, "muted" to p.muted, "card" to p.card, "raised" to p.raised,
                "divider" to p.divider, "outline" to p.outline, "track" to p.track) + listOfNotNull(p.cardEdge?.let { "card edge" to it }))
                .filter { chroma(it.second) >= MAX_GREY_CHROMA }.map { "$name ${it.first}: chroma ${"%.1f".format(chroma(it.second))}" }
        }
        assertEquals(emptyList<String>(), hued)
    }

    /** Dark mode's cards blend into the page (the fill is about 1.1:1 against it), so each has a hairline at 1.5:1 or more. */
    @Test
    fun cardsHaveAHairlineInDarkModeOnly() {
        assertEquals(null, FieldNotesLight.cardEdge)
        val dark = FieldNotesDark
        val edge = dark.cardEdge ?: error("dark mode's cards need their edge")
        assertTrue(contrast(edge, dark.surface) >= 1.5)
    }

    /** All or none: every card's fill goes through cardFill, which draws the edge, so no card is left without it. */
    @Test
    fun everyCardIsDrawnThroughCardFill() {
        val direct = Regex("""background\((?:p|palette|LocalPalette\.current)\.card\)""")
        val found = File("src/main/java/com/longlifeio/fineprint/ui").listFiles().orEmpty().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, line) -> direct.containsMatchIn(line) && "// the one card fill" !in line }.map { "${f.name}:${it.index + 1}" }
        }
        assertEquals(emptyList<String>(), found)
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
        const val MAX_GREY_CHROMA = 10.0
    }
}

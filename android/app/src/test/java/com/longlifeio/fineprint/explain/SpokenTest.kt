package com.longlifeio.fineprint.explain

import org.junit.Assert.assertEquals
import org.junit.Test

/** What TalkBack reads for a footnoted line (heard live on the emulator, 2026-10-07): no marks, no "right arrow". */
class SpokenTest {
    @Test
    fun footnoteMarksAreNotRead() {
        assertEquals("Life360 says it does not share or sell data of members it knows are under 18.", spoken("Life360 says it does not share or sell data of members it knows are under 18.¹"))
        assertEquals("A line with two sources", spoken("A line with two sources" + superscript(2) + "," + superscript(12)))
    }

    @Test
    fun anArrowIsReadAsTo() {
        assertEquals("Precise location to Select business partners: For their own use", spoken("Precise location → Select business partners: For their own use"))
        assertEquals("to Amplitude, AppsFlyer: Usage statistics", spoken("→ Amplitude, AppsFlyer: Usage statistics¹"))
    }
}

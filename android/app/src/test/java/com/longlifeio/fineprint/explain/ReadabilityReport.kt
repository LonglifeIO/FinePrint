package com.longlifeio.fineprint.explain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The two readability reports (docs/METHOD.md, Voice), written to build/reports/readability/ during the JVM tests:
 * app.txt over the app's strings, bundle.txt over the bundle's text people read. CI prints both and shows their counts
 * as warnings; nothing here fails over what they find, only if a reader found nothing to read.
 */
class ReadabilityReport {

    @Test
    fun writeBothReports() {
        val dir = File("build/reports/readability").apply { mkdirs() }
        val app = appSentences()
        val bundle = bundleSentences()
        dir.resolve("app.txt").writeText(report("the app's strings (src/main, src/device, src/preview)", app))
        dir.resolve("bundle.txt").writeText(report("the bundle's text people read (bundle.json, jurisdictions.json)", bundle))
        assertTrue("the app's strings: ${app.size} sentences", app.size > 200)
        assertTrue("the bundle's text: ${bundle.size} sentences", bundle.size > 100)
    }
}

/** The measures themselves, on sentences whose answers are known. */
class ReadabilityTest {

    @Test
    fun sentencesEndAtAFullStopButNotAfterAnAbbreviation() {
        assertEquals(
            listOf("A court let Texas v. Allstate go ahead.", "It cites 50 U.S.C. § 1881a.", "Then it ends."),
            sentences("A court let Texas v. Allstate go ahead. It cites 50 U.S.C. § 1881a. Then it ends."),
        )
    }

    @Test
    fun syllablesAndGrade() {
        assertEquals(listOf(1, 2, 1, 5, 1), listOf("cat", "money", "share", "communication", "2026").map(::syllables))
        assertTrue(grade("The app sends your location to other companies.") < MAX_GRADE)
        assertTrue(grade("Sensitive data goes to other companies by the app's own account or a ruling, or a court has ruled on, or let proceed, a case over this app's data.") > MAX_GRADE)
    }

    @Test
    fun aNameOfSeveralWordsGradesAsItsInitialsDo() {
        assertEquals(
            listOf("In", "2026", "the", "0", "and", "the", "0", "fined", "it"),
            gradedWords("In 2026 the Federal Trade Commission and the Data Protection Commission fined it."),
        )
        assertEquals(listOf("It", "cites", "50", "0", "1881a"), gradedWords("It cites 50 U.S.C. § 1881a."))
        assertEquals(listOf("Texas", "v", "Allstate"), gradedWords("Texas v. Allstate"))
        assertEquals(listOf("The", "0", "fined", "it"), gradedWords("The Federal Trade Commission fined it."))
        // Not names: a sentence's first word, and words apart by more than a space.
        assertEquals(listOf("When", "FinePrint", "infers", "it"), gradedWords("When FinePrint infers it."))
        assertEquals(listOf("Reported", "Journalists", "found", "it"), gradedWords("Reported: Journalists found it."))
        assertEquals(listOf("Apps", "this", "app", "Permissions", "Location"), gradedWords("Apps > this app > Permissions > Location"))
        assertEquals(grade("In 2026 the FTC fined the company that makes the app."), grade("In 2026 the Federal Trade Commission fined the company that makes the app."), 0.0)
        // The length limit still counts every word.
        assertEquals(13, measure(Sentence("In 2026 the Federal Trade Commission and the Data Protection Commission fined it.", "")).words)
    }

    @Test
    fun stackedQualifiersCountEachOrAndEachComma() {
        assertEquals(6, qualifiers("by its own account or a ruling, or a court has ruled on, or let proceed, a case"))
    }

    @Test
    fun jargonIsTheFileAndTheLedgerNotTheLegalSense() {
        assertEquals(listOf("flow", "line"), jargon("4 of 19 flows limited, one line each"))
        assertEquals(emptyList<String>(), jargon("4 of 28 data flows limited by your settings"))
        assertEquals(listOf("record"), jargon("FinePrint's record of this app"))
        assertEquals(emptyList<String>(), jargon("What regulators and courts have said: On the record. FinePrint relays the public record."))
        assertEquals(listOf("line", "status word"), jargon("An alleged line about it"))
        assertEquals(emptyList<String>(), jargon("Reported by journalists; alleged in a lawsuit; Alleged (not proven in court)"))
    }

    @Test
    fun onlyProseIsRead() {
        assertTrue(isProse("Search apps by name"))
        assertTrue(isProse("Stale"))
        listOf("detail", "section:summary", "status_kind", "https://example.org/a", "com.example.app", "bundle.json", """\b(\w+)""")
            .forEach { assertTrue(it, !isProse(it)) }
    }
}

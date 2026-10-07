package com.longlifeio.fineprint.explain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * docs/METHOD.md, Before each release: none of the brief's alarm words in the app's own text. Checks
 * every string literal in the app's code and the published method; the one sentence that names
 * uninstalling says nobody's asking you to.
 */
class WordsTest {

    private val banned = Regex("""\b(danger\w*|threat\w*|spy|spying|spies|creepy|infect\w*|risk score|unsafe|uninstall\w*)\b""", RegexOption.IGNORE_CASE)
    private val allowed = setOf("FinePrint relays the public record; it doesn't judge — nobody's telling you to uninstall anything.")

    @Test
    fun noAlarmWordsInTheAppsText() {
        // Comments and the code inside "${…}" templates aren't text anyone reads.
        val comments = Regex("""/\*[\s\S]*?\*/|//[^\n]*""")
        val templates = Regex("""\$\{[^}]*\}""")
        val literals = File("src/main/java").walk().filter { it.extension == "kt" }.flatMap { f ->
            Regex(""""((?:[^"\\]|\\.)*)"""").findAll(comments.replace(f.readText(), "")).map { f.name to templates.replace(it.groupValues[1], "") }
        }
        val found = literals.filter { (_, text) -> banned.containsMatchIn(text) && text !in allowed }.map { (file, text) -> "$file: $text" }.toList()
        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun noAlarmWordsInHowToRead() {
        // The release list itself names the words it rules out; everything else in the method is checked.
        val method = File("../../docs/METHOD.md").readText().substringBefore("## Before each release") +
            File("../../docs/METHOD.md").readText().substringAfter("## Licences")
        assertEquals(emptyList<String>(), banned.findAll(method).map { it.value }.toList())
    }
}

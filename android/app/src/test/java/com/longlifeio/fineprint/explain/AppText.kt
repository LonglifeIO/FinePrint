package com.longlifeio.fineprint.explain

import java.io.File

/** A string literal in the app's own code: its file, its line, and its text with the code in "${…}" templates replaced. */
data class AppString(val file: File, val line: Int, val text: String)

/**
 * Every string literal in the app's code, shared and each flavour's (WordsTest, ReadabilityReport). One pass, left to
 * right, so a "//" inside a string (a URL) isn't taken for a comment: raw strings, strings, character literals and
 * comments each match whole, and only the strings are read. A string stays on its line, and nothing here recurses per
 * character, so no stretch of code can overflow the stack. Comments and the code inside "${…}" templates aren't text
 * anyone reads: each template becomes [template] ("" drops it, "X" keeps a word in its place).
 */
fun appStrings(template: String = ""): List<AppString> {
    val tokens = Regex(""""{3}([\s\S]*?)"{3}|"([^"\\\n]*(?:\\.[^"\\\n]*)*)"|'(?:[^'\\]|\\.)'|/\*[\s\S]*?\*/|//[^\n]*""")
    val templates = Regex("""\$\{[^}]*\}""")
    val code = listOf("src/main/java", "src/device/java", "src/preview/java").map(::File)
    return code.asSequence().flatMap { it.walk() }.filter { it.extension == "kt" }.flatMap { f ->
        val source = f.readText()
        tokens.findAll(source).mapNotNull { m ->
            val text = m.groups[1]?.value ?: m.groups[2]?.value ?: return@mapNotNull null
            AppString(f, source.substring(0, m.range.first).count { it == '\n' } + 1, templates.replace(text, template))
        }
    }.toList()
}

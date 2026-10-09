package com.longlifeio.fineprint.explain

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/*
 * The readability report's measures (docs/METHOD.md, Voice): sentence length, a rough grade level, stacked qualifiers
 * and jargon, over the app's strings and over the bundle's text people read. A warning list for whoever writes
 * FinePrint's words, never a gate: CI prints the worst sentences and passes (ReadabilityReport).
 */

/** One sentence and where it's from: "Wording.kt:16", or a path in the bundle ("bundle.json.apps[…].summary"). */
data class Sentence(val text: String, val where: String)

const val MAX_WORDS = 20
const val MAX_GRADE = 8.0
const val MAX_QUALIFIERS = 2
const val WORST = 30

/** Shorter sentences are labels (a chip, a header): a grade means nothing for them, and the word limit covers them. */
const val MIN_GRADED_WORDS = 6

data class Measured(val sentence: Sentence, val words: Int, val grade: Double, val qualifiers: Int, val jargon: List<String>) {
    /** Too long, too hard, too many qualifiers stacked, or a jargon word. */
    val over: Boolean get() = words > MAX_WORDS || grade > MAX_GRADE || qualifiers > MAX_QUALIFIERS || jargon.isNotEmpty()

    /** How far over: what puts the worst first. */
    val score: Double get() =
        maxOf(0, words - MAX_WORDS) + 2 * maxOf(0.0, grade - MAX_GRADE) + 3 * maxOf(0, qualifiers - MAX_QUALIFIERS) + 4.0 * jargon.size
}

/** A full stop after these doesn't end a sentence ("Texas v. Allstate", "50 U.S.C. § 1881a"). */
private val ABBREVIATIONS = setOf("v", "vs", "u.s", "u.s.c", "e.g", "i.e", "no", "inc", "ltd", "co", "corp", "st", "mr", "ms", "dr", "jr", "art", "sec", "etc", "al", "cf")
private val BOUNDARY = Regex("""[.!?]["”’)]*\s+(?=["“‘(]*[A-Z0-9])""")
private val WORD = Regex("""[A-Za-z0-9][A-Za-z0-9’'\-]*""")

fun sentences(text: String): List<String> {
    val out = mutableListOf<String>()
    var start = 0
    for (m in BOUNDARY.findAll(text)) {
        if (text[m.range.first] == '.') {
            // The word the full stop ends: an abbreviation, or an initial ("J. Smith").
            val before = text.substring(start, m.range.first).substringAfterLast(' ').lowercase()
            if (before in ABBREVIATIONS || (before.length == 1 && before[0].isLetter())) continue
        }
        out += text.substring(start, m.range.last + 1).trim()
        start = m.range.last + 1
    }
    out += text.substring(start).trim()
    return out.filter { s -> s.any(Char::isLetter) }
}

fun words(sentence: String): List<String> = WORD.findAll(sentence).map { it.value }.toList()

/**
 * The words the grade counts: a name of several capitalized words ("Federal Trade Commission", "U.S.C.") is one word of
 * one syllable, as a number is, so a name spelled out grades as its initials do. A name's words are apart only by spaces
 * or full stops ("Permissions > Location" is two), and a sentence's first word is capitalized anyway ("When FinePrint").
 */
fun gradedWords(sentence: String): List<String> {
    val out = mutableListOf<String>()
    var run = 0 // the name's words so far
    var end = -1 // where the word before ended
    for (m in WORD.findAll(sentence)) {
        val joined = run > 0 && sentence.substring(end, m.range.first).all { it == ' ' || it == '.' }
        run = when {
            end < 0 || !m.value.first().isUpperCase() -> 0
            joined -> run + 1
            else -> 1
        }
        when {
            run == 2 -> out[out.lastIndex] = "0"
            run < 2 -> out += m.value
        }
        end = m.range.last + 1
    }
    return out
}

/** Vowel groups, less a silent final e; a number counts as one. */
fun syllables(word: String): Int {
    val w = word.lowercase().filter { it in 'a'..'z' }
    if (w.isEmpty()) return 1
    val groups = Regex("[aeiouy]+").findAll(w).count()
    return maxOf(1, if (w.endsWith("e") && !w.endsWith("le") && groups > 1) groups - 1 else groups)
}

/** Flesch–Kincaid grade for one sentence: rough, but it ranks a statute-like line above a plain one. */
fun grade(sentence: String): Double {
    val w = gradedWords(sentence)
    if (w.isEmpty()) return 0.0
    return maxOf(0.0, 0.39 * w.size + 11.8 * w.sumOf(::syllables) / w.size - 15.59)
}

/** Stacked qualifiers: each "or" and each comma. */
fun qualifiers(sentence: String): Int = Regex("""\bor\b""", RegexOption.IGNORE_CASE).findAll(sentence).count() + sentence.count { it == ',' }

private val JARGON = listOf(
    // "Data flows" is plain enough (docs/METHOD.md, Voice); a flow on its own is the ledger's word.
    "flow" to Regex("""(?<!\bdata )\bflows?\b""", RegexOption.IGNORE_CASE),
    "bucket" to Regex("""\bbuckets?\b""", RegexOption.IGNORE_CASE),
    // The data file. "On the record" and "the public record" are the legal sense, and stay.
    "record" to Regex("""(?<!\bon the )(?<!\bpublic )\brecords?\b""", RegexOption.IGNORE_CASE),
    "line" to Regex("""\blines?\b""", RegexOption.IGNORE_CASE),
    // A status word in front of a noun ("an alleged line"), not a verb ("reported by", "alleged in").
    "status word" to Regex(
        """\b(self-disclosed|reported|alleged|adjudicated)\s+(?!(?:by|in|to|that|on|at|as|from|with|for|of|and|or|but|before|after|until|when|which)\b)[a-z]""",
        RegexOption.IGNORE_CASE,
    ),
)

fun jargon(sentence: String): List<String> = JARGON.filter { it.second.containsMatchIn(sentence) }.map { it.first }

fun measure(s: Sentence): Measured {
    val count = words(s.text).size
    return Measured(s, count, if (count >= MIN_GRADED_WORDS) grade(s.text) else 0.0, qualifiers(s.text), jargon(s.text))
}

fun report(title: String, from: List<Sentence>): String {
    val measured = from.map(::measure)
    val over = measured.filter { it.over }
    val worst = over.sortedWith(compareByDescending<Measured> { it.score }.thenByDescending { it.words }).take(WORST)
    return buildString {
        appendLine("Readability: $title")
        appendLine(
            "${measured.size} sentences read; ${over.size} over a threshold (more than $MAX_WORDS words, grade above ${MAX_GRADE.toInt()}, " +
                "more than $MAX_QUALIFIERS stacked qualifiers, or a jargon word).",
        )
        appendLine()
        appendLine("The worst ${worst.size}:")
        worst.forEachIndexed { i, m ->
            appendLine()
            val jargonNote = if (m.jargon.isEmpty()) "" else " · jargon: ${m.jargon.joinToString()}"
            appendLine("${i + 1}. ${m.sentence.where} — ${m.words} words · grade ${"%.1f".format(m.grade)} · ${m.qualifiers} qualifiers$jargonNote")
            appendLine("   ${m.sentence.text}")
        }
    }
}

/** A string someone reads: not a key, tag, id, path, address or pattern. */
internal fun isProse(text: String): Boolean {
    val t = text.trim()
    if (t.none(Char::isLetter) || "://" in t || t.startsWith("android.") || t.startsWith("com.")) return false
    if (Regex("""^[a-z0-9_.:/#%-]+$""").matches(t) || Regex("""\\[a-zA-Z]|\(\?|\.(json|kt|md|xml|png)\b""").containsMatchIn(t)) return false
    // A constant or a class name ("UNUSED_PARAMETER", "UnusedReceiverParameter"), as in @Suppress.
    if (Regex("""^[A-Z][A-Z0-9_]*$|^[A-Z][a-z0-9]+(?:[A-Z][a-z0-9]+)+$""").matches(t)) return false
    return t.first().isUpperCase() || ' ' in t || t.first() in "“\"("
}

/** The app's strings, sentence by sentence; a template ("$name", "${…}", one cut off by a quote inside it) reads as one word. */
fun appSentences(): List<Sentence> = appStrings(template = "X").filter { isProse(it.text) }.flatMap { s ->
    val text = s.text.replace(Regex("""\$\{.*$|\$[A-Za-z_][A-Za-z0-9_]*"""), "X")
    sentences(text).map { Sentence(it, "${s.file.name}:${s.line}") }
}

/** The fields of the bundle that a screen shows; sources, quotes and the store's own words are other people's, and aren't read. */
private val BUNDLE_FIELDS = setOf("summary", "text", "wording", "purpose", "recipient_label", "notes", "body", "label", "how", "effect", "note", "plain", "why_it_matters", "privacy_controls")
private val SKIPPED = setOf("sources", "jurisdiction_sources", "store_tagline")

/** The bundle's text people read, sentence by sentence, each with its path: a legal item's title too, and each law's name and text. */
fun bundleSentences(bundle: File = File("../../bundle/bundle.json"), laws: File = File("../../bundle/jurisdictions.json")): List<Sentence> {
    val out = mutableListOf<Sentence>()
    fun walk(node: Any?, path: String, parent: String, fields: Set<String>) {
        when (node) {
            is JSONObject -> for (k in node.keys().asSequence().sorted()) {
                if (k in SKIPPED) continue
                val v = node.get(k)
                val read = k in fields || (k == "title" && parent == "regulatory_history") || (k == "name" && parent == "laws")
                if (v is String) { if (read) sentences(v).forEach { out += Sentence(it, "$path.$k") } } else walk(v, "$path.$k", k, fields)
            }
            is JSONArray -> for (i in 0 until node.length()) {
                val item = node.get(i)
                val id = (item as? JSONObject)?.let { it.optString("id").ifEmpty { it.optString("package_id") } }.orEmpty()
                walk(item, "$path[${id.ifEmpty { "$i" }}]", parent, fields)
            }
        }
    }
    walk(JSONObject(bundle.readText()), "bundle.json", "", BUNDLE_FIELDS)
    walk(JSONObject(laws.readText()), "jurisdictions.json", "", setOf("text"))
    return out
}

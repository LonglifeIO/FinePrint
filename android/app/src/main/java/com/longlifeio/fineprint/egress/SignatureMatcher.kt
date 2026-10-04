package com.longlifeio.fineprint.egress

import java.util.regex.Pattern

/**
 * Decides which tracker signatures match a class name, giving the same answer as Exodus Privacy's
 * own analysis (exodus-core runs `re.search(code_signature, name)` for every signature longer than
 * three characters), but fast enough to run over every class of every installed app on a phone.
 *
 * Exodus signatures are '|'-separated alternatives of literal text in which '.' is the regex
 * wildcard, e.g. "com.appsflyer.". Each alternative's most distinctive literal run is indexed in a
 * trie; a name is walked through the trie from every offset, and only alternatives whose run occurs
 * there are checked in full. A signature using any other regex syntax falls back to
 * java.util.regex (none do today; pipeline/fetch_trackers.py lists any that appear).
 *
 * Immutable after construction, so one instance can be shared by concurrent scans.
 */
class SignatureMatcher(val trackers: List<TrackerSignature>) {

    /** One literal alternative of a signature; its indexed run starts at [anchorAt]. */
    private class Alternative(val tracker: Int, val text: String, val anchorAt: Int)

    private val alternatives = ArrayList<Alternative>()
    private val fallbacks = ArrayList<Pair<Int, Pattern>>()

    // Trie over the indexed runs, on a dense alphabet of the characters they use:
    // next[node * width + symbol] is the child node, 0 meaning none (the root is node 0).
    private val alphabet = IntArray(128) { -1 }
    private var width = 0
    private val next: IntArray
    private val outputs: Array<IntArray?>

    init {
        val anchors = ArrayList<String>()
        trackers.forEachIndexed { index, tracker ->
            val signature = tracker.codeSignature
            if (signature.length <= 3) return@forEachIndexed // exodus-core skips these too
            if (!SIMPLE_SIGNATURE.matches(signature)) {
                runCatching { Pattern.compile(signature) }.onSuccess { fallbacks += index to it }
                return@forEachIndexed
            }
            for (text in signature.split('|')) {
                // An empty or all-wildcard alternative would match nearly everything; the pipeline
                // warns about them and none exist, so they are skipped rather than honoured.
                val run = indexedRun(text) ?: continue
                alternatives += Alternative(index, text, run.first)
                anchors += text.substring(run)
            }
        }
        for (anchor in anchors) for (ch in anchor) if (alphabet[ch.code] < 0) alphabet[ch.code] = width++
        val maxNodes = 1 + anchors.sumOf { it.length }
        next = IntArray(maxNodes * width)
        val ends = arrayOfNulls<MutableList<Int>>(maxNodes)
        var nodeCount = 1
        anchors.forEachIndexed { alt, anchor ->
            var node = 0
            for (ch in anchor) {
                val slot = node * width + alphabet[ch.code]
                if (next[slot] == 0) next[slot] = nodeCount++
                node = next[slot]
            }
            (ends[node] ?: ArrayList<Int>().also { ends[node] = it }) += alt
        }
        outputs = Array(nodeCount) { ends[it]?.toIntArray() }
    }

    /**
     * Returns the indices (into [trackers]) of signatures that match [name] and are not yet marked
     * in [found], marking them; null when there are none, which is almost always.
     */
    fun newMatches(name: String, found: BooleanArray): List<Int>? {
        var matches: MutableList<Int>? = null
        val n = name.length
        for (start in 0 until n) {
            var node = 0
            var i = start
            while (i < n) {
                val code = name[i].code
                if (code >= 128) break
                val symbol = alphabet[code]
                if (symbol < 0) break
                node = next[node * width + symbol]
                if (node == 0) break
                outputs[node]?.forEach { alt ->
                    val tracker = alternatives[alt].tracker
                    if (!found[tracker] && occursAt(alternatives[alt], name, start)) {
                        found[tracker] = true
                        (matches ?: ArrayList<Int>().also { matches = it }) += tracker
                    }
                }
                i++
            }
        }
        for ((tracker, pattern) in fallbacks) {
            if (!found[tracker] && pattern.matcher(name).find()) {
                found[tracker] = true
                (matches ?: ArrayList<Int>().also { matches = it }) += tracker
            }
        }
        return matches
    }

    /** Whether [alt] matches [name] when its indexed run sits at [anchorStart]. */
    private fun occursAt(alt: Alternative, name: String, anchorStart: Int): Boolean {
        val begin = anchorStart - alt.anchorAt
        if (begin < 0 || begin + alt.text.length > name.length) return false
        for (k in alt.text.indices) {
            val p = alt.text[k]
            val c = name[begin + k]
            // Python's '.' matches anything but '\n'.
            if (if (p == '.') c == '\n' else p != c) return false
        }
        return true
    }

    private companion object {
        /** Literal text, '.' wildcards and '|' alternation: what every Exodus signature uses. */
        val SIMPLE_SIGNATURE = Regex("[A-Za-z0-9_.|-]+")

        /** Runs that occur in a great many unrelated class names make poor anchors. */
        val COMMON_RUNS = setOf("com", "org", "net", "io", "co", "android", "androidx", "google", "gms", "sdk", "app", "internal")

        /** The literal run between '.' wildcards least likely to occur in unrelated class names. */
        fun indexedRun(text: String): IntRange? {
            var best: IntRange? = null
            var bestScore = 0
            var start = 0
            for (end in 0..text.length) {
                if (end < text.length && text[end] != '.') continue
                if (end > start) {
                    val run = start until end
                    val score = (end - start) * if (text.substring(run) in COMMON_RUNS) 1 else 4
                    if (score >= bestScore) {
                        best = run
                        bestScore = score
                    }
                }
                start = end + 1
            }
            return best
        }
    }
}

/**
 * The class names exodus-core extracts for a dex type descriptor. It runs
 * `re.findall(r'[A-Z]+((?:\w+\/)+\w+)', dexdump_output)`, so "Lcom/foo/Bar$Inner;" becomes
 * "com/foo/Bar" and a class in the root package yields nothing. Hand-rolled (a regex per class is
 * slow on a phone) to reproduce Python's backtracking exactly, quirks included: for "LAB/cd;" the
 * greedy [A-Z]+ backs off one letter and the name is "B/cd".
 */
fun exodusNames(descriptor: String): List<String> {
    var names: MutableList<String>? = null
    val n = descriptor.length
    var i = 0
    while (i < n) {
        if (descriptor[i] !in 'A'..'Z') {
            i++
            continue
        }
        var j = i
        while (j < n && descriptor[j] in 'A'..'Z') j++
        // The group first tries to start right after the capitals. If that character is a word
        // character, starting earlier only lengthens the same first segment; if it is not, only
        // the last capital can start the group, and only if [A-Z]+ keeps at least one letter.
        val start = when {
            j < n && isWord(descriptor[j]) -> j
            j - i >= 2 -> j - 1
            else -> -1
        }
        val end = if (start < 0) -1 else groupEnd(descriptor, start)
        if (end < 0) {
            i = j // every start inside this run of capitals fails the same way
        } else {
            (names ?: ArrayList<String>(1).also { names = it }) += descriptor.substring(start, end)
            i = end
        }
    }
    return names ?: emptyList()
}

/** End of `(?:\w+/)+\w+` matched greedily at [start] (at least two segments), or -1. */
private fun groupEnd(s: String, start: Int): Int {
    var p = start
    while (p < s.length && isWord(s[p])) p++
    if (p == start) return -1
    var end = -1
    while (p < s.length && s[p] == '/') {
        var q = p + 1
        while (q < s.length && isWord(s[q])) q++
        if (q == p + 1) break // "//" or a trailing '/': the group ends before this slash
        end = q
        p = q
    }
    return end
}

/** Python's Unicode `\w`: letters, digits, other numerics and '_'. */
private fun isWord(c: Char): Boolean = c == '_' || Character.isLetterOrDigit(c) ||
    Character.getType(c).let { it == Character.LETTER_NUMBER.toInt() || it == Character.OTHER_NUMBER.toInt() }

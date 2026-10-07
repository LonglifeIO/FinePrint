package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Source

/**
 * The page's footnote numbers: each distinct source (one document and section) numbered in the order
 * its claims appear on an app's page, so a claim can carry ¹ or ²,³ and the Sources card at the end
 * can list [1] to [n]. Numbers count claims in closed sections too, so they never change as you open one.
 */
class Footnotes(sources: List<Source>) {
    private val numbers = LinkedHashMap<String, Int>()
    /** In number order: the Sources card's list. */
    val ordered: List<Source>

    init {
        val list = ArrayList<Source>()
        for (s in sources) numbers.getOrPut(key(s)) { list += s; list.size }
        ordered = list
    }

    fun number(source: Source): Int? = numbers[key(source)]

    /** "¹" or "²,³": the numbers of a claim's sources, smallest first. */
    fun marks(sources: List<Source>): String = sources.mapNotNull(::number).distinct().sorted().joinToString(",") { superscript(it) }

    companion object {
        val NONE = Footnotes(emptyList())
        private fun key(s: Source) = s.url + "|" + s.title
    }
}

private const val SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹"
fun superscript(n: Int): String = n.toString().map { SUPERSCRIPTS[it - '0'] }.joinToString("")

private val MARKS = Regex("[$SUPERSCRIPTS]+(,[$SUPERSCRIPTS]+)*")

/**
 * What TalkBack reads for a line: without its footnote marks (the Sources rows carry the sources),
 * and "→" said as "to" ("Precise location to Select business partners").
 */
fun spoken(text: String): String = text.replace(MARKS, "").replace(" → ", " to ").replace("→ ", "to ")

/** In page order: Summary's notes and latest change, Where it goes and its countries, What you can do, On the record. */
fun footnotes(e: Explanation, check: WhatYouCanDo): Footnotes = Footnotes(
    e.summaryNotes.flatMap { it.sources } +
        e.changes.firstOrNull()?.sources.orEmpty() +
        BUCKETS.flatMap { b -> e.flows[b].orEmpty().flatMap { it.sources } } +
        e.governments.blocks.flatMap { block -> block.companies.flatMap { it.sources } + block.lines.flatMap { it.sources } } +
        check.items.flatMap { it.sources } +
        (e.onTheRecord.ongoing + e.onTheRecord.past + e.onTheRecord.alsoReported).flatMap { it.sources } +
        e.changes.flatMap { it.sources },
)

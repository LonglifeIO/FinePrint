package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Source

/*
 * A source's Open button goes to the quoted words (docs/METHOD.md, Status badges): its address with a text fragment,
 * "#:~:text=" and the quote (WICG, URL Fragment Text Directives). A browser that supports it scrolls to the words and
 * highlights them; any other ignores the fragment. Left out for a PDF, and for the pages a person captures by hand
 * (Google Play's), whose copies FinePrint quotes from a capture rather than the live page.
 */

/** The address a source's Open button hands the browser. */
fun sourceLink(s: Source): String = if (s.quote.isNotBlank() && takesTextFragment(s.url)) withTextFragment(s.url, s.quote) else s.url

/** Pages only a person captures, never a script (pipeline/README.md, "Quote checks, and pages saved by hand"). */
private val HAND_CAPTURED = setOf("play.google.com")

/** An http(s) page that isn't a PDF or a page captured by hand. */
internal fun takesTextFragment(url: String): Boolean {
    val scheme = url.substringBefore("://").lowercase()
    if (scheme != "http" && scheme != "https") return false
    val rest = url.substringAfter("://").substringBefore('#').substringBefore('?')
    val host = rest.substringBefore('/').substringAfter('@').substringBefore(':').lowercase()
    return !rest.substringAfter('/', "").lowercase().endsWith(".pdf") && host !in HAND_CAPTURED
}

/** "[…]", "…" or "...": a gap in a quote, which the page doesn't have. */
private val GAP = Regex("""\s*\[?(…|\.\.\.)]?\s*""")

/**
 * [url] with "#:~:text=" and [quote], or ":~:text=" after a fragment the address already has. A quote with a gap becomes
 * a range, from the words before the first gap to the words after the last ("text=start,end"), which a page shows
 * whole.
 */
fun withTextFragment(url: String, quote: String): String {
    val parts = quote.split(GAP).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return url
    val text = if (parts.size == 1) term(parts.single()) else "${term(parts.first())},${term(parts.last())}"
    return url + (if ('#' in url) ":~:" else "#:~:") + "text=" + text
}

/**
 * One term of a text directive: runs of white space as one space, then percent-encoded as UTF-8. Letters, digits and
 * . _ ~ stay as they are; everything else is escaped, the directive's own syntax (- , &) included, as the spec requires.
 */
internal fun term(text: String): String = buildString {
    for (byte in text.trim().replace(Regex("""\s+"""), " ").toByteArray(Charsets.UTF_8)) {
        val c = byte.toInt() and 0xff
        if (c < 0x80 && (c.toChar().isLetterOrDigit() || c.toChar() in "._~")) append(c.toChar()) else append("%%%02X".format(c))
    }
}

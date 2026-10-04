package com.longlifeio.fineprint.explain

/** One block of METHOD.md: a heading (level 1 or 2), a paragraph, or a bullet. */
data class MethodBlock(val kind: Kind, val text: String) {
    enum class Kind { TITLE, HEADING, PARAGRAPH, BULLET }
}

/** The small Markdown subset METHOD.md uses: "# ", "## ", "- " and paragraphs. */
fun parseMethod(markdown: String): List<MethodBlock> {
    val blocks = ArrayList<MethodBlock>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotEmpty()) blocks += MethodBlock(MethodBlock.Kind.PARAGRAPH, paragraph.toString())
        paragraph.clear()
    }
    for (raw in markdown.lines()) {
        val line = raw.trimEnd()
        when {
            line.startsWith("# ") -> { flush(); blocks += MethodBlock(MethodBlock.Kind.TITLE, line.removePrefix("# ")) }
            line.startsWith("## ") -> { flush(); blocks += MethodBlock(MethodBlock.Kind.HEADING, line.removePrefix("## ")) }
            line.startsWith("- ") -> { flush(); blocks += MethodBlock(MethodBlock.Kind.BULLET, line.removePrefix("- ")) }
            line.isBlank() -> flush()
            else -> paragraph.append(if (paragraph.isEmpty()) line else " $line")
        }
    }
    flush()
    return blocks
}

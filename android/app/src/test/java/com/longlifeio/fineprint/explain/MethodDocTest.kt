package com.longlifeio.fineprint.explain

import org.junit.Assert.assertEquals
import org.junit.Test
import com.longlifeio.fineprint.ui.CHANGES_OF_OWNERSHIP
import com.longlifeio.fineprint.ui.CURRENT_STATUS
import com.longlifeio.fineprint.ui.WHO_IT_BINDS
import java.io.File

/** docs/METHOD.md is the published method; the app must say exactly the same things. */
class MethodDocTest {

    private val published = File("../../docs/METHOD.md").readText()
    private val shipped = File("src/main/assets/METHOD.md").readText()

    @Test
    fun theAppShipsTheMethodWordForWord() {
        assertEquals("android/app/src/main/assets/METHOD.md must be a copy of docs/METHOD.md", published, shipped)
    }

    @Test
    fun everyDefinitionTheScreensShowIsInTheMethod() {
        val sections = listOf(SUMMARY_CURATED, SUMMARY_AUTO, SUMMARY_INHERITED, COLLECTS, WHERE_IT_GOES, APPLIES, WHAT_YOU_CAN_DO, ON_THE_RECORD, ONGOING, PAST, ALSO_REPORTED, DEVICE_ACCESS, EVIDENCE, RECENT_CHANGES, HISTORY, JURISDICTIONS, SOURCES, THEIR_WORDS, THE_FINE_PRINT)
        val shown = sections.flatMap { listOf(it.title, it.subtitle) } +
            BUCKET_TEXT.values.flatMap { listOf(it.title, it.subtitle) } +
            BADGES.values.flatMap { listOf(it.label, it.definition) } +
            DIRECTIONS.values.flatMap { listOf(it.label, it.definition) } +
            GOVERNMENT_LINES.values.flatMap { listOf(it.label, it.definition) } + listOf(UNPLACED, NONE_PLACED, NO_LAWS_REVIEWED, WHO_IT_BINDS, CURRENT_STATUS, CHANGES_OF_OWNERSHIP) +
            DEFAULTS.values.flatMap { listOf(it.label, it.definition) } + listOf(regionCaveat("TikTok", "United States")) +
            listOf(SYSTEM.label, SYSTEM.definition, OTHER_PREINSTALLED, OTHER_PREINSTALLED_NOTE, groupHeader("Google", 14)) +
            listOf(fromPolicy("Google"), fromPolicyForAll("Google"), noRecordFrom("Google")) +
            Tier.entries.flatMap { listOf(it.label, it.definition) } +
            listOf(NO_RECORD, NO_RECORD_DEFINITION, STALE_DEFINITION, REVIEWED, REVIEWED_DEFINITION, CHANGED, CHANGED_DEFINITION, LIMITED_DEFINITION) +
            listOf(NOT_RECORDED.title, NOT_RECORDED.subtitle, ALSO_COLLECTED, PURPOSE_NOT_RECORDED, CANT_SEE_SETTING) +
            listOf(CHECKED_BY, THEIR_WORDS_ONLY_LINE, NO_RECORD_LINE, ListFilter.CHECKED.label, ListFilter.THEIR_WORDS.label) +
            listOf(ASK_FOR_REVIEW, REVIEW_DISCLOSURE, OPEN_GITHUB) +
            listOf("$WHY:", whyItIs(Tier.FLAGGED), whyItIs(Tier.CAUTION), ALSO, lastChecked("this law", "2026-10-07"), "Show it in full") + BUCKETS.map { "${BUCKET_TEXT.getValue(it).title} · ${BUCKET_GLOSS.getValue(it)}" } +
            listOf(CHECK_ANDROID_OFF, CHECK_ANDROID_ON, CHECK_IN_APP, CHECK_ANDROID_UNSEEN)
        assertEquals(emptyList<String>(), shown.filterNot { it in published })
    }

    @Test
    fun theMethodParsesIntoTheBlocksTheScreenShows() {
        val blocks = parseMethod(shipped)
        assertEquals(MethodBlock(MethodBlock.Kind.TITLE, "How to read FinePrint"), blocks.first())
        val headings = blocks.filter { it.kind == MethodBlock.Kind.HEADING }.map { it.text }
        assertEquals(
            listOf("What FinePrint is, and isn't", "An app's page, section by section", "The home", "Where data goes", "Status badges", "Tiers",
                "What you can do", "Your Reviewed marks", "Apps that came with your phone", "How records are made", "Changes to a record", "Governments", "Reporting an error", "Voice", "Before each release", "Licences"),
            headings,
        )
        // Nothing is lost: every word of the file appears in some block.
        val words = { s: String -> s.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in setOf("#", "##", "-") } }
        assertEquals(words(shipped), blocks.flatMap { words(it.text) })
    }
}

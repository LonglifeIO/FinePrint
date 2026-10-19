package com.longlifeio.fineprint.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/** The section that opens with the page, under the Summary: What you can do. */
const val OPENS_WITH_THE_PAGE = "todo"

/** The hero, Their words and the fine print: closed when the page opens, like the sections below the Summary. */
const val THEIR_WORDS_CARD = "their-words"

/**
 * Which of an app's page's sections are open, below the Summary (docs/METHOD.md, An app's page). What you can do starts
 * open; each other starts closed, a row with its name and one-line summary; tapping one opens or closes it in place.
 * [flipped] are the sections set the other way. Kept for the visit (a rotation included), never across visits or
 * launches: unlike the home's sections and the buckets' See all, nothing is saved.
 */
class OpenCards(private val byDefault: Boolean = false, flipped: Collection<String> = emptyList()) {
    private val flipped = mutableStateListOf<String>().apply { addAll(flipped) }

    fun isOpen(card: String): Boolean = (card in flipped) != (byDefault || card == OPENS_WITH_THE_PAGE)

    fun toggle(card: String) {
        if (!flipped.remove(card)) flipped += card
    }

    companion object {
        /** Every section open: for tests and screenshots of what a section holds. */
        fun allOpen() = OpenCards(byDefault = true)

        val Saver = listSaver<OpenCards, String>(
            save = { listOf(it.byDefault.toString()) + it.flipped },
            restore = { OpenCards(it.first().toBoolean(), it.drop(1)) },
        )
    }
}

/** What you can do open, the rest closed, when the page opens. */
@Composable
fun rememberOpenCards(): OpenCards = rememberSaveable(saver = OpenCards.Saver) { OpenCards() }

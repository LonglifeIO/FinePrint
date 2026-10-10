package com.longlifeio.fineprint.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Which of an app's page's sections are open, below the fine print (docs/METHOD.md, An app's page). Each starts
 * closed, a row with its name and one-line summary; tapping it opens it in place. Kept for the visit (a rotation
 * included), never across visits or launches: unlike the home's sections and the buckets' See all, nothing is saved.
 */
class OpenCards(private val byDefault: Boolean = false, flipped: Collection<String> = emptyList()) {
    private val flipped = mutableStateListOf<String>().apply { addAll(flipped) }

    fun isOpen(card: String): Boolean = (card in flipped) != byDefault

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

/** All closed when the page opens. */
@Composable
fun rememberOpenCards(): OpenCards = rememberSaveable(saver = OpenCards.Saver) { OpenCards() }

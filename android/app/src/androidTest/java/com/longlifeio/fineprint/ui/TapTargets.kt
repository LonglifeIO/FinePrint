package com.longlifeio.fineprint.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp

/** Scrolls the list from top to bottom, collecting every interactive element under 48dp. */
fun ComposeTestRule.smallTargetsWhileScrolling(tag: String): List<String> {
    waitForIdle()
    val small = linkedSetOf<String>()
    var last = ""
    repeat(80) {
        val nodes = onAllNodes(interactive, useUnmergedTree = true).fetchSemanticsNodes()
        nodes.filter { it.layoutInfo.isPlaced }.forEach { node ->
            val (w, h) = with(node.layoutInfo.density) { node.size.width.toDp() to node.size.height.toDp() }
            if (w < TOUCH - 0.5.dp || h < TOUCH - 0.5.dp) small += "${describe(node)}: ${w.value.toInt()}×${h.value.toInt()}dp"
        }
        val seen = nodes.joinToString("|") { describe(it) + it.boundsInRoot.top }
        if (seen == last) return small.toList()
        last = seen
        onNodeWithTag(tag).performTouchInput { swipeUp() }
        waitForIdle()
    }
    return small.toList()
}

private val interactive = hasClickAction() or SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)

private fun describe(node: SemanticsNode): String {
    val c = node.config
    val words = c.getOrNull(SemanticsProperties.ContentDescription) ?: c.getOrNull(SemanticsProperties.Text)?.map { it.text }
    val own = words?.joinToString(" ")
    val children = node.children.flatMap { child -> child.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() }
    return (own ?: children.joinToString(" ")).ifBlank { "node ${node.id}" }.take(60)
}

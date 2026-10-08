package com.longlifeio.fineprint.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

// TalkBack's order in a Robolectric test, as Compose hands it to Android: once a screen reader is on, Compose
// links every node to the one read after it (in traversalBefore, and in this extra for tests). Reading that
// chain from its first node gives the order a swipe-right walk takes; live TalkBack is a release-time check.
private const val READ_BEFORE = "android.view.accessibility.extra.EXTRA_DATA_TEST_TRAVERSALBEFORE_VAL"

/** Call before setContent: Compose works the order out only while a screen reader is running. */
fun pretendAScreenReaderIsOn() {
    val manager = shadowOf(ApplicationProvider.getApplicationContext<Context>().getSystemService(AccessibilityManager::class.java))
    manager.setEnabledAccessibilityServiceList(listOf(AccessibilityServiceInfo()))
    manager.setEnabled(true)
    manager.setTouchExplorationEnabled(true)
}

/** What a walk through [view] reads, in order: each stop's text, or its content description when it has no text. */
fun talkBackOrder(view: View, compose: ComposeTestRule): List<String> {
    val provider = view.accessibilityNodeProvider
    val any = SemanticsMatcher("any node") { true }
    val ids = compose.onAllNodes(any, useUnmergedTree = true).fetchSemanticsNodes().map { it.id }
    val next = ids.mapNotNull { id -> provider.createAccessibilityNodeInfo(id)?.extras?.getInt(READ_BEFORE, -1)?.takeIf { it != -1 }?.let { id to it } }.toMap()
    // A stop's words come from the merged tree, as TalkBack gathers a button's from its icon.
    val stops = compose.onAllNodes(any).fetchSemanticsNodes().associateBy { it.id }
    var at: Int? = next.keys.single { it !in next.values }
    val words = mutableListOf<String>()
    while (at != null) {
        val config = stops[at]?.config
        val said = config?.getOrNull(SemanticsProperties.Text)?.joinToString(" ") ?: config?.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        said?.takeIf { it.isNotBlank() }?.let(words::add)
        at = next[at]
    }
    return words
}

package com.longlifeio.fineprint.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.longlifeio.fineprint.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Large text (the brief's 200% test): icons beside words grow with them, and package names wrap at a dot. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class LargeTextTest {

    @get:Rule val compose = createComposeRule()

    /**
     * A chip's 18sp glyph grows exactly as 18sp text does: 18dp at 100%, then larger at 150% and again
     * at 200% (Android scales large font sizes along a curve, so not simply 27dp and 36dp).
     */
    @Test
    fun iconsBesideWordsGrowWithTheFontSize() {
        var scale by mutableFloatStateOf(1f)
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                Box(Modifier.testTag("icon")) { TextIcon(R.drawable.ic_check, 18.sp, Color.Black) }
            }
        }
        val widths = listOf(1f, 1.5f, 2f).map { s ->
            scale = s
            compose.waitForIdle()
            val text = with(Density(density, s)) { 18.sp.toDp() }
            compose.onNodeWithTag("icon").assertWidthIsEqualTo(text)
            text
        }
        assertEquals(18.dp, widths[0])
        assertTrue(widths.toString(), widths[1] > widths[0] && widths[2] > widths[1])
    }

    private fun lines(tag: String): List<String> {
        val found = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(found) }
        val layout = found.single()
        val text = layout.layoutInput.text.text
        return List(layout.lineCount) { text.substring(layout.getLineStart(it), layout.getLineEnd(it)) }
    }

    /** At 200% in a narrow column, the name wraps after a dot; without the break marks it splits a word. */
    @Test
    fun packageNamesWrapAtADot() {
        val name = "com.life360.android.safetymapd"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Column(Modifier.width(200.dp)) {
                    PackageName(name, Color.Black, Modifier.testTag("name"))
                    Text(name, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.testTag("plain"))
                }
            }
        }
        val wrapped = lines("name")
        assertTrue(wrapped.toString(), wrapped.size > 1)
        wrapped.dropLast(1).forEach { assertTrue(wrapped.toString(), it.endsWith(".​")) }
        assertEquals(name, wrapped.joinToString("").replace("​", ""))
        assertFalse(lines("plain").dropLast(1).all { it.endsWith(".") }) // the control: a word split mid-way
        // TalkBack reads the name as written.
        compose.onNodeWithContentDescription(name).assertExists()
    }
}

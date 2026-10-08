package com.longlifeio.fineprint.preview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.longlifeio.fineprint.INTRO_ITS_RECORDS
import com.longlifeio.fineprint.INTRO_WHAT_IT_READS
import com.longlifeio.fineprint.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The preview's first launch: its introduction says it shows sample apps, reads nothing on the phone and has its records built in. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class PreviewIntroTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theIntroductionSaysThePreviewReadsNothingOnThisPhone() {
        compose.onNodeWithText("This preview shows sample apps. It reads nothing on this phone.").assertIsDisplayed()
        assertEquals("This preview shows sample apps. It reads nothing on this phone.", INTRO_WHAT_IT_READS)
        assertEquals(0, compose.onAllNodesWithText("reads the code of the apps on this phone", substring = true).fetchSemanticsNodes().size)
        compose.onNodeWithText("This preview has its records built in. The app itself downloads them whole, never one app at a time.").assertIsDisplayed()
        assertEquals("This preview has its records built in. The app itself downloads them whole, never one app at a time.", INTRO_ITS_RECORDS)
        assertEquals(0, compose.onAllNodesWithText("FinePrint downloads its records whole", substring = true).fetchSemanticsNodes().size)
    }
}

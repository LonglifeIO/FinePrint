package com.longlifeio.fineprint.ui

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import com.longlifeio.fineprint.GLANCE_SWITCH
import com.longlifeio.fineprint.ThemeFrame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app itself (the device flavour) has no theme switch: nothing under At a glance's notice, and a frame that hands
 * the screens the phone's own configuration, so the theme follows the phone (here, in dark mode).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "night")
class DeviceThemeTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun theAppFollowsThePhone() {
        assertNull(GLANCE_SWITCH)
        var phone: Configuration? = null
        var framed: Configuration? = null
        var dark = false
        compose.setContent {
            phone = LocalConfiguration.current
            ThemeFrame {
                framed = LocalConfiguration.current
                FinePrintTheme { dark = MaterialTheme.colorScheme.background.luminance() < 0.5f }
            }
        }
        compose.waitForIdle()
        assertSame(phone, framed)
        assertTrue(dark)
    }
}

package com.longlifeio.fineprint.explain

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * docs/privacy.md: "FinePrint makes no other network request, ever." AndroidX's emoji support would ask Google Play
 * services for an emoji font when FinePrint starts; the merged manifest drops that step and keeps the others.
 */
@RunWith(RobolectricTestRunner::class)
class NoOtherRequestTest {

    @Test
    fun noStartupStepAsksPlayServicesForAFont() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val startup = context.packageManager.getProviderInfo(
            ComponentName(context, "androidx.startup.InitializationProvider"),
            PackageManager.GET_META_DATA,
        )
        val steps = startup.metaData.keySet()
        assertTrue(steps.toString(), "androidx.lifecycle.ProcessLifecycleInitializer" in steps)
        assertFalse(steps.toString(), "androidx.emoji2.text.EmojiCompatInitializer" in steps)
    }
}

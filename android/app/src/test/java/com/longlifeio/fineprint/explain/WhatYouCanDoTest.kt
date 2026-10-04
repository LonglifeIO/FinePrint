package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class WhatYouCanDoTest {

    private val bundle = parseBundle(File("src/test/resources/bundle-fixture.json").readText())
    private val record = bundle.apps.getValue("com.example.family")
    private val feeds = bundle.permissions.mapValues { it.value.feeds }

    private fun app(fineLocationGranted: Boolean) = InstalledApp(
        packageName = "com.example.family", label = "Example Family", versionName = "1", versionCode = 1, lastUpdateTime = 0,
        isSystem = false, hasCode = true, apkPaths = emptyList(),
        permissions = listOf(RequestedPermission("android.permission.ACCESS_FINE_LOCATION", granted = fineLocationGranted, dangerous = true)),
    )

    private fun check(fineLocationGranted: Boolean, ticked: Set<String> = emptySet()): WhatYouCanDo {
        val a = app(fineLocationGranted)
        val scan = TrackerScanResult(listOf(DetectedTracker("fp-arity", "Arity", listOf("Location"), "x")), 1, 1, 1, emptyList())
        return whatYouCanDo(a, explain(a, scan, bundle, emptyMap()), record, feeds, ticked)
    }

    @Test
    fun androidItemsTickThemselvesFromWhatAndroidReports() {
        val on = check(fineLocationGranted = true)
        assertEquals(listOf("android:android.permission.ACCESS_FINE_LOCATION", "ctl-partners"), on.items.map { it.id })
        assertEquals(listOf(true, false), on.items.map { it.automatic })
        assertEquals(false, on.items.first().ticked)
        // Two current lines go beyond the app (Partners, and Arity's alleged flow); nothing limits them yet.
        assertEquals("0 of 2 flows limited by your settings", on.summary)
        // Turning precise location off in Android limits both: both are location or driving data.
        assertEquals("2 of 2 flows limited by your settings", check(fineLocationGranted = false).summary)
    }

    @Test
    fun anInAppItemYouTickLimitsTheFlowsItNames() {
        val ticked = check(fineLocationGranted = true, ticked = setOf("ctl-partners"))
        val partners = ticked.items.single { it.id == "ctl-partners" }
        assertEquals(true, partners.ticked)
        assertEquals("Example doesn't say what changes.", partners.effect)
        assertEquals("1 of 2 flows limited by your settings", ticked.summary)
        assertNull(ticked.inAppText) // the record's structured controls replace its free text
    }

    @Test
    fun nothingToLimitWhenNothingGoesBeyondTheApp() {
        val a = app(fineLocationGranted = true).copy(packageName = "org.other")
        val crashOnly = TrackerScanResult(listOf(DetectedTracker("exodus-27", "Crashlytics", listOf("Crash reporting"), "x")), 1, 1, 1, emptyList())
        val c = whatYouCanDo(a, explain(a, crashOnly, bundle, emptyMap()), null, feeds, emptySet())
        assertEquals(emptyList<CheckItem>(), c.items)
        assertNull(c.summary)
    }
}

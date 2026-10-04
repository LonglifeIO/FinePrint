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
    fun anInferredLineCountsOnceAndOnlyWhenItAddsData() {
        val reviewed = FlowLine("device_identifiers", GOES_ELSEWHERE, "Advertising partners", "p", "self_disclosed", null, false, emptyList(), null)
        val e = explain(app(true), null, bundle, emptyMap()).copy(
            flows = mapOf(GOES_ELSEWHERE to listOf(reviewed) + deriveFlows("AdMob", listOf("Advertisement"), null) + deriveFlows("Moloco", listOf("Advertisement", "Location"), null)),
        )
        // AdMob's and Moloco's ad lines restate the reviewed ad-partner flow (device IDs) or add one new kind
        // each (activity, location): the reviewed line, then in-app activity once, then precise location once.
        assertEquals(listOf("device_identifiers", "app_activity", "precise_location"), countedFlows(e).map { it.data })
    }

    /** The real Life360 record, as the emulator shows it: precise location on, background and activity off. */
    @Test
    fun life360CountsEachFlowOnceAndMarksTheInferredLimit() {
        val real = parseBundle(File("../../bundle/bundle.json").readText())
        fun p(name: String, granted: Boolean) = RequestedPermission(name, granted, dangerous = true)
        val life360 = app(true).copy(
            packageName = "com.life360.android.safetymapd",
            permissions = listOf(
                p("android.permission.ACCESS_FINE_LOCATION", true), p("android.permission.ACCESS_BACKGROUND_LOCATION", false),
                p("android.permission.ACTIVITY_RECOGNITION", false), p("com.google.android.gms.permission.AD_ID", true),
            ),
        )
        val scan = TrackerScanResult(
            listOf(DetectedTracker("fp-arity", "Arity", listOf("Location"), "x"), DetectedTracker("exodus-312", "Google AdMob", listOf("Advertisement"), "y")),
            1, 1, 1, emptyList(),
        )
        val c = whatYouCanDo(life360, explain(life360, scan, real, emptyMap()), real.apps.getValue(life360.packageName), real.permissions.mapValues { it.value.feeds }, emptySet())
        // Six current lines from Life360's record and Arity's alleged line; AdMob's lines restate the ad-partner flows.
        assertEquals("3 of 7 flows limited by your settings", c.summary)
        val choices = c.items.single { it.id == "ctl-l360-privacy-choices" }
        assertEquals(listOf(true), choices.notes.map { it.startsWith("Life360's policy says this covers sales of precise location") })
        assertEquals(CHECK_IN_APP, choices.subtext)
        assertEquals(CHECK_ANDROID_ON, c.items.single { it.id == "android:android.permission.ACCESS_FINE_LOCATION" }.subtext)
        assertEquals(CHECK_ANDROID_OFF, c.items.single { it.id == "android:android.permission.ACCESS_BACKGROUND_LOCATION" }.subtext)
        assertEquals(CHECK_ANDROID_UNSEEN, c.items.single { it.id == "android:com.google.android.gms.permission.AD_ID" }.subtext)
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

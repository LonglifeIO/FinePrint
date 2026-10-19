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
        assertEquals("Your settings limit 0 of the 2 ways it uses or shares data.", on.summary)
        // Turning precise location off in Android limits both: both are location or driving data.
        assertEquals("Your settings limit 2 of the 2 ways it uses or shares data.", check(fineLocationGranted = false).summary)
    }

    @Test
    fun anInAppItemYouTickLimitsTheFlowsItNames() {
        val ticked = check(fineLocationGranted = true, ticked = setOf("ctl-partners"))
        val partners = ticked.items.single { it.id == "ctl-partners" }
        assertEquals(true, partners.ticked)
        assertEquals("Example doesn't say what changes.", partners.effect)
        assertEquals("Your settings limit 1 of the 2 ways it uses or shares data.", ticked.summary)
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
        // Six current lines from Life360's record, Arity's alleged line and AdMob's three from Google's own disclosure.
        assertEquals("Your settings limit 3 of the 10 ways it uses or shares data.", c.summary)
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
        // It asked for precise location, which FinePrint can check: none of what it found is fed by it.
        assertEquals("No permission FinePrint can check feeds a way Example Family uses or shares data.", c.nothing)
        val none = a.copy(permissions = listOf(RequestedPermission("android.permission.POST_NOTIFICATIONS", granted = true, dangerous = true)))
        assertEquals("Example Family asked for no permissions FinePrint can check.", whatYouCanDo(none, explain(none, crashOnly, bundle, emptyMap()), null, feeds, emptySet()).nothing)
        // With an item to tick or check, there's no such line.
        assertNull(check(fineLocationGranted = true).nothing)
    }

    /** What FinePrint reads from Android, then the choice that limits it, in Android's words; once limited, just how it's set. */
    @Test
    fun anAndroidItemShowsItsSettingBeforeAndAfter() {
        fun p(name: String, granted: Boolean) = RequestedPermission("android.permission.$name", granted, dangerous = true)
        fun setting(name: String, vararg permissions: RequestedPermission) = androidSetting("android.permission.$name", app(true).copy(permissions = permissions.toList()))
        assertEquals("Use precise location: On → Off", setting("ACCESS_FINE_LOCATION", p("ACCESS_FINE_LOCATION", true), p("ACCESS_COARSE_LOCATION", true)))
        assertEquals("Use precise location: Off", setting("ACCESS_FINE_LOCATION", p("ACCESS_FINE_LOCATION", false), p("ACCESS_COARSE_LOCATION", true)))
        assertEquals("Location: Not allowed", setting("ACCESS_FINE_LOCATION", p("ACCESS_FINE_LOCATION", false)))
        assertEquals(
            "Location: Allowed all the time → Allow only while using the app",
            setting("ACCESS_BACKGROUND_LOCATION", p("ACCESS_FINE_LOCATION", true), p("ACCESS_BACKGROUND_LOCATION", true)),
        )
        assertEquals("Location: Allowed only while in use", setting("ACCESS_BACKGROUND_LOCATION", p("ACCESS_FINE_LOCATION", true), p("ACCESS_BACKGROUND_LOCATION", false)))
        assertEquals("Location: Not allowed", setting("ACCESS_BACKGROUND_LOCATION", p("ACCESS_BACKGROUND_LOCATION", false)))
        assertEquals("Physical activity: Allowed → Don't allow", setting("ACTIVITY_RECOGNITION", p("ACTIVITY_RECOGNITION", true)))
        assertEquals("Physical activity: Not allowed", setting("ACTIVITY_RECOGNITION", p("ACTIVITY_RECOGNITION", false)))
        assertEquals("Contacts: Allowed → Don't allow", setting("READ_CONTACTS", p("READ_CONTACTS", true)))
        // The checklist's own item carries it.
        assertEquals("Use precise location: On → Off", check(fineLocationGranted = true).items.first().setting)
        assertEquals("Location: Not allowed", check(fineLocationGranted = false).items.first().setting)
    }

    /** Android lists granted permissions first; the items keep one order, so turning precise location off doesn't move it. */
    @Test
    fun anItemStaysInItsPlaceWhenItsSettingChanges() {
        val real = parseBundle(File("../../bundle/bundle.json").readText())
        fun p(name: String, granted: Boolean) = RequestedPermission(name, granted, dangerous = true)
        fun ids(fineGranted: Boolean): List<String> {
            // As the scanner lists them (PackageScanner.kt): granted before denied, then by name.
            val reported = listOf(
                p("android.permission.ACCESS_FINE_LOCATION", fineGranted), p("android.permission.ACCESS_BACKGROUND_LOCATION", false),
                p("android.permission.ACTIVITY_RECOGNITION", false),
            ).sortedWith(compareByDescending<RequestedPermission> { it.granted }.thenBy { it.name })
            val a = app(true).copy(packageName = "com.life360.android.safetymapd", permissions = reported)
            val scan = TrackerScanResult(listOf(DetectedTracker("fp-arity", "Arity", listOf("Location"), "x")), 1, 1, 1, emptyList())
            return whatYouCanDo(a, explain(a, scan, real, emptyMap()), real.apps.getValue(a.packageName), real.permissions.mapValues { it.value.feeds }, emptySet())
                .items.filter { it.automatic }.map { it.id.substringAfterLast('.') }
        }
        assertEquals(listOf("ACCESS_FINE_LOCATION", "ACCESS_BACKGROUND_LOCATION", "ACTIVITY_RECOGNITION"), ids(fineGranted = true))
        assertEquals(ids(fineGranted = true), ids(fineGranted = false))
    }

    /** An item you tick keeps the day you ticked it; one you untick, or one FinePrint checks, has none. */
    @Test
    fun anItemYouTickShowsTheDay() {
        val a = app(fineLocationGranted = true)
        val scan = TrackerScanResult(listOf(DetectedTracker("fp-arity", "Arity", listOf("Location"), "x")), 1, 1, 1, emptyList())
        val e = explain(a, scan, bundle, emptyMap())
        val days = mapOf("ctl-partners" to "2026-10-19")
        assertEquals("2026-10-19", whatYouCanDo(a, e, record, feeds, setOf("ctl-partners"), days).items.single { it.id == "ctl-partners" }.tickedOn)
        assertNull(whatYouCanDo(a, e, record, feeds, emptySet(), days).items.single { it.id == "ctl-partners" }.tickedOn)
        assertNull(whatYouCanDo(a, e, record, feeds, setOf("ctl-partners"), days).items.single { it.automatic }.tickedOn)
    }
}

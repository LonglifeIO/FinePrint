package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.parseBundle
import com.longlifeio.fineprint.egress.DetectedTracker
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.RequestedPermission
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.egress.TrackerSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ExplanationTest {

    private val bundle = parseBundle(
        File("src/test/resources/bundle-fixture.json").readText(),
        File("src/test/resources/jurisdictions-fixture.json").readText(),
    )

    private fun app(pkg: String, granted: List<String> = emptyList(), reach: List<String> = emptyList()) = InstalledApp(
        packageName = pkg, label = pkg, versionName = "1.0", versionCode = 1, lastUpdateTime = 0, isSystem = false,
        hasCode = true, apkPaths = emptyList(),
        permissions = granted.map { RequestedPermission(it, granted = true, dangerous = true) } +
            RequestedPermission("android.permission.READ_CONTACTS", granted = false, dangerous = true),
        deviceReach = reach,
    )

    private fun tracker(id: String, name: String, vararg categories: String) = DetectedTracker(id, name, categories.toList(), "x.Y")

    private fun scan(trackers: List<DetectedTracker>, referencedOnly: List<String> = emptyList()) =
        TrackerScanResult(trackers, dexFiles = 1, classes = 10, durationMs = 1, problems = emptyList(), referencedOnly = referencedOnly)

    @Test
    fun parsesTheBundle() {
        assertEquals("2026.10.04", bundle.version)
        val record = bundle.apps.getValue("com.example.family")
        assertEquals(3, record.exodusReport?.trackerCount)
        assertTrue(record.stale)
        assertEquals("Partners", record.dataFlows.single().recipientLabel)
        assertEquals("partners for their own use", record.dataFlows.single().sources.single().quote)
        assertEquals(listOf("Unit", "Parent Co"), bundle.trackers.getValue("fp-arity").ownerChain)
        assertEquals("Parent Co", bundle.companies.getValue("co-parent").name)
        // An undated page has no as_of, only the day it was read.
        val undated = record.consequences.single().sources.single()
        assertNull(undated.asOf)
        assertEquals("2026-10-02", undated.accessed)
        val note = bundle.trackers.getValue("fp-arity").dataFlows.single().proceduralNote
        assertEquals("Part dismissed; on appeal.", note?.text)
        assertEquals("https://example.org/appeal", note?.sources?.single()?.url)
    }

    @Test
    fun curatedAppJoinsItsOwnFlowsAndItsTrackersRecords() {
        val e = explain(
            app("com.example.family", granted = listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.CAMERA"), reach = listOf("autostart", "vpn_service")),
            scan(listOf(tracker("exodus-12", "AppsFlyer", "Analytics"), tracker("fp-arity", "Arity", "Location")), referencedOnly = listOf("exodus-65", "exodus-72")),
            bundle,
            emptyMap(),
        )
        assertEquals("curated", e.coverage)
        val elsewhere = e.flows.getValue("goes_elsewhere")
        assertEquals("Partners", elsewhere[0].recipient)
        val arity = elsewhere.single { it.data == "movement_and_driving" }
        assertEquals("Unit → Parent Co", arity.recipient) // owner chain from the tracker record
        assertEquals("alleged", arity.status)
        // Every source is shown, primary first, and the procedural note travels with the line.
        assertEquals(listOf("https://example.org/opinion", "https://example.org/petition"), arity.sources.map { it.url })
        assertEquals("Part dismissed; on appeal.", arity.proceduralNote?.text)
        // AppsFlyer has no tracker record: derived from its Exodus category, marked auto (no status).
        assertNull(e.flows.getValue("stays_here").single { it.recipient == "AppsFlyer" }.status)
        // Only granted permissions that feed a shown data kind; CAMERA feeds nothing shown here.
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION"), e.applies.map { it.permission })
        assertEquals(listOf("autostart"), e.reach.map { it.id }) // vpn_service has no boilerplate
        assertTrue(e.stale)
        assertEquals("Exodus lists 3 trackers; 2 are adapter references with no code in this app.", e.exodusNote)
        // What it collects: plain labels from the flows, then from granted permissions; no permission names.
        assertEquals(
            listOf("Precise location", "What you do in the app", "Driving behaviour and movement", "Camera"),
            e.collects,
        )
        // The reported breach goes under "Also reported"; nothing alleged or adjudicated is in the app's own record.
        assertEquals(listOf("Emails leaked."), e.onTheRecord.alsoReported.map { it.details.first() })
        assertTrue(e.onTheRecord.actions.isEmpty())
        assertEquals(Tier.FLAGGED, e.tier.tier)
        assertEquals("Example Family says your location goes to other companies", e.tier.reason)
    }

    @Test
    fun changesComeNewestFirstWithTheDirectionBuildDerived() {
        val e = explain(app("com.example.family"), scan(emptyList()), bundle, emptyMap())
        // Recent changes shows the first (a worsening, with its tier move); History lists both.
        assertEquals(listOf("2026-01-01" to "worsened", "2025-11-03" to "improved"), e.changes.map { it.date to it.direction })
        assertEquals("Tier: Caution → Flagged", tierMove(e.changes.first().tierBefore, e.changes.first().tierAfter))
        assertNull(tierMove(e.changes.last().tierBefore, e.changes.last().tierAfter))
        assertEquals(listOf("Worsened", "Improved"), e.changes.map { DIRECTIONS.getValue(it.direction).label })
    }

    @Test
    fun inferredLinesWithTheSameDataAndPurposeShowAsOneAfterTheReviewedOnes() {
        val reviewed = FlowLine("precise_location", GOES_ELSEWHERE, "Partners", "p", "self_disclosed", null, false, emptyList(), null)
        val shown = forDisplay(
            deriveFlows("AdMob", listOf("Advertisement"), null) + reviewed + deriveFlows("Facebook Ads", listOf("Advertisement"), null),
        )
        assertEquals(listOf("Partners", "AdMob, Facebook Ads", "AdMob, Facebook Ads"), shown.map { it.recipient })
        assertEquals(listOf("precise_location", "device_identifiers", "app_activity"), shown.map { it.data })
    }

    @Test
    fun allegedLinesAlwaysSayNotProvenInCourt() {
        assertEquals("Alleged by a state (not proven in court)", attribution("alleged", "Alleged by a state"))
        assertEquals("Alleged; not proven in court.", attribution("alleged", "Alleged; not proven in court."))
        assertEquals("Alleged (not proven in court)", attribution("alleged", null))
        assertEquals("According to its policy.", attribution("self_disclosed", "According to its policy."))
    }

    @Test
    fun anAppWithNoRecordShowsWhatTheScanFound() {
        assertEquals(TierResult(null, "Checking its code…", "N"), explain(app("com.example.other"), null, bundle, emptyMap()).tier)
        val unreadable = TrackerScanResult(emptyList(), dexFiles = 0, classes = 0, durationMs = 1, problems = listOf("x"))
        assertEquals("Couldn't read its code · 1 permission", explain(app("com.example.other"), unreadable, bundle, emptyMap()).tier.reason)
        assertEquals("No third-party trackers found · 1 permission", explain(app("com.example.other"), scan(emptyList()), bundle, emptyMap()).tier.reason)
    }

    @Test
    fun appWithoutARecordRendersTheAutoView() {
        val signatures = mapOf("exodus-312" to TrackerSignature("exodus-312", "Google AdMob", "x", listOf("Advertisement")))
        val e = explain(app("com.example.other"), scan(listOf(tracker("exodus-312", "Google AdMob", "Advertisement"))), bundle, signatures)
        assertEquals("auto", e.coverage)
        assertTrue(e.summary.contains("Google AdMob"))
        assertEquals(setOf("device_identifiers", "app_activity"), e.flows.getValue("goes_elsewhere").map { it.data }.toSet())
        assertNull(e.lastReviewed)
        assertNull(e.exodusNote)
        assertEquals(Tier.CAUTION, e.tier.tier)
        assertEquals("Google AdMob code in this app can send your advertising ID to other companies", e.tier.reason)
    }

    @Test
    fun noBundleAtAllStillExplainsFromTheScan() {
        val e = explain(app("com.example.other"), scan(listOf(tracker("exodus-27", "Crashlytics", "Crash reporting"))), null, emptyMap())
        assertEquals("crash_diagnostics", e.flows.getValue("stays_here").single().data)
        assertTrue(e.applies.isEmpty())
        // No record: never Expected, just what the scan found.
        assertEquals(TierResult(null, "1 tracker found · 1 permission", "N"), e.tier)
    }

    @Test
    fun oneRecordCoversSeveralTrackersAndTellsItsLinesOnce() {
        val found = listOf(tracker("exodus-65", "Facebook Ads", "Advertisement"), tracker("exodus-66", "Facebook Analytics", "Analytics"))
        assertEquals("fp-kit", bundle.trackers.getValue("exodus-66").id)
        val e = explain(app("com.example.other"), scan(found), bundle, emptyMap())
        // Both kits are found; the record's two lines show once each, and no line is inferred for either kit.
        assertEquals(listOf("app_activity", "device_identifiers"), e.flows.getValue(GOES_ELSEWHERE).map { it.data })
        assertTrue(e.flows.values.flatten().all { it.recipient == "Kit Co" && it.status == "self_disclosed" })
        // The record's ruling, once, about the company whose kits they are: it doesn't name this app, so it sets no tier.
        assertEquals(listOf("2025-05-05 · Regulator · ruled · about Kit"), e.onTheRecord.actions.map { it.line })
        // Credited to the company whose kits they are, not to one kit.
        assertEquals("Kit says what you do in the app goes to other companies", e.tier.reason)
    }

    @Test
    fun aTrackerMadeByTheAppsOwnDeveloperSendsNothingElsewhere() {
        val e = explain(app("com.example.kit"), scan(listOf(tracker("exodus-65", "Facebook Ads", "Advertisement"))), bundle, emptyMap())
        assertNull(e.flows[GOES_ELSEWHERE])
        // The app's own line, then the one line the tracker's record places in its owner's apps; the other is left out.
        assertEquals(listOf("Kit's own ads", "Measuring Kit's ads"), e.flows.getValue(USED_FOR_MORE).map { it.purpose })
        assertTrue(sameCompany("co-kitlabs", "co-kit", bundle)) // one owns the other
        assertTrue(!sameCompany("co-kit", "co-dev", bundle))
    }

    @Test
    fun jurisdictionsPlaceEachCompanyAndListEachCountrysLaws() {
        val e = explain(app("com.example.watched"), scan(listOf(tracker("exodus-65", "Facebook Ads", "Advertisement"))), bundle, emptyMap())
        val g = e.governments
        // Kit Co's head office is in Israel, though it's registered in the Cayman Islands; Parent Co is in the United States.
        assertEquals("Your data goes to companies based in: Israel, United States", g.line)
        assertTrue(g.unplaced) // "Partners" isn't named
        assertEquals(listOf("Canada", "Cayman Islands", "Israel", "United States"), g.blocks.map { it.name })
        val (canada, cayman, israel, us) = g.blocks
        assertEquals(listOf("Kit Co: subject to its law"), cayman.companies.map { it.text })
        assertTrue(!cayman.lawsReviewed && cayman.lines.isEmpty())
        assertEquals(listOf("Kit Co: headquartered here and subject to its law"), israel.companies.map { it.text })
        assertTrue(israel.lawsReviewed && israel.lines.isEmpty())
        assertEquals(listOf("Parent Co: headquartered here and subject to its law"), us.companies.map { it.text })
        assertEquals(listOf("can_compel" to "Test Act (1 U.S.C. § 1)", "has_bought" to "A US agency"), us.lines.map { it.kind to it.title })
        // No recipient is subject to Canada's law, so only the use on record shows there, not Canada's laws.
        assertEquals(listOf("has_used"), canada.lines.map { it.kind })
        assertTrue(canada.companies.isEmpty() && canada.lawsReviewed)
        // Government lines stay out of the buckets, What it collects, On the record and the tier.
        assertTrue(e.flows.values.flatten().none { it.recipient == "A US agency" })
        assertTrue(e.onTheRecord.alsoReported.isEmpty())
        assertEquals("Watched App says your location goes to other companies", e.tier.reason)
        assertEquals("Cayman Islands", countryName("KY"))
    }

    @Test
    fun aPreinstalledAppWithoutARecordInheritsItsMakersLines() {
        val mail = app("com.kitco.mail").copy(isSystem = true)
        val e = explain(mail, scan(emptyList()), bundle, emptyMap())
        assertEquals("Kit", e.maker?.name)
        assertTrue(e.maker!!.inherited)
        assertEquals("auto", e.coverage) // still no record of its own
        assertEquals("From Kit's privacy policy, which covers this app.", e.flows.getValue(GOES_ELSEWHERE).single().wording)
        assertEquals(listOf("Kit's privacy policy applies to all its apps."), e.summaryNotes.map { it.text })
        assertTrue(e.summary.contains("its package name starts with com.kitco."))
        // Sensitive data going elsewhere would flag a reviewed app; without a record of its own it's Caution at most.
        assertEquals(TierResult(Tier.CAUTION, "Kit says your location goes to other companies", "F1", capped = true), e.tier)
        assertEquals("Not checked yet · from Kit's policy", noRecordFrom(e.maker!!.name))
        // Its lines show before the scan has finished, too.
        assertEquals(Tier.CAUTION, explain(mail, null, bundle, emptyMap()).tier.tier)
    }

    @Test
    fun onlyAPreinstalledAppWithCodeAndNoRecordInherits() {
        // Installed later, the same package name could be a lookalike: no maker, nothing inherited.
        assertNull(explain(app("com.kitco.mail"), scan(emptyList()), bundle, emptyMap()).maker)
        assertNull(explain(app("com.kitco.overlay").copy(isSystem = true, hasCode = false), scan(emptyList()), bundle, emptyMap()).maker)
        // An app with its own record keeps it; its maker is still known, for grouping.
        val kit = explain(app("com.example.kit").copy(isSystem = true), scan(emptyList()), bundle, emptyMap())
        assertEquals("Kit", kit.maker?.name)
        assertTrue(!kit.maker!!.inherited && kit.flows.values.flatten().none { it.purpose == "Their own use" })
    }

    @Test
    fun aFlowOffByDefaultWithASettingDoesntSetTheTier() {
        val e = explain(app("com.example.optional"), scan(emptyList()), bundle, emptyMap())
        // The partner line is off unless you turn its setting on, so the opt-in ads line sets the tier, not Flagged.
        assertEquals(TierResult(Tier.CAUTION, "Optional App says it uses what you do in the app for more than running the app", "C1"), e.tier)
        assertEquals(listOf("off" to true), e.flows.getValue(GOES_ELSEWHERE).map { it.default to it.controlled })
        assertEquals("opt_in", e.flows.getValue(USED_FOR_MORE).single().default)
        assertEquals("This page follows Optional App's privacy policy for the United States. Where you live, a different policy may apply.", e.regionCaveat)
        assertNull(explain(app("com.example.kit"), scan(emptyList()), bundle, emptyMap()).regionCaveat)
    }

    @Test
    fun anAppWhoseRecipientsFinePrintCantPlaceSaysSo() {
        val e = explain(app("com.example.other"), scan(listOf(tracker("exodus-312", "Google AdMob", "Advertisement"))), bundle, emptyMap())
        assertEquals(null, e.governments.line)
        assertTrue(e.governments.unplaced && e.governments.blocks.isEmpty())
    }

    /** Present over past: a 2019 settlement that has ended is shown but scores nothing; a 2019 order still in force does. */
    @Test
    fun onlyOngoingOrRecentActionsSetTheTier() {
        val today = LocalDate.of(2026, 10, 4)
        val settled = explain(app("com.example.settled"), scan(emptyList()), bundle, emptyMap(), today)
        assertEquals(TierResult(Tier.EXPECTED, "Nothing found beyond running the app", "E"), settled.tier)
        assertEquals(listOf("2019-03-01 · A regulator · settled · \$1 million · closed 2019-06-01"), settled.onTheRecord.past.map { it.line })
        // The developer's order about its other app is ongoing, and marked as about the company.
        assertEquals(listOf("2019-07-24 · A regulator · consent order, in force · about Old"), settled.onTheRecord.ongoing.map { it.line })

        val ordered = explain(app("com.example.ordered"), scan(emptyList()), bundle, emptyMap(), today)
        assertEquals(TierResult(Tier.FLAGGED, "A 2019 ruling on this app's data: 2019 privacy order", "F2"), ordered.tier)
        assertEquals(listOf("2019-07-24 · A regulator · consent order, in force"), ordered.onTheRecord.ongoing.map { it.line })

        // The same settlement, had it ended within three years, would still count.
        val thenToday = LocalDate.of(2022, 5, 31)
        assertEquals(Tier.FLAGGED, explain(app("com.example.settled"), scan(emptyList()), bundle, emptyMap(), thenToday).tier.tier)
    }

    @Test
    fun firstPartyAdvertisingIsUsedForMoreNotElsewhere() {
        val flows = deriveFlows("Meta", listOf("Advertisement"), "first_party")
        assertTrue(flows.all { it.bucket == USED_FOR_MORE })
    }

    @Test
    fun exodusNoteOnlyWhenExodusCountsMore() {
        val s = scan(listOf(tracker("exodus-12", "A")), referencedOnly = listOf("exodus-65"))
        assertNull(exodusNote(1, listOf("exodus-12"), s))
        assertEquals("Exodus lists 2 trackers; 1 is an adapter reference with no code in this app.", exodusNote(2, listOf("exodus-12", "exodus-65"), s))
        assertEquals("Exodus may list up to 2; 1 is an adapter reference with no code in this app.", exodusNote(null, emptyList(), s))
    }
}

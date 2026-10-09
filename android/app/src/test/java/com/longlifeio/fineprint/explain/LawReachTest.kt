package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.parseBundle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The laws no app can show yet, read from the real bundle. A country's laws show under Jurisdictions only when a
 * company that gets an app's data is based there (Governments.kt), so a law of a country where no such company is
 * based shows on no app. Pinned, so a records change that moves this list (a new country's laws with no company
 * based there, or the first company based in one) shows on its pull request, in CI's rebuilt-bundle run.
 */
class LawReachTest {

    private val bundle = parseBundle(File("../../bundle/bundle.json").readText(), File("../../bundle/jurisdictions.json").readText())

    @Test
    fun theLawsNoAppCanShowYet() {
        // Every company a counted line can name: the recipients of the records' flows, leaving out a government's, past
        // ones and conditional ones (governments() reads only these), and companies with no country on record.
        val flows = bundle.apps.values.flatMap { it.dataFlows } + bundle.trackers.values.distinct().flatMap { it.dataFlows } +
            bundle.companies.values.flatMap { it.defaultFlows }
        val recipients = flows.filter { it.government == null && it.conditional == null && !it.historical }
            .mapNotNull { f -> f.recipient?.let { bundle.companies[it] } }.filter { it.jurisdiction != null }
        val places = recipients.flatMap { listOfNotNull(it.jurisdiction, it.headquarters) }.distinct()
        val shown = places.flatMap { lawsBinding(it, bundle) }.map { it.id }.toSet()
        assertEquals(
            "Canada's, China's, Russia's and Singapore's: no company that gets data is based there",
            listOf("law-ca-criminal-code-487-014", "law-ca-criminal-code-487-016", "law-cn-national-intelligence", "law-ru-149-fz-10-1", "law-sg-cpc-20"),
            bundle.jurisdictions.values.flatMap { it.laws }.map { it.id }.filterNot { it in shown }.sorted(),
        )
    }
}

package com.longlifeio.fineprint.bundle

/** One cited source. [asOf] is its own date; undated pages carry only [accessed], the day FinePrint read them. */
data class Source(
    val url: String,
    val title: String,
    val type: String,
    val status: String,
    val asOf: String?,
    val accessed: String?,
    val quote: String,
    val id: String? = null,
    /** Id of the source this one re-reports: such copies don't count as independent. */
    val derivesFrom: String? = null,
    /** The claim rests on one original investigation or study. */
    val singleSource: Boolean = false,
)

/** Where a legal matter stands (a dismissal, an appeal), sourced separately; never changes the status. */
data class ProceduralNote(val text: String, val sources: List<Source>)

/** One "what goes where" line. [status] is null only for flows FinePrint derived itself ("auto"). */
data class DataFlow(
    /** Set when a control names this flow ("flow-…"). */
    val id: String?,
    val data: String,
    val recipient: String?,
    val recipientLabel: String?,
    val purpose: String,
    val bucket: String,
    val status: String?,
    val wording: String?,
    val historical: Boolean,
    val proceduralNote: ProceduralNote?,
    /** The first source is the primary one. */
    val sources: List<Source>,
    /** Tracker flows: the bucket inside an app the tracker's owner made; null leaves a goes-elsewhere flow out there. */
    val inOwnerApps: String? = null,
)

data class Consequence(
    val text: String,
    val status: String,
    val wording: String?,
    val historical: Boolean,
    val proceduralNote: ProceduralNote?,
    val sources: List<Source>,
    /** filed, survived_motion_to_dismiss, dismissed (alleged); ruling, settlement, ... (adjudicated). */
    val statusKind: String? = null,
    /** Who the action is against, when that isn't the app's developer. */
    val subjectCompany: String? = null,
    /** The package whose data the action concerns. */
    val concernsApp: String? = null,
    val appealPending: Boolean = false,
)

/** A company's regulatory or legal history entry; [date] may be year and month only ("2024-03"). */
data class LegalEvent(
    val date: String,
    val title: String,
    /** The court or regulator. */
    val body: String?,
    val type: String,
    val amount: String?,
    val status: String,
    val statusKind: String?,
    val subjectCompany: String?,
    val concernsApp: String?,
    val notes: String?,
    val proceduralNote: ProceduralNote?,
    val sources: List<Source>,
    val appealPending: Boolean = false,
)

data class ExodusReport(val id: Int, val appVersion: String, val created: String, val trackerCount: Int)

/** A flow a control limits; [inferred] when the sources don't say so in as many words, with a [note] saying what is inferred. */
data class Limit(val flow: String, val inferred: Boolean = false, val note: String? = null)

/**
 * An in-app setting that limits some of the app's flows (by flow id); the user ticks it when done.
 * [effect] is what turning it off changes, in the app's own quoted words, or that the app doesn't say.
 */
data class Control(val id: String, val label: String, val how: String, val effect: String, val limits: List<Limit>, val sources: List<Source>)

/** A short sourced line shown under the summary, such as what the app's policy says it doesn't do. */
data class SummaryNote(val text: String, val status: String, val wording: String?, val sources: List<Source>)

data class AppRecord(
    /** SHA-256 of the record's JSON (without the build-computed stale flag): changes only when the record does. */
    val hash: String,
    val packageId: String,
    val displayName: String,
    val developerCompany: String?,
    val summary: String,
    val summaryNotes: List<SummaryNote>,
    val trackers: List<String>,
    val exodusReport: ExodusReport?,
    val dataFlows: List<DataFlow>,
    val consequences: List<Consequence>,
    val privacyControls: String?,
    val riskTags: List<String>,
    /** Qualifier shown with a tag, e.g. regulatory_action -> "against Allstate/Arity concerning this app's data". */
    val riskTagNotes: Map<String, String>,
    val controls: List<Control>,
    val stale: Boolean,
    val lastReviewed: String,
)

/** A tracker explanation, found under its own id and under every tracker id it [covers] (Meta's kits). */
data class TrackerRecord(
    val hash: String,
    val id: String,
    val owner: String,
    val ownerCompany: String?,
    val covers: List<String>,
    val ownerChain: List<String>,
    val party: String?,
    val categories: List<String>,
    val dataFlows: List<DataFlow>,
    val consequences: List<Consequence>,
    val lastReviewed: String,
)

data class Company(
    val hash: String,
    val id: String,
    val name: String,
    /** For running text ("Allstate"); defaults to [name]. */
    val shortName: String?,
    val subsidiaries: List<String>,
    /** The company that owns this one, when the record names it. */
    val parent: String? = null,
    val events: List<LegalEvent> = emptyList(),
)

data class PermissionText(val id: String, val plain: String, val whyItMatters: String, val feeds: List<String>)

data class ReachText(val id: String, val plain: String, val whyItMatters: String)

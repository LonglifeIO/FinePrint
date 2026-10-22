package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.GuidedPath
import com.longlifeio.fineprint.bundle.VerifiedOn

/*
 * A guided path's words (docs/METHOD.md, What you can do): the button under an item you tick, each card's count, and the
 * last card's button and line. The steps themselves are the help page's words, from the record.
 */

const val SHOW_ME_HOW = "Show me how"
const val HIDE_THE_STEPS = "Hide the steps"
const val STEP_BACK = "Back"
const val STEP_NEXT = "Next"

/** Beside the tick box under the last card, before you've ticked it; after, the day you did ([tickedOnLine]). */
const val TICK_ONCE_CHANGED = "Tick this once you've changed it."

/** "Step 2 of 4": each card's count, read with its heading. */
fun stepCount(step: Int, of: Int) = "Step $step of $of"

/** The last card's button: "Open Life360" for the app itself, or the setting's name for a web page. */
fun openThe(name: String) = "Open $name"

/** The heading of the help page's quotes, on their Sources sheet: "How to reach Your Privacy Choices". */
fun howToReach(label: String) = "How to reach $label"

/** What TalkBack reads for a card's wireframe: the row to tap, in the help page's words. */
fun tapThe(label: String) = "Tap $label"

/**
 * Where the path comes from, on the last card. Walked in the app: "Verified for Life360 26.37.0, 2026-10-22."; once the
 * walk is more than 90 days before the bundle (Stale): "Last checked 2026-10-22; the app may have changed." Not walked:
 * "From Life360's help page, dated 2026-05-11. Not yet checked in the app.", never Stale, since the page's date is the
 * company's and the watcher re-reads the page.
 */
fun pathLine(appName: String, path: GuidedPath, verified: VerifiedOn?): String = when {
    verified != null && path.stale -> "Last checked ${verified.date}; the app may have changed."
    verified != null -> "Verified for $appName ${verified.appVersion}, ${verified.date}."
    else -> "From ${possessive(appName)} help page${path.helpDate?.let { ", dated $it" }.orEmpty()}. Not yet checked in the app."
}

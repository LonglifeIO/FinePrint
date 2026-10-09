package com.longlifeio.fineprint.explain

import java.net.URLEncoder

/*
 * Asking FinePrint to check an app (docs/METHOD.md, An app's page): on a page that reads Their words only or Not
 * checked yet, a button opens GitHub's new-issue form in the browser, its fields filled in from the address. FinePrint
 * itself sends nothing; the browser does, only when the person submits the form.
 */

const val ASK_FOR_REVIEW = "Ask FinePrint to check this app"
const val REVIEW_DISCLOSURE = "This opens GitHub in your browser with the app's name and package in the address. FinePrint itself " +
    "sends nothing. Nothing is posted until you submit it there, and the post is public under your GitHub account."
const val OPEN_GITHUB = "Open GitHub"

/** About's one sentence on the request. */
const val ABOUT_REVIEW_REQUESTS = "On an app FinePrint hasn't checked, $ASK_FOR_REVIEW opens GitHub's form in your browser with the " +
    "app's name and package filled in; FinePrint itself sends nothing."

/** Pages FinePrint hasn't checked in full: the only ones that offer the request. */
val Explanation.asksForReview: Boolean get() = coverageState != Coverage.CHECKED

/**
 * GitHub's new-issue page with the review-request form, its title and fields filled in by query parameter: a form
 * field takes the parameter named by its id. No labels parameter: anyone who can't label issues would get a 404, so
 * the form applies the review-request label itself.
 */
fun reviewRequestUrl(appName: String, packageName: String): String =
    "https://github.com/LonglifeIO/FinePrint/issues/new?" + listOf(
        "template" to "review-request.yml", "title" to "Review request: $appName", "app_name" to appName, "package" to packageName,
    ).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }

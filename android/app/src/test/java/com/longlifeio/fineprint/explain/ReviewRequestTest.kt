package com.longlifeio.fineprint.explain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The review request's address (docs/METHOD.md, An app's page): GitHub's form, its title and text fields filled in. */
class ReviewRequestTest {

    @Test
    fun theAddressFillsTheFormByItsFieldIds() {
        val url = reviewRequestUrl("Lean for Instapaper", "com.olivierpayen.leaninstapaper")
        assertEquals(
            "https://github.com/LonglifeIO/FinePrint/issues/new?template=review-request.yml&title=Review+request%3A+Lean+for+Instapaper" +
                "&app_name=Lean+for+Instapaper&package=com.olivierpayen.leaninstapaper",
            url,
        )
        // No labels parameter: anyone who can't label issues would get a 404; the form applies the label itself.
        assertFalse(url.contains("labels="))
        assertEquals("Review+request%3A+Caf%C3%A9+%26+Co", reviewRequestUrl("Café & Co", "x").substringAfter("title=").substringBefore("&"))
    }
}

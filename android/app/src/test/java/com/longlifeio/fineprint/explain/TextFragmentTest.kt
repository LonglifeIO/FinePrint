package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.Source
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A source's Open button goes to the quoted words with a text fragment (WICG, URL Fragment Text Directives): each term
 * percent-encoded as UTF-8, the directive's own syntax (- , &) escaped inside it; a PDF, a page captured by hand and a
 * source with no quote open as they are.
 */
class TextFragmentTest {

    private fun source(url: String, quote: String) = Source(url, "A page", "company_site", "self_disclosed", "2026-10-01", null, quote)

    @Test
    fun commasHyphensAndAmpersandsInsideTheQuoteAreEscaped() {
        assertEquals(
            "https://example.com/privacy#:~:text=Adjust%20shares%20all%20metrics%2C%20with%20third%2Dparty%20partners%20%26%20advertisers",
            withTextFragment("https://example.com/privacy", "Adjust shares all metrics, with third-party partners & advertisers"),
        )
    }

    @Test
    fun apostrophesAndCurlyQuotesArePercentEncodedAsUtf8() {
        assertEquals("you%27ve%20configured", term("you've configured"))
        assertEquals("%E2%80%9Cwhether%20or%20not%20you%E2%80%99re%20logged%20in%E2%80%9D", term("“whether or not you’re logged in”"))
        // Letters, digits and . _ ~ stay; runs of white space read as one space.
        assertEquals("v2.40.3_beta~1%20is%20out", term("  v2.40.3_beta~1 \n is   out "))
    }

    @Test
    fun aFragmentTheAddressAlreadyHasKeepsItsPlace() {
        assertEquals("https://example.com/docs#partners:~:text=all%20metrics", withTextFragment("https://example.com/docs#partners", "all metrics"))
    }

    @Test
    fun aQuoteWithAGapBecomesARangeFromTheWordsBeforeItToTheWordsAfter() {
        assertEquals(
            "https://ftc.gov/order#:~:text=collected%20outside%20the%20United%20States,conducted%20abroad",
            withTextFragment("https://ftc.gov/order", "collected outside the United States … conducted abroad"),
        )
        assertEquals("https://e.com/a#:~:text=first%20part,last%20part", withTextFragment("https://e.com/a", "first part [...] middle ... last part"))
    }

    @Test
    fun pdfsPagesCapturedByHandAndOtherSchemesOpenAsTheyAre() {
        val quote = "the exact words"
        assertEquals("https://www.ftc.gov/system/files/order.PDF", sourceLink(source("https://www.ftc.gov/system/files/order.PDF", quote)))
        assertEquals("https://example.com/report.pdf?download=1", sourceLink(source("https://example.com/report.pdf?download=1", quote)))
        assertEquals("https://play.google.com/store/apps/details?id=com.life360.android.safetymapd", sourceLink(source("https://play.google.com/store/apps/details?id=com.life360.android.safetymapd", quote)))
        assertEquals("mailto:privacy@example.com", sourceLink(source("mailto:privacy@example.com", quote)))
        assertEquals("https://example.com/policy", sourceLink(source("https://example.com/policy", " ")))
        // A PDF's address can carry a query; the path decides.
        assertEquals("https://example.com/pdf-guide#:~:text=the%20exact%20words", sourceLink(source("https://example.com/pdf-guide", quote)))
        assertEquals("http://example.com/p#:~:text=the%20exact%20words", sourceLink(source("http://example.com/p", quote)))
    }
}

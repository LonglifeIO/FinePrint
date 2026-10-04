package com.longlifeio.fineprint.egress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.regex.Pattern
import kotlin.random.Random

class SignatureMatcherTest {

    private val trackers = bundledSignatures.trackers
    private val matcher = SignatureMatcher(trackers)

    /** exodus-core's rule, literally: re.search(code_signature, name) for signatures over 3 chars. */
    private val reference = trackers.mapIndexed { i, t ->
        i to (if (t.codeSignature.length > 3) Pattern.compile(t.codeSignature) else null)
    }

    private fun expected(name: String): Set<Int> =
        reference.filter { (_, p) -> p != null && p.matcher(name).find() }.map { it.first }.toSet()

    private fun actual(name: String): Set<Int> =
        matcher.newMatches(name, BooleanArray(trackers.size))?.toSet() ?: emptySet()

    private fun ids(name: String) = actual(name).map { trackers[it].id }.toSet()

    @Test
    fun knownTrackers() {
        assertEquals(setOf("exodus-12"), ids("com/appsflyer/AppsFlyerLib"))
        assertEquals(setOf("exodus-49"), ids("com/google/firebase/analytics/FirebaseAnalytics"))
        assertEquals(setOf("exodus-66"), ids("com/facebook/appevents/AppEventsLogger"))
        assertEquals(setOf("exodus-13"), ids("de/foo/LigatusManager")) // leading-wildcard alternative
        assertEquals(emptySet<String>(), ids("com/example/app/MainActivity"))
    }

    @Test
    fun trailingWildcardNeedsAnotherCharacterLikeExodus() {
        // "com.adobe.mobile.Config." needs one more character after "Config".
        assertEquals(emptySet<String>(), ids("com/adobe/mobile/Config"))
        assertEquals(setOf("exodus-40"), ids("com/adobe/mobile/ConfigSynchronizer"))
    }

    @Test
    fun reportsEachTrackerOnce() {
        val found = BooleanArray(trackers.size)
        assertEquals(1, matcher.newMatches("com/appsflyer/A", found)?.size)
        assertNull(matcher.newMatches("com/appsflyer/B", found))
    }

    @Test
    fun agreesWithRegexOnNamesBuiltFromEverySignature() {
        val random = Random(42)
        val fillers = "/aZ0_x"
        var checked = 0
        for (t in trackers) {
            if (t.codeSignature.length <= 3) continue
            for (alt in t.codeSignature.split('|')) {
                repeat(6) { variant ->
                    val core = alt.map { if (it == '.') fillers[random.nextInt(fillers.length)] else it }.joinToString("")
                    val mutated = if (variant % 2 == 1 && core.isNotEmpty()) {
                        val at = random.nextInt(core.length)
                        core.substring(0, at) + "q" + core.substring(at + 1)
                    } else {
                        core
                    }
                    val name = listOf("", "com/", "x")[variant % 3] + mutated + listOf("", "/Foo", "\$1", "B")[variant % 4]
                    assertEquals("name: $name", expected(name), actual(name))
                    checked++
                }
            }
        }
        check(checked > 3_000) { "only $checked names checked" }
    }

    @Test
    fun agreesWithRegexOnRealClassNames() {
        for (descriptor in classpathDescriptors()) {
            for (name in exodusNames(descriptor)) assertEquals("name: $name", expected(name), actual(name))
        }
    }

    @Test
    fun agreesWithRegexOnRandomNames() {
        val alphabet = "comgleafbkpsyrtdi/._ACFGS0"
        val random = Random(7)
        repeat(20_000) {
            val name = String(CharArray(random.nextInt(1, 40)) { alphabet[random.nextInt(alphabet.length)] })
            assertEquals("name: $name", expected(name), actual(name))
        }
    }

    @Test
    fun regexFallbackForUnusualSignatures() {
        val custom = listOf(
            TrackerSignature("fp-test", "Test", """com\.example\.(ads|track)er""", emptyList()),
            TrackerSignature("fp-short", "Short", "abc", emptyList()), // too short: ignored, like Exodus
        )
        val m = SignatureMatcher(custom)
        assertEquals(listOf(0), m.newMatches("com.example.tracker", BooleanArray(2)))
        assertNull(m.newMatches("com/example/tracker", BooleanArray(2)))
        assertNull(m.newMatches("abc", BooleanArray(2)))
    }
}

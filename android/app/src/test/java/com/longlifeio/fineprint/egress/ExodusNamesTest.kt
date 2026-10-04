package com.longlifeio.fineprint.egress

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.regex.Pattern
import kotlin.random.Random

class ExodusNamesTest {

    /** exodus-core's extraction, verbatim (Java's ASCII \w equals Python's on ASCII input). */
    private val exodusRegex = Pattern.compile("""[A-Z]+((?:\w+/)+\w+)""")

    private fun reference(s: String): List<String> {
        val m = exodusRegex.matcher(s)
        val out = ArrayList<String>()
        while (m.find()) out += m.group(1)
        return out
    }

    @Test
    fun typicalDescriptors() {
        assertEquals(listOf("com/appsflyer/AppsFlyerLib"), exodusNames("Lcom/appsflyer/AppsFlyerLib;"))
        assertEquals(listOf("com/foo/Bar"), exodusNames("Lcom/foo/Bar\$Inner;"))
        assertEquals(listOf("o/a"), exodusNames("Lo/a;"))
        assertEquals(emptyList<String>(), exodusNames("LMain;")) // root package: Exodus sees nothing
    }

    @Test
    fun pythonBacktrackingQuirks() {
        assertEquals(listOf("B/cd"), exodusNames("LAB/cd;"))
        assertEquals(listOf("c/d"), exodusNames("LABc/d;"))
        assertEquals(listOf("A/b"), exodusNames("LA/b;"))
        assertEquals(emptyList<String>(), exodusNames("L/b;"))
        assertEquals(listOf("a/b"), exodusNames("La/b//c;"))
        assertEquals(listOf("a/b"), exodusNames("La/b/;"))
    }

    @Test
    fun matchesTheRegexOnRandomInput() {
        val alphabet = "ABLZabz09_/\$;-"
        val random = Random(20261002)
        repeat(50_000) {
            val s = String(CharArray(random.nextInt(0, 24)) { alphabet[random.nextInt(alphabet.length)] })
            assertEquals("input: $s", reference(s), exodusNames(s))
        }
    }

    @Test
    fun matchesTheRegexOnRealClassNames() {
        // Every class on the test classpath (Kotlin stdlib, dexlib2, Guava, JUnit, org.json...).
        var checked = 0
        for (descriptor in classpathDescriptors()) {
            assertEquals(descriptor, reference(descriptor), exodusNames(descriptor))
            checked++
        }
        check(checked > 5_000) { "only $checked classes found on the test classpath" }
    }
}

/** "L<name>;" descriptors for every class in the jars on the test runtime classpath. */
fun classpathDescriptors(): Sequence<String> =
    checkNotNull(System.getProperty("java.class.path")).split(java.io.File.pathSeparator)
        .asSequence()
        .filter { it.endsWith(".jar") }
        .flatMap { jar ->
            java.util.zip.ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".class") && !it.startsWith("META-INF/") }
                    .map { "L" + it.removeSuffix(".class") + ";" }
                    .toList()
            }
        }

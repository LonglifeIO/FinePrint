package com.longlifeio.fineprint.explain

import com.longlifeio.fineprint.bundle.BUNDLE_FILE
import com.longlifeio.fineprint.bundle.JURISDICTIONS_FILE
import com.longlifeio.fineprint.bundle.TRACKERS_FILE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * docs/privacy.md says what the build on main does; these are the facts in it that the build files decide. Change the
 * build and the page together.
 */
class PrivacyPageTest {

    private val page = File("../../docs/privacy.md").readText()
    private fun permissions(manifest: String) =
        Regex("""<uses-permission\s+android:name="android\.permission\.(\w+)"(?![^>]*tools:node="remove")""").findAll(File(manifest).readText()).map { it.groupValues[1] }.toList()

    @Test
    fun itNamesTheTestBuildsBundleAddressAndItsThreeFiles() {
        val release = Regex("""release \{\s*buildConfigField\("String", "BUNDLE_BASE_URL", "\\"([^\\]+)\\""""").find(File("build.gradle.kts").readText())!!.groupValues[1]
        assertTrue(release, "`$release`" in page)
        listOf(BUNDLE_FILE, TRACKERS_FILE, JURISDICTIONS_FILE).forEach { assertTrue(it, "`$it`" in page) }
    }

    @Test
    fun itNamesEveryPermissionTheAppAndTheDebugBuildAskFor() {
        assertEquals(listOf("QUERY_ALL_PACKAGES", "INTERNET"), permissions("src/main/AndroidManifest.xml"))
        assertEquals(listOf("ACCESS_LOCAL_NETWORK"), permissions("src/deviceDebug/AndroidManifest.xml"))
        assertEquals("the preview removes both", emptyList<String>(), permissions("src/preview/AndroidManifest.xml"))
        listOf("QUERY_ALL_PACKAGES", "INTERNET", "ACCESS_LOCAL_NETWORK", "DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION").forEach { assertTrue(it, "`$it`" in page) }
    }

    @Test
    fun backupIsOffAsThePageSaysAndThePageIsDated() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("""android:allowBackup="false"""" in manifest && """android:fullBackupContent="false"""" in manifest)
        val rules = File("src/main/res/xml/data_extraction_rules.xml").readText()
        listOf("cloud-backup", "device-transfer").forEach { part ->
            val excluded = Regex("""<$part>([\s\S]*?)</$part>""").find(rules)!!.groupValues[1]
            listOf("root", "file", "database", "sharedpref", "external").forEach { assertTrue("$part excludes $it", """domain="$it"""" in excluded) }
        }
        assertTrue(page.lines()[2], Regex("""Last changed: \d{4}-\d{2}-\d{2}\.""").matches(page.lines()[2]))
    }
}

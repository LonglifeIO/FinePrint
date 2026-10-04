package com.longlifeio.fineprint.egress

import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.dexbacked.raw.ClassDefItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipException
import java.util.zip.ZipFile

class DexTrackerScannerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val matcher = SignatureMatcher(bundledSignatures.trackers)

    @Test
    fun findsTrackersAcrossDexFilesAndSplits() {
        val base = writeApk(
            tmp.newFile("base.apk"),
            mapOf(
                "AndroidManifest.xml" to ByteArray(16),
                "classes.dex" to dexDefining("Lcom/example/Main;", "Lcom/appsflyer/AppsFlyerLib;"),
                "classes2.dex" to dexDefining("Lcom/google/firebase/analytics/FirebaseAnalytics;"),
            ),
        )
        val split = writeApk(
            tmp.newFile("split_feature.apk"),
            mapOf("classes.dex" to dexDefining("Lio/branch/referral/Branch;")),
        )

        val result = scanForTrackers(listOf(base, split), matcher)

        assertEquals(listOf("exodus-12", "exodus-167", "exodus-49"), result.trackers.map { it.id })
        assertEquals("com.appsflyer.AppsFlyerLib", result.trackers.first { it.id == "exodus-12" }.matchedClass)
        assertEquals(3, result.dexFiles)
        assertEquals(4, result.classes)
        assertEquals(emptyList<String>(), result.problems)
    }

    @Test
    fun readsDexEntriesWhereverExodusDoes() {
        val apk = writeApk(
            tmp.newFile("base.apk"),
            mapOf(
                "classes.dex" to dexDefining("Lcom/example/Main;"),
                "assets/plugin/classes.dex" to dexDefining("Lcom/kochava/base/Tracker;"),
                "assets/notes-classes.dex.txt" to "not a dex file".toByteArray(), // name matches; skipped quietly
            ),
        )

        val result = scanForTrackers(listOf(apk), matcher)

        assertEquals(listOf("exodus-127"), result.trackers.map { it.id })
        assertEquals(2, result.dexFiles)
        assertEquals(emptyList<String>(), result.problems)
    }

    @Test
    fun aTrackerTypeThatIsOnlyReferencedDoesNotCount() {
        // exodus-core would report AppsFlyer here (it reads the superclass from dexdump output),
        // but the SDK's code is not in the APK, so it is not contains_code.
        val apk = writeApk(
            tmp.newFile("base.apk"),
            mapOf("classes.dex" to dexDefining("Lcom/example/Main;:Lcom/appsflyer/AppsFlyerLib;")),
        )
        val result = scanForTrackers(listOf(apk), matcher)
        assertEquals(emptyList<DetectedTracker>(), result.trackers)
        assertEquals(listOf("exodus-12"), result.referencedOnly) // what Exodus would add
    }

    @Test
    fun innerClassesFollowExodusNaming() {
        // Exodus names "Lcom/adobe/mobile/Config$1;" as "com/adobe/mobile/Config", which does not
        // satisfy "com.adobe.mobile.Config." (one more character needed).
        val apk = writeApk(
            tmp.newFile("base.apk"),
            mapOf("classes.dex" to dexDefining("Lcom/adobe/mobile/Config\$1;", "LMain;")),
        )
        assertEquals(emptyList<DetectedTracker>(), scanForTrackers(listOf(apk), matcher).trackers)
    }

    @Test
    fun findsArityWithFinePrintsOwnSignature() {
        for (arityClass in listOf(
            "Lcom/arity/coreengine/driving/CoreEngineManager;", // Life360 26.x
            "Lcom/arity/coreEngine/driving/DrivingEngineService;", // Life360 19.x to 23.x
        )) {
            val apk = writeApk(tmp.newFile(), mapOf("classes.dex" to dexDefining(arityClass)))
            assertEquals(arityClass, listOf("fp-arity"), scanForTrackers(listOf(apk), matcher).trackers.map { it.id })
        }
        val unrelated = writeApk(
            tmp.newFile(),
            mapOf(
                "classes.dex" to dexDefining(
                    "Lorg/javia/arity/Calculator;", // namespaces that merely contain "arity"
                    "Lcom/microsoft/clarity/Clarity;",
                    "Lcom/arity/arityhrmpro/MainActivity;", // an unrelated developer's com.arity app
                ),
            ),
        )
        assertEquals(emptyList<DetectedTracker>(), scanForTrackers(listOf(unrelated), matcher).trackers)
    }

    @Test
    fun readsApksWhoseOddEntriesMakeZipFileGiveUp() {
        val dex = dexDefining("Lcom/appsflyer/AppsFlyerLib;")
        val deflated = zipBytes(mapOf("classes.dex" to dex, "assets/blob.bin" to ByteArray(64) { it.toByte() }))
        val stored = zipBytes(mapOf("classes.dex" to dex, "assets/blob.bin" to ByteArray(64)), stored = setOf("classes.dex"))
        val variants = mapOf(
            "encrypted flag on another entry" to patchZipEntry(deflated, "assets/blob.bin", setFlags = 1),
            "unknown compression method on another entry" to patchZipEntry(deflated, "assets/blob.bin", method = 99),
            "encrypted flag on the dex entry itself" to patchZipEntry(deflated, "classes.dex", setFlags = 1),
            "stored dex, odd neighbour" to patchZipEntry(stored, "assets/blob.bin", setFlags = 1),
            "entry comment that is not UTF-8" to corruptEntryComment(
                zipBytes(mapOf("classes.dex" to dex, "assets/blob.bin" to ByteArray(8)), comments = mapOf("assets/blob.bin" to "x")),
                "assets/blob.bin",
                0xFF.toByte(),
            ),
        )
        for ((label, bytes) in variants) {
            val file = tmp.newFile().apply { writeBytes(bytes) }
            assertThrows(label, ZipException::class.java) { ZipFile(file).close() } // as on Android
            val result = scanForTrackers(listOf(file.path), matcher)
            assertEquals(label, listOf("exodus-12"), result.trackers.map { it.id })
            assertEquals(label, emptyList<String>(), result.problems)
        }
    }

    @Test
    fun aHostileClassNameLengthIsSkippedRatherThanAllocated() {
        val dex = dexDefining("Lcom/appsflyer/AppsFlyerLib;", "Lcom/example/Huge;")
        // Point "Lcom/example/Huge;" at string data claiming about two billion characters, written
        // into the header's SHA-1 field, which dexlib2 does not check.
        val parsed = DexBackedDexFile(null, dex)
        val index = (0 until parsed.stringSection.size).first { parsed.stringSection[it] == "Lcom/example/Huge;" }
        put32(dex, parsed.stringSection.getOffset(index), 12)
        byteArrayOf(0xF0.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x07).copyInto(dex, 12)
        val apk = writeApk(tmp.newFile("base.apk"), mapOf("classes.dex" to dex))

        val result = scanForTrackers(listOf(apk), matcher)

        assertEquals(listOf("exodus-12"), result.trackers.map { it.id })
        assertEquals(1, result.classes)
        assertEquals(listOf("base.apk!classes.dex: skipped 1 malformed class names"), result.problems)
        assertFalse(result.outOfMemory)
    }

    @Test
    fun aClassTableThatRepeatsOneNameIsReadOnce() {
        val classes = (0 until 2000).map { "Lcom/example/C$it;" } + "Lcom/appsflyer/AppsFlyerLib;"
        val dex = dexDefining(*classes.toTypedArray())
        val parsed = DexBackedDexFile(null, dex)
        val target = (0 until parsed.typeSection.size).first { parsed.typeSection[it] == "Lcom/appsflyer/AppsFlyerLib;" }
        for (i in 0 until parsed.classSection.size) {
            put32(dex, parsed.classSection.getOffset(i) + ClassDefItem.CLASS_OFFSET, target)
        }
        val apk = writeApk(tmp.newFile("base.apk"), mapOf("classes.dex" to dex))

        val result = scanForTrackers(listOf(apk), matcher)

        assertEquals(listOf("exodus-12"), result.trackers.map { it.id })
        assertEquals(1, result.classes) // 2,001 class_defs, one distinct name
        assertEquals(emptyList<String>(), result.problems)
    }

    @Test
    fun aClassTableRunningPastTheFileIsReportedNotRead() {
        val bad = dexDefining("Lcom/example/A;").also { put32(it, 96, 0x00FFFFFF) } // header class_defs_size
        val apk = writeApk(
            tmp.newFile("base.apk"),
            mapOf("classes.dex" to bad, "classes2.dex" to dexDefining("Lcom/appsflyer/AppsFlyerLib;")),
        )

        val result = scanForTrackers(listOf(apk), matcher)

        assertEquals(listOf("exodus-12"), result.trackers.map { it.id })
        assertEquals(listOf("base.apk!classes.dex: class table runs past the end of the dex"), result.problems)
    }

    @Test
    fun dexEntriesWithLineBreaksInTheirNamesAreReadLikeExodusDoes() {
        // Python's '.' in exodus-core's classes.*\.dex matches '\r'; Java's would not.
        val apk = writeApk(tmp.newFile("base.apk"), mapOf("classes\r2.dex" to dexDefining("Lcom/appsflyer/AppsFlyerLib;")))
        assertEquals(listOf("exodus-12"), scanForTrackers(listOf(apk), matcher).trackers.map { it.id })
    }

    @Test
    fun reportsUnreadableInputsAndKeepsGoing() {
        val corrupt = byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte(),
            '0'.code.toByte(), '3'.code.toByte(), '5'.code.toByte(), 0) + ByteArray(64)
        val apk = writeApk(
            tmp.newFile("base.apk"),
            mapOf(
                "classes.dex" to corrupt,
                "classes2.dex" to dexDefining("Lcom/appsflyer/AppsFlyerLib;"),
            ),
        )
        val missing = tmp.root.resolve("gone.apk").path

        val result = scanForTrackers(listOf(apk, missing), matcher)

        assertEquals(listOf("exodus-12"), result.trackers.map { it.id })
        assertEquals(2, result.problems.size)
        assertTrue(result.problems[0], result.problems[0].startsWith("base.apk!classes.dex: "))
        assertTrue(result.problems[1], result.problems[1].startsWith("gone.apk: "))
    }
}

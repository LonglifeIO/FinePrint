package com.longlifeio.fineprint.egress

import com.android.tools.smali.dexlib2.DexFileFactory
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Not a test: runs the app's scanner over real APKs on the Mac, for checking results against
 * Exodus reports and for finding the package names of SDKs Exodus lacks (G0/G1 work).
 *
 *   FINEPRINT_APKS=/path/life360,/path/other.apk FINEPRINT_GREP=arity \
 *     ./gradlew :app:testDebugUnitTest --tests '*ApkScanHarness*' --rerun
 *
 * Each entry is an APK, or a directory holding one app's base.apk and splits. The report is
 * written to app/build/reports/apk-scan.txt. Skipped when FINEPRINT_APKS is not set.
 */
class ApkScanHarness {

    @Test
    fun scanApksFromEnvironment() {
        val spec = System.getenv("FINEPRINT_APKS")
        assumeTrue("set FINEPRINT_APKS to run", !spec.isNullOrBlank())
        val grep = System.getenv("FINEPRINT_GREP")?.takeIf { it.isNotBlank() }?.let { Regex(it, RegexOption.IGNORE_CASE) }
        val matcher = SignatureMatcher(bundledSignatures.trackers)
        val report = StringBuilder("Signatures fetched ${bundledSignatures.fetchedAt}\n")

        for (target in spec!!.split(',').map { File(it.trim()) }) {
            val apks = if (target.isDirectory) {
                target.listFiles { f -> f.name.endsWith(".apk") }.orEmpty()
                    .sortedWith(compareBy({ it.name != "base.apk" }, { it.name })).map { it.path }
            } else {
                listOf(target.path)
            }
            val result = scanForTrackers(apks, matcher)
            report.appendLine("\n== ${target.name} (${apks.size} APKs): ${result.trackers.size} trackers, " +
                "${result.dexFiles} dex, ${result.classes} classes, ${result.durationMs} ms")
            result.trackers.forEach { report.appendLine("   ${it.id.padEnd(12)} ${it.name}   <- ${it.matchedClass}") }
            result.problems.forEach { report.appendLine("   problem: $it") }

            // exodus-core also matches types an app only references; show what that adds.
            val found = BooleanArray(matcher.trackers.size)
            result.trackers.forEach { t -> found[matcher.trackers.indexOfFirst { it.id == t.id }] = true }
            val packages = sortedMapOf<String, Int>()
            forEachDex(apks) { dex ->
                val types = dex.typeSection
                for (i in 0 until types.size) {
                    for (name in exodusNames(types[i])) {
                        matcher.newMatches(name, found)?.forEach { report.appendLine("   referenced only: " +
                            "${matcher.trackers[it].id} ${matcher.trackers[it].name}   <- $name") }
                    }
                }
                if (grep != null) {
                    for (classDef in dex.classes) {
                        val pkg = classDef.type.removePrefix("L").substringBeforeLast('/', "").replace('/', '.')
                        if (grep.containsMatchIn(pkg)) packages.merge(pkg, 1, Int::plus)
                    }
                }
            }
            packages.forEach { (pkg, n) -> report.appendLine("   package matching /${grep!!.pattern}/: $pkg ($n classes)") }
        }

        val out = File("build/reports/apk-scan.txt")
        out.parentFile?.mkdirs()
        out.writeText(report.toString())
        println(report)
    }

    private fun forEachDex(apks: List<String>, action: (com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile) -> Unit) {
        for (apk in apks) {
            val container = runCatching { DexFileFactory.loadDexContainer(File(apk), null) }.getOrNull() ?: continue
            for (entry in container.dexEntryNames) {
                runCatching { container.getEntry(entry)?.dexFile }.getOrNull()?.let(action)
            }
        }
    }
}

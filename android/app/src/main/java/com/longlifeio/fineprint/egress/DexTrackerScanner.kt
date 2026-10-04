package com.longlifeio.fineprint.egress

import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.dexbacked.raw.ClassDefItem
import java.io.File
import java.io.IOException
import java.util.BitSet

/**
 * A tracker whose code ships inside the app. This is evidence tier `contains_code` only: it says
 * the SDK is present, not that it ran or sent anything (`observed_contact`).
 */
data class DetectedTracker(
    val id: String,
    val name: String,
    val categories: List<String>,
    /** The first class (Java name) that matched the signature: the evidence for the claim. */
    val matchedClass: String,
)

data class TrackerScanResult(
    val trackers: List<DetectedTracker>,
    val dexFiles: Int,
    val classes: Int,
    val durationMs: Long,
    /** APK or dex entries that could not be read. Detection is incomplete when this is not empty. */
    val problems: List<String>,
    /** Some entry did not fit in memory; worth retrying when fewer scans run at once. */
    val outOfMemory: Boolean = false,
    /**
     * Ids of Exodus trackers whose types the code only references (e.g. ad-mediation adapters) with no
     * code of their own here. exodus-core counts these; FinePrint does not.
     */
    val referencedOnly: List<String> = emptyList(),
)

/** Default cap on one dex entry held in memory. Real dex files stay under about 32 MB. */
const val DEFAULT_MAX_DEX_BYTES = 64L shl 20

/** Longer "class names" are malformed or hostile: dexlib2 allocates the declared length up front. */
private const val MAX_DESCRIPTOR_CHARS = 8192

/** Most class_defs one app's scan visits. Google Play services, about the largest app, has ~210,000. */
private const val MAX_CLASS_VISITS = 10_000_000

/** Most dex files read from one DEX v41 container entry. */
private const val MAX_DEX_PER_CONTAINER = 256

/**
 * Looks for tracker code in an app's APK files (base.apk and any splits): reads each dex entry,
 * lists the classes it defines with dexlib2, and matches them the way Exodus does.
 *
 * Unlike exodus-core, which also matches types an app merely references (in method signatures,
 * fields, catch clauses), only classes actually defined in the APK count here. That is what
 * `contains_code` claims. Nested APKs inside an APK, which exodus-core also opens, are not read.
 */
fun scanForTrackers(
    apkPaths: List<String>,
    matcher: SignatureMatcher,
    maxDexBytes: Long = DEFAULT_MAX_DEX_BYTES,
): TrackerScanResult {
    val started = System.nanoTime()
    val found = BooleanArray(matcher.trackers.size)
    val referenced = BooleanArray(matcher.trackers.size) // matched by any type the dex mentions
    val detected = LinkedHashMap<String, DetectedTracker>() // by id: the UI keys rows by it
    val problems = ArrayList<String>()
    var dexFiles = 0
    var classes = 0
    var outOfMemory = false
    val budget = Budget(MAX_CLASS_VISITS)
    apks@ for (path in apkPaths) {
        val apk = File(path).name
        val archive = try {
            openApk(path)
        } catch (e: IOException) {
            problems += "$apk: ${e.describe()}"
            continue
        } catch (e: RuntimeException) { // one malformed APK must not hide the app's other splits
            problems += "$apk: ${e.describe()}"
            continue
        }
        archive.use {
            for (name in archive.dexEntryNames) {
                if (budget.left < 0) break@apks
                try {
                    val bytes = archive.read(name, maxDexBytes)
                    if (!isDex(bytes)) continue // e.g. a "classes.dex.jar" archive, which dexlib2 can't read
                    val seen = BitSet(bytes.size) // string data already matched in this entry
                    val seenRefs = BitSet(bytes.size)
                    var headerOffset = 0
                    var dexInEntry = 0
                    while (true) { // a DEX v41 container holds several dex files back to back
                        if (++dexInEntry > MAX_DEX_PER_CONTAINER) {
                            problems += "$apk!$name: read only the first $MAX_DEX_PER_CONTAINER dex files"
                            break
                        }
                        val dex = ContainerDexFile(bytes, headerOffset)
                        dexFiles++
                        val malformed = forEachDefinedClass(dex, bytes, seen, budget) { descriptor ->
                            classes++
                            for (exodusName in exodusNames(descriptor)) {
                                matcher.newMatches(exodusName, found)?.forEach { index ->
                                    val t = matcher.trackers[index]
                                    detected.putIfAbsent(t.id, DetectedTracker(t.id, t.name, t.categories, javaName(descriptor)))
                                }
                            }
                        }
                        forEachReferencedType(dex, bytes, seenRefs, budget) { descriptor ->
                            for (exodusName in exodusNames(descriptor)) matcher.newMatches(exodusName, referenced)
                        }
                        if (malformed > 0) problems += "$apk!$name: skipped $malformed malformed class names"
                        if (dex.isDexContainerLastEntry || budget.left < 0) break
                        headerOffset += dex.fileSize
                    }
                } catch (e: IOException) {
                    problems += "$apk!$name: ${e.describe()}"
                } catch (e: RuntimeException) { // dexlib2's NotADexFile, InvalidFile, UnsupportedFile, ...
                    problems += "$apk!$name: ${e.describe()}"
                } catch (e: OutOfMemoryError) { // keep what earlier entries found
                    outOfMemory = true
                    problems += "$apk!$name: not enough memory to read it"
                }
            }
        }
    }
    if (budget.left < 0) problems += "stopped at the limit of $MAX_CLASS_VISITS classes per app"
    return TrackerScanResult(
        trackers = detected.values.sortedBy { it.name.lowercase() },
        dexFiles = dexFiles,
        classes = classes,
        durationMs = (System.nanoTime() - started) / 1_000_000,
        problems = problems,
        outOfMemory = outOfMemory,
        referencedOnly = matcher.trackers.indices
            .filter { referenced[it] && !found[it] && matcher.trackers[it].id.startsWith("exodus-") }
            .map { matcher.trackers[it].id },
    )
}

/** Class visits left for one app's scan; shared across its APKs and dex entries. */
private class Budget(var left: Int)

/**
 * Calls [action] once per distinct descriptor of the classes [dex] defines, and returns how many
 * were skipped as malformed. Reads class_def -> type_id -> string_data directly so the declared
 * length can be checked before dexlib2 allocates a char array of that size (and pins it in a
 * ThreadLocal). [seen] skips descriptors already handled, which defeats tables that repeat one
 * long name; ART rejects duplicate classes, so nothing real is lost.
 */
private inline fun forEachDefinedClass(
    dex: DexBackedDexFile,
    bytes: ByteArray,
    seen: BitSet,
    budget: Budget,
    action: (String) -> Unit,
): Int {
    var malformed = 0
    val classSection = dex.classSection
    val count = classSection.size
    if (count > 0 && classSection.getOffset(0).toLong() + count.toLong() * ClassDefItem.ITEM_SIZE > bytes.size) {
        throw IOException("class table runs past the end of the dex")
    }
    for (i in 0 until count) {
        if (--budget.left < 0) break
        val typeIndex = dex.buffer.readSmallUint(classSection.getOffset(i) + ClassDefItem.CLASS_OFFSET)
        when (val d = descriptorOf(dex, bytes, typeIndex, seen)) {
            null -> {}
            MALFORMED -> malformed++
            else -> action(d)
        }
    }
    return malformed
}

/**
 * Calls [action] once per distinct type the dex mentions anywhere (its type_ids table), a superset
 * of what exodus-core reads from dexdump. Only used to say how Exodus's count would differ.
 */
private inline fun forEachReferencedType(
    dex: DexBackedDexFile,
    bytes: ByteArray,
    seen: BitSet,
    budget: Budget,
    action: (String) -> Unit,
) {
    for (typeIndex in 0 until dex.typeSection.size) {
        if (--budget.left < 0) break
        val d = descriptorOf(dex, bytes, typeIndex, seen)
        if (d != null && d !== MALFORMED) action(d)
    }
}

private const val MALFORMED = "\u0000malformed"

/**
 * The descriptor of type [typeIndex], or null if [seen] already has it, or [MALFORMED]. Checks the
 * declared length before dexlib2 allocates a char array of that size (and pins it in a ThreadLocal).
 */
private fun descriptorOf(dex: DexBackedDexFile, bytes: ByteArray, typeIndex: Int, seen: BitSet): String? {
    val stringIndex = dex.buffer.readSmallUint(dex.typeSection.getOffset(typeIndex))
    val dataOffset = dex.buffer.readSmallUint(dex.stringSection.getOffset(stringIndex))
    if (dataOffset < bytes.size && seen[dataOffset]) return null
    val reader = dex.dataBuffer.readerAt(dataOffset)
    val chars = reader.readSmallUleb128()
    // Each UTF-16 unit takes at least one byte of MUTF-8, so a longer claim cannot be real.
    if (chars > MAX_DESCRIPTOR_CHARS || chars > bytes.size - reader.offset) return MALFORMED
    seen.set(dataOffset)
    return dex.stringSection[stringIndex]
}

/** Reaches dexlib2's container-aware constructor, the way its own ZipDexContainer walks entries. */
private class ContainerDexFile(buf: ByteArray, headerOffset: Int) :
    DexBackedDexFile(null, buf, 0, true, headerOffset)

/** Dex magic: "dex\n" followed by a three-digit version and a NUL. */
private fun isDex(bytes: ByteArray): Boolean = bytes.size >= 8 &&
    bytes[0] == 'd'.code.toByte() && bytes[1] == 'e'.code.toByte() && bytes[2] == 'x'.code.toByte() &&
    bytes[3] == '\n'.code.toByte() && bytes[7] == 0.toByte()

/** "Lcom/foo/Bar$Inner;" -> "com.foo.Bar$Inner" */
private fun javaName(descriptor: String): String =
    descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')

private fun Throwable.describe(): String = message ?: javaClass.simpleName

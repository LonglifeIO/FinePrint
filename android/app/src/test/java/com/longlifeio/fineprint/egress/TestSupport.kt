package com.longlifeio.fineprint.egress

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The signature list the app ships (unit tests run with the module directory as working dir). */
val bundledSignatures: TrackerSignatures by lazy {
    parseTrackerSignatures(File("src/main/assets/$TRACKERS_ASSET").readText())
}

/** A real dex file, written by dexlib2, defining empty classes. Each is "descriptor" or "descriptor:superclass". */
fun dexDefining(vararg classes: String): ByteArray {
    val defs = classes.map {
        val type = it.substringBefore(':')
        val superclass = it.substringAfter(':', "Ljava/lang/Object;")
        ImmutableClassDef(type, AccessFlags.PUBLIC.value, superclass, null, null, null, null, null)
    }
    val store = MemoryDataStore()
    DexPool.writeTo(store, ImmutableDexFile(Opcodes.getDefault(), defs))
    return store.data
}

/** Zip bytes with the given entries; names in [stored] are written uncompressed. */
fun zipBytes(
    entries: Map<String, ByteArray>,
    stored: Set<String> = emptySet(),
    comments: Map<String, String> = emptyMap(),
): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, bytes) in entries) {
            val entry = ZipEntry(name)
            comments[name]?.let { entry.comment = it }
            if (name in stored) {
                entry.method = ZipEntry.STORED
                entry.size = bytes.size.toLong()
                entry.crc = CRC32().apply { update(bytes) }.value
            }
            zip.putNextEntry(entry)
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/** Writes a zip (an APK, as far as the scanner cares) with the given entries. */
fun writeApk(file: File, entries: Map<String, ByteArray>): String {
    file.writeBytes(zipBytes(entries))
    return file.path
}

/** Sets general-purpose flag bits and/or the compression method of one entry, in both zip headers. */
fun patchZipEntry(zip: ByteArray, name: String, setFlags: Int = 0, method: Int? = null): ByteArray {
    val out = zip.copyOf()
    val nameBytes = name.toByteArray()
    var patched = 0
    for (i in 0 until out.size - 4) {
        // (offset of name length, of name, of flags, of method) for local and central headers
        val layout = when (u32(out, i)) {
            0x04034b50L -> intArrayOf(i + 26, i + 30, i + 6, i + 8)
            0x02014b50L -> intArrayOf(i + 28, i + 46, i + 8, i + 10)
            else -> continue
        }
        val (nameLengthAt, nameAt, flagsAt, methodAt) = layout.toList()
        if (nameAt + nameBytes.size > out.size || u16(out, nameLengthAt) != nameBytes.size) continue
        if (!out.copyOfRange(nameAt, nameAt + nameBytes.size).contentEquals(nameBytes)) continue
        put16(out, flagsAt, u16(out, flagsAt) or setFlags)
        if (method != null) put16(out, methodAt, method)
        patched++
    }
    check(patched == 2) { "patched $patched headers for $name" }
    return out
}

/** Overwrites the first byte of one entry's central-directory comment (see [zipBytes]' comments). */
fun corruptEntryComment(zip: ByteArray, name: String, byte: Byte): ByteArray {
    val out = zip.copyOf()
    val nameBytes = name.toByteArray()
    for (i in 0 until out.size - 46) {
        if (u32(out, i) != 0x02014b50L || u16(out, i + 28) != nameBytes.size) continue
        if (!out.copyOfRange(i + 46, i + 46 + nameBytes.size).contentEquals(nameBytes)) continue
        check(u16(out, i + 32) > 0) { "$name has no comment" }
        out[i + 46 + nameBytes.size + u16(out, i + 30)] = byte
        return out
    }
    error("no central directory record for $name")
}

private fun u16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

private fun u32(b: ByteArray, at: Int) = u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16)

private fun put16(b: ByteArray, at: Int, value: Int) {
    b[at] = value.toByte()
    b[at + 1] = (value shr 8).toByte()
}

fun put32(b: ByteArray, at: Int, value: Int) {
    for (k in 0 until 4) b[at + k] = (value shr (8 * k)).toByte()
}

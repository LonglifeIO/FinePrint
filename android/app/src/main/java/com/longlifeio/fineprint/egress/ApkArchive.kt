package com.longlifeio.fineprint.egress

import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** The entries of an APK that may hold dex code, read one at a time. */
internal interface ApkArchive : Closeable {
    /** Entries exodus-core would read as dex files, in archive order. */
    val dexEntryNames: List<String>

    /** The entry's uncompressed bytes; IOException if it is malformed or larger than [maxBytes]. */
    fun read(name: String, maxBytes: Long): ByteArray
}

/**
 * exodus-core reads every zip entry whose name contains a match for Python's `classes.*\.dex`.
 * Python's '.' excludes only '\n'; Java's would also refuse '\r' and other line separators.
 */
private val DEX_ENTRY = Regex("""classes[^\n]*\.dex""")

private const val MAX_CENTRAL_DIRECTORY = 32L shl 20

/**
 * Opens [path] with java.util.zip, or, when that rejects the whole archive because of one odd entry
 * (flagged encrypted, an unknown compression method, a name or comment that is not valid UTF-8),
 * with a reader as lenient as Android's own. Adware and malware use exactly those tricks to break
 * analysis tools while still installing.
 */
internal fun openApk(path: String): ApkArchive {
    val zip = try {
        ZipFile(path)
    } catch (e: ZipException) {
        return LenientApk(path)
    }
    return try {
        JdkApk(zip) // lists the entries, decoding every name and comment
    } catch (e: Exception) {
        zip.close()
        // Android 10-14 decode names and comments strictly and throw IllegalArgumentException.
        if (e is ZipException || e is RuntimeException) LenientApk(path) else throw e
    }
}

private class JdkApk(private val zip: ZipFile) : ApkArchive {
    override val dexEntryNames: List<String> = zip.entries().asSequence()
        .filter { !it.isDirectory && DEX_ENTRY.containsMatchIn(it.name) }
        .map { it.name }
        .toList()

    override fun read(name: String, maxBytes: Long): ByteArray {
        val entry = zip.getEntry(name) ?: throw IOException("entry not found")
        val size = entry.size
        // Android's ZipFile skips ZIP64 size validation, so a crafted entry can report -1.
        if (size < 0) throw IOException("dex entry of unknown size")
        if (size > maxBytes) throw IOException("${size shr 20} MB dex entry, too large to read here")
        return zip.getInputStream(entry).use { input ->
            ByteArray(size.toInt()).also { DataInputStream(input).readFully(it) }
        }
    }

    override fun close() = zip.close()
}

/**
 * Reads dex entries straight from the zip central directory, ignoring the encrypted flag, entry
 * comments and every entry it does not read. Only stored and deflated dex entries are supported;
 * ZIP64 archives are not (no APK needs it).
 */
private class LenientApk(path: String) : ApkArchive {
    private class Entry(val method: Int, val compressedSize: Long, val size: Long, val localHeader: Long)

    private val file = RandomAccessFile(path, "r")
    private val entries = LinkedHashMap<String, Entry>()

    init {
        try {
            readCentralDirectory()
        } catch (e: Throwable) {
            file.close()
            throw if (e is Exception && e !is IOException) IOException(e) else e
        }
    }

    override val dexEntryNames: List<String> get() = entries.keys.toList()

    private fun readCentralDirectory() {
        val length = file.length()
        val tail = readAt(length - minOf(length, 22L + 0xFFFF), minOf(length, 22L + 0xFFFF).toInt())
        val end = (tail.size - 22 downTo 0).firstOrNull { u32(tail, it) == 0x06054b50L }
            ?: throw IOException("not a zip archive")
        val count = u16(tail, end + 10)
        val directorySize = u32(tail, end + 12)
        val directoryOffset = u32(tail, end + 16)
        if (count == 0xFFFF || directoryOffset == 0xFFFFFFFFL) throw IOException("ZIP64 archive")
        if (directorySize > MAX_CENTRAL_DIRECTORY || directoryOffset + directorySize > length) {
            throw IOException("bad zip central directory")
        }
        val cd = readAt(directoryOffset, directorySize.toInt())
        var p = 0
        while (p + 46 <= cd.size && u32(cd, p) == 0x02014b50L) {
            val nameLength = u16(cd, p + 28)
            if (p + 46 + nameLength > cd.size) break
            // Without the UTF-8 flag Python decodes cp437; ISO-8859-1 differs only in which
            // characters bytes >= 0x80 become, never in whether DEX_ENTRY matches.
            val utf8 = u16(cd, p + 8) and 0x800 != 0
            val name = String(cd, p + 46, nameLength, if (utf8) Charsets.UTF_8 else Charsets.ISO_8859_1)
            if (!name.endsWith("/") && DEX_ENTRY.containsMatchIn(name)) {
                entries.putIfAbsent(name, Entry(u16(cd, p + 10), u32(cd, p + 20), u32(cd, p + 24), u32(cd, p + 42)))
            }
            p += 46 + nameLength + u16(cd, p + 30) + u16(cd, p + 32)
        }
    }

    override fun read(name: String, maxBytes: Long): ByteArray {
        val entry = entries[name] ?: throw IOException("entry not found")
        if (entry.size > maxBytes || entry.compressedSize > maxBytes) {
            throw IOException("${entry.size shr 20} MB dex entry, too large to read here")
        }
        val header = readAt(entry.localHeader, 30)
        if (u32(header, 0) != 0x04034b50L) throw IOException("bad zip local header")
        val dataStart = entry.localHeader + 30 + u16(header, 26) + u16(header, 28)
        return when (entry.method) {
            0 -> readAt(dataStart, entry.compressedSize.toInt())
            // Inflate straight from the file, so only the output is held in memory.
            8 -> {
                val inflater = Inflater(true) // a caller-supplied Inflater is not ended by close()
                try {
                    val input = InflaterInputStream(FileSlice(file, dataStart, entry.compressedSize), inflater, 64 * 1024)
                    ByteArray(entry.size.toInt()).also { DataInputStream(input).readFully(it) }
                } finally {
                    inflater.end()
                }
            }
            else -> throw IOException("unsupported compression method ${entry.method}")
        }
    }

    private fun readAt(offset: Long, size: Int): ByteArray =
        ByteArray(size).also { file.seek(offset); file.readFully(it) }

    override fun close() = file.close()
}

/**
 * [length] bytes of [file] from [start], then the single extra zero byte that java.util.zip's own
 * ZipFile feeds a "nowrap" Inflater at the end of the data.
 */
private class FileSlice(private val file: RandomAccessFile, private var position: Long, private var left: Long) : InputStream() {
    private var padded = false

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (left <= 0) {
            if (padded) return -1
            padded = true
            b[off] = 0
            return 1
        }
        file.seek(position)
        val n = file.read(b, off, minOf(len.toLong(), left).toInt())
        if (n < 0) throw IOException("zip entry runs past the end of the file")
        position += n
        left -= n
        return n
    }
}

private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

private fun u32(b: ByteArray, at: Int): Long = u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16)

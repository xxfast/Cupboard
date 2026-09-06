package io.github.xxfast.cupboard.export

/**
 * The date every entry is stamped with, in DOS's packed form: 1 January 2000.
 *
 * Fixed rather than the clock, so exporting the same deck twice produces the same
 * bytes twice, which is what makes a diff of two exports mean something.
 */
private const val DosDate: Int = 0x2821

/** One entry of an archive: the path inside it, and the bytes stored there. */
data class ZipEntry(val path: String, val bytes: ByteArray)

/**
 * The entries as a ZIP archive, every one of them *stored* rather than
 * compressed.
 *
 * What this is for is OOXML: a `.pptx` is a ZIP of XML parts, and PowerPoint,
 * Keynote and LibreOffice all read a stored archive perfectly well. Stored means
 * no deflate to get wrong, and the only arithmetic left is the CRC and the
 * offsets, which is a good trade for a format nobody will open in a text editor
 * anyway.
 *
 * No ZIP64, so an archive is capped near 4GB, which no deck reaches.
 */
fun zipArchive(entries: List<ZipEntry>): ByteArray {
    val out = ByteSink(1 shl 16)
    val offsets = IntArray(entries.size)
    val checksums = IntArray(entries.size)

    for ((index, entry) in entries.withIndex()) {
        val name: ByteArray = entry.path.encodeToByteArray()
        offsets[index] = out.size
        checksums[index] = crc32(entry.bytes)

        out.leInt(0x04034B50)
        // Version 2.0, no flags, method 0 (stored), and a fixed timestamp: an
        // export of the same deck should be the same bytes twice running.
        out.leShort(20)
        out.leShort(0)
        out.leShort(0)
        out.leShort(0)
        out.leShort(DosDate)
        out.leInt(checksums[index])
        out.leInt(entry.bytes.size)
        out.leInt(entry.bytes.size)
        out.leShort(name.size)
        out.leShort(0)
        out.bytes(name)
        out.bytes(entry.bytes)
    }

    val directory: Int = out.size
    for ((index, entry) in entries.withIndex()) {
        val name: ByteArray = entry.path.encodeToByteArray()
        out.leInt(0x02014B50)
        out.leShort(20)
        out.leShort(20)
        out.leShort(0)
        out.leShort(0)
        out.leShort(0)
        out.leShort(DosDate)
        out.leInt(checksums[index])
        out.leInt(entry.bytes.size)
        out.leInt(entry.bytes.size)
        out.leShort(name.size)
        out.leShort(0)
        out.leShort(0)
        out.leShort(0)
        out.leShort(0)
        out.leInt(0)
        out.leInt(offsets[index])
        out.bytes(name)
    }

    val directoryEnd: Int = out.size
    out.leInt(0x06054B50)
    out.leShort(0)
    out.leShort(0)
    out.leShort(entries.size)
    out.leShort(entries.size)
    out.leInt(directoryEnd - directory)
    out.leInt(directory)
    out.leShort(0)
    return out.toByteArray()
}

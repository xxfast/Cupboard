package io.github.xxfast.cupboard.export

/**
 * The two checksums and the one compressed stream every binary format here
 * needs, written by hand.
 *
 * Pure Kotlin on purpose: `:cupboard` carries mingwX64, so `java.util.zip` is not
 * on the table, and a deck must export the same bytes from every shell. What is
 * here is deliberately not a compressor: a deflate stream made only of *stored*
 * blocks is a valid deflate stream, so PDF's `/FlateDecode` and PNG's `IDAT`
 * both read it, and the file is simply as large as its pixels are. Real
 * compression can go in behind this same function later without a caller
 * noticing.
 */

/** The largest a stored deflate block may declare, since its length is 16 bits. */
private const val StoredBlockSize: Int = 0xFFFF

/** Adler-32 over [data], the checksum a zlib stream ends on. */
internal fun adler32(data: ByteArray): Int {
    var a = 1
    var b = 0
    for (byte in data) {
        a = (a + (byte.toInt() and 0xFF)) % 65521
        b = (b + a) % 65521
    }
    return (b shl 16) or a
}

/** The CRC-32 table, built once: PNG chunks and ZIP entries both run on it. */
private val Crc32Table: IntArray = IntArray(256) { index ->
    var value: Int = index
    repeat(8) { value = if (value and 1 != 0) 0xEDB88320.toInt() xor (value ushr 1) else value ushr 1 }
    value
}

/** CRC-32 over [data]: PNG's per-chunk check and ZIP's per-entry one. */
internal fun crc32(data: ByteArray): Int {
    var crc: Int = -1
    for (byte in data) crc = Crc32Table[(crc xor byte.toInt()) and 0xFF] xor (crc ushr 8)
    return crc.inv()
}

/**
 * [data] as a zlib stream: the two-byte header, the data in stored blocks, and
 * the Adler-32 trailer.
 *
 * Empty input still writes one (final, empty) block, because a zlib stream with
 * no block at all is not one.
 */
internal fun zlibStored(data: ByteArray): ByteArray {
    val out = ByteSink(data.size + data.size / StoredBlockSize * 5 + 16)
    // 0x78 0x01: deflate, 32K window, no preset dictionary, and the pair divides
    // by 31, which is the header's own check.
    out.byte(0x78)
    out.byte(0x01)

    var start = 0
    do {
        val length: Int = minOf(StoredBlockSize, data.size - start)
        val last: Boolean = start + length >= data.size
        out.byte(if (last) 1 else 0)
        out.leShort(length)
        out.leShort(length.inv() and 0xFFFF)
        out.bytes(data.copyOfRange(start, start + length))
        start += length
    } while (!last)

    out.beInt(adler32(data))
    return out.toByteArray()
}

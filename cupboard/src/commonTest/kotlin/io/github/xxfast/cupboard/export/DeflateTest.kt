package io.github.xxfast.cupboard.export

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeflateTest {
    @Test
    fun adlerIsTheTwoRunningSums() {
        // "abc": a runs 1, 98, 196, 295 and b sums those, which is 589.
        assertEquals((589 shl 16) or 295, adler32("abc".encodeToByteArray()))
        assertEquals(1, adler32(ByteArray(0)))
    }

    @Test
    fun crcMatchesTheCheckValueEveryImplementationQuotes() {
        assertEquals(0xCBF43926.toInt(), crc32("123456789".encodeToByteArray()))
    }

    @Test
    fun aStoredStreamInflatesBackToWhatWentIn() {
        val data = ByteArray(200_000) { (it * 31 + it / 97).toByte() }
        val stream: ByteArray = zlibStored(data)

        // More than one block, since a stored block cannot declare 200,000 bytes.
        assertTrue(stream.size > data.size, "the stream carries a header and block headers")
        assertContentEquals(data, inflateStored(stream))
    }

    @Test
    fun anEmptyStreamIsStillAStream() {
        val stream: ByteArray = zlibStored(ByteArray(0))
        assertEquals(0x78, stream[0].toInt() and 0xFF)
        assertContentEquals(ByteArray(0), inflateStored(stream))
    }

    @Test
    fun theHeaderPassesTheCheckEveryReaderMakes() {
        val stream: ByteArray = zlibStored("hello".encodeToByteArray())
        val header: Int = ((stream[0].toInt() and 0xFF) shl 8) or (stream[1].toInt() and 0xFF)
        assertEquals(0, header % 31)
    }
}

/**
 * A zlib stream of stored blocks, read back: the header, the blocks, and the
 * Adler-32 checked against what came out.
 *
 * Only stored blocks, which is all [zlibStored] writes. A real inflate would be
 * an order of magnitude more code and would test somebody else's algorithm.
 */
private fun inflateStored(stream: ByteArray): ByteArray {
    val out = mutableListOf<Byte>()
    var at = 2
    while (true) {
        val header: Int = stream[at].toInt() and 0xFF
        val last: Boolean = header and 1 == 1
        check(header shr 1 and 3 == 0) { "not a stored block" }
        val length: Int = stream.leShort(at + 1)
        val complement: Int = stream.leShort(at + 3)
        check(length == complement.inv() and 0xFFFF) { "length and its complement disagree" }
        for (index in 0 until length) out += stream[at + 5 + index]
        at += 5 + length
        if (last) break
    }

    val data: ByteArray = out.toByteArray()
    check(stream.beInt(at) == adler32(data)) { "the trailer does not match the data" }
    check(at + 4 == stream.size) { "there are bytes past the trailer" }
    return data
}

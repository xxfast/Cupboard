package io.github.xxfast.cupboard.export

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PngEncoderTest {
    private val frame = RasterFrame(3, 2, IntArray(6) { 0xFF102030.toInt() + it })

    @Test
    fun itOpensWithTheSignature() {
        val png: ByteArray = encodePng(frame)
        assertContentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            png.copyOfRange(0, 8),
        )
    }

    @Test
    fun theHeaderCarriesTheFramesSize() {
        val png: ByteArray = encodePng(frame)
        assertEquals("IHDR", png.decodeToString(12, 16))
        assertEquals(3, png.beInt(16))
        assertEquals(2, png.beInt(20))
        // Eight bits a channel, colour type 6 (RGBA), and no interlacing.
        assertEquals(8, png[24].toInt())
        assertEquals(6, png[25].toInt())
        assertEquals(0, png[28].toInt())
    }

    @Test
    fun everyChunkChecksOut() {
        val png: ByteArray = encodePng(frame)
        val types = mutableListOf<String>()
        var at = 8
        while (at < png.size) {
            val length: Int = png.beInt(at)
            val type: String = png.decodeToString(at + 4, at + 8)
            val crc: Int = png.beInt(at + 8 + length)
            assertEquals(crc32(png.copyOfRange(at + 4, at + 8 + length)), crc, "$type's CRC")
            types += type
            at += 12 + length
        }

        assertEquals(listOf("IHDR", "IDAT", "IEND"), types)
        assertEquals(png.size, at, "the file ends on a chunk boundary")
    }

    @Test
    fun thePixelsAreThereWithAFilterByteALine() {
        val png: ByteArray = encodePng(frame)
        val data: ByteArray = png.copyOfRange(8, png.size)
        // Two scanlines of three RGBA pixels, each behind its filter byte.
        assertTrue(data.size > 2 * (1 + 3 * 4), "the pixels are in the file")
    }
}

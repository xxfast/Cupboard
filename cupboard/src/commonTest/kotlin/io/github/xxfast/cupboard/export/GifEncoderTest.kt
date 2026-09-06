package io.github.xxfast.cupboard.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GifEncoderTest {
    @Test
    fun itIsAGif89aWithAGlobalTable() {
        val gif: ByteArray = exportGif(plainDeck(2), FakeRasterizer())
        assertEquals("GIF89a", gif.decodeToString(0, 6))
        // The fake's frames are 4x4, which is smaller than the export width, so
        // nothing is scaled and the screen is the frame.
        assertEquals(4, gif.leShort(6))
        assertEquals(4, gif.leShort(8))
        assertEquals(0xF7, gif[10].toInt() and 0xFF, "a 256-entry global colour table")
        assertEquals(0x3B, gif.last().toInt() and 0xFF, "the trailer")
    }

    @Test
    fun thereIsAControlBlockPerFrame() {
        val control = byteArrayOf(0x21, 0xF9.toByte(), 0x04)
        assertEquals(3, exportGif(plainDeck(3), FakeRasterizer()).occurrencesOf(control))
        // Two builds on the one slide is three steps, and every one is a frame.
        assertEquals(3, exportGif(builtDeck(), FakeRasterizer()).occurrencesOf(control))
        assertEquals(
            1,
            exportGif(builtDeck(), FakeRasterizer(), GifOptions(everyBuild = false))
                .occurrencesOf(control),
        )
    }

    @Test
    fun theDelayIsWrittenInHundredths() {
        val gif: ByteArray =
            exportGif(plainDeck(1), FakeRasterizer(), GifOptions(frameDelayMs = 2500))
        val control: Int = gif.indexOfSequence(byteArrayOf(0x21, 0xF9.toByte(), 0x04))
        assertTrue(control > 0, "there is a graphic control block")
        assertEquals(250, gif.leShort(control + 4))
    }

    @Test
    fun aLoopingGifCarriesTheNetscapeExtension() {
        val looping: ByteArray = exportGif(plainDeck(1), FakeRasterizer())
        assertTrue(looping.latin1().contains("NETSCAPE2.0"))

        val once: ByteArray = exportGif(plainDeck(1), FakeRasterizer(), GifOptions(loop = false))
        assertTrue(!once.latin1().contains("NETSCAPE2.0"))
    }

    @Test
    fun theImageDataIsBlockedUnder256Bytes() {
        val gif: ByteArray = exportGif(plainDeck(1), FakeRasterizer(size = 64))
        val control: Int = gif.indexOfSequence(byteArrayOf(0x21, 0xF9.toByte(), 0x04))
        // The image descriptor is ten bytes, and the LZW code size one more, so
        // the sub-blocks start eleven past it.
        val descriptor: Int = gif.indexOfSequence(byteArrayOf(0x2C), control + 8)
        var at: Int = descriptor + 11
        var blocks = 0
        while (gif[at].toInt() != 0) {
            val length: Int = gif[at].toInt() and 0xFF
            assertTrue(length in 1..255)
            at += length + 1
            blocks++
        }
        assertTrue(blocks > 0, "the frame has image data")
    }
}

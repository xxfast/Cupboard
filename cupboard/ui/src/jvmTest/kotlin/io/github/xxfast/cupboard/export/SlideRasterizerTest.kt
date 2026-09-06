package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.sampleDocument
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlideRasterizerTest {
    private val document: Document = sampleDocument()

    @Test
    fun itDrawsTheSlideRatherThanAnEmptyBox() {
        val rasterizer = SlideRasterizer(document, width = 384)
        assertEquals(216, rasterizer.height, "16:9, from the deck's own slide size")

        val png: ByteArray = rasterizer.render(0, 0)
        assertEquals("PNG", png.decodeToString(1, 4), "Skia wrote a PNG")

        val frame: RasterFrame = rasterizer.frame(0, 0)
        assertEquals(384 * 216, frame.argb.size)
        // The middle of the slide, rather than all of it: the surface is rounded,
        // so its own corners are transparent by design.
        val middle: Int = frame.argb[frame.height / 2 * frame.width + frame.width / 2]
        assertEquals(0xFF, (middle ushr 24) and 0xFF, "the slide itself is opaque")
        // The title slide is type on a dark ground, so something has to be
        // brighter than the ground it is on.
        val brightest: Int = frame.argb.maxOf { (it shr 16) and 0xFF }
        assertTrue(brightest > 0x80, "something was drawn (brightest red channel $brightest)")
    }

    /**
     * The sample deck through every writer, onto disk, for a human to open.
     *
     * Not an assertion of what a PDF should look like, which no number would
     * catch: the assertions are that each file is the format it claims to be and
     * that it is not empty, and the paths are printed so the files can be opened.
     */
    @Test
    fun itWritesTheSampleDeckInEveryFormat() {
        val directory = File(System.getProperty("exports.out") ?: "build/exports")
        directory.mkdirs()
        val rasterizer = SlideRasterizer(document, width = 960)

        val pdf: ByteArray = exportPdf(document, rasterizer, PdfOptions(includeNotes = true))
        val gif: ByteArray = exportGif(
            document,
            rasterizer,
            GifOptions(width = 640, everyBuild = false),
        )
        val pptx: ByteArray = exportPptx(document, rasterizer)
        val html: String = exportHtmlPlayer(document, SlideRasterizer(document, width = 640))

        for ((name, bytes) in listOf("deck.pdf" to pdf, "deck.gif" to gif, "deck.pptx" to pptx)) {
            File(directory, name).writeBytes(bytes)
            println("export: ${File(directory, name).absolutePath} (${bytes.size} bytes)")
        }
        File(directory, "deck.html").writeText(html)
        println("export: ${File(directory, "deck.html").absolutePath} (${html.length} chars)")

        assertEquals("%PDF-1.4", pdf.decodeToString(0, 8))
        assertEquals("GIF89a", gif.decodeToString(0, 6))
        assertEquals(0x04034B50, pptx.leInt(0), "the pptx is a zip")
        assertTrue(html.contains("<img"), "the player carries its frames")
    }

    @Test
    fun everySlideOfTheDeckIsAPageOfTheHandout() {
        val handout: ByteArray = exportPdf(
            document,
            SlideRasterizer(document, width = 320),
            PdfOptions(layout = HandoutLayout.SixPerPage, includeNotes = true),
        )

        val directory = File(System.getProperty("exports.out") ?: "build/exports")
        directory.mkdirs()
        File(directory, "handout.pdf").writeBytes(handout)
        println("export: ${File(directory, "handout.pdf").absolutePath} (${handout.size} bytes)")

        val played: Int = document.slides.count { !it.skipped }
        val pages: Int = (played + 5) / 6
        assertTrue(handout.decodeToString(0, 8) == "%PDF-1.4")
        assertTrue(
            handout.latin1().contains("/Type /Pages /Count $pages"),
            "$played slides six up is $pages pages",
        )
    }
}

/** The little-endian int at [at], for reading a ZIP's signature. */
private fun ByteArray.leInt(at: Int): Int =
    (this[at].toInt() and 0xFF) or
        ((this[at + 1].toInt() and 0xFF) shl 8) or
        ((this[at + 2].toInt() and 0xFF) shl 16) or
        ((this[at + 3].toInt() and 0xFF) shl 24)

/** These bytes one char each, so a search does not trip over the image data. */
private fun ByteArray.latin1(): String =
    buildString(size) { for (byte in this@latin1) append((byte.toInt() and 0xFF).toChar()) }

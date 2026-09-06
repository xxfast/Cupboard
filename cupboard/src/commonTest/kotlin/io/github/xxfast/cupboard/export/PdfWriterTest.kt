package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.setSlideSkipped
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfWriterTest {
    @Test
    fun itIsAPdfFromTheFirstByte() {
        val pdf: ByteArray = exportPdf(plainDeck(2), FakeRasterizer())
        assertEquals("%PDF-1.4", pdf.decodeToString(0, 8))
        assertTrue(pdf.latin1().trimEnd().endsWith("%%EOF"), "it ends on the end marker")
    }

    @Test
    fun everyCrossReferenceEntryPointsAtItsObject() {
        val pdf: ByteArray = exportPdf(plainDeck(3), FakeRasterizer())
        val text: String = pdf.latin1()
        // "\nxref\n", not "xref\n": the trailer's own `startxref` ends in one too.
        val lines: List<String> = text.substring(text.lastIndexOf("\nxref\n") + 1).lines()
        // "xref", then "0 <size>", then the free head, then one line per object.
        val size: Int = lines[1].split(" ")[1].toInt()

        for (number in 1 until size) {
            val offset: Int = lines[number + 2].take(10).toInt()
            assertEquals("$number 0 obj", text.substring(offset, offset + "$number 0 obj".length))
        }
    }

    @Test
    fun aHandoutFitsFourSlidesToThePage() {
        val pdf: String =
            exportPdf(plainDeck(9), FakeRasterizer(), PdfOptions(layout = HandoutLayout.FourPerPage))
                .latin1()

        assertTrue(pdf.contains("/Type /Pages /Count 3"), "nine slides four up is three pages")
        assertEquals(3, Regex("/Type /Page /Parent").findAll(pdf).count(), "three page objects")
    }

    @Test
    fun oneSlideToThePageIsAPageASlide() {
        val pdf: String = exportPdf(plainDeck(5), FakeRasterizer()).latin1()
        assertTrue(pdf.contains("/Type /Pages /Count 5"))
    }

    @Test
    fun skippedSlidesAreOutUnlessAskedFor() {
        val deck: Document = plainDeck(4).setSlideSkipped("slide-1", true)

        val rasterizer = FakeRasterizer()
        exportPdf(deck, rasterizer)
        assertEquals(listOf(0, 2, 3), rasterizer.asked.map { it.slideIndex })

        val all = FakeRasterizer()
        exportPdf(deck, all, PdfOptions(skippedSlides = true))
        assertEquals(listOf(0, 1, 2, 3), all.asked.map { it.slideIndex })
    }

    @Test
    fun everyBuildIsAFrameOfItsOwn() {
        val rasterizer = FakeRasterizer()
        exportPdf(builtDeck(), rasterizer, PdfOptions(everyBuild = true))
        assertEquals(listOf(0, 1, 2), rasterizer.asked.map { it.step })
    }

    @Test
    fun notesGoUnderTheSlideWhenTheyAreAskedFor() {
        val deck: Document = builtDeck(notes = "Mention the deadline")
        val withNotes: String =
            exportPdf(deck, FakeRasterizer(), PdfOptions(layout = HandoutLayout.OneWithNotes))
                .latin1()
        val without: String = exportPdf(deck, FakeRasterizer()).latin1()

        assertTrue(withNotes.contains("(Mention the deadline) Tj"))
        assertTrue(!without.contains("Mention"), "a plain export carries no notes")
    }

    @Test
    fun anImageIsWrittenAsAFlateRgbXObject() {
        val pdf: String = exportPdf(plainDeck(1), FakeRasterizer()).latin1()
        assertTrue(pdf.contains("/Subtype /Image /Width 4 /Height 4"))
        assertTrue(pdf.contains("/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode"))
    }
}

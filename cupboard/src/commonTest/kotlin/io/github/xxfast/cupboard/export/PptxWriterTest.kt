package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Document
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PptxWriterTest {
    private fun parts(document: Document): Map<String, String> =
        readStoredZip(exportPptx(document, FakeRasterizer()))
            .mapValues { (_, bytes) -> bytes.latin1() }

    @Test
    fun thePackageHasThePartsPowerPointLooksFor() {
        val parts: Map<String, String> = parts(plainDeck(2))
        assertTrue("[Content_Types].xml" in parts)
        assertTrue("_rels/.rels" in parts)
        assertTrue("ppt/presentation.xml" in parts)
        assertTrue("ppt/_rels/presentation.xml.rels" in parts)
        assertTrue("ppt/slideMasters/slideMaster1.xml" in parts)
        assertTrue("ppt/slideLayouts/slideLayout1.xml" in parts)
        assertTrue("ppt/theme/theme1.xml" in parts)
        assertTrue("ppt/slides/slide1.xml" in parts)
        assertTrue("ppt/slides/slide2.xml" in parts)
        assertTrue("ppt/media/slide1.png" in parts)
    }

    @Test
    fun theContentTypesListEverySlide() {
        val types: String = parts(plainDeck(3))["[Content_Types].xml"]!!
        for (number in 1..3) assertTrue(types.contains("""PartName="/ppt/slides/slide$number.xml""""))
        assertTrue(!types.contains("slide4.xml"))
        assertTrue(types.contains("""Extension="png""""), "the media needs a default type")
    }

    @Test
    fun thePresentationListsEverySlideOnce() {
        val presentation: String = parts(plainDeck(4))["ppt/presentation.xml"]!!
        assertEquals(4, Regex("<p:sldId ").findAll(presentation).count())
        // rId1 is the master, so the slides run from rId2.
        assertTrue(presentation.contains("""r:id="rId2""""))
        assertTrue(presentation.contains("""r:id="rId5""""))
    }

    @Test
    fun theSlideSizeIsTheDecksOwnInEmu() {
        val presentation: String = parts(plainDeck(1))["ppt/presentation.xml"]!!
        // 1920 x 1080 points, at 12700 EMU a point.
        assertTrue(presentation.contains("""<p:sldSz cx="24384000" cy="13716000"/>"""))
    }

    @Test
    fun aSlideIsAPictureWithItsTextOverIt() {
        val slide: String = parts(builtDeck())["ppt/slides/slide1.xml"]!!
        assertTrue(slide.contains("""<a:blip r:embed="rId2"/>"""), "the raster is the background")
        assertTrue(slide.contains("<a:t>First</a:t>"), "and the text is editable over it")
        assertTrue(slide.contains("<a:t>Second</a:t>"))
    }

    @Test
    fun everySlideIsRasterisedAtItsLastStep() {
        val rasterizer = FakeRasterizer()
        exportPptx(builtDeck(), rasterizer)
        assertEquals(listOf(ExportFrame(0, 2)), rasterizer.asked)
    }
}

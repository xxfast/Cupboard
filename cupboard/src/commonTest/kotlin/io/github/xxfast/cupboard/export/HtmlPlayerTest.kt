package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.setSlideSkipped
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlPlayerTest {
    @Test
    fun everyStepIsAnImageOfItsOwn() {
        // Three plain slides is three frames; the built deck's one slide is three
        // steps, and so is three frames too.
        assertEquals(3, exportHtmlPlayer(plainDeck(3), FakeRasterizer()).count("<img"))
        assertEquals(3, exportHtmlPlayer(builtDeck(), FakeRasterizer()).count("<img"))
    }

    @Test
    fun theImagesAreInlineSoTheFileStandsAlone() {
        val html: String = exportHtmlPlayer(plainDeck(1), FakeRasterizer())
        assertTrue(html.contains("src=\"data:image/png;base64,iVBORw0KGgo"), "an inlined PNG")
        assertTrue(!html.contains("src=\"slide"), "nothing is loaded off disk")
    }

    @Test
    fun theNotesTravelWithIt() {
        val html: String = exportHtmlPlayer(builtDeck(notes = "Pause here"), FakeRasterizer())
        assertTrue(html.contains("Pause here"))
        assertTrue(html.contains("""<aside id="notes" hidden>"""), "and start out of the way")
    }

    @Test
    fun theDecksNameIsTheTitle() {
        val html: String = exportHtmlPlayer(plainDeck(1), FakeRasterizer())
        assertTrue(html.contains("<title>Test deck</title>"))
    }

    @Test
    fun skippedSlidesAreNotInTheShow() {
        val deck: Document = plainDeck(3).setSlideSkipped("slide-1", true)
        assertEquals(2, exportHtmlPlayer(deck, FakeRasterizer()).count("<img"))
    }

    @Test
    fun whatWouldCloseATagIsEscaped() {
        val deck: Document = plainDeck(1).let { document ->
            document.copy(slides = listOf(document.slides[0].copy(notes = "a < b & c")))
        }
        val html: String = exportHtmlPlayer(deck, FakeRasterizer())
        assertTrue(html.contains("a &lt; b &amp; c"))
    }
}

/** How many times [needle] occurs, which is what these tests count. */
private fun String.count(needle: String): Int = split(needle).size - 1

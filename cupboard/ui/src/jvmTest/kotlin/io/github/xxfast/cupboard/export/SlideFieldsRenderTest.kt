package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * Slide number fields as an export draws them, which is [SlideRasterizer]: the
 * one renderer every picture of a deck goes through, so what holds here holds
 * for the PNG sequence, the PDF and the rest.
 */
class SlideFieldsRenderTest {
    private val edge = 400f

    private fun layout(text: String?): Slide = Slide(
        id = "layout",
        elements = listOfNotNull(
            text?.let {
                TextElement(frame = Frame(20f, 20f, 200f, 80f), text = it, fontSize = 60f)
            },
        ),
    )

    private fun deck(layout: Slide?, vararg slides: Slide): Document = Document(
        slideWidth = edge,
        slideHeight = edge,
        slides = slides.map { it.copy(layoutId = layout?.id) },
        layouts = listOfNotNull(layout),
    )

    private fun Document.frame(index: Int): IntArray =
        SlideRasterizer(this, width = edge.toInt()).renderPixels(index, 0).argb

    @Test
    fun aBoxOnTheLayoutReadsEachSlidesOwnNumber() {
        val fielded = deck(layout("{number}"), Slide(), Slide())
        val typed = deck(layout("2"), Slide(), Slide())

        assertContentEquals(typed.frame(1), fielded.frame(1))
    }

    @Test
    fun aSlideWithItsNumberSwitchedOffDrawsNoBox() {
        val fielded = deck(layout("{number} / {total}"), Slide(showsSlideNumber = false))
        val empty = deck(layout(null), Slide(showsSlideNumber = false))

        assertContentEquals(empty.frame(0), fielded.frame(0))
    }
}

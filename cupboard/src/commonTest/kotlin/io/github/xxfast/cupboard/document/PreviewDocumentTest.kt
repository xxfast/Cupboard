package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class PreviewDocumentTest {

    @Test
    fun aPreviewIsTheOneSlideOnTheDecksOwnFurniture() {
        val document = sampleDocument()
        val slide = document.slides[3]
        val preview = document.previewOf(slide.id)

        assertEquals(listOf(slide.id), preview.slides.map { it.id })
        assertEquals(document.layouts, preview.layouts)
        assertEquals(document.background, preview.background)
        assertEquals(document.slideWidth, preview.slideWidth)
        assertEquals(document.slideHeight, preview.slideHeight)
    }

    @Test
    fun aSkippedSlideIsStillPreviewed() {
        val document = sampleDocument()
        val slide = document.slides[1]
        val preview = document.setSlideSkipped(slide.id, true).previewOf(slide.id)

        assertEquals(1, preview.slides.size)
        assertFalse(preview.slides.single().skipped)
    }

    /** A layout is a slide like any other, so previewing one shows the template. */
    @Test
    fun aLayoutPreviewsAsItself() {
        val document = sampleDocument()
        val layout = document.layouts.first()

        assertEquals(listOf(layout.id), document.previewOf(layout.id).slides.map { it.id })
    }

    @Test
    fun anUnknownIdPreviewsTheDeckUnchanged() {
        val document = sampleDocument()
        assertSame(document, document.previewOf("nothing-here"))
    }
}

package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The deck's slide size through the loop: one entry, and what it moves. */
class EditorSlideSizeTest {
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                elements = listOf(
                    TextElement(id = "text", frame = Frame(100f, 200f, 400f, 300f), fontSize = 40f),
                ),
            ),
        ),
    )

    @Test
    fun aResizeScalesTheDeckAndCostsOneHistoryEntry() = runTest {
        val viewModel = editor(document())

        viewModel.onSetSlideSize(1440f, 1080f, scaleContent = true)
        val resized = viewModel.await { it.document.slideWidth == 1440f }
        val text = resized.document.slides.single().elements.single() as TextElement
        assertEquals(Frame(75f, 200f, 300f, 300f), text.frame)
        assertEquals(30f, text.fontSize)
        assertTrue(resized.canUndo)

        viewModel.onUndo()
        val undone = viewModel.await { it.document.slideWidth == 1920f }
        assertEquals(document(), undone.document)
        assertFalse(undone.canUndo)
    }

    @Test
    fun theSizeTheDeckIsAlreadyOnMakesNoHistoryEntry() = runTest {
        val viewModel = editor(document())

        viewModel.onSetSlideSize(1920f, 1080f, scaleContent = true)
        // Nothing about the state changes, so the wait is on a later event
        // settling: this one has been through the loop by the time it lands.
        viewModel.onToggleNotes()
        val settled = viewModel.await { !it.showNotes }
        assertEquals(document(), settled.document)
        assertFalse(settled.canUndo)
    }

    @Test
    fun anInsertionCentresOnTheNewSlide() = runTest {
        val viewModel = editor(document())

        viewModel.onSetSlideSize(1440f, 1080f, scaleContent = false)
        val resized = viewModel.await { it.document.slideWidth == 1440f }
        assertEquals(Frame(520f, 440f, 400f, 200f), resized.insertionFrame(400f, 200f))
    }
}

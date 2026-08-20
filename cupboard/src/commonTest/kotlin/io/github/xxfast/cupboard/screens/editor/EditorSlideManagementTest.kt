package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reordering slides, skipping them, and the per-slide look: everything the
 * navigator's drag and the document inspector drive through the loop. The deck
 * nests a run under "Two" so a move has children to carry or to let out.
 */
class EditorSlideManagementTest {
    // Fixed ids, so a freshly built document compares equal to the opened one.
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(id = "one", title = "One"),
            Slide(id = "two", title = "Two"),
            Slide(id = "two-a", title = "Two A", depth = 1),
            Slide(id = "two-b", title = "Two B", depth = 1),
            Slide(id = "three", title = "Three"),
        ),
    )

    private fun EditorState.slideIds(): List<String> = document.slides.map { it.id }

    @Test
    fun aDropMovesTheSlideSelectsItAndCostsOneHistoryEntry() = runTest {
        val viewModel = editor(document())

        viewModel.onMoveSlide("three", null)
        val moved = viewModel.await { it.document.slides.first().id == "three" }
        assertEquals(listOf("three", "one", "two", "two-a", "two-b"), moved.slideIds())
        assertEquals("three", moved.selectedSlideId)
        assertTrue(moved.canUndo)

        viewModel.onUndo()
        val undone = viewModel.await { it.document.slides.first().id == "one" }
        assertEquals(document(), undone.document)
        assertFalse(undone.canUndo)
    }

    @Test
    fun aDropThatMovesNothingMakesNoHistoryEntry() = runTest {
        val viewModel = editor(document())

        // Back where it came from. The drop still lands in the navigator, which
        // is the change the state settles on.
        viewModel.onMoveSlide("one", null)
        val settled = viewModel.await { it.focusedPane == EditorPane.Navigator }
        assertEquals(document(), settled.document)
        assertFalse(settled.canUndo)
    }

    @Test
    fun aDragRidesTheLoopWithoutTouchingTheDocument() = runTest {
        val viewModel = editor(document())

        viewModel.onPreviewSlideDrag("three", "one")
        val dragging = viewModel.await { it.slideDrag != null }
        assertEquals(SlideDrag("three", "one"), dragging.slideDrag)
        assertEquals(document(), dragging.document)
        assertFalse(dragging.canUndo)

        viewModel.onEndSlideDrag()
        val ended = viewModel.await { it.slideDrag == null }
        assertEquals(document(), ended.document)
        assertFalse(ended.canUndo)
    }

    @Test
    fun theDropClearsTheDragItFinishes() = runTest {
        val viewModel = editor(document())

        viewModel.onPreviewSlideDrag("three", "one")
        viewModel.await { it.slideDrag != null }

        viewModel.onMoveSlide("three", "one")
        val dropped = viewModel.await { it.document.slides[1].id == "three" }
        assertNull(dropped.slideDrag)
        assertEquals(listOf("one", "three", "two", "two-a", "two-b"), dropped.slideIds())
    }

    @Test
    fun skippingIsOneHistoryEntryAndUndoes() = runTest {
        val viewModel = editor(document())

        viewModel.onSetSlideSkipped("two", true)
        val skipped = viewModel.await { it.document.slides[1].skipped }
        // The presentation closes up over the skipped slide; the deck doesn't.
        assertEquals(listOf(1, null, 2, 3, 4), skipped.fullOutline().map { it.number })
        assertTrue(skipped.fullOutline()[1].skipped)
        assertTrue(skipped.canUndo)

        viewModel.onUndo()
        val undone = viewModel.await { !it.document.slides[1].skipped }
        assertEquals(document(), undone.document)
        assertFalse(undone.canUndo)
    }

    @Test
    fun skippingASlideThatAlreadyIsMakesNoHistoryEntry() = runTest {
        val viewModel = editor(document())

        viewModel.onSetSlideSkipped("two", false)
        viewModel.onSelectSlide("three")
        val settled = viewModel.await { it.selectedSlideId == "three" }
        assertEquals(document(), settled.document)
        assertFalse(settled.canUndo)
    }

    @Test
    fun aSlidesOwnLookRidesTheOrdinarySlideUpdate() = runTest {
        val viewModel = editor(document())
        val background = SlideBackground.Gradient(start = 0xFF2A2452, end = 0xFF101223)

        viewModel.onUpdateSlide(
            document().slides[1].copy(showsSlideNumber = true, background = background),
        )
        val dressed = viewModel.await { it.document.slides[1].showsSlideNumber }
        assertEquals(background, dressed.document.slides[1].background)
        assertEquals(2, dressed.slideNumber("two"))
        assertTrue(dressed.canUndo)
    }
}

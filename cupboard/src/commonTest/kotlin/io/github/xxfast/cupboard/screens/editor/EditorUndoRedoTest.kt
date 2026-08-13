package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.allSlides
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * History is the presenter's, so it's exercised the way a shell drives it:
 * events in, flags and document out. The 100-entry cap is not covered, there is
 * no seam to shrink it and 100 round trips through the presenter is not a test.
 */
class EditorUndoRedoTest {
    @Test
    fun undoRestoresTheDocumentAsItWasBeforeTheEdit() = runTest {
        val viewModel = editor()
        assertFalse(viewModel.states.value.canUndo)
        val before = viewModel.states.value.selectedSlide.title

        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "Renamed"))
        val edited = viewModel.await { it.selectedSlide.title == "Renamed" }
        assertTrue(edited.canUndo)
        assertFalse(edited.canRedo)

        viewModel.onUndo()
        val undone = viewModel.await { it.selectedSlide.title == before }
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)
        // Selection survives the swap: it's the same slide either way.
        assertEquals(edited.selectedSlideId, undone.selectedSlideId)
    }

    @Test
    fun redoReappliesTheUndoneEdit() = runTest {
        val viewModel = editor()
        val before = viewModel.states.value.selectedSlide.title

        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "Renamed"))
        viewModel.await { it.selectedSlide.title == "Renamed" }
        viewModel.onUndo()
        viewModel.await { it.selectedSlide.title == before }

        viewModel.onRedo()
        val redone = viewModel.await { it.selectedSlide.title == "Renamed" }
        assertTrue(redone.canUndo)
        assertFalse(redone.canRedo)
    }

    @Test
    fun undoingBackToTheStartAndForwardAgainWalksEveryEdit() = runTest {
        val viewModel = editor()
        val before = viewModel.states.value.selectedSlide.title

        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "One"))
        viewModel.await { it.selectedSlide.title == "One" }
        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "Two"))
        viewModel.await { it.selectedSlide.title == "Two" }

        viewModel.onUndo()
        viewModel.await { it.selectedSlide.title == "One" }
        viewModel.onUndo()
        val start = viewModel.await { it.selectedSlide.title == before }
        assertFalse(start.canUndo)

        viewModel.onRedo()
        viewModel.await { it.selectedSlide.title == "One" }
        viewModel.onRedo()
        assertEquals("Two", viewModel.await { it.selectedSlide.title == "Two" }.selectedSlide.title)
    }

    @Test
    fun anEditAfterAnUndoDropsTheRedo() = runTest {
        val viewModel = editor()

        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "Renamed"))
        viewModel.await { it.selectedSlide.title == "Renamed" }
        viewModel.onUndo()
        viewModel.await { it.canRedo }

        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "Elsewhere"))
        val branched = viewModel.await { it.selectedSlide.title == "Elsewhere" }
        assertFalse(branched.canRedo)

        // The dropped redo really is gone: this lands behind an ordered event,
        // so by the time the selection arrives the redo has been through too.
        viewModel.onRedo()
        viewModel.onSelectSlideAt(2)
        val after = viewModel.await { it.selectedSlideIndex() == 2 }
        assertEquals(1, after.document.allSlides().count { it.title == "Elsewhere" })
        assertEquals(0, after.document.allSlides().count { it.title == "Renamed" })
        assertFalse(after.canRedo)
    }

    @Test
    fun previewsFoldIntoTheDocumentButMakeNoHistory() = runTest {
        val viewModel = editor()
        val slide = viewModel.states.value.selectedSlide

        viewModel.onPreviewSlide(slide.copy(title = "One"))
        viewModel.onPreviewSlide(slide.copy(title = "Two"))
        viewModel.onPreviewSlide(slide.copy(title = "Three"))
        val previewed = viewModel.await { it.selectedSlide.title == "Three" }
        assertEquals("Three", previewed.selectedSlide.title)
        assertFalse(previewed.canUndo)
    }

    @Test
    fun previewsFlagTheGestureUntilItCommits() = runTest {
        val viewModel = editor()
        val slide = viewModel.states.value.selectedSlide
        assertFalse(viewModel.states.value.isPreviewing)

        viewModel.onPreviewSlide(slide.copy(title = "One"))
        assertTrue(viewModel.await { it.selectedSlide.title == "One" }.isPreviewing)

        viewModel.onUpdateSlide(slide.copy(title = "Final"))
        assertFalse(viewModel.await { it.selectedSlide.title == "Final" }.isPreviewing)
    }

    @Test
    fun aCancelledGestureClearsThePreviewFlag() = runTest {
        val viewModel = editor()
        val before = viewModel.states.value.document
        val slide = viewModel.states.value.selectedSlide

        viewModel.onPreviewSlide(slide.copy(title = "One"))
        assertTrue(viewModel.await { it.selectedSlide.title == "One" }.isPreviewing)

        viewModel.onCancelPreview()
        assertFalse(viewModel.await { it.document == before }.isPreviewing)
    }

    @Test
    fun aGestureOfPreviewsUndoesAsOneEdit() = runTest {
        val viewModel = editor()
        val before = viewModel.states.value.document
        val slide = viewModel.states.value.selectedSlide

        viewModel.onPreviewSlide(slide.copy(title = "One"))
        viewModel.onPreviewSlide(slide.copy(title = "Two"))
        viewModel.onUpdateSlide(slide.copy(title = "Final"))
        val committed = viewModel.await { it.selectedSlide.title == "Final" }
        assertTrue(committed.canUndo)

        // One undo lands on the pre-gesture document, not a mid-drag frame.
        viewModel.onUndo()
        val undone = viewModel.await { it.document == before }
        assertFalse(undone.canUndo)
    }

    @Test
    fun aCancelledGestureRestoresThePreGestureDocument() = runTest {
        val viewModel = editor()
        val before = viewModel.states.value.document
        val slide = viewModel.states.value.selectedSlide

        viewModel.onPreviewSlide(slide.copy(title = "One"))
        viewModel.onPreviewSlide(slide.copy(title = "Two"))
        viewModel.await { it.selectedSlide.title == "Two" }

        viewModel.onCancelPreview()
        val restored = viewModel.await { it.document == before }
        assertFalse(restored.canUndo)
        assertFalse(restored.canRedo)
    }

    @Test
    fun collapsingASlideIsNotUndoable() = runTest {
        val viewModel = editor()
        val whyKmp = viewModel.states.value.document.slides.first { it.title == "Why KMP" }

        viewModel.onToggleCollapsed(whyKmp.id)
        val collapsed = viewModel.await { it.document.slides.first { slide -> slide.id == whyKmp.id }.collapsed }
        assertFalse(collapsed.canUndo)
        assertFalse(collapsed.canRedo)
    }

    @Test
    fun selectionIsNotUndoable() = runTest {
        val viewModel = editor()
        viewModel.onSelectSlideAt(3)
        viewModel.await { it.selectedSlideIndex() == 3 }
        val elementId = viewModel.states.value.selectedSlide.elements.firstOrNull()?.id
        viewModel.onSelectElement(elementId)
        val selected = viewModel.await { it.selectedElementIds == listOfNotNull(elementId) }
        assertFalse(selected.canUndo)
        assertFalse(selected.canRedo)
    }

    @Test
    fun undoWithNothingBehindItIsANoOp() = runTest {
        val viewModel = editor()
        val opened = viewModel.states.value.document

        viewModel.onUndo()
        viewModel.onRedo()
        viewModel.onSelectSlideAt(1)
        val after = viewModel.await { it.selectedSlideIndex() == 1 }
        assertEquals(opened, after.document)
        assertFalse(after.canUndo)
        assertFalse(after.canRedo)
    }
}

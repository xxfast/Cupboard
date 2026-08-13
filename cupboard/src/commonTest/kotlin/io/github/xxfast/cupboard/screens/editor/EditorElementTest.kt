package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.ZOrderMove
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Element property editing, driven the way a shell drives it. Three elements in
 * a known z-order (a under b under c) so reordering has somewhere to go.
 */
class EditorElementTest {
    private fun document(): Document = Document(
        slides = listOf(
            Slide(
                id = "slide",
                title = "Elements",
                elements = listOf(
                    ShapeElement(id = "a", frame = Frame(0f, 0f, 100f, 100f)),
                    ShapeElement(id = "b", frame = Frame(120f, 0f, 100f, 100f)),
                    ShapeElement(id = "c", frame = Frame(240f, 0f, 100f, 100f)),
                ),
            ),
        ),
    )

    private fun EditorState.element(id: String): Element =
        selectedSlide.elements.first { it.id == id }

    private fun EditorState.order(): List<String> = selectedSlide.elements.map { it.id }

    @Test
    fun updateElementCommitsAndUndoesAsOneEntry() = runTest {
        val viewModel = editor(document())
        val before = viewModel.states.value.element("b")
        assertFalse(viewModel.states.value.canUndo)

        viewModel.onUpdateElements(listOf(before.update(opacity = 0.5f, rotation = 45f)))
        val edited = viewModel.await { it.element("b").opacity == 0.5f }
        assertEquals(45f, edited.element("b").rotation)
        assertTrue(edited.canUndo)
        assertFalse(edited.canRedo)
        assertFalse(edited.isPreviewing)

        viewModel.onUndo()
        val undone = viewModel.await { it.element("b").opacity == 1f }
        assertEquals(before, undone.element("b"))
        assertFalse(undone.canUndo)
    }

    @Test
    fun updateElementLeavesTheOtherElementsAlone() = runTest {
        val viewModel = editor(document())
        val before = viewModel.states.value.selectedSlide

        viewModel.onUpdateElements(listOf(viewModel.states.value.element("b").update(opacity = 0.25f)))
        val edited = viewModel.await { it.element("b").opacity == 0.25f }
        assertEquals(before.elements.map { it.id }, edited.order())
        assertEquals(before.elements[0], edited.element("a"))
        assertEquals(before.elements[2], edited.element("c"))
    }

    @Test
    fun selectedElementResolvesInsideTheSelectedSlide() = runTest {
        val viewModel = editor(document())
        assertEquals(null, viewModel.states.value.primaryElement)

        viewModel.onSelectElement("c")
        val selected = viewModel.await { it.selectedElementIds == listOf("c") }
        assertEquals("c", selected.primaryElement?.id)

        // A stale id resolves to nothing rather than to the wrong element.
        viewModel.onSelectElement("gone")
        val stale = viewModel.await { it.selectedElementIds == listOf("gone") }
        assertEquals(null, stale.primaryElement)
        assertEquals(emptyList(), stale.selectedElements)

        // Null clears it outright.
        viewModel.onSelectElement(null)
        assertEquals(emptyList(), viewModel.await { it.selectedElementIds.isEmpty() }.selectedElementIds)
    }

    @Test
    fun aGestureOfElementPreviewsUndoesAsOneEdit() = runTest {
        val viewModel = editor(document())
        val before = viewModel.states.value.document
        val element = viewModel.states.value.element("b")

        viewModel.onPreviewElements(listOf(element.update(opacity = 0.9f)))
        viewModel.onPreviewElements(listOf(element.update(opacity = 0.7f)))
        val previewing = viewModel.await { it.element("b").opacity == 0.7f }
        assertTrue(previewing.isPreviewing)
        assertFalse(previewing.canUndo)

        viewModel.onUpdateElements(listOf(element.update(opacity = 0.5f)))
        val committed = viewModel.await { it.element("b").opacity == 0.5f }
        assertFalse(committed.isPreviewing)
        assertTrue(committed.canUndo)

        // One undo lands on the pre-gesture document, not a mid-slider frame.
        viewModel.onUndo()
        val undone = viewModel.await { it.document == before }
        assertFalse(undone.canUndo)
    }

    @Test
    fun aCancelledElementGestureRestoresThePreGestureDocument() = runTest {
        val viewModel = editor(document())
        val before = viewModel.states.value.document
        val element = viewModel.states.value.element("b")

        viewModel.onPreviewElements(listOf(element.update(opacity = 0.9f)))
        viewModel.onPreviewElements(listOf(element.update(opacity = 0.4f)))
        assertTrue(viewModel.await { it.element("b").opacity == 0.4f }.isPreviewing)

        viewModel.onCancelPreview()
        val restored = viewModel.await { it.document == before }
        assertFalse(restored.isPreviewing)
        assertFalse(restored.canUndo)
        assertFalse(restored.canRedo)
    }

    @Test
    fun reorderWalksTheZOrderAndClampsAtTheEnds() = runTest {
        val viewModel = editor(document())
        assertEquals(listOf("a", "b", "c"), viewModel.states.value.order())

        viewModel.onReorderElements(listOf("a"), ZOrderMove.Forward)
        assertEquals(listOf("b", "a", "c"), viewModel.await { it.order().first() == "b" }.order())

        viewModel.onReorderElements(listOf("a"), ZOrderMove.Backward)
        assertEquals(listOf("a", "b", "c"), viewModel.await { it.order().first() == "a" }.order())

        viewModel.onReorderElements(listOf("a"), ZOrderMove.ToFront)
        assertEquals(listOf("b", "c", "a"), viewModel.await { it.order().last() == "a" }.order())

        viewModel.onReorderElements(listOf("a"), ZOrderMove.ToBack)
        assertEquals(listOf("a", "b", "c"), viewModel.await { it.order().last() == "c" }.order())
    }

    @Test
    fun aReorderThatChangesNothingMakesNoHistoryEntry() = runTest {
        val viewModel = editor(document())

        // "a" is already at the back, "c" already at the front: four no-ops.
        viewModel.onReorderElements(listOf("a"), ZOrderMove.Backward)
        viewModel.onReorderElements(listOf("a"), ZOrderMove.ToBack)
        viewModel.onReorderElements(listOf("c"), ZOrderMove.Forward)
        viewModel.onReorderElements(listOf("c"), ZOrderMove.ToFront)
        // An id that doesn't resolve is a no-op too.
        viewModel.onReorderElements(listOf("gone"), ZOrderMove.ToFront)

        // Ordered behind them, so by the time this lands they have all been through.
        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertEquals(listOf("a", "b", "c"), after.order())
        assertFalse(after.canUndo)
    }

    @Test
    fun flipTogglesBothWaysOnBothAxes() = runTest {
        val viewModel = editor(document())

        viewModel.onFlipElements(listOf("b"), FlipAxis.Horizontal)
        assertTrue(viewModel.await { it.element("b").flippedHorizontally }.canUndo)

        viewModel.onFlipElements(listOf("b"), FlipAxis.Vertical)
        val both = viewModel.await { it.element("b").flippedVertically }
        assertTrue(both.element("b").flippedHorizontally)

        viewModel.onFlipElements(listOf("b"), FlipAxis.Horizontal)
        viewModel.onFlipElements(listOf("b"), FlipAxis.Vertical)
        val cleared = viewModel.await { !it.element("b").flippedVertically }
        assertFalse(cleared.element("b").flippedHorizontally)

        // Four flips, four undo entries: each one is its own edit.
        viewModel.onUndo()
        assertTrue(viewModel.await { it.element("b").flippedVertically }.canRedo)
    }

    @Test
    fun aLockedElementIgnoresEveryEditButUnlocking() = runTest {
        val viewModel = editor(document())
        val element = viewModel.states.value.element("b")

        viewModel.onSetElementsLocked(listOf("b"), locked = true)
        val locked = viewModel.await { it.element("b").locked }
        assertTrue(locked.canUndo)

        viewModel.onUpdateElements(listOf(element.update(opacity = 0.5f)))
        viewModel.onPreviewElements(listOf(element.update(opacity = 0.2f)))
        viewModel.onFlipElements(listOf("b"), FlipAxis.Horizontal)
        viewModel.onReorderElements(listOf("b"), ZOrderMove.ToFront)

        // Ordered behind the rejected events, so this landing means they have
        // all been through the presenter already.
        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertEquals(locked.document, after.document)
        assertFalse(after.isPreviewing)
        assertEquals(listOf("a", "b", "c"), after.order())

        // Unlocking is the way back in, and the edit lands once it has happened.
        viewModel.onSetElementsLocked(listOf("b"), locked = false)
        viewModel.await { !it.element("b").locked }
        viewModel.onUpdateElements(listOf(element.update(opacity = 0.5f)))
        assertEquals(0.5f, viewModel.await { it.element("b").opacity == 0.5f }.element("b").opacity)
    }

    @Test
    fun lockingIsItselfUndoable() = runTest {
        val viewModel = editor(document())

        viewModel.onSetElementsLocked(listOf("b"), locked = true)
        viewModel.await { it.element("b").locked }

        viewModel.onUndo()
        val undone = viewModel.await { !it.element("b").locked }
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)

        viewModel.onRedo()
        assertTrue(viewModel.await { it.element("b").locked }.element("b").locked)
    }

    @Test
    fun lockingWhatIsAlreadyLockedIsNotAnEdit() = runTest {
        val viewModel = editor(document())

        viewModel.onSetElementsLocked(listOf("b"), locked = false)
        viewModel.onSetElementsLocked(listOf("a", "c"), locked = false)

        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertFalse(after.canUndo)
    }

    @Test
    fun anElementEventForAnUnknownIdIsANoOp() = runTest {
        val viewModel = editor(document())
        val opened = viewModel.states.value.document

        viewModel.onUpdateElements(listOf(ShapeElement(id = "gone", frame = Frame(0f, 0f, 1f, 1f))))
        viewModel.onPreviewElements(listOf(ShapeElement(id = "gone", frame = Frame(0f, 0f, 1f, 1f))))
        viewModel.onFlipElements(listOf("gone"), FlipAxis.Vertical)
        viewModel.onSetElementsLocked(listOf("gone"), locked = true)

        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertEquals(opened, after.document)
        assertFalse(after.canUndo)
    }
}

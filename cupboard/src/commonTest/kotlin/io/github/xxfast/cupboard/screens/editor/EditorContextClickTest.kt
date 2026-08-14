package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The right-click, driven the way the canvas drives it: three elements on one
 * slide, so a click has something inside the selection and something outside it
 * to land on.
 */
class EditorContextClickTest {
    // Fixed ids, so a freshly built document compares equal to the opened one.
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                elements = listOf(
                    ShapeElement(id = "a", frame = Frame(0f, 0f, 100f, 100f)),
                    ShapeElement(id = "b", frame = Frame(120f, 0f, 100f, 100f)),
                    ShapeElement(id = "c", frame = Frame(240f, 0f, 100f, 100f)),
                ),
            ),
        ),
    )

    private fun EditorState.order(): List<String> = selectedSlide.elements.map { it.id }

    @Test
    fun clickingOutsideTheSelectionTakesItOver() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElements(listOf("a", "b"))
        viewModel.await { it.selectedElementIds.size == 2 }

        viewModel.onContextClick("c")
        val clicked = viewModel.await { it.selectedElementIds == listOf("c") }
        assertEquals("c", clicked.primaryElement?.id)
    }

    @Test
    fun clickingInsideTheSelectionLeavesItExactlyAsItIs() = runTest {
        val viewModel = editor(document())
        // Not in document order, and the click is not on the first: a menu acts
        // on the whole selection, primary element and all.
        viewModel.onSelectElements(listOf("c", "a", "b"))
        viewModel.await { it.selectedElementIds.size == 3 }

        viewModel.onContextClick("b")
        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertEquals(listOf("c", "a", "b"), after.selectedElementIds)
        assertEquals("c", after.primaryElement?.id)
    }

    @Test
    fun clickingEmptyCanvasClearsTheSelection() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElements(listOf("a", "b"))
        viewModel.await { it.selectedElementIds.size == 2 }

        viewModel.onContextClick(null)
        val cleared = viewModel.await { it.selectedElementIds.isEmpty() }
        assertEquals(document(), cleared.document)
    }

    @Test
    fun aContextClickIsNotAnEdit() = runTest {
        val viewModel = editor(document())

        viewModel.onContextClick("a")
        viewModel.onContextClick(null)
        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertEquals(document(), after.document)
        assertFalse(after.canUndo)
        assertFalse(after.canRedo)
    }

    @Test
    fun undoAfterAContextClickUndoesTheEditBeforeIt() = runTest {
        val viewModel = editor(document())

        viewModel.onDeleteElements(listOf("a"))
        val deleted = viewModel.await { it.selectedSlide.elements.size == 2 }
        assertTrue(deleted.canUndo)

        viewModel.onContextClick("b")
        val clicked = viewModel.await { it.selectedElementIds == listOf("b") }
        assertTrue(clicked.canUndo)

        viewModel.onUndo()
        val undone = viewModel.await { it.document == document() }
        assertEquals(listOf("a", "b", "c"), undone.order())
        assertFalse(undone.canUndo)
    }
}

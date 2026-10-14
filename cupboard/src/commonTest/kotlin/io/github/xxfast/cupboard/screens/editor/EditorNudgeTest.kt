package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.elementById
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The arrow keys on the canvas: a held run is a keyboard drag, previews while
 * the key is down and one history entry when it comes back up.
 */
class EditorNudgeTest {
    // Fixed ids, so a freshly built document compares equal to the opened one.
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "slide",
                title = "Nudge",
                elements = listOf(
                    ShapeElement(id = "a", frame = Frame(0f, 0f, 100f, 100f)),
                    ShapeElement(id = "b", frame = Frame(120f, 40f, 100f, 100f)),
                    TextElement(id = "text", frame = Frame(0f, 200f, 200f, 40f), text = "Hello"),
                ),
            ),
        ),
    )

    private fun EditorState.frame(id: String): Frame = selectedSlide.elementById(id)!!.frame

    @Test
    fun aHeldRunMovesTheSelectionAsOneUndoEntry() = runTest {
        val viewModel = editor(document())
        val before: Document = viewModel.await { true }.document
        viewModel.onSelectElements(listOf("a", "b"))
        viewModel.await { it.selectedElementIds.size == 2 }

        viewModel.onNudgeSelection(1f, 0f)
        viewModel.onNudgeSelection(1f, 0f)
        viewModel.onNudgeSelection(0f, 10f)
        val previewing = viewModel.await { it.frame("a") == Frame(2f, 10f, 100f, 100f) }
        assertTrue(previewing.isPreviewing)
        assertEquals(Frame(122f, 50f, 100f, 100f), previewing.frame("b"))
        assertFalse(previewing.canUndo)

        viewModel.onCommitNudge()
        val committed = viewModel.await { !it.isPreviewing && it.canUndo }
        assertEquals(Frame(2f, 10f, 100f, 100f), committed.frame("a"))

        viewModel.onUndo()
        val undone = viewModel.await { it.document == before }
        assertFalse(undone.canUndo)
    }

    @Test
    fun lockedElementsStayAndGroupsMoveAsAUnit() = runTest {
        val viewModel = editor(document())
        viewModel.onGroupElements(listOf("a", "b"))
        val grouped = viewModel.await { state -> state.selectedSlide.elements.any { it is GroupElement } }
        val group: GroupElement = grouped.selectedSlide.elements.filterIsInstance<GroupElement>().single()
        viewModel.onSetElementsLocked(listOf("text"), locked = true)
        viewModel.await { it.selectedSlide.elementById("text")!!.locked }
        viewModel.onSelectElements(listOf(group.id, "text"))
        viewModel.await { it.selectedElementIds.size == 2 }

        viewModel.onNudgeSelection(-10f, 0f)
        viewModel.onCommitNudge()
        val moved = viewModel.await { !it.isPreviewing && it.frame("a").x == -10f }
        assertEquals(110f, moved.frame("b").x)
        assertEquals(Frame(0f, 200f, 200f, 40f), moved.frame("text"))
    }

    @Test
    fun theCaretKeepsTheArrows() = runTest {
        val viewModel = editor(document())
        viewModel.onBeginTextEdit("text")
        val editing = viewModel.await { it.isEditingText }
        assertFalse(editing.canNudge)

        viewModel.onNudgeSelection(1f, 0f)
        viewModel.onCommitNudge()
        // A marker event behind them, so the two have been through the loop.
        viewModel.onToggleRulers()
        val after = viewModel.await { it.showRulers != editing.showRulers }
        assertTrue(after.isEditingText)
        assertEquals(Frame(0f, 200f, 200f, 40f), after.frame("text"))
        assertFalse(after.isPreviewing)
    }

    @Test
    fun anythingElseLandingMidRunSettlesItFirst() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElement("a")
        viewModel.await { it.selectedElementIds == listOf("a") }

        viewModel.onNudgeSelection(0f, 1f)
        viewModel.await { it.isPreviewing }
        viewModel.onSelectElement(null)
        val settled = viewModel.await { it.selectedElementIds.isEmpty() }
        assertFalse(settled.isPreviewing)
        assertTrue(settled.canUndo)
        assertEquals(Frame(0f, 1f, 100f, 100f), settled.frame("a"))

        // Its key coming up afterwards finds nothing left to settle.
        viewModel.onCommitNudge()
        viewModel.onUndo()
        val undone = viewModel.await { !it.canUndo }
        assertEquals(Frame(0f, 0f, 100f, 100f), undone.frame("a"))
    }
}

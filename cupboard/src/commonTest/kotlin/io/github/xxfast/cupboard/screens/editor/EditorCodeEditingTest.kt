package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The caret in a code block: the same session a text box gets, and the same one
 * history entry however many keystrokes went into it.
 *
 * Slide "one" carries a code block to type in, a locked one that refuses the
 * caret, and a shape that has nothing to put a caret in.
 */
class EditorCodeEditingTest {
    // Fixed ids, so a freshly built document compares equal to the opened one.
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                elements = listOf(
                    CodeElement(
                        id = "code",
                        frame = Frame(0f, 0f, 400f, 200f),
                        code = "val x = 1",
                    ),
                    CodeElement(
                        id = "locked",
                        frame = Frame(0f, 220f, 400f, 200f),
                        code = "val nope = 0",
                        locked = true,
                    ),
                    ShapeElement(id = "shape", frame = Frame(0f, 440f, 100f, 100f)),
                ),
            ),
            Slide(id = "two", title = "Two"),
        ),
    )

    private fun EditorState.code(id: String): String =
        (selectedSlide.elements.first { it.id == id } as CodeElement).code

    /** The block as it stands, with [code] typed into it: what a keystroke previews. */
    private fun EditorState.typed(id: String, code: String): CodeElement =
        (selectedSlide.elements.first { it.id == id } as CodeElement).copy(code = code)

    @Test
    fun beginningAnEditSelectsTheCodeBlockAndPutsTheCaretInIt() = runTest {
        val viewModel = editor(document())

        viewModel.onBeginTextEdit("code")
        val editing = viewModel.await { it.isEditingText }
        assertEquals("code", editing.editingElementId)
        assertEquals(listOf("code"), editing.selectedElementIds)
        assertEquals(EditorPane.Canvas, editing.focusedPane)
        assertFalse(editing.canUndo)
    }

    @Test
    fun aLockedCodeBlockRefusesTheCaret() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElement("shape")
        viewModel.await { it.selectedElementIds == listOf("shape") }

        viewModel.onBeginTextEdit("locked")
        val state = viewModel.await { it.selectedElementIds == listOf("shape") }
        assertNull(state.editingElementId)
    }

    @Test
    fun typingCodeThenEndingCostsOneUndoEntry() = runTest {
        val viewModel = editor(document())
        viewModel.onBeginTextEdit("code")
        viewModel.await { it.isEditingText }

        // One preview per keystroke, the way the canvas streams them.
        for (typed in listOf("val x = 1\n", "val x = 1\nval y", "val x = 1\nval y = 2")) {
            viewModel.onPreviewElements(listOf(viewModel.states.value.typed("code", typed)))
            viewModel.await { it.code("code") == typed }
        }

        val previewing = viewModel.states.value
        assertTrue(previewing.isPreviewing)
        assertFalse(previewing.canUndo)

        viewModel.onEndTextEdit()
        val committed = viewModel.await { !it.isEditingText }
        assertEquals("val x = 1\nval y = 2", committed.code("code"))
        assertTrue(committed.canUndo)
        assertFalse(committed.isPreviewing)

        // One entry for the whole session: a single undo lands on the code as it
        // was before the first keystroke.
        viewModel.onUndo()
        val undone = viewModel.await { it.code("code") == "val x = 1" }
        assertFalse(undone.canUndo)
    }

    @Test
    fun aCodeSessionThatChangedNothingMakesNoHistoryEntry() = runTest {
        val viewModel = editor(document())
        viewModel.onBeginTextEdit("code")
        viewModel.await { it.isEditingText }

        // Typed and taken back out again: the document is where it started.
        viewModel.onPreviewElements(listOf(viewModel.states.value.typed("code", "val x = 2")))
        viewModel.await { it.code("code") == "val x = 2" }
        viewModel.onPreviewElements(listOf(viewModel.states.value.typed("code", "val x = 1")))
        viewModel.await { it.code("code") == "val x = 1" }

        viewModel.onEndTextEdit()
        val ended = viewModel.await { !it.isEditingText }
        assertEquals("val x = 1", ended.code("code"))
        assertFalse(ended.canUndo)
        assertFalse(ended.isPreviewing)
    }

    @Test
    fun selectingAnotherElementEndsTheCodeEditAndCommitsIt() = runTest {
        val viewModel = editor(document())
        viewModel.onBeginTextEdit("code")
        viewModel.await { it.isEditingText }
        viewModel.onPreviewElements(listOf(viewModel.states.value.typed("code", "val x = 3")))
        viewModel.await { it.code("code") == "val x = 3" }

        // The auto-end: an event that isn't allowed to land mid-edit settles the
        // session first, so the typing is its own history entry.
        viewModel.onSelectElement("shape")
        val selected = viewModel.await { it.selectedElementIds == listOf("shape") }
        assertNull(selected.editingElementId)
        assertEquals("val x = 3", selected.code("code"))
        assertTrue(selected.canUndo)
    }

    @Test
    fun theEditVerbsAreTheCodeFieldsWhileTheCaretIsInABlock() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElement("code")
        val selected = viewModel.await { it.selectedElementIds == listOf("code") }
        assertTrue(selected.canDelete)
        assertTrue(selected.canCut)

        viewModel.onBeginTextEdit("code")
        val editing = viewModel.await { it.isEditingText }
        assertFalse(editing.canDelete)
        assertFalse(editing.canCut)
        assertFalse(editing.canCopy)
        assertFalse(editing.canDuplicate)
    }
}

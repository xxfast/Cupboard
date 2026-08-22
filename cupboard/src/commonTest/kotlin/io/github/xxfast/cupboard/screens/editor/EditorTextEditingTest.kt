package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The caret on the canvas: what begins an edit session, what ends it, and the
 * one history entry a session is worth however many keystrokes went into it.
 *
 * Slide "one" carries a text element to type in, a locked one that refuses the
 * caret and a shape that has no text to place it in.
 */
class EditorTextEditingTest {
    // Fixed ids, so a freshly built document compares equal to the opened one.
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                elements = listOf(
                    TextElement(id = "text", frame = Frame(0f, 0f, 200f, 40f), text = "Hello"),
                    TextElement(
                        id = "locked",
                        frame = Frame(0f, 60f, 200f, 40f),
                        text = "Nope",
                        locked = true,
                    ),
                    ShapeElement(id = "shape", frame = Frame(0f, 120f, 100f, 100f)),
                ),
            ),
            Slide(id = "two", title = "Two"),
        ),
    )

    private fun EditorState.text(id: String): String =
        (selectedSlide.elements.first { it.id == id } as TextElement).text

    /** The element as it stands, with [text] typed into it: what a keystroke previews. */
    private fun EditorState.typed(id: String, text: String): TextElement =
        (selectedSlide.elements.first { it.id == id } as TextElement).copy(text = text)

    @Test
    fun beginningAnEditSelectsTheElementAndPutsTheCaretInIt() = runTest {
        val viewModel = editor(document())

        viewModel.onBeginTextEdit("text")
        val editing = viewModel.await { it.isEditingText }
        assertEquals("text", editing.editingElementId)
        assertEquals(listOf("text"), editing.selectedElementIds)
        assertEquals(EditorPane.Canvas, editing.focusedPane)
        assertFalse(editing.canUndo)
    }

    @Test
    fun aLockedElementRefusesTheCaret() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElement("shape")
        viewModel.await { it.selectedElementIds == listOf("shape") }

        viewModel.onBeginTextEdit("locked")
        val state = viewModel.await { it.selectedElementIds == listOf("shape") }
        assertNull(state.editingElementId)
    }

    @Test
    fun anElementWithNoTextAndAnUnknownIdBeginNothing() = runTest {
        val viewModel = editor(document())

        viewModel.onBeginTextEdit("shape")
        viewModel.onBeginTextEdit("nowhere")
        viewModel.onSelectElement("text")
        val state = viewModel.await { it.selectedElementIds == listOf("text") }
        assertNull(state.editingElementId)
    }

    @Test
    fun typingThenEndingCostsOneUndoEntry() = runTest {
        val viewModel = editor(document())
        viewModel.onBeginTextEdit("text")
        viewModel.await { it.isEditingText }

        // One preview per keystroke, the way the canvas streams them.
        for (typed in listOf("Hell", "Hel", "Hello!")) {
            viewModel.onPreviewElements(listOf(viewModel.states.value.typed("text", typed)))
            viewModel.await { it.text("text") == typed }
        }

        val previewing = viewModel.states.value
        assertTrue(previewing.isPreviewing)
        assertFalse(previewing.canUndo)

        viewModel.onEndTextEdit()
        val committed = viewModel.await { !it.isEditingText }
        assertEquals("Hello!", committed.text("text"))
        assertTrue(committed.canUndo)
        assertFalse(committed.isPreviewing)

        // One entry for the whole session: a single undo lands on the text as
        // it was before the first keystroke.
        viewModel.onUndo()
        val undone = viewModel.await { it.text("text") == "Hello" }
        assertFalse(undone.canUndo)
    }

    @Test
    fun aSessionThatChangedNothingMakesNoHistoryEntry() = runTest {
        val viewModel = editor(document())
        viewModel.onBeginTextEdit("text")
        viewModel.await { it.isEditingText }

        // Typed and taken back out again: the document is where it started.
        viewModel.onPreviewElements(listOf(viewModel.states.value.typed("text", "Hello?")))
        viewModel.await { it.text("text") == "Hello?" }
        viewModel.onPreviewElements(listOf(viewModel.states.value.typed("text", "Hello")))
        viewModel.await { it.text("text") == "Hello" }

        viewModel.onEndTextEdit()
        val ended = viewModel.await { !it.isEditingText }
        assertEquals("Hello", ended.text("text"))
        assertFalse(ended.canUndo)
        assertFalse(ended.isPreviewing)
    }

    @Test
    fun selectingAnotherElementEndsTheEditAndCommitsTheText() = runTest {
        val viewModel = editor(document())
        viewModel.onBeginTextEdit("text")
        viewModel.await { it.isEditingText }
        viewModel.onPreviewElements(listOf(viewModel.states.value.typed("text", "Typed")))
        viewModel.await { it.text("text") == "Typed" }

        viewModel.onSelectElement("shape")
        val selected = viewModel.await { it.selectedElementIds == listOf("shape") }
        assertNull(selected.editingElementId)
        assertEquals("Typed", selected.text("text"))
        assertTrue(selected.canUndo)
    }

    @Test
    fun selectingAnotherSlideEndsTheEditAndCommitsTheText() = runTest {
        val viewModel = editor(document())
        viewModel.onBeginTextEdit("text")
        viewModel.await { it.isEditingText }
        viewModel.onPreviewElements(listOf(viewModel.states.value.typed("text", "Typed")))
        viewModel.await { it.text("text") == "Typed" }

        viewModel.onSelectSlide("two")
        val moved = viewModel.await { it.selectedSlideId == "two" }
        assertNull(moved.editingElementId)
        assertTrue(moved.canUndo)

        viewModel.onSelectSlide("one")
        val back = viewModel.await { it.selectedSlideId == "one" }
        assertEquals("Typed", back.text("text"))
    }

    @Test
    fun theEditVerbsAreTheTextFieldsWhileTheCaretIsInAnElement() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElement("text")
        val selected = viewModel.await { it.selectedElementIds == listOf("text") }
        assertTrue(selected.canDelete)
        assertTrue(selected.canCut)
        assertTrue(selected.canCopy)
        assertTrue(selected.canDuplicate)

        viewModel.onBeginTextEdit("text")
        val editing = viewModel.await { it.isEditingText }
        assertFalse(editing.canDelete)
        assertFalse(editing.canCut)
        assertFalse(editing.canCopy)
        assertFalse(editing.canDuplicate)

        viewModel.onEndTextEdit()
        val ended = viewModel.await { !it.isEditingText }
        assertTrue(ended.canDelete)
    }

    @Test
    fun undoingWhileEditingEndsTheEditFirstAndUndoesTheTypedText() = runTest {
        val viewModel = editor(document())
        viewModel.onBeginTextEdit("text")
        viewModel.await { it.isEditingText }
        viewModel.onPreviewElements(listOf(viewModel.states.value.typed("text", "Typed")))
        viewModel.await { it.text("text") == "Typed" }

        // The session is settled before the undo lands, so the undo has the
        // typing to take back rather than the edit before it.
        viewModel.onUndo()
        val undone = viewModel.await { !it.isEditingText }
        assertEquals("Hello", undone.text("text"))
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)
    }

    @Test
    fun theChromeToggleAndTheTypingLeaveTheCaretWhereItIs() = runTest {
        val viewModel = editor(document())
        viewModel.onBeginTextEdit("text")
        viewModel.await { it.isEditingText }

        viewModel.onToggleNotes()
        val toggled = viewModel.await { !it.showNotes }
        assertTrue(toggled.isEditingText)

        viewModel.onPreviewElements(listOf(viewModel.states.value.typed("text", "Typed")))
        val typed = viewModel.await { it.text("text") == "Typed" }
        assertTrue(typed.isEditingText)
    }
}

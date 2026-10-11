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

/**
 * The speaker-notes strip: typing in it is one history entry per visit of the
 * caret, anything else the editor does settles it first, and its height is view
 * state with a floor.
 */
class EditorNotesTest {
    // Fixed ids, so a freshly built document compares equal to the opened one.
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(id = "one", title = "One", notes = "Hello"),
            Slide(id = "two", title = "Two"),
        ),
    )

    private fun EditorState.notes(id: String): String = document.slides.first { it.id == id }.notes

    @Test
    fun typingThenLeavingCostsOneUndoEntry() = runTest {
        val viewModel = editor(document())

        // One event per keystroke, the way the strip streams them.
        for (typed in listOf("Hello,", "Hello, w", "Hello, world")) {
            viewModel.onEditNotes(typed)
            viewModel.await { it.notes("one") == typed }
        }

        val typing = viewModel.states.value
        assertTrue(typing.isEditingNotes)
        assertTrue(typing.isPreviewing)
        assertFalse(typing.canUndo)

        viewModel.onEndNotesEdit()
        val committed = viewModel.await { !it.isEditingNotes }
        assertEquals("Hello, world", committed.notes("one"))
        assertTrue(committed.canUndo)
        assertFalse(committed.isPreviewing)

        viewModel.onUndo()
        val undone = viewModel.await { it.notes("one") == "Hello" }
        assertFalse(undone.canUndo)
    }

    @Test
    fun aVisitThatChangedNothingMakesNoHistoryEntry() = runTest {
        val viewModel = editor(document())

        viewModel.onEditNotes("Hello?")
        viewModel.await { it.notes("one") == "Hello?" }
        viewModel.onEditNotes("Hello")
        viewModel.await { it.isEditingNotes && it.notes("one") == "Hello" }

        viewModel.onEndNotesEdit()
        val ended = viewModel.await { !it.isEditingNotes }
        assertFalse(ended.canUndo)
        assertFalse(ended.isPreviewing)
    }

    @Test
    fun selectingAnotherSlideSettlesTheNotesFirst() = runTest {
        val viewModel = editor(document())
        viewModel.onEditNotes("Typed")
        viewModel.await { it.isEditingNotes }

        // No EndNotesEdit from the shell: the click on the navigator is enough.
        viewModel.onSelectSlide("two")
        val moved = viewModel.await { it.selectedSlideId == "two" }
        assertFalse(moved.isEditingNotes)
        assertEquals("Typed", moved.notes("one"))
        assertEquals("", moved.notes("two"))
        assertTrue(moved.canUndo)
    }

    @Test
    fun aCaretInTheNotesGreysTheEditVerbsAndALateBlurLeavesACanvasCaretAlone() = runTest {
        val text = TextElement(id = "text", frame = Frame(0f, 0f, 200f, 40f), text = "Hi")
        val deck: Document = document()
        val first: Slide = deck.slides[0].copy(elements = listOf(text))
        val viewModel = editor(deck.copy(slides = listOf(first, deck.slides[1])))
        viewModel.onFocusPane(EditorPane.Navigator)
        assertTrue(viewModel.await { it.focusedPane == EditorPane.Navigator }.canDelete)

        // The caret alone is enough, before anything is typed: Backspace in an
        // empty strip must not take the slide.
        viewModel.onBeginNotesEdit()
        val caret = viewModel.await { it.isEditingNotes }
        assertFalse(caret.canDelete)
        assertFalse(caret.canCopy)

        // A double click on the canvas opens the text, and the strip's blur
        // follows it in.
        viewModel.onBeginTextEdit("text")
        viewModel.onEndNotesEdit()
        viewModel.onToggleSidebar()
        val editing = viewModel.await { !it.sidebarOpen }
        assertFalse(editing.isEditingNotes)
        assertTrue(editing.isEditingText)
    }

    @Test
    fun chromeLeavesTheCaretInTheNotesButHidingTheStripDoesNot() = runTest {
        val viewModel = editor(document())
        viewModel.onEditNotes("Typed")
        viewModel.await { it.isEditingNotes }

        viewModel.onToggleSidebar()
        val chrome = viewModel.await { !it.sidebarOpen }
        assertTrue(chrome.isEditingNotes)

        viewModel.onToggleNotes()
        val hidden = viewModel.await { !it.showNotes }
        assertFalse(hidden.isEditingNotes)
        assertTrue(hidden.canUndo)
    }

    @Test
    fun theStripResizesDownToItsFloorAndKeepsItsHeightWhileHidden() = runTest {
        val viewModel = editor(document())
        assertEquals(NotesDefaultHeight, viewModel.states.value.notesHeight)

        viewModel.onResizeNotes(240f)
        viewModel.await { it.notesHeight == 240f }

        viewModel.onToggleNotes()
        viewModel.onToggleNotes()
        val shown = viewModel.await { it.showNotes && it.notesHeight == 240f }
        assertFalse(shown.canUndo)

        viewModel.onResizeNotes(10f)
        viewModel.await { it.notesHeight == NotesMinHeight }
    }
}

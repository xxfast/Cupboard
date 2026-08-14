package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The keyboard focus, and the Edit-menu verbs that read it: the same Cut acts on
 * a slide with the navigator focused and on the selection with the canvas
 * focused, the way Keynote's does. Slide "one" carries three elements so a
 * canvas verb has something to take; the deck has three slides so a navigator
 * one does too.
 */
class EditorFocusTest {
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
            Slide(id = "two", title = "Two"),
            Slide(id = "three", title = "Three"),
        ),
    )

    private fun EditorState.element(id: String): Element =
        selectedSlide.elements.first { it.id == id }

    private fun EditorState.order(): List<String> = selectedSlide.elements.map { it.id }

    private fun EditorState.slideIds(): List<String> = document.slides.map { it.id }

    private fun EditorState.titles(): List<String> = document.slides.map { it.title }

    @Test
    fun theEditorOpensWithTheCanvasFocused() = runTest {
        val viewModel = editor(document())
        assertEquals(EditorPane.Canvas, viewModel.states.value.focusedPane)
    }

    @Test
    fun focusFollowsWhicheverPaneWasLastInteractedWith() = runTest {
        val viewModel = editor(document())

        viewModel.onSelectSlide("two")
        val navigator = viewModel.await { it.selectedSlideId == "two" }
        assertEquals(EditorPane.Navigator, navigator.focusedPane)

        viewModel.onSelectSlide("one")
        viewModel.await { it.selectedSlideId == "one" }
        viewModel.onSelectElement("a")
        val canvas = viewModel.await { it.selectedElementIds == listOf("a") }
        assertEquals(EditorPane.Canvas, canvas.focusedPane)
    }

    @Test
    fun aContextClickFocusesTheCanvas() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectSlide("one")
        viewModel.await { it.focusedPane == EditorPane.Navigator }

        viewModel.onContextClick("b")
        val clicked = viewModel.await { it.selectedElementIds == listOf("b") }
        assertEquals(EditorPane.Canvas, clicked.focusedPane)
    }

    @Test
    fun cuttingWithTheNavigatorFocusedTakesTheSlideAndPasteBringsItBack() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectSlide("two")
        viewModel.await { it.selectedSlideId == "two" }

        viewModel.onCut()
        val cut = viewModel.await { it.document.slides.size == 2 }
        assertEquals(listOf("one", "three"), cut.slideIds())
        assertTrue(cut.canPaste)
        assertTrue(cut.canUndo)

        viewModel.onSelectSlide("one")
        viewModel.await { it.selectedSlideId == "one" }
        viewModel.onPaste()
        val pasted = viewModel.await { it.document.slides.size == 3 }
        assertEquals(listOf("One", "Two", "Three"), pasted.titles())
    }

    @Test
    fun cuttingWithTheCanvasFocusedTakesTheSelectedElements() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElements(listOf("a", "b"))
        viewModel.await { it.selectedElementIds.size == 2 }

        viewModel.onCut()
        val cut = viewModel.await { it.selectedSlide.elements.size == 1 }
        assertEquals(listOf("c"), cut.order())
        // The deck is untouched: the canvas verb never reached the navigator.
        assertEquals(listOf("one", "two", "three"), cut.slideIds())

        viewModel.onPaste()
        val pasted = viewModel.await { it.selectedSlide.elements.size == 3 }
        assertEquals(3, pasted.document.slides.size)
        assertEquals(pasted.selectedSlide.elements.takeLast(2).map { it.id }, pasted.selectedElementIds)
    }

    @Test
    fun deletingWithTheNavigatorFocusedTakesTheSlide() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectSlide("two")
        viewModel.await { it.selectedSlideId == "two" }

        viewModel.onDelete()
        val deleted = viewModel.await { it.document.slides.size == 2 }
        assertEquals(listOf("one", "three"), deleted.slideIds())
        // Delete is not a cut: it leaves the clipboard as it found it.
        assertFalse(deleted.canPaste)
    }

    @Test
    fun copyingWithTheNavigatorFocusedTakesTheSlide() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectSlide("two")
        viewModel.await { it.selectedSlideId == "two" }

        viewModel.onCopy()
        viewModel.await { it.canPaste }
        viewModel.onPaste()
        val pasted = viewModel.await { it.document.slides.size == 4 }
        assertEquals(listOf("One", "Two", "Two", "Three"), pasted.titles())
        // Copying is not an edit, so the paste is the only history entry.
        viewModel.onUndo()
        assertEquals(document(), viewModel.await { !it.canUndo }.document)
    }

    @Test
    fun copyingWithTheCanvasFocusedTakesTheElements() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElement("a")
        viewModel.await { it.selectedElementIds == listOf("a") }

        viewModel.onCopy()
        viewModel.await { it.canPaste }
        viewModel.onPaste()
        val pasted = viewModel.await { it.selectedSlide.elements.size == 4 }
        assertEquals(3, pasted.document.slides.size)
    }

    @Test
    fun duplicatingFollowsTheFocusToo() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectSlide("two")
        viewModel.await { it.selectedSlideId == "two" }

        viewModel.onDuplicate()
        val duplicated = viewModel.await { it.document.slides.size == 4 }
        assertEquals(listOf("One", "Two", "Two", "Three"), duplicated.titles())
        // Duplicating never touches the clipboard, whichever pane it lands in.
        assertFalse(duplicated.canPaste)

        viewModel.onSelectSlide("one")
        viewModel.await { it.selectedSlideId == "one" }
        viewModel.onSelectElement("a")
        viewModel.await { it.selectedElementIds == listOf("a") }
        viewModel.onDuplicate()
        val copied = viewModel.await { it.selectedSlide.elements.size == 4 }
        assertEquals(4, copied.document.slides.size)
        assertEquals(Frame(24f, 24f, 100f, 100f), copied.selectedSlide.elements.last().frame)
    }

    @Test
    fun theVerbsAreLiveInTheNavigatorWithNoElementSelected() = runTest {
        val viewModel = editor(document())
        val opened = viewModel.states.value
        // Nothing selected on the canvas: every verb but paste is greyed out.
        assertFalse(opened.canCut)
        assertFalse(opened.canCopy)
        assertFalse(opened.canDuplicate)
        assertFalse(opened.canDelete)

        viewModel.onFocusPane(EditorPane.Navigator)
        val navigator = viewModel.await { it.focusedPane == EditorPane.Navigator }
        // The selection never left, the pane the verbs read did.
        assertTrue(navigator.selectedElementIds.isEmpty())
        assertTrue(navigator.canCut)
        assertTrue(navigator.canCopy)
        assertTrue(navigator.canDuplicate)
        assertTrue(navigator.canDelete)
    }

    @Test
    fun aLockedOnlySelectionGreysEverythingButCopy() = runTest {
        val viewModel = editor(document())
        viewModel.onSetElementsLocked(listOf("a"), locked = true)
        viewModel.await { it.element("a").locked }

        viewModel.onSelectElement("a")
        val locked = viewModel.await { it.selectedElementIds == listOf("a") }
        assertEquals(EditorPane.Canvas, locked.focusedPane)
        assertFalse(locked.canCut)
        assertFalse(locked.canDuplicate)
        assertFalse(locked.canDelete)
        // A copy is not an edit, so the lock has nothing to say about it.
        assertTrue(locked.canCopy)
    }

    @Test
    fun focusingAPaneIsNotAnEdit() = runTest {
        val viewModel = editor(document())

        viewModel.onFocusPane(EditorPane.Navigator)
        viewModel.await { it.focusedPane == EditorPane.Navigator }
        viewModel.onFocusPane(EditorPane.Canvas)
        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertEquals(EditorPane.Canvas, after.focusedPane)
        assertEquals(document(), after.document)
        assertFalse(after.canUndo)
        assertFalse(after.canRedo)
    }

    @Test
    fun undoingAGenericCutRestoresTheDeckInOneStep() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectSlide("two")
        viewModel.await { it.selectedSlideId == "two" }

        viewModel.onCut()
        viewModel.await { it.document.slides.size == 2 }

        viewModel.onUndo()
        val undone = viewModel.await { it.document.slides.size == 3 }
        assertEquals(document(), undone.document)
        // One entry, exactly what CutSlide would have made.
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)
    }
}

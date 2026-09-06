package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The deck as a document rather than as slides: what it is called, and whether
 * what you just did has reached disk yet. Both are what a window's title bar
 * shows, so both come out of the loop like everything else.
 */
class EditorDocumentTest {
    private fun document(): Document = Document(
        id = "doc",
        name = "Talk",
        slides = listOf(Slide(id = "one", title = "One")),
    )

    @Test
    fun theTitleIsTheDecksName() = runTest {
        val viewModel = editor(document())

        assertEquals("Talk", viewModel.await { it.title.isNotEmpty() }.title)
    }

    @Test
    fun renamingIsAnEditLikeAnyOther() = runTest {
        val viewModel = editor(document())

        viewModel.onRenameDocument("Better Talk")
        val renamed = viewModel.await { it.title == "Better Talk" }
        assertEquals("Better Talk", renamed.document.name)
        assertTrue(renamed.canUndo)

        viewModel.onUndo()
        assertEquals("Talk", viewModel.await { it.title == "Talk" }.document.name)
    }

    @Test
    fun aNameTheDeckAlreadyHasChangesNothing() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectSlide("one")
        assertFalse(viewModel.await { it.selectedSlideId == "one" }.canUndo)

        // Blank is not a name, and the name it is already on is not a change:
        // neither may cost an undo entry.
        viewModel.onRenameDocument("   ")
        viewModel.onRenameDocument("Talk")
        viewModel.onSelectSlide("one")

        assertFalse(viewModel.await { it.selectedSlideId == "one" }.canUndo)
        assertEquals("Talk", viewModel.states.value.document.name)
    }

    @Test
    fun anEditIsPendingUntilItIsWritten() = runTest {
        val viewModel = editor(document())
        // Nothing has been touched, so nothing is on its way anywhere.
        assertFalse(viewModel.await { it.title == "Talk" }.savePending)

        viewModel.onRenameDocument("Renamed")

        // True in the very state the edit arrives in: a title bar must never
        // show the new name and no dot beside it. The write itself is debounced,
        // and `EditorAutosaveTest` has the other end of this.
        assertTrue(viewModel.await { it.document.name == "Renamed" }.savePending)
    }
}

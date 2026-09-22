package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.sources
import io.github.xxfast.cupboard.document.withSource
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The versions of a code block, from the editor's side: which one is on show,
 * the four edits that shape the list, and the promise that one of them is one
 * undo.
 *
 * Slide "one" carries a block with three versions, a locked one that refuses
 * every edit, and a shape that has no versions at all.
 */
class EditorCodeVersionsTest {
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
                        code = "one",
                        versions = listOf("two", "three"),
                    ),
                    CodeElement(
                        id = "locked",
                        frame = Frame(0f, 220f, 400f, 200f),
                        code = "nope",
                        versions = listOf("still nope"),
                        locked = true,
                    ),
                    ShapeElement(id = "shape", frame = Frame(0f, 440f, 100f, 100f)),
                ),
            ),
            Slide(id = "two", title = "Two"),
        ),
    )

    private fun EditorState.block(id: String = "code"): CodeElement =
        selectedSlide.elements.first { it.id == id } as CodeElement

    /** The block as it stands with [text] typed into the version on show. */
    private fun EditorState.typed(text: String): CodeElement =
        block().withSource(codeVersion, text)

    private suspend fun TestScope.selected(): EditorViewModel {
        val viewModel = editor(document())
        viewModel.onSelectElement("code")
        viewModel.await { it.selectedElementIds == listOf("code") }
        return viewModel
    }

    @Test
    fun theSelectedBlockShowsThePickedVersionAndEveryOtherShowsItsFirst() = runTest {
        val viewModel = selected()

        viewModel.onSelectCodeVersion(2)
        val state = viewModel.await { it.codeVersion == 2 }
        assertEquals(3, state.codeVersionCount)
        assertEquals("three", state.shownSource(state.block()))
        assertEquals("nope", state.shownSource(state.block("locked")))
        // Looking is not editing.
        assertFalse(state.canUndo)
    }

    @Test
    fun pickingAVersionTheBlockDoesNotHaveClampsOntoOneItDoes() = runTest {
        val viewModel = selected()

        viewModel.onSelectCodeVersion(9)
        assertEquals(2, viewModel.await { it.codeVersion == 2 }.codeVersion)

        viewModel.onSelectCodeVersion(-4)
        assertEquals(0, viewModel.await { it.codeVersion == 0 }.codeVersion)
    }

    @Test
    fun theVersionResetsWhenTheSelectionMovesOn() = runTest {
        val viewModel = selected()
        viewModel.onSelectCodeVersion(2)
        viewModel.await { it.codeVersion == 2 }

        viewModel.onSelectElement("shape")
        assertEquals(0, viewModel.await { it.selectedElementIds == listOf("shape") }.codeVersion)

        viewModel.onSelectElement("code")
        viewModel.await { it.selectedElementIds == listOf("code") }
        viewModel.onSelectCodeVersion(1)
        viewModel.await { it.codeVersion == 1 }

        viewModel.onSelectSlide("two")
        assertEquals(0, viewModel.await { it.selectedSlideId == "two" }.codeVersion)
    }

    @Test
    fun addingAVersionCopiesTheOneOnShowAndMovesToIt() = runTest {
        val viewModel = selected()
        viewModel.onSelectCodeVersion(1)
        viewModel.await { it.codeVersion == 1 }

        viewModel.onAddCodeVersion("code")
        val added = viewModel.await { it.block().sources.size == 4 }
        assertEquals(listOf("one", "two", "two", "three"), added.block().sources)
        assertEquals(2, added.codeVersion)
        assertTrue(added.canUndo)

        // One entry: the list, and the version on show, come back together.
        viewModel.onUndo()
        val undone = viewModel.await { it.block().sources.size == 3 }
        assertEquals(listOf("one", "two", "three"), undone.block().sources)
        assertFalse(undone.canUndo)
    }

    @Test
    fun removingAVersionKeepsTheEditorOnTheTextItWasOn() = runTest {
        val viewModel = selected()
        viewModel.onSelectCodeVersion(2)
        viewModel.await { it.codeVersion == 2 }

        viewModel.onRemoveCodeVersion("code", 1)
        val removed = viewModel.await { it.block().sources.size == 2 }
        assertEquals(listOf("one", "three"), removed.block().sources)
        assertEquals(1, removed.codeVersion)
        assertEquals("three", removed.shownSource(removed.block()))

        viewModel.onUndo()
        val undone = viewModel.await { it.block().sources.size == 3 }
        assertEquals(listOf("one", "two", "three"), undone.block().sources)
    }

    @Test
    fun movingAVersionCarriesTheEditorWithIt() = runTest {
        val viewModel = selected()
        viewModel.onSelectCodeVersion(0)
        viewModel.await { it.codeVersion == 0 }

        viewModel.onMoveCodeVersion("code", 0, 2)
        val moved = viewModel.await { it.block().sources == listOf("two", "three", "one") }
        assertEquals(2, moved.codeVersion)
        assertTrue(moved.canUndo)
    }

    @Test
    fun aStaleOrPointlessVersionEditChangesNothing() = runTest {
        val viewModel = selected()

        // An id that names nothing, an element that is not a code block, a
        // locked block, an index the block does not have, and a move that lands
        // where it started: none of them is an edit.
        viewModel.onAddCodeVersion("gone")
        viewModel.onAddCodeVersion("shape")
        viewModel.onAddCodeVersion("locked")
        viewModel.onRemoveCodeVersion("code", 9)
        viewModel.onMoveCodeVersion("code", 1, 1)

        // A real edit behind them, so the wait has something to land on.
        viewModel.onSelectCodeVersion(1)
        val state = viewModel.await { it.codeVersion == 1 }
        assertFalse(state.canUndo)
        assertEquals(listOf("one", "two", "three"), state.block().sources)
        assertEquals(listOf("nope", "still nope"), state.block("locked").sources)
    }

    @Test
    fun removingTheOnlyVersionLeavesTheBlockWithItsSource() = runTest {
        val viewModel = editor(
            Document(
                id = "doc",
                slides = listOf(
                    Slide(
                        id = "one",
                        elements = listOf(
                            CodeElement(
                                id = "code",
                                frame = Frame(0f, 0f, 10f, 10f),
                                code = "only",
                            ),
                        ),
                    ),
                ),
            ),
        )
        viewModel.onSelectElement("code")
        viewModel.await { it.selectedElementIds == listOf("code") }

        viewModel.onRemoveCodeVersion("code", 0)
        viewModel.onSelectCodeVersion(0)
        val state = viewModel.await { it.codeVersion == 0 }
        assertEquals(listOf("only"), state.block().sources)
        assertFalse(state.canUndo)
    }

    @Test
    fun typingIntoAVersionWritesThatVersionAndLeavesTheFirstAlone() = runTest {
        val viewModel = selected()
        viewModel.onSelectCodeVersion(1)
        viewModel.await { it.codeVersion == 1 }

        viewModel.onBeginTextEdit("code")
        viewModel.await { it.isEditingText }

        // One preview per keystroke, into the version on show.
        for (text in listOf("tw", "two point")) {
            viewModel.onPreviewElements(listOf(viewModel.states.value.typed(text)))
            viewModel.await { it.shownSource(it.block()) == text }
        }

        viewModel.onEndTextEdit()
        val committed = viewModel.await { !it.isEditingText }
        assertEquals(listOf("one", "two point", "three"), committed.block().sources)
        assertEquals("one", committed.block().code)
        assertEquals(1, committed.codeVersion)

        // Still one session, so still one undo.
        viewModel.onUndo()
        val undone = viewModel.await { it.block().sources == listOf("one", "two", "three") }
        assertFalse(undone.canUndo)
    }
}

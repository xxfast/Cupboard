package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.LineElement
import io.github.xxfast.cupboard.document.LineEnd
import io.github.xxfast.cupboard.document.LineStyle
import io.github.xxfast.cupboard.document.Point
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.StrokeStyle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The selected slide's line with [id]. */
private fun EditorState.line(id: String): LineElement =
    selectedSlide.elements.first { it.id == id } as LineElement

/** Line styles through the loop, [EditorObjectStylesTest]'s four events over lines. */
class EditorLineStylesTest {
    private fun line(id: String, locked: Boolean = false): LineElement = LineElement(
        id = id,
        start = Point(0f, 0f),
        end = Point(100f, 0f),
        strokeColor = 0xFFFFFFFF,
        endEnd = LineEnd.Arrow,
        locked = locked,
    )

    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                elements = listOf(line("line"), line("locked", locked = true)),
            ),
        ),
    )

    @Test
    fun applyingAStyleDressesUnlockedLinesAndCostsOneUndo() = runTest {
        val viewModel = editor(document())
        val dashed: LineStyle =
            viewModel.states.value.lineStyles.first { it.id == "line-long-dash" }

        viewModel.onApplyLineStyle(listOf("line", "locked"), dashed.id)
        val styled = viewModel.await { it.line("line").strokeStyle == StrokeStyle.LongDash }

        assertEquals(dashed.strokeWidth, styled.line("line").strokeWidth)
        assertEquals(LineEnd.None, styled.line("line").endEnd)
        assertEquals(StrokeStyle.Solid, styled.line("locked").strokeStyle)

        viewModel.onUndo()
        val back = viewModel.await { !it.canUndo }
        assertEquals(document().slides, back.document.slides)
    }

    @Test
    fun savingRenamingAndDeletingAStyleAreEachOneEdit() = runTest {
        val viewModel = editor(document())
        assertEquals(6, viewModel.states.value.lineStyles.size)

        viewModel.onSaveLineStyle("line", "Mine")
        val saved = viewModel.await { state -> state.lineStyles.any { it.name == "Mine" } }
        val mine: LineStyle = saved.lineStyles.first { it.name == "Mine" }
        assertEquals(7, saved.lineStyles.size)
        assertEquals(LineEnd.Arrow, mine.endEnd)

        viewModel.onRenameLineStyle(mine.id, "Ours")
        val renamed = viewModel.await { state -> state.lineStyles.any { it.name == "Ours" } }
        assertEquals(mine.copy(name = "Ours"), renamed.lineStyles.first { it.id == mine.id })

        viewModel.onDeleteLineStyle(mine.id)
        val deleted = viewModel.await { state -> state.lineStyles.none { it.id == mine.id } }
        assertEquals(6, deleted.lineStyles.size)

        // Three edits, three undos, and the deletion is the first one back.
        viewModel.onUndo()
        val undone = viewModel.await { state -> state.lineStyles.any { it.id == mine.id } }
        assertEquals("Ours", undone.lineStyles.first { it.id == mine.id }.name)
    }

    @Test
    fun aStyleOrALineNoOneAnswersToIsANoOp() = runTest {
        val viewModel = editor(document())

        viewModel.onApplyLineStyle(listOf("line"), "no-such-style")
        viewModel.onApplyLineStyle(listOf("locked"), "line-dots")
        viewModel.onSaveLineStyle("no-such-line", "Nope")
        viewModel.onDeleteLineStyle("no-such-style")
        viewModel.onRenameLineStyle("line-solid", "Solid")
        viewModel.onToggleNotes()
        val settled = viewModel.await { !it.showNotes }

        assertFalse(settled.canUndo)
        assertEquals(6, settled.lineStyles.size)
        assertEquals(document().slides, settled.document.slides)
    }
}

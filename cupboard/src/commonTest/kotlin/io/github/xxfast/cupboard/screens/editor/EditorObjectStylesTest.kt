package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.ElementDefaults
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ObjectStyle
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextFont
import io.github.xxfast.cupboard.document.shapeElement
import io.github.xxfast.cupboard.document.textBoxElement
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The selected slide's element with [id], as whatever it is expected to be. */
private inline fun <reified T> EditorState.element(id: String): T =
    selectedSlide.elements.first { it.id == id } as T

/**
 * Object styles through the loop: dressing shapes in a saved look, keeping the
 * library, and writing an element's look back into the deck's defaults.
 */
class EditorObjectStylesTest {
    private val box = Frame(0f, 0f, 100f, 100f)

    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                elements = listOf(
                    ShapeElement(id = "shape", frame = box),
                    ShapeElement(id = "locked", frame = box, locked = true),
                    TextElement(id = "text", frame = box, text = "Hi"),
                ),
            ),
        ),
    )

    @Test
    fun applyingAStyleDressesUnlockedShapesAndCostsOneUndo() = runTest {
        val viewModel = editor(document())
        val warning: ObjectStyle = viewModel.states.value.objectStyles.first { it.name == "Warning" }

        viewModel.onApplyObjectStyle(listOf("shape", "locked", "text"), warning.id)
        val styled = viewModel.await { it.element<ShapeElement>("shape").fill == warning.fill }

        assertEquals(warning.strokeColor, styled.element<ShapeElement>("shape").strokeColor)
        assertEquals(warning.strokeWidth, styled.element<ShapeElement>("shape").strokeWidth)

        // The locked shape and the text box are skipped one by one, like every
        // other batch edit: a style is for shapes, and a lock says "not this one".
        assertEquals(ShapeElement(frame = box).fill, styled.element<ShapeElement>("locked").fill)
        assertEquals("Hi", styled.element<TextElement>("text").text)
        assertTrue(styled.canUndo)

        // One entry however many shapes it dressed.
        viewModel.onUndo()
        val back = viewModel.await { !it.canUndo }
        assertEquals(document().slides, back.document.slides)
    }

    @Test
    fun aStyleNoOneAnswersToAndASelectionWithNoShapeInItAreBothNoOps() = runTest {
        val viewModel = editor(document())

        viewModel.onApplyObjectStyle(listOf("shape"), "no-such-style")
        viewModel.onApplyObjectStyle(listOf("text", "locked"), "warning")
        // Nothing to wait for, so wait for the event after them instead.
        viewModel.onToggleNotes()
        val settled = viewModel.await { !it.showNotes }

        assertFalse(settled.canUndo)
        assertEquals(document().slides, settled.document.slides)
    }

    @Test
    fun savingRenamingAndDeletingAStyleAreEachOneEdit() = runTest {
        val viewModel = editor(document())
        assertEquals(6, viewModel.states.value.objectStyles.size)

        viewModel.onUpdateElements(
            listOf(
                ShapeElement(
                    id = "shape",
                    frame = box,
                    fill = 0xFF102030,
                    strokeColor = 0xFF405060,
                    strokeWidth = 4f,
                    cornerRadius = 2f,
                ),
            ),
        )
        viewModel.await { it.element<ShapeElement>("shape").fill == 0xFF102030 }

        viewModel.onSaveObjectStyle("shape", "Mine")
        val saved = viewModel.await { state -> state.objectStyles.any { it.name == "Mine" } }
        val mine: ObjectStyle = saved.objectStyles.first { it.name == "Mine" }

        assertEquals(7, saved.objectStyles.size)
        assertEquals(0xFF102030, mine.fill)
        assertEquals(0xFF405060, mine.strokeColor)
        assertEquals(4f, mine.strokeWidth)
        assertEquals(2f, mine.cornerRadius)

        viewModel.onRenameObjectStyle(mine.id, "Ours")
        val renamed = viewModel.await { state -> state.objectStyles.any { it.name == "Ours" } }
        assertEquals(7, renamed.objectStyles.size)
        assertEquals(mine.copy(name = "Ours"), renamed.objectStyles.first { it.id == mine.id })

        viewModel.onDeleteObjectStyle(mine.id)
        val deleted = viewModel.await { state -> state.objectStyles.none { it.id == mine.id } }
        assertEquals(6, deleted.objectStyles.size)
        // Nothing wearing it changed: a style is applied by copy.
        assertEquals(0xFF102030, deleted.element<ShapeElement>("shape").fill)

        // Three edits, three undos, and the deletion is the first one back.
        viewModel.onUndo()
        val undone = viewModel.await { state -> state.objectStyles.any { it.id == mine.id } }
        assertEquals("Ours", undone.objectStyles.first { it.id == mine.id }.name)
    }

    @Test
    fun aStyleTheDeckDoesntHoldIsNeitherRenamedNorDeleted() = runTest {
        val viewModel = editor(document())

        viewModel.onDeleteObjectStyle("no-such-style")
        viewModel.onRenameObjectStyle("no-such-style", "Nope")
        // A rename to the name it already has is a no-op too.
        viewModel.onRenameObjectStyle("filled", "Filled")
        viewModel.onToggleNotes()
        val settled = viewModel.await { !it.showNotes }

        assertFalse(settled.canUndo)
        assertEquals(6, settled.objectStyles.size)
    }

    @Test
    fun useAsDefaultForATextBoxWritesTheDeckAndTheNextInsertionFollows() = runTest {
        val viewModel = editor(document())
        assertFalse(viewModel.states.value.canUseAsDefaultTextStyle)

        viewModel.onUpdateElements(
            listOf(
                TextElement(
                    id = "text",
                    frame = box,
                    text = "Hi",
                    fontSize = 44f,
                    fontWeight = 700,
                    lineHeight = 1.8f,
                    color = 0xFF00FF00,
                    align = TextAlign.Center,
                    fontFamily = TextFont.Monospace,
                ),
            ),
        )
        viewModel.await { it.element<TextElement>("text").fontSize == 44f }

        viewModel.onUseAsDefaultTextStyle("text")
        val set = viewModel.await { it.defaults.textSize == 44f }

        assertEquals(700, set.defaults.textWeight)
        assertEquals(1.8f, set.defaults.textLineHeight)
        assertEquals(0xFF00FF00, set.defaults.textColor)
        assertEquals(TextAlign.Center, set.defaults.textAlign)
        assertEquals(TextFont.Monospace, set.defaults.textFont)
        assertTrue(set.canUndo)

        // Which is the whole point: the next box a shell inserts is set that way.
        val fresh: TextElement = textBoxElement(box, set.defaults)
        assertEquals(44f, fresh.fontSize)
        assertEquals(700, fresh.fontWeight)
        assertEquals(TextAlign.Center, fresh.align)
        assertEquals(TextFont.Monospace, fresh.fontFamily)

        // Nothing already on the slide moved, shapes included.
        assertEquals(3, set.selectedSlide.elements.size)
        assertEquals(ElementDefaults().shapeFill, set.element<ShapeElement>("shape").fill)

        // A shape is no text box, so it writes nothing.
        viewModel.onUseAsDefaultTextStyle("shape")
        viewModel.onToggleNotes()
        val settled = viewModel.await { !it.showNotes }
        assertEquals(44f, settled.defaults.textSize)
    }

    @Test
    fun useAsDefaultForAShapeWritesFillStrokeAndLabelColour() = runTest {
        val viewModel = editor(document())

        viewModel.onSelectElement("shape")
        val selected = viewModel.await { it.primaryElement?.id == "shape" }
        assertTrue(selected.canUseAsDefaultShapeStyle)
        assertFalse(selected.canUseAsDefaultTextStyle)

        viewModel.onUpdateElements(
            listOf(
                ShapeElement(
                    id = "shape",
                    frame = box,
                    fill = 0xFF102030,
                    strokeColor = 0xFF405060,
                    labelColor = 0xFF708090,
                ),
            ),
        )
        viewModel.await { it.element<ShapeElement>("shape").fill == 0xFF102030 }

        viewModel.onUseAsDefaultShapeStyle("shape")
        val set = viewModel.await { it.defaults.shapeFill == 0xFF102030 }

        assertEquals(0xFF405060, set.defaults.shapeStroke)
        assertEquals(0xFF708090, set.defaults.shapeLabelColor)
        assertTrue(set.canUndo)

        val fresh: ShapeElement = shapeElement(ShapeKind.Rectangle, box, set.defaults)
        assertEquals(0xFF102030, fresh.fill)
        assertEquals(0xFF405060, fresh.strokeColor)
        assertEquals(0xFF708090, fresh.labelColor)

        // A text box is no shape, and a deck already dressed like this is a no-op.
        viewModel.onUseAsDefaultShapeStyle("text")
        viewModel.onUseAsDefaultShapeStyle("shape")
        viewModel.onUndo()
        val back = viewModel.await { it.defaults.shapeFill != 0xFF102030 }
        assertEquals(ElementDefaults().shapeFill, back.defaults.shapeFill)
    }
}

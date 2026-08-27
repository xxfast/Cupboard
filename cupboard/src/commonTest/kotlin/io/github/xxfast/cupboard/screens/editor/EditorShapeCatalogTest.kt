package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.DefaultLineHeight
import io.github.xxfast.cupboard.document.DefaultLineWidth
import io.github.xxfast.cupboard.document.DefaultShapeHeight
import io.github.xxfast.cupboard.document.DefaultShapeWidth
import io.github.xxfast.cupboard.document.DefaultTextBoxHeight
import io.github.xxfast.cupboard.document.DefaultTextBoxWidth
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeCatalog
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.element
import io.github.xxfast.cupboard.document.textBoxElement
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The shape catalog, and the insert event every shell reaches it through. */
class EditorShapeCatalogTest {
    private fun document(): Document = Document(
        slides = listOf(
            Slide(
                id = "slide",
                title = "Shapes",
                elements = listOf(ShapeElement(id = "a", frame = Frame(0f, 0f, 100f, 100f))),
            ),
        ),
    )

    private val frame = Frame(0f, 0f, 200f, 100f)

    @Test
    fun everyCatalogEntryBuildsItsOwnKind() {
        for (entry in ShapeCatalog.entries) {
            val element: ShapeElement = entry.element(frame)
            assertEquals(entry.kind, element.kind, entry.title)
            assertEquals(entry.cornerRadius, element.cornerRadius, entry.title)
            assertEquals(frame, element.frame, entry.title)
        }
    }

    @Test
    fun theCatalogIsTheDozenInMenuOrder() {
        assertEquals(
            listOf(
                "Rectangle", "Rounded Rectangle", "Oval", "Triangle", "Arrow", "Diamond",
                "Star", "Hexagon", "Quote Bubble", "Callout", "Line",
            ),
            ShapeCatalog.entries.map { it.title },
        )

        // The two rectangles are one kind at two radii: square and rounded.
        val rectangles = ShapeCatalog.entries.filter { it.kind == ShapeKind.Rectangle }
        assertEquals(listOf(0f, 10f), rectangles.map { it.cornerRadius })
    }

    @Test
    fun aLineInsertsHeavierAndWiderThanAShape() {
        val line = ShapeCatalog.entries.first { it.kind == ShapeKind.Line }
        assertEquals(DefaultLineWidth, line.width)
        assertEquals(DefaultLineHeight, line.height)
        assertEquals(3f, line.element(frame).strokeWidth)

        val rectangle = ShapeCatalog.entries.first()
        assertEquals(DefaultShapeWidth, rectangle.width)
        assertEquals(DefaultShapeHeight, rectangle.height)
        assertEquals(1.5f, rectangle.element(frame).strokeWidth)
    }

    @Test
    fun insertionFrameCentersOnTheSlide() = runTest {
        val state = editor(document()).states.value
        val centered: Frame = state.insertionFrame(DefaultShapeWidth, DefaultShapeHeight)

        assertEquals(state.document.slideWidth / 2, centered.centerX)
        assertEquals(state.document.slideHeight / 2, centered.centerY)
        assertEquals(DefaultShapeWidth, centered.width)
        assertEquals(DefaultShapeHeight, centered.height)
    }

    @Test
    fun insertAppendsSelectsAloneAndFocusesTheCanvas() = runTest {
        val viewModel = editor(document())
        // Selecting focuses the canvas itself, so the navigator takes the focus
        // back afterwards: the insert has to be what moves it.
        viewModel.onSelectElement("a")
        viewModel.onFocusPane(EditorPane.Navigator)
        viewModel.await { it.focusedPane == EditorPane.Navigator && it.selectedElementIds == listOf("a") }

        val inserted: ShapeElement = ShapeCatalog.entries
            .first { it.kind == ShapeKind.Star }
            .element(Frame(10f, 20f, 30f, 40f))
        viewModel.onInsertElement(inserted)

        val state = viewModel.await { it.selectedSlide.elements.size == 2 }
        assertEquals(listOf("a", inserted.id), state.selectedSlide.elements.map { it.id })
        assertEquals(listOf(inserted.id), state.selectedElementIds)
        assertEquals(EditorPane.Canvas, state.focusedPane)
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test
    fun insertIsOneUndoStep() = runTest {
        val viewModel = editor(document())
        viewModel.onInsertElement(textBoxElement(Frame(0f, 0f, 400f, 60f)))
        viewModel.await { it.selectedSlide.elements.size == 2 }

        viewModel.onUndo()
        val undone = viewModel.await { it.selectedSlide.elements.size == 1 }
        assertEquals(listOf("a"), undone.selectedSlide.elements.map { it.id })
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)
    }

    @Test
    fun aTextBoxInsertsWithSomethingToType() {
        val box: TextElement = textBoxElement(Frame(0f, 0f, DefaultTextBoxWidth, DefaultTextBoxHeight))
        assertEquals("Text", box.text)
        assertEquals(32f, box.fontSize)
    }
}

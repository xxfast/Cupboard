package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.DefaultLineHeight
import io.github.xxfast.cupboard.document.DefaultLineWidth
import io.github.xxfast.cupboard.document.DefaultShapeHeight
import io.github.xxfast.cupboard.document.DefaultShapeWidth
import io.github.xxfast.cupboard.document.DefaultTextBoxHeight
import io.github.xxfast.cupboard.document.DefaultTextBoxWidth
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.LineElement
import io.github.xxfast.cupboard.document.Point
import io.github.xxfast.cupboard.document.ShapeCatalog
import io.github.xxfast.cupboard.document.ShapeCatalogEntry
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.textBoxElement
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
            when (entry) {
                is ShapeCatalogEntry.Shape -> {
                    val element = assertIs<ShapeElement>(entry.element(frame), entry.title)
                    assertEquals(entry.kind, element.kind, entry.title)
                    assertEquals(entry.cornerRadius, element.cornerRadius, entry.title)
                    assertEquals(frame, element.frame, entry.title)
                }

                // The box's rising diagonal, bottom-left to top-right.
                is ShapeCatalogEntry.Line -> {
                    val element = assertIs<LineElement>(entry.element(frame), entry.title)
                    assertEquals(Point(0f, 100f), element.start, entry.title)
                    assertEquals(Point(200f, 0f), element.end, entry.title)
                    assertEquals(entry.startEnd, element.startEnd, entry.title)
                    assertEquals(entry.endEnd, element.endEnd, entry.title)
                    assertEquals(entry.curved, element.bend != null, entry.title)
                    // A curve's control sits in the box's corner, so it stays inside it.
                    assertEquals(frame, element.frame, entry.title)
                }
            }
        }
    }

    @Test
    fun theCatalogIsTheDozenInMenuOrder() {
        assertEquals(
            listOf(
                "Rectangle", "Rounded Rectangle", "Oval", "Triangle", "Arrow", "Diamond",
                "Star", "Hexagon", "Quote Bubble", "Callout", "Line", "Line with Arrowhead",
                "Line with Two Arrowheads", "Curved Line",
            ),
            ShapeCatalog.entries.map { it.title },
        )

        // The two rectangles are one kind at two radii: square and rounded.
        val rectangles = ShapeCatalog.entries
            .filterIsInstance<ShapeCatalogEntry.Shape>()
            .filter { it.kind == ShapeKind.Rectangle }
        assertEquals(listOf(0f, 10f), rectangles.map { it.cornerRadius })
    }

    @Test
    fun aLineInsertsHeavierAndWiderThanAShape() {
        val line = ShapeCatalog.entries.filterIsInstance<ShapeCatalogEntry.Line>().first()
        assertEquals(DefaultLineWidth, line.width)
        assertEquals(DefaultLineHeight, line.height)
        assertEquals(3f, line.element(frame).strokeWidth)

        val rectangle = ShapeCatalog.entries.filterIsInstance<ShapeCatalogEntry.Shape>().first()
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

    private fun TestScope.stateWith(vararg frames: Frame): EditorState {
        val elements: List<ShapeElement> =
            frames.mapIndexed { index, frame -> ShapeElement(id = "e$index", frame = frame) }
        val document = Document(slides = listOf(Slide("slide", "Slide", elements = elements)))
        return editor(document).states.value
    }

    @Test
    fun anEmptySlideInsertsInTheMiddle() = runTest {
        val state = stateWith()
        val frame: Frame = state.insertionFrame(200f, 100f)

        assertEquals(state.document.slideWidth / 2, frame.centerX)
        assertEquals(state.document.slideHeight / 2, frame.centerY)
    }

    @Test
    fun aTitleLayoutInsertsOffItsPlaceholders() = runTest {
        val width: Float = Document().slideWidth
        val height: Float = Document().slideHeight
        val title = Frame(83f, height / 2 - 100f, width - 166f, 141f)
        val subtitle = Frame(83f, height / 2 + 50f, width - 166f, 80f)
        val state = stateWith(title, subtitle)
        val frame: Frame = state.insertionFrame(300f, 150f)

        assertFalse(frame.overlaps(title))
        assertFalse(frame.overlaps(subtitle))
        assertTrue(frame.x >= 0f && frame.y >= 0f)
        assertTrue(frame.x + frame.width <= width && frame.y + frame.height <= height)
    }

    @Test
    fun aRotatedElementBlocksItsRotatedBounds() = runTest {
        val width: Float = Document().slideWidth
        val height: Float = Document().slideHeight
        val bar = ShapeElement(
            id = "bar",
            frame = Frame(width / 2 - 200f, height / 2 - 5f, 400f, 10f),
            rotation = 90f,
        )
        val document = Document(slides = listOf(Slide("slide", "Slide", elements = listOf(bar))))
        val frame: Frame = editor(document).states.value.insertionFrame(100f, 100f)

        assertFalse(frame.overlaps(Frame(width / 2 - 5f, height / 2 - 200f, 10f, 400f)))
    }

    @Test
    fun aFullSlideCascadesAndStaysInside() = runTest {
        val width: Float = Document().slideWidth
        val height: Float = Document().slideHeight
        val full = Frame(0f, 0f, width, height)
        val first: Frame = stateWith(full).insertionFrame(200f, 100f)
        val centre = Frame((width - 200f) / 2, (height - 100f) / 2, 200f, 100f)
        assertEquals(centre, first)

        val second: Frame = stateWith(full, first).insertionFrame(200f, 100f)
        assertEquals(centre.translate(20f, 20f), second)

        val third: Frame = stateWith(full, first, second).insertionFrame(200f, 100f)
        assertEquals(centre.translate(40f, 40f), third)
        assertTrue(third.x + third.width <= width && third.y + third.height <= height)
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
            .filterIsInstance<ShapeCatalogEntry.Shape>()
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

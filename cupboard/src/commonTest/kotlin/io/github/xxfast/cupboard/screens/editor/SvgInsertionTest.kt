package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.Slide
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * An SVG's bytes through the editor loop, the way a shell hands them over from a
 * file dialog, a drop or a paste: read into shapes, sized against the slide and
 * inserted where the caller's `insertionFrame` puts them.
 */
class SvgInsertionTest {
    // The default slide, 1920x1080, so an insert may take 1152x648 of it.
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(Slide(id = "slide", title = "Drawing")),
    )

    private fun svg(body: String, size: Int): ByteArray =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $size $size">$body</svg>"""
            .encodeToByteArray()

    @Test
    fun anSvgLandsAsASelectedShapeWhereTheCallerPutsIt() = runTest {
        val viewModel = editor(document())
        val before = viewModel.states.value.document

        val inserted: Boolean = viewModel.insertSvg(
            bytes = svg("""<path d="M4 4h16v8H4z" fill="#ff0000"/>""", size = 24),
            insertionFrame = viewModel.states.value::insertionFrame,
        )
        assertTrue(inserted)

        val state = viewModel.await { it.selectedSlide.elements.size == 1 }
        val shape: ShapeElement = assertIs<ShapeElement>(state.selectedSlide.elements.single())
        assertEquals(ShapeKind.Path, shape.kind)
        assertEquals(0xFFFF0000, shape.fill)
        // Its own size, in the middle of the slide: never scaled up to fill it.
        assertEquals(Frame(952f, 536f, 16f, 8f), shape.frame)
        assertEquals(listOf(shape.id), state.selectedElementIds)

        viewModel.onUndo()
        assertFalse(viewModel.await { it.document == before }.canUndo)
    }

    @Test
    fun aDrawingBiggerThanTheSlideIsScaledDownToItsShare() = runTest {
        val viewModel = editor(document())

        viewModel.insertSvg(
            bytes = svg("""<rect width="6480" height="6480"/>""", size = 6480),
            insertionFrame = viewModel.states.value::insertionFrame,
        )

        val state = viewModel.await { it.selectedSlide.elements.size == 1 }
        val frame: Frame = state.selectedSlide.elements.single().frame
        assertEquals(648f, frame.width, 0.01f)
        assertEquals(648f, frame.height, 0.01f)
        assertEquals(216f, frame.y, 0.01f)
    }

    @Test
    fun severalPaintsLandAsOneGroupWithItsShapesMovedAlong() = runTest {
        val viewModel = editor(document())

        viewModel.insertSvg(
            bytes = svg(
                """
                <rect width="100" height="100" fill="red"/>
                <rect x="100" y="100" width="100" height="100" fill="blue"/>
                """.trimIndent(),
                size = 200,
            ),
            insertionFrame = viewModel.states.value::insertionFrame,
        )

        val state = viewModel.await { it.selectedSlide.elements.size == 1 }
        val group: GroupElement = assertIs<GroupElement>(state.selectedSlide.elements.single())
        assertEquals(Frame(860f, 440f, 200f, 200f), group.frame)
        assertEquals(
            listOf(Frame(860f, 440f, 100f, 100f), Frame(960f, 540f, 100f, 100f)),
            group.children.map { it.frame },
        )
        assertEquals(listOf(group.id), state.selectedElementIds)
    }

    @Test
    fun bytesThatAreNoDrawingInsertNothing() = runTest {
        val viewModel = editor(document())

        fun insert(text: String): Boolean = viewModel.insertSvg(
            bytes = text.encodeToByteArray(),
            insertionFrame = viewModel.states.value::insertionFrame,
        )

        assertFalse(insert("not an svg"))
        assertFalse(insert("<svg xmlns=\"http://www.w3.org/2000/svg\"><text>Words</text></svg>"))
        assertFalse(viewModel.insertSvg(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), viewModel.states.value::insertionFrame))

        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertEquals(document(), after.document)
        assertFalse(after.canUndo)
    }
}

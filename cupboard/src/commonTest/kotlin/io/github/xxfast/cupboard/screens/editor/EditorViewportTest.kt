package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Canvas zoom and scroll, driven the way a canvas drives them: it reports its
 * size, then zooms and scrolls. The default deck, 1920x1080, so the numbers are
 * easy to check by hand.
 */
class EditorViewportTest {
    private fun document(extra: List<Element> = emptyList()): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "slide",
                title = "Viewport",
                elements = listOf(
                    ShapeElement(id = "a", frame = Frame(100f, 100f, 200f, 100f)),
                    ShapeElement(id = "b", frame = Frame(600f, 400f, 200f, 100f)),
                ) + extra,
            ),
        ),
    )

    private fun assertNear(expected: Float, actual: Float) =
        assertTrue(abs(expected - actual) < 0.01f, "expected $expected, was $actual")

    /**
     * An event that changes nothing emits nothing, so a test that wants to see a
     * no-op lands one it can wait for behind it.
     */
    private suspend fun EditorViewModel.settled(): EditorState {
        onToggleRulers()
        return await { it.showRulers }
    }

    @Test
    fun fitFollowsTheView() = runTest {
        val editor = editor(document())
        val opened: EditorState = editor.await { true }
        assertNull(opened.canvasScale)
        assertNull(opened.effectiveZoomPercent)

        editor.onCanvasResized(960f, 1080f)
        val wide: EditorState = editor.await { it.viewport.viewWidth == 960f }
        assertEquals(0.5f, wide.canvasScale)
        assertEquals(50, wide.effectiveZoomPercent)

        editor.onCanvasResized(480f, 1080f)
        assertEquals(25, editor.await { it.viewport.viewWidth == 480f }.effectiveZoomPercent)
        assertFalse(editor.states.value.canUndo)
    }

    @Test
    fun zoomInFromFitSnapsToTheNextStep() = runTest {
        val editor = editor(document())
        editor.onCanvasResized(614.4f, 1000f)
        assertEquals(32, editor.await { it.viewport.viewWidth > 0f }.effectiveZoomPercent)

        editor.onZoomIn()
        assertEquals(50, editor.await { it.viewport.zoomPercent != 0 }.viewport.zoomPercent)

        editor.onZoomOut()
        assertEquals(25, editor.await { it.viewport.zoomPercent != 50 }.viewport.zoomPercent)

        editor.onSetZoom(10)
        editor.await { it.viewport.zoomPercent == 10 }
        editor.onZoomOut()
        val bottom: EditorState = editor.settled()
        assertEquals(10, bottom.viewport.zoomPercent)
        assertFalse(bottom.canUndo)
    }

    @Test
    fun panStopsAtTheSlideEdgeAndAFittingAxisStaysCentred() = runTest {
        val editor = editor(document())
        editor.onCanvasResized(960f, 1080f)
        editor.onSetZoom(100)
        editor.await { it.viewport.zoomPercent == 100 }

        // 960 of 1920 units across, so 480 either way; all 1080 down, so none.
        editor.onPanCanvas(10_000f, 500f)
        val panned: EditorState = editor.await { it.viewport.panX != 0f }
        assertEquals(480f, panned.canvasViewport.panX)
        assertEquals(0f, panned.canvasViewport.panY)
        assertFalse(panned.canUndo)
    }

    @Test
    fun aStalePanHasNoDeadZone() = runTest {
        val editor = editor(document())
        editor.onCanvasResized(960f, 540f)
        editor.onSetZoom(400)
        editor.await { it.viewport.zoomPercent == 400 }
        editor.onPanCanvas(10_000f, 0f)
        assertEquals(840f, editor.await { it.viewport.panX != 0f }.viewport.panX)

        // Zooming out leaves the stored pan past where 100% can scroll to.
        editor.onSetZoom(100)
        val zoomedOut: EditorState = editor.await { it.viewport.zoomPercent == 100 }
        assertEquals(840f, zoomedOut.viewport.panX)
        assertEquals(480f, zoomedOut.canvasViewport.panX)

        editor.onPanCanvas(-10f, 0f)
        assertEquals(470f, editor.await { it.canvasViewport.panX != 480f }.canvasViewport.panX)
    }

    @Test
    fun pinchKeepsTheAnchorUnderThePointer() = runTest {
        val editor = editor(document())
        editor.onCanvasResized(960f, 540f)
        val before: EditorState = editor.await { it.canvasScale != null }

        // Where a document point lands, in dp from the view centre.
        fun EditorState.screenX(x: Float): Float = (x - canvasViewport.panX) * canvasScale!!
        fun EditorState.screenY(y: Float): Float = (y - canvasViewport.panY) * canvasScale!!

        editor.onZoomBy(2f, 400f, 200f)
        val after: EditorState = editor.await { it.viewport.zoomPercent != 0 }
        assertEquals(100, after.viewport.zoomPercent)
        assertNear(before.screenX(400f), after.screenX(400f))
        assertNear(before.screenY(200f), after.screenY(200f))
        assertFalse(after.canUndo)
    }

    @Test
    fun zoomToSelectionCentresOnIt() = runTest {
        val editor = editor(document())
        editor.onCanvasResized(400f, 300f)
        editor.onSelectElement("b")
        editor.await { it.canZoomToSelection }

        editor.onZoomToSelection()
        val zoomed: EditorState = editor.await { it.viewport.zoomPercent != 0 }
        // min(400 / 200, 300 / 100) at 90% fill.
        assertEquals(180, zoomed.viewport.zoomPercent)
        assertEquals(700f - 960f, zoomed.canvasViewport.panX)
        assertEquals(450f - 540f, zoomed.canvasViewport.panY)
        assertFalse(zoomed.canUndo)
    }

    @Test
    fun zoomToSelectionWithNothingSelectedDoesNothing() = runTest {
        val editor = editor(document())
        editor.onCanvasResized(400f, 300f)
        val opened: EditorState = editor.await { it.viewport.viewWidth > 0f }
        assertFalse(opened.canZoomToSelection)

        editor.onZoomToSelection()
        assertEquals(opened.viewport, editor.settled().viewport)
    }

    @Test
    fun fitContentTakesInWhatHangsOffTheSlide() = runTest {
        val offSlide = ShapeElement(id = "off", frame = Frame(2000f, 500f, 200f, 100f))
        val editor = editor(document(extra = listOf(offSlide)))
        editor.onCanvasResized(960f, 540f)
        val opened: EditorState = editor.await { it.viewport.viewWidth > 0f }
        assertEquals(Frame(0f, 0f, 2200f, 1080f), opened.contentFrame)

        editor.onZoomToFitContent()
        val fitted: EditorState = editor.await { it.viewport.zoomPercent != 0 }
        // min(960 / 2200, 540 / 1080) at 90% fill, floored: smaller than Fit Slide's 50.
        assertEquals(39, fitted.viewport.zoomPercent)
        assertEquals(1100f - 960f, fitted.viewport.panX)
        assertFalse(fitted.canUndo)
    }
}

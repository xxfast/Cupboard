package io.github.xxfast.cupboard.editor

import io.github.xxfast.cupboard.document.Frame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeometryTest {
    private val frame = Frame(100f, 100f, 200f, 100f)

    @Test
    fun cornerHandleResizesBothAxes() {
        val resized = resizeFrame(frame, Handle.BottomRight, dx = 50f, dy = 30f)
        assertEquals(Frame(100f, 100f, 250f, 130f), resized)
    }

    @Test
    fun edgeHandleResizesOneAxis() {
        val resized = resizeFrame(frame, Handle.Right, dx = 40f, dy = 999f)
        assertEquals(Frame(100f, 100f, 240f, 100f), resized)
    }

    @Test
    fun leftHandleMovesOriginAndWidth() {
        val resized = resizeFrame(frame, Handle.Left, dx = 20f, dy = 0f)
        assertEquals(Frame(120f, 100f, 180f, 100f), resized)
    }

    @Test
    fun resizeClampsToMinSize() {
        val resized = resizeFrame(frame, Handle.Right, dx = -500f, dy = 0f)
        assertEquals(40f, resized.width)
    }

    @Test
    fun handleHitTestFindsCorner() {
        assertEquals(Handle.TopLeft, hitTestHandle(frame, 102f, 98f))
        assertEquals(Handle.Bottom, hitTestHandle(frame, 200f, 201f))
        assertNull(hitTestHandle(frame, 200f, 150f))
    }

    @Test
    fun snapsCenterWithinThreshold() {
        // slide center x = 960; frame width 200 → centered x = 860
        val near = frame.copy(x = 866f)
        val snapped = snapToSlideCenter(near)
        assertTrue(snapped.snappedX)
        assertEquals(860f, snapped.frame.x)
        assertFalse(snapped.snappedY)
    }

    @Test
    fun noSnapOutsideThreshold() {
        val far = frame.copy(x = 845f)
        val result = snapToSlideCenter(far)
        assertFalse(result.snappedX)
        assertEquals(845f, result.frame.x)
    }
}

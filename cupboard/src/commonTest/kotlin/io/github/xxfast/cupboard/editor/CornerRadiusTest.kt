package io.github.xxfast.cupboard.editor

import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GuideAxis
import io.github.xxfast.cupboard.document.Point
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CornerRadiusTest {
    // 200 wide, 100 tall: the pill is a radius of 50.
    private val shape = ShapeElement(frame = Frame(100f, 40f, 200f, 100f), cornerRadius = 10f)

    @Test
    fun dotSitsOnTheTopEdgeRadiusInFromTheLeft() {
        assertEquals(Point(110f, 40f), shape.radiusHandle())
        assertEquals(Point(130f, 40f), shape.copy(cornerRadius = 30f).radiusHandle())
    }

    @Test
    fun dotPastThePillSitsWhereThePillsArcDoes() {
        assertEquals(Point(150f, 40f), shape.copy(cornerRadius = 80f).radiusHandle())
    }

    @Test
    fun dotWaitsAnInsetOffTheCornerUntilTheArcPassesIt() {
        assertEquals(Point(111f, 40f), shape.copy(cornerRadius = 0f).radiusHandle(inset = 11f))
        assertEquals(Point(111f, 40f), shape.radiusHandle(inset = 11f))
        assertEquals(Point(130f, 40f), shape.copy(cornerRadius = 30f).radiusHandle(inset = 11f))
    }

    @Test
    fun noDotOnALockedTurnedOrUnroundedShape() {
        assertNull(shape.copy(locked = true).radiusHandle())
        assertNull(shape.copy(rotation = 15f).radiusHandle())
        assertNull(shape.copy(kind = ShapeKind.Ellipse).radiusHandle())
        assertNull(shape.copy(kind = ShapeKind.Path).radiusHandle())
    }

    @Test
    fun dragSetsTheRadiusToHowFarInTheDotIs() {
        assertEquals(36f, shape.withCornerRadiusAt(136f).cornerRadius)
    }

    @Test
    fun dragFloorsAtSquareCorners() {
        assertEquals(0f, shape.withCornerRadiusAt(60f).cornerRadius)
    }

    @Test
    fun dragCapsAtThePill() {
        assertEquals(50f, shape.withCornerRadiusAt(400f).cornerRadius)
        val tall: ShapeElement = shape.copy(frame = Frame(100f, 40f, 60f, 300f))
        assertEquals(30f, tall.withCornerRadiusAt(400f).cornerRadius)
    }

    @Test
    fun dragLeavesEverythingButTheRadius() {
        val dragged: ShapeElement = shape.withCornerRadiusAt(120f)
        assertEquals(shape.copy(cornerRadius = 20f), dragged)
    }

    @Test
    fun shiftSnapsToTheNearerEnd() {
        assertEquals(0f, shape.withCornerRadiusAt(120f, snapped = true).cornerRadius)
        assertEquals(50f, shape.withCornerRadiusAt(130f, snapped = true).cornerRadius)
        assertEquals(50f, shape.withCornerRadiusAt(400f, snapped = true).cornerRadius)
    }

    @Test
    fun radiusDragMarksNoGuidesOrArrows() {
        val overlay: AlignmentOverlay = alignmentOverlay(
            frame = shape.frame,
            lines = listOf(SnapLine(GuideAxis.Vertical, 100f, SnapKind.Edges)),
            others = listOf(Frame(320f, 40f, 200f, 100f)),
            drag = DragSample(120f, 40f, resizing = false, cornerRadius = 20f),
        )
        assertTrue(overlay.guides.isEmpty())
        assertTrue(overlay.spacing.isEmpty())
        assertTrue(overlay.sizes.isEmpty())
        assertEquals(20f, overlay.drag.cornerRadius)
    }
}

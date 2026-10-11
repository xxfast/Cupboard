package io.github.xxfast.cupboard.editor

import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.GuideAxis
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.groupElements
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
        assertEquals(SnapKind.Center, snapped.snappedX?.kind)
        assertEquals(860f, snapped.frame.x)
        assertNull(snapped.snappedY)
    }

    @Test
    fun noSnapOutsideThreshold() {
        val far = frame.copy(x = 845f)
        val result = snapToSlideCenter(far)
        assertNull(result.snappedX)
        assertEquals(845f, result.frame.x)
    }

    @Test
    fun snapFramePicksTheNearestLineOnEachAxis() {
        // 200x100 at (100,100): left 100, center 200, right 300, and top 100,
        // middle 150, bottom 200.
        val lines = listOf(
            SnapLine(GuideAxis.Vertical, 306f, SnapKind.Guides),
            SnapLine(GuideAxis.Vertical, 296f, SnapKind.Objects),
            SnapLine(GuideAxis.Horizontal, 148f, SnapKind.Guides),
        )
        val snapped = snapFrame(frame, lines)

        // The right edge is 4 from one line and 6 from the other: nearest wins.
        assertEquals(96f, snapped.frame.x)
        assertEquals(SnapKind.Objects, snapped.snappedX?.kind)
        assertEquals(296f, snapped.snappedX?.position)
        assertEquals(98f, snapped.frame.y)
        assertEquals(SnapKind.Guides, snapped.snappedY?.kind)
    }

    @Test
    fun snapFrameSnapsAnEdgeNotJustTheCenter() {
        // The left edge, 100, is 3 off the line; no center is anywhere near it.
        val snapped = snapFrame(frame, listOf(SnapLine(GuideAxis.Vertical, 103f, SnapKind.Guides)))
        assertEquals(103f, snapped.frame.x)
    }

    @Test
    fun snapFrameLeavesAnAxisFreeBeyondTheThreshold() {
        val near = snapFrame(frame, listOf(SnapLine(GuideAxis.Vertical, 308f, SnapKind.Guides)))
        assertEquals(SnapKind.Guides, near.snappedX?.kind)

        val far = snapFrame(frame, listOf(SnapLine(GuideAxis.Vertical, 340f, SnapKind.Guides)))
        assertNull(far.snappedX)
        assertNull(far.snappedY)
        assertEquals(frame, far.frame)
    }

    // Keynote's survey deck: three 308-squares in a row at y 386, gaps of 94 and 93.
    private val square = Frame(404f, 386f, 308f, 308f)
    private val circle = Frame(806f, 386f, 308f, 308f)
    private val triangle = Frame(1207f, 386f, 308f, 308f)
    private val move = DragSample(0f, 0f, resizing = false)
    private val resize = DragSample(0f, 0f, resizing = true)

    @Test
    fun snapMoveSettlesOnAnEqualGapToTheNextPair() {
        // The circle-triangle gap is 93; the square 6 short of matching it.
        val near = square.copy(x = 404f - 6f)
        val snapped = snapMove(near, emptyList(), listOf(circle, triangle))
        assertEquals(806f - 93f - 308f, snapped.frame.x)
        assertEquals(386f, snapped.frame.y)
        assertNull(snapped.snappedX)
    }

    @Test
    fun snapMoveCentersBetweenTwoNeighbours() {
        // The square ends at 712 and the triangle starts at 1207: 495 less the
        // circle's 308 leaves 93.5 a side, so the circle's x is 805.5.
        val near = circle.copy(x = 810f)
        val snapped = snapMove(near, emptyList(), listOf(square, triangle))
        assertEquals(805.5f, snapped.frame.x)
    }

    @Test
    fun snapMovePrefersALineOverAGap() {
        val near = square.copy(x = 404f - 6f)
        val line = SnapLine(GuideAxis.Vertical, 400f, SnapKind.Guides)
        val snapped = snapMove(near, listOf(line), listOf(circle, triangle))
        assertEquals(400f, snapped.frame.x)
        assertEquals(line, snapped.snappedX)
    }

    @Test
    fun snapMoveIgnoresAGapPastTheThreshold() {
        val far = square.copy(x = 404f - 20f)
        val snapped = snapMove(far, emptyList(), listOf(circle, triangle))
        assertEquals(far, snapped.frame)
    }

    @Test
    fun snapMoveOnlyCountsNeighboursBesideTheFrame() {
        // Same x as before, but nothing shares a row with it: no gap to match.
        val alone = square.copy(x = 404f - 6f, y = 900f)
        val snapped = snapMove(alone, emptyList(), listOf(circle, triangle))
        assertEquals(alone, snapped.frame)
    }

    @Test
    fun snapResizeMatchesANeighboursWidthAndHoldsTheAnchor() {
        val stretched = resizeFrame(Frame(100f, 100f, 200f, 100f), Handle.Right, dx = 104f, dy = 0f)
        val matched = snapResize(stretched, Handle.Right, listOf(circle))
        assertEquals(Frame(100f, 100f, 308f, 100f), matched)

        // A left handle grows leftwards, so the right edge stays at 300.
        val leftward = resizeFrame(Frame(100f, 100f, 200f, 100f), Handle.Left, dx = -104f, dy = 0f)
        assertEquals(Frame(-8f, 100f, 308f, 100f), snapResize(leftward, Handle.Left, listOf(circle)))
    }

    @Test
    fun snapResizeOnAnEdgeHandleLeavesTheOtherAxisAlone() {
        // 305 tall is within reach of 308, but a right handle never touches height.
        val frame = Frame(100f, 100f, 305f, 305f)
        assertEquals(frame.copy(width = 308f), snapResize(frame, Handle.Right, listOf(circle)))
        assertEquals(Frame(100f, 100f, 308f, 308f), snapResize(frame, Handle.BottomRight, listOf(circle)))
    }

    @Test
    fun overlayNamesTheLinesTheFrameSitsOn() {
        val centered = circle.copy(x = 806f, y = 386f)
        val lines = listOf(
            SnapLine(GuideAxis.Vertical, 960f, SnapKind.Center),
            SnapLine(GuideAxis.Horizontal, 540f, SnapKind.Center),
            SnapLine(GuideAxis.Horizontal, 540f, SnapKind.Objects),
            SnapLine(GuideAxis.Vertical, 100f, SnapKind.Guides),
        )
        val overlay = alignmentOverlay(centered, lines, emptyList(), move)
        // One line per position, however many settings offer it.
        assertEquals(listOf(lines[0], lines[1]), overlay.guides)
        assertTrue(overlay.spacing.isEmpty())
    }

    @Test
    fun overlayMarksBothGapsWhenTheyAreEqual() {
        // The square 93 short of the circle, as the circle is of the triangle.
        val even = square.copy(x = 806f - 93f - 308f)
        val overlay = alignmentOverlay(even, emptyList(), listOf(circle, triangle), move)
        assertEquals(
            listOf(
                SpacingMark(Axis.Horizontal, 713f, 806f, 540f),
                SpacingMark(Axis.Horizontal, 1114f, 1207f, 540f),
            ),
            overlay.spacing.sortedBy { it.from },
        )
        assertTrue(overlay.sizes.isEmpty())
    }

    @Test
    fun spacingArrowSitsMidwayAlongTheOverlap() {
        // The square 40 higher than its neighbours: the shared run is 386..654.
        val raised = square.copy(x = 806f - 93f - 308f, y = 346f)
        val overlay = alignmentOverlay(raised, emptyList(), listOf(circle, triangle), move)
        assertEquals(520f, overlay.spacing.first { it.to == 806f }.at)
        // The circle and triangle share their whole height, so theirs sits at 540.
        assertEquals(540f, overlay.spacing.first { it.to == 1207f }.at)
    }

    @Test
    fun overlayMarksMatchingSizesOnlyMidResize() {
        val lines = emptyList<SnapLine>()
        val resizing = alignmentOverlay(square, lines, listOf(circle, triangle), resize)
        assertEquals(
            listOf(square, circle, triangle),
            resizing.sizes.filter { it.axis == Axis.Horizontal }.map { it.frame },
        )
        assertEquals(3, resizing.sizes.count { it.axis == Axis.Vertical })
        assertTrue(resizing.spacing.isEmpty())

        val moving = alignmentOverlay(square, lines, listOf(circle, triangle), move)
        assertTrue(moving.sizes.isEmpty())
    }

    @Test
    fun unrotatedElementHitTestsItsFrame() {
        val element = TextElement(frame = frame)
        assertTrue(element.contains(101f, 101f))
        assertFalse(element.contains(99f, 150f))
    }

    @Test
    fun rotatedElementHitTestsWhereItIsDrawn() {
        // 200x100 at (100,100), center (200,150). Rotated 90 degrees it draws
        // as 100x200: x 150..250, y 50..250.
        val element = TextElement(frame = frame, rotation = 90f)
        // On the drawn box but outside the raw frame.
        assertTrue(element.contains(200f, 60f))
        // Inside the raw frame but off the drawn box.
        assertFalse(element.contains(105f, 105f))
    }

    @Test
    fun rotatedResizeRotatesTheDeltaAndHoldsTheAnchor() {
        // 200x100 at (100,100) drawn at 90 degrees: x 150..250, y 50..250.
        // Dragging the BottomRight handle 40 left is 40 down in frame space,
        // so height grows by 40, and the drawn opposite corner (250,50) holds:
        // the frame shifts to (80,80) to keep it there.
        val resized = resizeFrame(frame, rotation = 90f, Handle.BottomRight, dx = -40f, dy = 0f)
        assertFrame(Frame(80f, 80f, 200f, 140f), resized)
    }

    @Test
    fun unrotatedResizeMatchesThePlainOverload() {
        val resized = resizeFrame(frame, rotation = 0f, Handle.BottomRight, dx = 50f, dy = 30f)
        assertEquals(resizeFrame(frame, Handle.BottomRight, dx = 50f, dy = 30f), resized)
    }

    @Test
    fun toLocalAndToSlideRoundTrip() {
        val element = TextElement(frame = frame, rotation = 37f)
        val (sx, sy) = element.toSlide(120f, 180f)
        val (lx, ly) = element.toLocal(sx, sy)
        assertEquals(120f, lx, absoluteTolerance = 0.001f)
        assertEquals(180f, ly, absoluteTolerance = 0.001f)
    }

    /** Frame equality with float tolerance: rotation math never lands exact. */
    private fun assertFrame(expected: Frame, actual: Frame, tolerance: Float = 0.001f) {
        assertEquals(expected.x, actual.x, tolerance)
        assertEquals(expected.y, actual.y, tolerance)
        assertEquals(expected.width, actual.width, tolerance)
        assertEquals(expected.height, actual.height, tolerance)
    }

    @Test
    fun unrotatedHandlesResizeAlongTheirOwnAxis() {
        assertEquals(ResizeDirection.Horizontal, resizeDirection(Handle.Left, rotation = 0f))
        assertEquals(ResizeDirection.Horizontal, resizeDirection(Handle.Right, rotation = 0f))
        assertEquals(ResizeDirection.Vertical, resizeDirection(Handle.Top, rotation = 0f))
        assertEquals(ResizeDirection.Vertical, resizeDirection(Handle.Bottom, rotation = 0f))
        assertEquals(ResizeDirection.DiagonalDown, resizeDirection(Handle.TopLeft, rotation = 0f))
        assertEquals(ResizeDirection.DiagonalDown, resizeDirection(Handle.BottomRight, rotation = 0f))
        assertEquals(ResizeDirection.DiagonalUp, resizeDirection(Handle.TopRight, rotation = 0f))
        assertEquals(ResizeDirection.DiagonalUp, resizeDirection(Handle.BottomLeft, rotation = 0f))
    }

    @Test
    fun quarterTurnSwapsTheAxes() {
        assertEquals(ResizeDirection.Vertical, resizeDirection(Handle.Right, rotation = 90f))
        assertEquals(ResizeDirection.Horizontal, resizeDirection(Handle.Top, rotation = 90f))
        assertEquals(ResizeDirection.DiagonalUp, resizeDirection(Handle.TopLeft, rotation = 90f))
    }

    @Test
    fun eighthTurnMakesEdgeHandlesDiagonal() {
        assertEquals(ResizeDirection.DiagonalDown, resizeDirection(Handle.Right, rotation = 45f))
        assertEquals(ResizeDirection.DiagonalUp, resizeDirection(Handle.Bottom, rotation = 45f))
    }

    @Test
    fun negativeRotationTurnsTheOtherWay() {
        assertEquals(ResizeDirection.DiagonalUp, resizeDirection(Handle.Right, rotation = -45f))
        assertEquals(ResizeDirection.Horizontal, resizeDirection(Handle.Top, rotation = -90f))
        assertEquals(ResizeDirection.Vertical, resizeDirection(Handle.BottomRight, rotation = -315f))
    }

    @Test
    fun rotationBucketsToTheNearestAxis() {
        assertEquals(ResizeDirection.Horizontal, resizeDirection(Handle.Right, rotation = 20f))
        assertEquals(ResizeDirection.DiagonalDown, resizeDirection(Handle.Right, rotation = 30f))
        // 170 is nearer 180 than 135, and 180 is horizontal again.
        assertEquals(ResizeDirection.Horizontal, resizeDirection(Handle.Right, rotation = 170f))
    }

    @Test
    fun flippedElementKeepsItsFootprint() {
        val element = TextElement(frame = frame, flippedHorizontally = true, flippedVertically = true)
        assertTrue(element.contains(101f, 101f))
        assertFalse(element.contains(99f, 150f))
    }

    private fun shape(id: String, x: Float, y: Float, width: Float = 100f, height: Float = 100f) =
        ShapeElement(id = id, frame = Frame(x, y, width, height))

    private fun group(vararg children: Element, id: String = "group"): GroupElement =
        Slide(elements = children.toList())
            .groupElements(children.map { it.id }, groupId = id)
            .elements
            .single() as GroupElement

    @Test
    fun aGroupIsHitOnItsChildrenNotOnItsBox() {
        val group = group(shape("a", 0f, 0f), shape("b", 240f, 0f))
        assertEquals(Frame(0f, 0f, 340f, 100f), group.frame)

        assertTrue(group.contains(50f, 50f))
        assertTrue(group.contains(290f, 50f))
        // In the bounding box, between the children: a click there belongs to
        // whatever is behind the group.
        assertFalse(group.contains(170f, 50f))
        assertFalse(group.contains(400f, 50f))
    }

    @Test
    fun aRotatedGroupIsHitWhereItIsDrawn() {
        // Bounds (0,0,340,100), center (170,50). A quarter turn puts child "a"
        // (center 120 to the left of that) 120 above it.
        val group = group(shape("a", 0f, 0f), shape("b", 240f, 0f)).copy(rotation = 90f)
        assertTrue(group.contains(170f, -70f))
        assertFalse(group.contains(50f, 50f))
    }

    @Test
    fun nestedGroupsHitThroughToTheirLeaves() {
        val inner = group(shape("a", 0f, 0f), shape("b", 240f, 0f), id = "inner")
        val outer = group(inner, shape("c", 500f, 0f), id = "outer")
        assertTrue(outer.contains(50f, 50f))
        assertTrue(outer.contains(550f, 50f))
        assertFalse(outer.contains(170f, 50f))
    }

    @Test
    fun alignLinesElementsUpOnTheSelectionBounds() {
        // Bounds (0,0,250,200): a is the left and top edge, b the right and bottom.
        val a = shape("a", 0f, 0f, width = 100f, height = 50f)
        val b = shape("b", 200f, 100f, width = 50f, height = 100f)
        val elements = listOf(a, b)

        // Only what moves comes back, so aligning left leaves "a" out of it.
        val left = alignFrames(elements, AlignEdge.Left)
        assertEquals(listOf("b"), left.map { it.id })
        assertEquals(0f, left.single().frame.x)

        assertEquals(150f, alignFrames(elements, AlignEdge.Right).single().frame.x)
        assertEquals(0f, alignFrames(elements, AlignEdge.Top).single().frame.y)
        assertEquals(150f, alignFrames(elements, AlignEdge.Bottom).single().frame.y)

        val centerX = alignFrames(elements, AlignEdge.CenterX)
        assertEquals(listOf(75f, 100f), centerX.map { it.frame.x })
        val centerY = alignFrames(elements, AlignEdge.CenterY)
        assertEquals(listOf(75f, 50f), centerY.map { it.frame.y })
    }

    @Test
    fun aLoneElementAlignsToTheSlide() {
        val element = shape("a", 100f, 100f, width = 200f, height = 100f)
        val single = listOf(element)

        assertEquals(0f, alignFrames(single, AlignEdge.Left).single().frame.x)
        assertEquals(860f, alignFrames(single, AlignEdge.CenterX).single().frame.x)
        assertEquals(1720f, alignFrames(single, AlignEdge.Right).single().frame.x)
        assertEquals(0f, alignFrames(single, AlignEdge.Top).single().frame.y)
        assertEquals(490f, alignFrames(single, AlignEdge.CenterY).single().frame.y)
        assertEquals(980f, alignFrames(single, AlignEdge.Bottom).single().frame.y)

        assertEquals(emptyList(), alignFrames(emptyList(), AlignEdge.Left))
    }

    @Test
    fun distributeEqualizesTheGapsAndLeavesTheEndsPut() {
        val elements = listOf(
            shape("a", 0f, 0f),
            shape("b", 150f, 0f),
            shape("c", 500f, 0f),
        )
        // Span 600 across 300 of element, so two gaps of 150 each.
        val spread = distributeFrames(elements, Axis.Horizontal)
        assertEquals(listOf("b"), spread.map { it.id })
        assertEquals(250f, spread.single().frame.x)

        // Two have no gap to equalize, and neither does an empty selection.
        assertEquals(emptyList(), distributeFrames(elements.take(2), Axis.Horizontal))
        assertEquals(emptyList(), distributeFrames(emptyList(), Axis.Vertical))
    }

    @Test
    fun distributeSortsByPositionNotByTheOrderGiven() {
        val elements = listOf(
            shape("c", 0f, 500f),
            shape("a", 0f, 0f),
            shape("b", 0f, 90f),
        )
        val spread = distributeFrames(elements, Axis.Vertical)
        assertEquals(listOf("b"), spread.map { it.id })
        assertEquals(250f, spread.single().frame.y)
    }
}

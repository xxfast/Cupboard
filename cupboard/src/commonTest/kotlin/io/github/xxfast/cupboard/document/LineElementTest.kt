package io.github.xxfast.cupboard.document

import io.github.xxfast.cupboard.editor.Handle
import io.github.xxfast.cupboard.editor.ResizeDirection
import io.github.xxfast.cupboard.editor.contains
import io.github.xxfast.cupboard.editor.handlePositions
import io.github.xxfast.cupboard.editor.resizeDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A [LineElement] is its two points: the box, the angle and every shared edit
 * are read off them or written back into them.
 */
class LineElementTest {
    private fun line(start: Point, end: Point, strokeWidth: Float = 3f): LineElement = LineElement(
        id = "line",
        start = start,
        end = end,
        strokeColor = 0xFFFFFFFF,
        strokeWidth = strokeWidth,
    )

    private fun assertNear(expected: Point, actual: Point) {
        assertEquals(expected.x, actual.x, 0.001f, "x of $actual")
        assertEquals(expected.y, actual.y, 0.001f, "y of $actual")
    }

    @Test
    fun theFrameIsTheBoxOfBothPoints() {
        assertEquals(Frame(10f, 20f, 90f, 40f), line(Point(100f, 20f), Point(10f, 60f)).frame)
    }

    @Test
    fun aFlatLineGetsAOnePointFrameAcrossIt() {
        assertEquals(Frame(0f, 49.5f, 100f, 1f), line(Point(0f, 50f), Point(100f, 50f)).frame)
        assertEquals(Frame(29.5f, 0f, 1f, 80f), line(Point(30f, 80f), Point(30f, 0f)).frame)
    }

    @Test
    fun aNewFrameMapsBothPointsIntoIt() {
        val moved = line(Point(0f, 0f), Point(100f, 50f)).update(frame = Frame(10f, 20f, 200f, 100f))

        val mapped = assertIs<LineElement>(moved)
        assertEquals(Point(10f, 20f), mapped.start)
        assertEquals(Point(210f, 120f), mapped.end)
    }

    @Test
    fun aFlatLineMovesWithoutLosingItsLevel() {
        val flat = line(Point(0f, 50f), Point(100f, 50f))

        val moved: LineElement = assertIs(flat.update(frame = flat.frame.translate(5f, 10f)))
        assertEquals(Point(5f, 60f), moved.start)
        assertEquals(Point(105f, 60f), moved.end)
    }

    @Test
    fun aRotationTurnsThePointsAndIsNotKept() {
        val turned = assertIs<LineElement>(line(Point(0f, 0f), Point(100f, 0f)).update(rotation = 90f))

        // Clockwise about the centre (50, 0): the start swings up, the end down.
        assertNear(Point(50f, -50f), turned.start)
        assertNear(Point(50f, 50f), turned.end)
        assertEquals(0f, turned.rotation)
    }

    @Test
    fun flipsSwapThePointsAndAreNotKept() {
        val original = line(Point(0f, 0f), Point(100f, 50f))

        val horizontal = assertIs<LineElement>(original.update(flippedHorizontally = true))
        assertEquals(Point(100f, 0f), horizontal.start)
        assertEquals(Point(0f, 50f), horizontal.end)
        assertFalse(horizontal.flippedHorizontally)

        val vertical = assertIs<LineElement>(original.update(flippedVertically = true))
        assertEquals(Point(0f, 50f), vertical.start)
        assertEquals(Point(100f, 0f), vertical.end)
        assertFalse(vertical.flippedVertically)
    }

    @Test
    fun theAngleIsCounterclockwiseWithYUp() {
        assertEquals(0f, line(Point(0f, 0f), Point(10f, 0f)).angle, 0.001f)
        // The end above the start, in a y-down slide, is straight up.
        assertEquals(90f, line(Point(0f, 0f), Point(0f, -10f)).angle, 0.001f)
        assertEquals(180f, line(Point(0f, 0f), Point(-10f, 0f)).angle, 0.001f)
        assertEquals(225f, line(Point(0f, 0f), Point(-10f, 10f)).angle, 0.001f)
        assertEquals(315f, line(Point(0f, 0f), Point(10f, 10f)).angle, 0.001f)
    }

    @Test
    fun anAngleTurnsTheLineAboutItsMidpointKeepingItsLength() {
        val turned = line(Point(0f, 0f), Point(100f, 0f)).withAngle(90f)

        assertNear(Point(50f, 50f), turned.start)
        assertNear(Point(50f, -50f), turned.end)
        assertEquals(100f, turned.length, 0.001f)
        assertEquals(90f, turned.angle, 0.001f)
        assertNear(Point(50f, 0f), turned.midpoint)
    }

    @Test
    fun anEndLandingOnTheStartStaysOnePointOff() {
        val original = line(Point(0f, 0f), Point(100f, 0f))

        // Closer than 1pt keeps its direction and is pushed out to 1pt.
        assertNear(Point(1f, 0f), original.withEnd(Point(0.5f, 0f)).end)
        // Right on top has no direction, so it keeps the one it came from.
        assertNear(Point(1f, 0f), original.withEnd(Point(0f, 0f)).end)
        assertNear(Point(99f, 0f), original.withStart(Point(100f, 0f)).start)
        // Anywhere further off lands exactly there.
        assertEquals(Point(0f, 30f), original.withEnd(Point(0f, 30f)).end)
    }

    @Test
    fun aPointLandsOnTheStrokeOrWithinTheTolerance() {
        val thin = line(Point(0f, 0f), Point(100f, 0f), strokeWidth = 2f)

        assertTrue(thin.contains(50f, 0.9f))
        assertFalse(thin.contains(50f, 3f))
        assertFalse(thin.contains(50f, 50f), "inside the box is not on the line")

        assertTrue(thin.contains(50f, 5f, tolerance = 6f))
        assertFalse(thin.contains(50f, 7f, tolerance = 6f))
        // The ends are round to the tolerance, past either one.
        assertTrue(thin.contains(105f, 0f, tolerance = 6f))
        assertFalse(thin.contains(107f, 0f, tolerance = 6f))

        // A diagonal measures square to itself, not to its box.
        val diagonal = line(Point(0f, 100f), Point(100f, 0f), strokeWidth = 1f)
        assertTrue(diagonal.contains(52f, 52f, tolerance = 6f))
        assertFalse(diagonal.contains(10f, 10f, tolerance = 6f))
    }

    @Test
    fun aLinesHandlesAreItsTwoEndsPullingAlongIt() {
        val rising = line(Point(0f, 100f), Point(100f, 0f))

        assertEquals(
            mapOf(Handle.Start to (0f to 100f), Handle.End to (100f to 0f)),
            rising.handlePositions(),
        )
        assertEquals(ResizeDirection.DiagonalUp, rising.resizeDirection(Handle.End))
        val shallow: LineElement = line(Point(0f, 0f), Point(100f, 5f))
        assertEquals(ResizeDirection.Horizontal, shallow.resizeDirection(Handle.Start))
        val steep: LineElement = line(Point(0f, 0f), Point(5f, 100f))
        assertEquals(ResizeDirection.Vertical, steep.resizeDirection(Handle.Start))
    }

    @Test
    fun aLineSerializesAsItsPointsAlone() {
        val original: Element = line(Point(0f, 120f), Point(320f, 0f)).copy(
            strokeStyle = StrokeStyle.RoundDots,
            startEnd = LineEnd.Diamond,
            endEnd = LineEnd.SweptArrow,
        )

        val json: String = DocumentJson.encodeToString(Element.serializer(), original)
        assertTrue("\"type\": \"line\"" in json, json)
        assertFalse("frame" in json, json)
        assertFalse("rotation" in json, json)
        assertEquals(original, DocumentJson.decodeFromString(Element.serializer(), json))
    }

    private fun curve(start: Point, end: Point, bend: Point): LineElement =
        line(start, end).copy(bend = bend)

    @Test
    fun aCurvePassesThroughItsBendHalfwayAlong() {
        val curved: LineElement = curve(Point(0f, 0f), Point(100f, 0f), bend = Point(30f, 40f))

        assertNear(Point(0f, 0f), curved.pointAt(0f))
        assertNear(Point(30f, 40f), curved.pointAt(0.5f))
        assertNear(Point(100f, 0f), curved.pointAt(1f))
        // The control is twice as far off the chord as the bend.
        assertNear(Point(10f, 80f), curved.control!!)
        assertEquals(null, line(Point(0f, 0f), Point(100f, 0f)).control)
        assertNear(Point(25f, 0f), line(Point(0f, 0f), Point(100f, 0f)).pointAt(0.25f))
    }

    @Test
    fun aCurvesFrameIsTheTightBoxOfItsQuadratic() {
        // An arch: the curve tops out at the bend, half as high as its control.
        val arch: LineElement = curve(Point(0f, 0f), Point(100f, 0f), bend = Point(50f, 50f))
        assertEquals(Frame(0f, 0f, 100f, 50f), arch.frame)

        // A bend past the end swings the curve out beyond it, and the box with it:
        // control (190, 80), so x turns back at t = 19 / 28.
        val overhang: Frame = curve(Point(0f, 0f), Point(100f, 0f), bend = Point(120f, 40f)).frame
        assertEquals(0f, overhang.x, 0.001f)
        assertEquals(0f, overhang.y, 0.001f)
        assertEquals(128.9286f, overhang.width, 0.01f)
        assertEquals(40f, overhang.height, 0.001f)
    }

    @Test
    fun movingAnEndKeepsTheBulgesDepthAndStretchesItsOffset() {
        val curved: LineElement = curve(Point(0f, 0f), Point(100f, 0f), bend = Point(30f, -35f))

        // Twice the chord: the offset along it doubles, the depth off it stays.
        assertNear(Point(60f, -35f), curved.withEnd(Point(200f, 0f)).bend!!)

        // Turned to point down, the bulge turns with the chord.
        assertNear(Point(35f, 60f), curved.withEnd(Point(0f, 200f)).bend!!)

        // The start too, measured from wherever the start now is.
        assertNear(Point(65f, -35f), curved.withStart(Point(50f, 0f)).bend!!)
    }

    @Test
    fun turningACurveTurnsItsBend() {
        val curved: LineElement = curve(Point(0f, 0f), Point(100f, 0f), bend = Point(50f, -30f))

        val upright: LineElement = curved.withAngle(90f)
        assertNear(Point(50f, 50f), upright.start)
        assertNear(Point(50f, -50f), upright.end)
        // Still on the left of the way it runs, still 30 off the chord.
        assertNear(Point(20f, 0f), upright.bend!!)
    }

    @Test
    fun theSharedEditsCarryTheBendWithThePoints() {
        val curved: LineElement = curve(Point(0f, 0f), Point(100f, 0f), bend = Point(50f, 40f))

        val moved = assertIs<LineElement>(curved.update(frame = curved.frame.translate(10f, 20f)))
        assertNear(Point(60f, 60f), moved.bend!!)

        // Flipped upside down about the box's middle (y = 20).
        val flipped = assertIs<LineElement>(curved.update(flippedVertically = true))
        assertNear(Point(0f, 40f), flipped.start)
        assertNear(Point(50f, 0f), flipped.bend!!)

        // A half turn about the box's centre (50, 20).
        val turned = assertIs<LineElement>(curved.update(rotation = 180f))
        assertNear(Point(50f, 0f), turned.bend!!)
    }

    @Test
    fun aCurveIsHitOnTheCurveAndNotOnItsChord() {
        val deep: LineElement = curve(Point(0f, 0f), Point(100f, 0f), bend = Point(50f, 40f))

        assertTrue(deep.contains(50f, 40f))
        assertTrue(deep.contains(50f, 41f))
        assertFalse(deep.contains(50f, 0f))
        assertFalse(deep.contains(50f, 20f, tolerance = 6f))
    }

    @Test
    fun onlyACurveHasABendHandle() {
        assertFalse(Handle.Bend in line(Point(0f, 0f), Point(100f, 0f)).handlePositions())

        val curved: LineElement = curve(Point(0f, 0f), Point(100f, 0f), bend = Point(50f, 40f))
        assertEquals(50f to 40f, curved.handlePositions()[Handle.Bend])
        assertEquals(curved.resizeDirection(Handle.End), curved.resizeDirection(Handle.Bend))
    }

    @Test
    fun aFreshCurvedLineBulgesUpAndLeftLikeKeynotesTile() {
        val box = Frame(0f, 0f, 200f, 100f)
        val curved: LineElement = lineElement(box, curved = true)

        assertNear(Point(50f, 25f), curved.bend!!)
        assertNear(Point(0f, 0f), curved.control!!)
        assertEquals(box, curved.frame)
        assertEquals(null, lineElement(box).bend)
    }

    @Test
    fun aCurveSerializesItsBend() {
        val original: Element = curve(Point(0f, 0f), Point(100f, 0f), bend = Point(50f, 40f))

        val json: String = DocumentJson.encodeToString(Element.serializer(), original)
        assertTrue("bend" in json, json)
        assertEquals(original, DocumentJson.decodeFromString(Element.serializer(), json))
    }
}

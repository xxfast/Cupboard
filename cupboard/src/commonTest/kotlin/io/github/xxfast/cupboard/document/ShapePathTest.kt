package io.github.xxfast.cupboard.document

import io.github.xxfast.cupboard.document.PathCommand.Close
import io.github.xxfast.cupboard.document.PathCommand.CubicTo
import io.github.xxfast.cupboard.document.PathCommand.LineTo
import io.github.xxfast.cupboard.document.PathCommand.MoveTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ShapePath] as data: what its string parses to, what a command list writes
 * back as, how tightly an outline is boxed and where it splits into contours.
 */
class ShapePathTest {
    private val unit = Frame(0f, 0f, 100f, 100f)

    private fun commands(data: String): List<PathCommand> = ShapePath(data, unit).commands

    private fun pathShape(data: String): ShapeElement =
        ShapeElement(frame = unit, kind = ShapeKind.Path, path = ShapePath(data, unit))

    @Test
    fun dataParsesIntoItsFourCommands() {
        assertEquals(
            listOf(
                MoveTo(0f, 0f),
                LineTo(10f, 0f),
                CubicTo(10f, 5f, 5f, 10f, 0f, 10f),
                Close,
            ),
            commands("M0 0 L10 0 C10 5 5 10 0 10 Z"),
        )

        // Separators are whatever SVG allows: commas, none at all, a sign.
        assertEquals(
            listOf(MoveTo(1.5f, 0.5f), LineTo(-1f, -2f), LineTo(0.001f, 3f)),
            commands("M1.5.5L-1-2,1e-3,3"),
        )
    }

    @Test
    fun aMalformedTailIsDroppedRatherThanThrown() {
        assertEquals(listOf(MoveTo(0f, 0f), LineTo(10f, 10f)), commands("M0 0 L10 10 L5"))
        assertEquals(listOf(MoveTo(0f, 0f)), commands("M0 0 Q1 2 3 4 L5 5"))
        assertEquals(listOf(MoveTo(0f, 0f), LineTo(1f, 1f)), commands("M0 0 L1 1 !!"))
        assertEquals(emptyList(), commands(""))
        assertEquals(emptyList(), commands("nonsense"))
    }

    @Test
    fun writingKeepsFourDecimalsAndStripsTrailingZeros() {
        val written: String = listOf(
            MoveTo(0f, -0f),
            LineTo(10.5f, -3.25f),
            CubicTo(0.1f, 1f / 3f, 2.00004f, 1234.5f, -0.00004f, 100f),
            Close,
        ).toPathData()

        assertEquals("M0 0 L10.5 -3.25 C0.1 0.3333 2 1234.5 0 100 Z", written)
    }

    @Test
    fun writtenDataReadsBackAsTheSameCommands() {
        val outline: List<PathCommand> = listOf(
            MoveTo(12.5f, -4f),
            LineTo(100f, 0.25f),
            CubicTo(1f, 2f, 3f, 4f, 5f, 6f),
            Close,
            MoveTo(7f, 7f),
            LineTo(8f, 8f),
        )

        assertEquals(outline, commands(outline.toPathData()))
    }

    @Test
    fun boundsMeasureACurveWhereItTurnsNotByItsControlPoints() {
        // The control points reach y = 100, the curve itself only three quarters
        // of the way there.
        val arch: List<PathCommand> = commands("M0 0 C0 100 100 100 100 0")
        assertEquals(Frame(0f, 0f, 100f, 75f), arch.tightBounds())

        // An S-bend overshoots both ends sideways, where no endpoint is.
        val bend: Frame = commands("M0 0 C100 0 -100 100 0 100").tightBounds()
        assertTrue(bend.x < -15f && bend.x > -40f, "left edge at ${bend.x}")
        assertTrue(bend.x + bend.width > 15f, "right edge at ${bend.x + bend.width}")
        assertEquals(0f, bend.y)
        assertEquals(100f, bend.height)
    }

    @Test
    fun boundsIgnoreAMoveNothingIsDrawnFrom() {
        assertEquals(
            Frame(10f, 10f, 10f, 10f),
            commands("M-500 -500 M10 10 L20 20 M900 900").tightBounds(),
        )
        assertEquals(Frame(0f, 0f, 0f, 0f), commands("M5 5").tightBounds())

        // A line drawn after a close starts back at the contour's first point.
        assertEquals(
            Frame(0f, 0f, 30f, 10f),
            commands("M0 0 L10 0 L10 10 Z L30 5").tightBounds(),
        )
    }

    @Test
    fun anOutlineSplitsAtEachMove() {
        val square = "M0 0 L10 0 L10 10 L0 10 Z"
        val hole = "M2 2 L2 8 L8 8 L8 2 Z"
        val split: List<List<PathCommand>> = commands("$square $hole").subpaths()

        assertEquals(listOf(square, hole), split.map { it.toPathData() })
    }

    @Test
    fun splittingDropsBareMovesAndRestartsAfterAClose() {
        // The lone moves are no contour, and the line after Z picks up from
        // where the closed one began, with that move written out.
        val split: List<List<PathCommand>> =
            commands("M9 9 M0 0 L10 0 L10 10 Z L5 5 M3 3 Z M7 7").subpaths()

        assertEquals(listOf("M0 0 L10 0 L10 10 Z", "M0 0 L5 5"), split.map { it.toPathData() })
    }

    @Test
    fun onlyAPathShapeWithTwoContoursIsCompound() {
        val two = "M0 0 L10 0 L10 10 Z M20 20 L30 20 L30 30 Z"
        assertTrue(pathShape(two).isCompoundShape)
        assertFalse(pathShape("M0 0 L10 0 L10 10 Z").isCompoundShape)
        assertFalse(pathShape("M0 0 L10 0 L10 10 Z M5 5").isCompoundShape)

        // The path only counts on the kind that draws by it.
        assertFalse(pathShape(two).copy(kind = ShapeKind.Rectangle).isCompoundShape)
        assertFalse(ShapeElement(frame = unit, kind = ShapeKind.Path).isCompoundShape)
        assertFalse(TextElement(frame = unit).isCompoundShape)
        assertFalse(GroupElement(frame = unit, children = listOf(pathShape(two))).isCompoundShape)
    }

    @Test
    fun aBoxShorterThanItsMinimumGrowsAroundItsCentre() {
        assertEquals(Frame(0f, 8f, 100f, 4f), Frame(0f, 10f, 100f, 0f).atLeast(4f, 4f))
        assertEquals(Frame(-1f, -1f, 2f, 2f), Frame(0f, 0f, 0f, 0f).atLeast(2f, 2f))
        // One already big enough is left exactly as it was.
        assertEquals(Frame(3f, 4f, 50f, 60f), Frame(3f, 4f, 50f, 60f).atLeast(1f, 1f))
    }
}

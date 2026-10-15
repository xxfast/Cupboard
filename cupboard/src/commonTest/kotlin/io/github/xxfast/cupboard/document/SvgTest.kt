package io.github.xxfast.cupboard.document

import io.github.xxfast.cupboard.document.PathCommand.CubicTo
import io.github.xxfast.cupboard.document.PathCommand.LineTo
import io.github.xxfast.cupboard.document.PathCommand.MoveTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The SVG reader, a feature at a time: what counts as an SVG, each element and
 * each path command it draws, transforms, paint in all the places SVG lets it
 * be written, how the result is sized, and what XML it puts up with.
 *
 * Most cases read an outline back as its written [ShapePath.data], which is
 * exact to four decimals and so says in one string what a list of floats would
 * take a tolerance per coordinate to.
 */
class SvgTest {
    private val roomy = 10_000f

    private fun svg(body: String, attributes: String = """viewBox="0 0 100 100""""): String =
        """<svg xmlns="http://www.w3.org/2000/svg" $attributes>$body</svg>"""

    private fun shape(source: String, max: Float = roomy): ShapeElement =
        assertIs<ShapeElement>(svgElement(source, max, max))

    private fun shapeOf(body: String): ShapeElement = shape(svg(body))

    private fun pathOf(body: String): ShapePath = assertNotNull(shapeOf(body).path)

    private fun dataOf(body: String): String = pathOf(body).data

    private fun pathData(d: String): String = svgPathCommands(d).toPathData()

    private fun assertFrame(expected: Frame, actual: Frame, tolerance: Float = 0.05f) {
        assertEquals(expected.x, actual.x, tolerance, "x of $actual")
        assertEquals(expected.y, actual.y, tolerance, "y of $actual")
        assertEquals(expected.width, actual.width, tolerance, "width of $actual")
        assertEquals(expected.height, actual.height, tolerance, "height of $actual")
    }

    // What counts

    @Test
    fun textThatIsNotAnSvgOrDrawsNothingIsNull() {
        assertNull(svgElement("", roomy, roomy))
        assertNull(svgElement("just some words", roomy, roomy))
        assertNull(svgElement("""{ "svg": true }""", roomy, roomy))
        assertNull(svgElement("<html><body><p>Hello</p></body></html>", roomy, roomy))
        assertNull(svgElement("<svg/>", roomy, roomy))
        assertNull(svgElement(svg("<g><g/></g>"), roomy, roomy))
        assertNull(svgElement(svg("""<path d=""/><rect width="0" height="10"/>"""), roomy, roomy))
        // Drawn, but with nothing to see.
        assertNull(svgElement(svg("""<path d="M0 0h24v24H0z" fill="none"/>"""), roomy, roomy))
        assertNull(svgElement(svg("""<rect width="9" height="9" opacity="0"/>"""), roomy, roomy))
    }

    @Test
    fun aSinglePathIsOneShapeHuggingItsOutline() {
        val shape: ShapeElement = shape(
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <path d="M4 6h16v12H4z"/>
            </svg>
            """.trimIndent(),
        )

        // The viewBox's padding is gone: the frame starts at the outline, and
        // the path keeps the numbers it was written in.
        assertEquals(Frame(0f, 0f, 16f, 12f), shape.frame)
        assertEquals(ShapePath("M4 6 L20 6 L20 18 L4 18 Z", Frame(4f, 6f, 16f, 12f)), shape.path)
        assertEquals(ShapeKind.Path, shape.kind)
        assertEquals(0xFF000000, shape.fill)
        assertEquals(0f, shape.strokeWidth)
        assertEquals(0f, shape.cornerRadius)
        assertEquals(1f, shape.opacity)
        assertEquals("", shape.label)
        assertNull(shape.shadow)
        assertNull(shape.gradient)
        assertEquals("Shape", shape.displayName)
    }

    @Test
    fun anIconsInvisibleBoundingSquareIsLeftOut() {
        val shape: ShapeElement = shape(
            svg(
                """<path d="M0 0h24v24H0z" fill="none"/><path d="M12 2L2 22h20z"/>""",
                """viewBox="0 0 24 24"""",
            ),
        )

        assertEquals(Frame(0f, 0f, 20f, 20f), shape.frame)
        assertEquals("M12 2 L2 22 L22 22 Z", shape.path?.data)
    }

    // Elements

    @Test
    fun aRectIsFourLinesAndARoundedOneFourCorners() {
        assertEquals(
            "M1 2 L11 2 L11 22 L1 22 Z",
            dataOf("""<rect x="1" y="2" width="10" height="20"/>"""),
        )

        val rounded: ShapePath = pathOf("""<rect x="1" y="2" width="10" height="20" rx="2"/>""")
        val outline: List<PathCommand> = rounded.commands
        // One radius stands for both.
        assertEquals(MoveTo(3f, 2f), outline.first())
        assertEquals(4, outline.count { it is CubicTo })
        assertEquals(4, outline.count { it is LineTo })
        assertEquals(Frame(1f, 2f, 10f, 20f), rounded.bounds)

        // Radii past the middle stop there: this one is an ellipse in a box.
        val pill: ShapePath = pathOf("""<rect width="10" height="20" rx="99" ry="99"/>""")
        assertEquals(MoveTo(5f, 0f), pill.commands.first())
        assertEquals(Frame(0f, 0f, 10f, 20f), pill.bounds)
    }

    @Test
    fun aCircleAndAnEllipseAreFourQuarterTurns() {
        val circle: ShapePath = pathOf("""<circle cx="10" cy="10" r="5"/>""")
        assertEquals(MoveTo(15f, 10f), circle.commands.first())
        assertEquals(4, circle.commands.count { it is CubicTo })
        assertFrame(Frame(5f, 5f, 10f, 10f), circle.bounds)

        val ellipse: ShapePath = pathOf("""<ellipse cx="50" cy="50" rx="30" ry="10"/>""")
        assertFrame(Frame(20f, 40f, 60f, 20f), ellipse.bounds)
        assertNull(svgElement(svg("""<circle cx="10" cy="10" r="0"/>"""), roomy, roomy))
    }

    @Test
    fun aLineIsItsStrokeAndNothingWithoutOne() {
        val line: ShapeElement =
            shapeOf("""<line x1="0" y1="0" x2="10" y2="20" stroke="red" stroke-width="2"/>""")
        assertEquals("M0 0 L10 20", line.path?.data)
        // No inside, so SVG's default black fill is not carried over.
        assertEquals(0x00000000L, line.fill)
        assertEquals(0xFFFF0000, line.strokeColor)
        assertEquals(2f, line.strokeWidth)

        assertNull(svgElement(svg("""<line x1="0" y1="0" x2="10" y2="20"/>"""), roomy, roomy))
    }

    @Test
    fun aPolylineStaysOpenAndAPolygonCloses() {
        assertEquals(
            "M0 0 L10 0 L10 10",
            dataOf("""<polyline points="0,0 10,0 10,10" fill="none" stroke="black"/>"""),
        )
        assertEquals("M0 0 L10 0 L10 10 Z", dataOf("""<polygon points="0 0 10 0 10 10"/>"""))
        // An odd coordinate left over is dropped.
        assertEquals("M0 0 L10 0 Z", dataOf("""<polygon points="0,0 10,0 7"/>"""))
    }

    // Path data

    @Test
    fun relativeCommandsAndTheAxisShorthandsResolveToAbsoluteLines() {
        assertEquals("M10 10 L20 10 L20 20 L10 20 Z", pathData("m10 10 h10 v10 h-10 z"))
        assertEquals("M10 10 L30 10 L30 5 L12 7", pathData("M10 10 H30 V5 l-18 2"))
    }

    @Test
    fun extraPairsRepeatTheCommandAndAfterAMoveAreLines() {
        assertEquals("M0 0 L10 0 L10 10", pathData("M0 0 10 0 10 10"))
        assertEquals("M1 1 L3 3 L6 6", pathData("m1 1 2 2 3 3"))
        assertEquals("M0 0 L1 0 L2 0 L2 5 L2 9", pathData("M0 0 h1 1 v5 4"))
    }

    @Test
    fun numbersPackTogetherTheWaySvgAllows() {
        assertEquals("M1.5 0.5 L-1 -2 L10 2", pathData("M1.5.5L-1-2 1e1,2E0"))
        // Arc flags run straight into the number after them.
        val arc: List<PathCommand> = svgPathCommands("M0 0a1 1 0 01.5.5")
        assertEquals(2, arc.size)
        val curve: CubicTo = assertIs<CubicTo>(arc[1])
        assertEquals(0.5f, curve.x)
        assertEquals(0.5f, curve.y)
    }

    @Test
    fun everyContourOpensWithItsOwnMove() {
        // Picked back up after a close: SVG starts again from the contour's first point.
        assertEquals(
            "M0 0 L10 0 L10 10 Z M0 0 L-5 -5",
            pathData("M0 0 L10 0 L10 10 Z L-5 -5"),
        )
        // A move nothing is drawn from is not written at all.
        assertEquals("M2 2 L3 3", pathData("M1 1 M2 2 L3 3 M9 9"))
        assertEquals("", pathData("M1 1 Z"))
    }

    @Test
    fun malformedDataKeepsWhatParsedBeforeIt() {
        assertEquals("M0 0 L10 10", pathData("M0 0 L10 10 L5"))
        assertEquals("M0 0 L10 10", pathData("M0 0 L10 10 X 1 2 L3 3"))
        assertEquals("M0 0 L10 10", pathData("M0 0 L10 10 A5 5 0 2 1 9 9"))
        // Path data that does not open with a move draws nothing.
        assertEquals("", pathData("L10 10 L20 20"))
        assertEquals("", pathData("garbage"))
    }

    @Test
    fun smoothCubicsReflectTheControlPointBeforeThem() {
        assertEquals(
            "M0 0 C0 10 10 10 10 0 C10 -10 20 -10 20 0",
            pathData("M0 0 C0 10 10 10 10 0 S20 -10 20 0"),
        )
        // With no cubic before it there is nothing to reflect.
        assertEquals("M0 0 C0 0 5 5 10 0", pathData("M0 0 S5 5 10 0"))
        assertEquals("M0 0 L4 0 C4 0 5 5 10 0", pathData("M0 0 L4 0 s1 5 6 0"))
    }

    @Test
    fun quadraticsAreRaisedToTheCubicThatIsTheSameCurve() {
        assertEquals("M0 0 C20 20 40 20 60 0", pathData("M0 0 Q30 30 60 0"))
        assertEquals("M0 0 C20 20 40 20 60 0", pathData("M0 0 q30 30 60 0"))
        // T reflects the quadratic's control point, not the raised cubic's.
        assertEquals(
            "M0 0 C20 20 40 20 60 0 C80 -20 100 -20 120 0",
            pathData("M0 0 Q30 30 60 0 T120 0"),
        )
        assertEquals("M0 0 C0 0 2 0 6 0", pathData("M0 0 T6 0"))
    }

    @Test
    fun arcsBecomeCubicsOfAQuarterTurnAtMost() {
        val quarter: List<PathCommand> = svgPathCommands("M10 0 A10 10 0 0 1 0 10")
        assertEquals(1, quarter.count { it is CubicTo })
        val curve: CubicTo = assertIs<CubicTo>(quarter[1])
        assertEquals(10f, curve.x1, 0.001f)
        assertEquals(5.5228f, curve.y1, 0.001f)
        assertEquals(5.5228f, curve.x2, 0.001f)
        assertEquals(10f, curve.y2, 0.001f)
        assertEquals(0f, curve.x)
        assertEquals(10f, curve.y)

        // Sweep picks the side: the same two ends, over the top or underneath.
        val over: List<PathCommand> = svgPathCommands("M0 0 A50 50 0 0 1 100 0")
        assertEquals(2, over.count { it is CubicTo })
        assertFrame(Frame(0f, -50f, 100f, 50f), over.tightBounds())
        val under: List<PathCommand> = svgPathCommands("M0 0 A50 50 0 0 0 100 0")
        assertFrame(Frame(0f, 0f, 100f, 50f), under.tightBounds())

        // The large arc is the long way round: three quarters of this circle.
        val long: List<PathCommand> = svgPathCommands("M10 0 A10 10 0 1 1 0 10")
        assertEquals(3, long.count { it is CubicTo })
        assertFrame(Frame(0f, 0f, 20f, 20f), long.tightBounds())

        // Two half turns is the circle icons draw with arcs.
        val circle: List<PathCommand> = svgPathCommands("M0 10 a10 10 0 1 0 20 0 a10 10 0 1 0 -20 0 z")
        assertEquals(4, circle.count { it is CubicTo })
        assertFrame(Frame(0f, 0f, 20f, 20f), circle.tightBounds())
    }

    @Test
    fun anArcsRotationTurnsItsEllipse() {
        // Radii 20 by 10 turned a quarter turn: 10 wide and 20 tall.
        val turned: List<PathCommand> = svgPathCommands("M0 0 A20 10 90 0 1 20 0")
        assertFrame(Frame(0f, -20f, 20f, 20f), turned.tightBounds())
    }

    @Test
    fun degenerateArcsFollowSvgsOutOfRangeRules() {
        // No radius is a straight line, and an arc to where the pen is draws nothing.
        assertEquals("M0 0 L10 10", pathData("M0 0 A0 5 0 0 1 10 10"))
        assertEquals("M5 5 L9 9", pathData("M5 5 A10 10 0 0 1 5 5 L9 9"))

        // Radii too small to reach are scaled up until they just do: a half circle.
        val stretched: List<PathCommand> = svgPathCommands("M0 0 A1 1 0 0 1 100 0")
        assertFrame(Frame(0f, -50f, 100f, 50f), stretched.tightBounds())
        // A negative radius is its absolute value.
        val negative: List<PathCommand> = svgPathCommands("M0 0 A-50 -50 0 0 1 100 0")
        assertFrame(Frame(0f, -50f, 100f, 50f), negative.tightBounds())
    }

    // Transforms

    @Test
    fun eachTransformFunctionIsFlattenedIntoTheCoordinates() {
        fun transformed(transform: String): String =
            dataOf("""<rect width="10" height="10" transform="$transform"/>""")

        assertEquals("M5 7 L15 7 L15 17 L5 17 Z", transformed("translate(5 7)"))
        assertEquals("M5 0 L15 0 L15 10 L5 10 Z", transformed("translate(5)"))
        assertEquals("M0 0 L20 0 L20 20 L0 20 Z", transformed("scale(2)"))
        assertEquals("M0 0 L20 0 L20 30 L0 30 Z", transformed("scale(2, 3)"))
        assertEquals("M0 0 L0 10 L-10 10 L-10 0 Z", transformed("rotate(90)"))
        assertEquals("M20 0 L20 10 L10 10 L10 0 Z", transformed("rotate(90 10 10)"))
        assertEquals("M3 4 L13 4 L13 14 L3 14 Z", transformed("matrix(1 0 0 1 3 4)"))
        assertEquals("M0 0 L10 0 L20 10 L10 10 Z", transformed("skewX(45)"))
        assertEquals("M0 0 L10 10 L10 20 L0 10 Z", transformed("skewY(45)"))
        // One this doesn't know is the identity, not the end of the list.
        assertEquals("M5 7 L15 7 L15 17 L5 17 Z", transformed("perspective(3) translate(5 7)"))
    }

    @Test
    fun transformsComposeDownTheTreeAndAlongAList() {
        val nested: String = dataOf(
            """
            <g transform="translate(10,0)">
              <g transform="scale(2)"><rect x="1" y="1" width="2" height="2"/></g>
            </g>
            """.trimIndent(),
        )
        assertEquals("M12 2 L16 2 L16 6 L12 6 Z", nested)

        val listed: String =
            dataOf("""<rect x="1" y="1" width="2" height="2" transform="translate(10,0) scale(2)"/>""")
        assertEquals(nested, listed)
    }

    @Test
    fun aTransformScalesTheStrokeByItsAverageStretch() {
        val line: ShapeElement = shapeOf(
            """
            <g transform="scale(2 8)">
              <line x1="0" y1="0" x2="10" y2="10" stroke="black" stroke-width="1"/>
            </g>
            """.trimIndent(),
        )
        assertEquals(4f, line.strokeWidth, 0.001f)
    }

    @Test
    fun aNestedSvgIsAViewportOfItsOwn() {
        val data: String = dataOf(
            """
            <svg x="10" y="20" width="50" height="50" viewBox="0 0 10 10">
              <rect width="10" height="10"/>
            </svg>
            """.trimIndent(),
        )
        assertEquals("M10 20 L60 20 L60 70 L10 70 Z", data)
    }

    // Paint

    @Test
    fun paintReadsFromAttributes() {
        val shape: ShapeElement = shapeOf(
            """<rect width="9" height="9" fill="#f00" stroke="#00ff00" stroke-width="3"/>""",
        )
        assertEquals(0xFFFF0000, shape.fill)
        assertEquals(0xFF00FF00, shape.strokeColor)
        assertEquals(3f, shape.strokeWidth)
    }

    @Test
    fun svgsDefaultsAreBlackFillAndNoStroke() {
        val plain: ShapeElement = shapeOf("""<rect width="9" height="9"/>""")
        assertEquals(0xFF000000, plain.fill)
        assertEquals(0f, plain.strokeWidth)

        // A stroke with no width given is one unit wide.
        val stroked: ShapeElement = shapeOf("""<rect width="9" height="9" stroke="blue"/>""")
        assertEquals(1f, stroked.strokeWidth)

        // No fill is a clear one, and a stroke of no width is no stroke.
        val hollow: ShapeElement =
            shapeOf("""<rect width="9" height="9" fill="none" stroke="red" stroke-width="2"/>""")
        assertEquals(0x00000000L, hollow.fill)
        val unstroked: ShapeElement =
            shapeOf("""<rect width="9" height="9" stroke="red" stroke-width="0"/>""")
        assertEquals(0f, unstroked.strokeWidth)
    }

    @Test
    fun inlineStyleWinsOverAClassWinsOverAnAttribute() {
        val sheet = "<style>.a{fill:green} .b,.c{stroke:blue;stroke-width:2}</style>"

        val classed: ShapeElement =
            shapeOf("""$sheet<rect class="a c" width="9" height="9" fill="red"/>""")
        assertEquals(0xFF008000, classed.fill)
        assertEquals(0xFF0000FF, classed.strokeColor)
        assertEquals(2f, classed.strokeWidth)

        val inline: ShapeElement = shapeOf(
            """$sheet<rect class="a b" width="9" height="9" fill="red" style="fill: Blue; stroke-width:5"/>""",
        )
        assertEquals(0xFF0000FF, inline.fill)
        assertEquals(5f, inline.strokeWidth)

        val attribute: ShapeElement = shapeOf("""$sheet<rect width="9" height="9" fill="red"/>""")
        assertEquals(0xFFFF0000, attribute.fill)
        assertEquals(0f, attribute.strokeWidth)
    }

    @Test
    fun aStylesheetIsReadWhereverItSitsAndOnlyForPlainClasses() {
        val shape: ShapeElement = shapeOf(
            """
            <rect class="x" width="9" height="9"/>
            <defs>
              <style type="text/css"><![CDATA[
                /* a comment with a brace { in it */
                rect { fill: red }
                g .x { fill: red }
                #x { fill: red }
                .x { fill: #00f !important; }
              ]]></style>
            </defs>
            """.trimIndent(),
        )
        assertEquals(0xFF0000FF, shape.fill)
    }

    @Test
    fun paintIsInheritedThroughGroups() {
        val inherited: ShapeElement = shapeOf(
            """
            <g fill="red" stroke="blue" stroke-width="2" fill-rule="evenodd">
              <g><rect width="9" height="9"/></g>
            </g>
            """.trimIndent(),
        )
        assertEquals(0xFFFF0000, inherited.fill)
        assertEquals(0xFF0000FF, inherited.strokeColor)
        assertEquals(2f, inherited.strokeWidth)
        assertEquals(true, inherited.path?.evenOdd)

        val overridden: ShapeElement = shapeOf(
            """<g fill="red" fill-rule="evenodd"><rect width="9" height="9" fill="lime" fill-rule="nonzero"/></g>""",
        )
        assertEquals(0xFF00FF00, overridden.fill)
        assertEquals(false, overridden.path?.evenOdd)
    }

    @Test
    fun opacityMultipliesDownTheTreeAndPaintOpacityGoesIntoTheColour() {
        val shape: ShapeElement = shapeOf(
            """
            <g opacity="0.5">
              <rect width="9" height="9" opacity="50%" fill="#ff0000" fill-opacity="0.5"
                    stroke="#0000ff" stroke-opacity="0.25"/>
            </g>
            """.trimIndent(),
        )
        assertEquals(0.25f, shape.opacity)
        assertEquals(0x80FF0000, shape.fill)
        assertEquals(0x400000FFL, shape.strokeColor)
    }

    @Test
    fun coloursReadInEveryFormSvgWritesThem() {
        assertEquals(0xFFFF0000, svgColor("#f00"))
        assertEquals(0x88FF0000, svgColor("#F008"))
        assertEquals(0xFF336699, svgColor("#336699"))
        assertEquals(0x80336699, svgColor("#33669980"))
        assertEquals(0xFFFF0000, svgColor("rgb(255, 0, 0)"))
        assertEquals(0xFFFF0080, svgColor("rgb(100%, 0%, 50%)"))
        assertEquals(0x800000FF, svgColor("rgba(0,0,255,0.5)"))
        assertEquals(0x80008000, svgColor("rgb(0 128 0 / 50%)"))
        assertEquals(0xFFFF0000, svgColor("rgb(300, -5, 0)"))
        assertEquals(0x00000000L, svgColor("transparent"))
        assertEquals(0xFF000000, svgColor("currentColor"))
        assertEquals(0xFFFFA500, svgColor(" Orange "))
        assertEquals(0xFF000080, svgColor("navy"))

        assertNull(svgColor("chartreuse"))
        assertNull(svgColor("#12"))
        assertNull(svgColor("#ggg"))
        assertNull(svgColor("rgb(1, 2)"))
        assertNull(svgColor("inherit"))
    }

    @Test
    fun aValueThisCantReadLeavesTheInheritedOneStanding() {
        val shape: ShapeElement = shapeOf(
            """<g fill="red"><rect width="9" height="9" fill="chartreuse" stroke-width="2em"/></g>""",
        )
        assertEquals(0xFFFF0000, shape.fill)
    }

    @Test
    fun aLinearGradientKeepsItsEndsAndItsDirection() {
        fun gradient(attributes: String): ShapeElement = shapeOf(
            """
            <defs>
              <linearGradient id="g" $attributes>
                <stop offset="0" stop-color="#ff0000"/>
                <stop offset="0.5" stop-color="#00ff00"/>
                <stop offset="1" style="stop-color:#0000ff;stop-opacity:0.5"/>
              </linearGradient>
            </defs>
            <rect width="9" height="9" fill="url(#g)"/>
            """.trimIndent(),
        )

        val down: ShapeElement = gradient("""x1="0" y1="0" x2="0" y2="1"""")
        assertEquals(0xFFFF0000, down.gradient?.start)
        assertEquals(0x800000FF, down.gradient?.end)
        assertEquals(180f, down.gradient!!.angle, 0.01f)
        // The solid underneath is the first stop, so dropping the gradient lands on it.
        assertEquals(0xFFFF0000, down.fill)

        // CSS degrees: 0 points up and the angle turns clockwise.
        assertEquals(90f, gradient("").gradient!!.angle, 0.01f)
        assertEquals(0f, gradient("""x1="0" y1="1" x2="0" y2="0"""").gradient!!.angle, 0.01f)
        assertEquals(135f, gradient("""x2="100%" y2="100%"""").gradient!!.angle, 0.01f)
        assertEquals(270f, gradient("""x1="1" x2="0"""").gradient!!.angle, 0.01f)
        assertEquals(180f, gradient("""gradientTransform="rotate(90)"""").gradient!!.angle, 0.01f)
    }

    @Test
    fun aGradientBorrowsItsStopsThroughOneHref() {
        val shape: ShapeElement = shapeOf(
            """
            <linearGradient id="colours">
              <stop offset="0" stop-color="white"/><stop offset="1" stop-color="navy"/>
            </linearGradient>
            <linearGradient id="g" xlink:href="#colours" x1="0" y1="1" x2="0" y2="0"/>
            <rect width="9" height="9" fill="url('#g')" fill-opacity="0.5"/>
            """.trimIndent(),
        )
        assertEquals(ShapeGradient(0x80FFFFFF, 0x80000080, 0f), shape.gradient)
    }

    @Test
    fun aRadialGradientKeepsItsCentreAndItsEnds() {
        fun gradient(attributes: String): ShapeGradient = assertNotNull(
            shapeOf(
                """
                <radialGradient id="r" $attributes>
                  <stop offset="0" stop-color="#ff0000"/><stop offset="1" stop-color="#0000ff"/>
                </radialGradient>
                <rect width="100" height="50" fill="url(#r)"/>
                """.trimIndent(),
            ).gradient,
        )

        val offCentre: ShapeGradient = gradient("""cx="0.25" cy="75%" r="0.4"""")
        assertEquals(GradientKind.Radial, offCentre.kind)
        assertEquals(0xFFFF0000, offCentre.start)
        assertEquals(0xFF0000FF, offCentre.end)
        assertEquals(0.25f, offCentre.centerX, 0.001f)
        assertEquals(0.75f, offCentre.centerY, 0.001f)
        assertEquals(0.4f, offCentre.radius, 0.001f)

        // SVG's defaults fill the box from its middle, which are the document's.
        assertEquals(ShapeGradient(0xFFFF0000, 0xFF0000FF, kind = GradientKind.Radial), gradient(""))
    }

    /**
     * In user space the circle is measured against the box it fills, through the
     * `gradientTransform` that is how design tools place one: a unit circle at
     * the origin, moved and scaled into place.
     */
    @Test
    fun aRadialGradientInUserSpaceIsMeasuredAgainstItsBox() {
        val shape: ShapeElement = shapeOf(
            """
            <radialGradient id="r" cx="0" cy="0" r="1" gradientUnits="userSpaceOnUse"
              gradientTransform="translate(60 45) scale(25)">
              <stop stop-color="#ff0000"/><stop offset="1" stop-color="#0000ff"/>
            </radialGradient>
            <rect x="10" y="20" width="100" height="50" fill="url(#r)"/>
            """.trimIndent(),
        )

        val gradient: ShapeGradient = assertNotNull(shape.gradient)
        assertEquals(GradientKind.Radial, gradient.kind)
        assertEquals(0.5f, gradient.centerX, 0.001f)
        assertEquals(0.5f, gradient.centerY, 0.001f)
        // A fraction of the longer side.
        assertEquals(0.25f, gradient.radius, 0.001f)
    }

    @Test
    fun aMissingGradientFallsBackToASolid() {
        val missing: ShapeElement = shapeOf("""<rect width="9" height="9" fill="url(#nowhere)"/>""")
        assertNull(missing.gradient)
        assertEquals(0xFF000000, missing.fill)
    }

    // Shadows

    /**
     * Figma's export, the shape of kotlin-native-nuget's icon: two paths in a
     * group wearing a drop shadow spelled the long way, the flag filled with a
     * radial gradient placed in user space. Each shape casts the group's shadow.
     */
    private val flag: String = """
        <svg width="291" height="292" viewBox="0 0 291 292" fill="none" xmlns="http://www.w3.org/2000/svg">
          <g filter="url(#filter0_d_1_2)">
            <path d="M40 20 H60 V280 H40 Z" fill="#7F52FF"/>
            <path d="M60 20 H250 L200 90 L250 160 H60 Z" fill="url(#paint0_radial_1_2)"/>
          </g>
          <defs>
            <filter id="filter0_d_1_2" x="0" y="0" width="291" height="292"
              filterUnits="userSpaceOnUse" color-interpolation-filters="sRGB">
              <feFlood flood-opacity="0" result="BackgroundImageFix"/>
              <feColorMatrix in="SourceAlpha" type="matrix"
                values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0" result="hardAlpha"/>
              <feOffset dy="4"/>
              <feGaussianBlur stdDeviation="2"/>
              <feComposite in2="hardAlpha" operator="out"/>
              <feColorMatrix type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.25 0"/>
              <feBlend mode="normal" in2="BackgroundImageFix" result="effect1_dropShadow_1_2"/>
              <feBlend mode="normal" in="SourceGraphic" in2="effect1_dropShadow_1_2" result="shape"/>
            </filter>
            <radialGradient id="paint0_radial_1_2" cx="0" cy="0" r="1" gradientUnits="userSpaceOnUse"
              gradientTransform="translate(155 90) rotate(90) scale(95)">
              <stop stop-color="#E44857"/>
              <stop offset="1" stop-color="#7F52FF"/>
            </radialGradient>
          </defs>
        </svg>
    """.trimIndent()

    @Test
    fun aGroupsDropShadowIsCastByEveryShapeInIt() {
        val group: GroupElement = assertIs(svgElement(flag, roomy, roomy))
        val (pole: ShapeElement, banner: ShapeElement) = group.children.map { assertIs<ShapeElement>(it) }

        for (shape in listOf(pole, banner)) {
            val shadow: ShapeShadow = assertNotNull(shape.shadow, shape.toString())
            assertEquals(ShadowKind.Drop, shadow.kind)
            assertEquals(0xFF000000, shadow.color)
            assertEquals(4f, shadow.offset, 0.001f)
            assertEquals(270f, shadow.angle, 0.001f)
            // A standard deviation of 2 is a blur 4 across.
            assertEquals(4f, shadow.blur, 0.001f)
            assertEquals(0.25f, shadow.opacity, 0.001f)
        }

        val gradient: ShapeGradient = assertNotNull(banner.gradient)
        assertEquals(GradientKind.Radial, gradient.kind)
        assertEquals(0xFFE44857, gradient.start)
        assertEquals(0xFF7F52FF, gradient.end)
        assertEquals(0.5f, gradient.centerX, 0.001f)
        assertEquals(0.5f, gradient.centerY, 0.001f)
        assertEquals(0.5f, gradient.radius, 0.001f)
    }

    /** Fit to half its size, the shadow shrinks with the drawing it falls from. */
    @Test
    fun aShadowScalesWithTheFit() {
        val group: GroupElement = assertIs(svgElement(flag, 145.5f, 146f))
        val shadow: ShapeShadow = assertNotNull((group.children.first() as ShapeElement).shadow)

        assertEquals(2f, shadow.offset, 0.001f)
        assertEquals(2f, shadow.blur, 0.001f)
    }

    @Test
    fun aDropShadowPrimitiveSaysItAllInOne() {
        val shape: ShapeElement = shapeOf(
            """
            <filter id="d">
              <feDropShadow dx="3" dy="-3" stdDeviation="1.5" flood-color="#ff0000" flood-opacity="0.5"/>
            </filter>
            <rect width="40" height="40" filter="url(#d)"/>
            """.trimIndent(),
        )

        val shadow: ShapeShadow = assertNotNull(shape.shadow)
        assertEquals(0xFFFF0000, shadow.color)
        assertEquals(0.5f, shadow.opacity, 0.001f)
        // Up and to the right, on Keynote's dial.
        assertEquals(45f, shadow.angle, 0.001f)
        assertEquals(4.243f, shadow.offset, 0.001f)
        assertEquals(3f, shadow.blur, 0.001f)
    }

    @Test
    fun aFloodMergedUnderTheSourceIsAShadowInThatColour() {
        val shape: ShapeElement = shapeOf(
            """
            <filter id="m">
              <feGaussianBlur in="SourceAlpha" stdDeviation="3"/>
              <feOffset dx="0" dy="5" result="o"/>
              <feFlood flood-color="#00ff00" flood-opacity="0.6"/>
              <feComposite in2="o" operator="in"/>
              <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>
            </filter>
            <rect width="40" height="40" filter="url(#m)"/>
            """.trimIndent(),
        )

        assertEquals(
            ShapeShadow(color = 0xFF00FF00, blur = 6f, offset = 5f, angle = 270f, opacity = 0.6f),
            shape.shadow,
        )
    }

    /** A filter that does something other than cast a shadow still imports what it was on. */
    @Test
    fun aFilterThatIsNoShadowImportsWithout() {
        fun filtered(filter: String): ShapeElement = shapeOf(
            """
            <filter id="f">$filter</filter>
            <rect width="40" height="40" filter="url(#f)"/>
            """.trimIndent(),
        )

        // A blur of the shape itself: nothing puts the source back over it.
        assertNull(filtered("""<feGaussianBlur stdDeviation="2"/>""").shadow)
        assertNull(filtered("""<feTurbulence baseFrequency="0.05"/>""").shadow)
        // An inner shadow cuts itself out of the shape with an arithmetic composite.
        val inner: ShapeElement = filtered(
            """
            <feBlend in="SourceGraphic" in2="BackgroundImageFix" result="shape"/>
            <feOffset dy="4"/>
            <feGaussianBlur stdDeviation="2"/>
            <feComposite in2="hardAlpha" operator="arithmetic" k2="-1" k3="1"/>
            <feBlend in2="shape"/>
            """.trimIndent(),
        )
        assertNull(inner.shadow)
        assertEquals("M0 0 L40 0 L40 40 L0 40 Z", inner.path?.data)

        val nowhere: ShapeElement = shapeOf("""<rect width="40" height="40" filter="url(#nowhere)"/>""")
        assertNull(nowhere.shadow)
    }

    // One shape or several

    @Test
    fun everythingInOnePaintIsOneCompoundShape() {
        val shape: ShapeElement = shapeOf(
            """
            <g fill="#333">
              <rect x="0" y="0" width="10" height="10"/>
              <path d="M20 20 L30 20 L30 30 Z"/>
            </g>
            <polygon points="50,50 60,50 60,60" fill="#333333"/>
            """.trimIndent(),
        )

        assertEquals(
            "M0 0 L10 0 L10 10 L0 10 Z M20 20 L30 20 L30 30 Z M50 50 L60 50 L60 60 Z",
            shape.path?.data,
        )
        assertEquals(Frame(0f, 0f, 60f, 60f), shape.frame)
        assertTrue(shape.isCompoundShape)
    }

    @Test
    fun severalPaintsAreAGroupInDocumentOrder() {
        val group: GroupElement = assertIs<GroupElement>(
            svgElement(
                svg(
                    """
                    <rect x="0" y="0" width="50" height="50" fill="red"/>
                    <rect x="50" y="50" width="50" height="50" fill="red"/>
                    <circle cx="50" cy="50" r="10" fill="blue"/>
                    <rect x="10" y="10" width="5" height="5" fill="red"/>
                    """.trimIndent(),
                ),
                roomy,
                roomy,
            ),
        )

        // The two reds in a row merge; the one after the blue is over it, so it
        // cannot join them without changing what draws on top of what.
        val shapes: List<ShapeElement> = group.children.map { assertIs<ShapeElement>(it) }
        assertEquals(listOf(0xFFFF0000, 0xFF0000FF, 0xFFFF0000), shapes.map { it.fill })
        assertTrue(shapes[0].isCompoundShape)
        assertFalse(shapes[2].isCompoundShape)

        assertEquals(Frame(0f, 0f, 100f, 100f), group.frame)
        assertEquals(Frame(0f, 0f, 100f, 100f), shapes[0].frame)
        assertFrame(Frame(40f, 40f, 20f, 20f), shapes[1].frame)
        assertEquals(Frame(10f, 10f, 5f, 5f), shapes[2].frame)
        assertEquals(group.children.size, group.children.map { it.id }.toSet().size)
    }

    // Size

    @Test
    fun widthAndHeightScaleTheFramesAndTheStrokeButNotThePath() {
        val shape: ShapeElement = shape(
            svg(
                """<rect x="6" y="6" width="12" height="12" stroke="black" stroke-width="1"/>""",
                """viewBox="0 0 24 24" width="48" height="48"""",
            ),
        )

        assertEquals(Frame(0f, 0f, 24f, 24f), shape.frame)
        assertEquals(Frame(6f, 6f, 12f, 12f), shape.path?.bounds)
        assertEquals("M6 6 L18 6 L18 18 L6 18 Z", shape.path?.data)
        assertEquals(2f, shape.strokeWidth)
    }

    @Test
    fun theNaturalSizeIsWidthAndHeightThenTheViewBoxThenTheContent() {
        fun width(attributes: String): Float =
            shape(svg("""<rect width="24" height="24"/>""", attributes)).frame.width

        assertEquals(24f, width("""viewBox="0 0 24 24""""))
        assertEquals(48f, width("""viewBox="0 0 24 24" width="48px" height="48""""))
        // One side given sizes the other by the viewBox's aspect.
        assertEquals(48f, width("""viewBox="0 0 24 24" width="48""""))
        assertEquals(72f, width("""viewBox="0 0 24 24" height="72""""))
        // A percentage says nothing about size by itself.
        assertEquals(24f, width("""viewBox="0 0 24 24" width="100%" height="100%""""))
        assertEquals(24f, width(""))
        // With no viewBox a user unit is a pixel whatever the size says.
        assertEquals(24f, width("""width="48" height="48""""))
    }

    @Test
    fun absoluteUnitsConvertAtNinetySixToTheInch() {
        fun width(size: String): Float = shape(
            svg("""<rect width="96" height="96"/>""", """viewBox="0 0 96 96" width="$size" height="$size""""),
        ).frame.width

        assertEquals(192f, width("2in"), 0.01f)
        assertEquals(96f, width("72pt"), 0.01f)
        assertEquals(96f, width("25.4mm"), 0.01f)
        assertEquals(96f, width("2.54cm"), 0.01f)
        assertEquals(96f, width("6pc"), 0.01f)
    }

    @Test
    fun aViewBoxFitsUniformlyUnlessToldToStretch() {
        val body = """<rect width="10" height="10"/>"""
        val fitted: ShapeElement = shape(svg(body, """viewBox="0 0 10 10" width="20" height="40""""))
        assertEquals(Frame(0f, 0f, 20f, 20f), fitted.frame)

        val stretched: ShapeElement = shape(
            svg(body, """viewBox="0 0 10 10" width="20" height="40" preserveAspectRatio="none""""),
        )
        assertEquals(Frame(0f, 0f, 20f, 40f), stretched.frame)
    }

    @Test
    fun aBigDrawingIsScaledDownToFitAndASmallOneIsLeftAlone() {
        val wide: String = svg(
            """<rect width="1000" height="500" stroke="black" stroke-width="10"/>""",
            """viewBox="0 0 1000 500"""",
        )
        val fitted: ShapeElement = shape(wide, max = 100f)
        assertEquals(Frame(0f, 0f, 100f, 50f), fitted.frame)
        assertEquals(1f, fitted.strokeWidth, 0.001f)
        // The outline itself is untouched: only the frame it is shown in shrank.
        assertEquals(Frame(0f, 0f, 1000f, 500f), fitted.path?.bounds)

        val icon: String = svg("""<rect width="24" height="24"/>""", """viewBox="0 0 24 24"""")
        assertEquals(Frame(0f, 0f, 24f, 24f), shape(icon, max = 1000f).frame)

        // The fit is the natural size's, padding included, so a small mark in a
        // big viewBox comes in at the size it had in the drawing.
        val padded: String = svg(
            """<rect x="400" y="400" width="200" height="200"/>""",
            """viewBox="0 0 1000 1000"""",
        )
        assertEquals(Frame(0f, 0f, 20f, 20f), shape(padded, max = 100f).frame)
    }

    @Test
    fun withNoSizeAtAllTheContentIsTheNaturalSize() {
        val bare = """<svg><rect x="10" y="10" width="300" height="100"/></svg>"""
        assertEquals(Frame(0f, 0f, 150f, 50f), shape(bare, max = 150f).frame)

        // Nothing clips to the viewBox, so artwork spilling out of it has to fit too.
        val spilling: String = svg("""<rect width="100" height="10"/>""", """viewBox="0 0 10 10"""")
        assertEquals(Frame(0f, 0f, 50f, 5f), shape(spilling, max = 50f).frame)
    }

    @Test
    fun aStraightLinesBoxIsWidenedToItsStroke() {
        val line: ShapeElement = shapeOf(
            """<line x1="10" y1="50" x2="90" y2="50" stroke="black" stroke-width="4"/>""",
        )
        // Around its centre, in the path's bounds and the frame alike, so the
        // line is still drawn through the middle of its box.
        assertEquals(Frame(10f, 48f, 80f, 4f), line.path?.bounds)
        assertEquals(Frame(0f, 0f, 80f, 4f), line.frame)

        // Never thinner than one unit, however fine the stroke.
        val hairline: ShapeElement = shapeOf(
            """<line x1="50" y1="10" x2="50" y2="90" stroke="black" stroke-width="0.5"/>""",
        )
        assertEquals(Frame(49.5f, 10f, 1f, 80f), hairline.path?.bounds)
        assertEquals(Frame(0f, 0f, 1f, 80f), hairline.frame)

        // The stroke is measured on the slide: at double size 4 is 8 there, and
        // 4 back in the path's own space.
        val doubled: ShapeElement = shape(
            svg(
                """<line x1="10" y1="50" x2="90" y2="50" stroke="black" stroke-width="4"/>""",
                """viewBox="0 0 100 100" width="200" height="200"""",
            ),
        )
        assertEquals(Frame(10f, 48f, 80f, 4f), doubled.path?.bounds)
        assertEquals(Frame(0f, 0f, 160f, 8f), doubled.frame)
        assertEquals(8f, doubled.strokeWidth)
    }

    // What is skipped, and what XML is put up with

    @Test
    fun whatThisCantDrawIsSkippedWithItsSubtree() {
        val shape: ShapeElement = shapeOf(
            """
            <defs><rect width="90" height="90"/></defs>
            <clipPath id="c"><rect width="90" height="90"/></clipPath>
            <mask id="m"><rect width="90" height="90"/></mask>
            <symbol id="s"><rect width="90" height="90"/></symbol>
            <pattern id="p"><rect width="90" height="90"/></pattern>
            <marker id="k"><path d="M0 0 L90 90 L0 90 Z"/></marker>
            <filter id="f"><feGaussianBlur stdDeviation="2"/></filter>
            <text x="0" y="90">Words</text>
            <image href="a.png" width="90" height="90"/>
            <use href="#s"/>
            <foreignObject width="90" height="90"><rect width="90" height="90"/></foreignObject>
            <g display="none"><rect width="90" height="90"/></g>
            <g style="display: none"><rect width="90" height="90"/></g>
            <rect width="90" height="90" visibility="hidden"/>
            <title>A square</title>
            <a href="https://example.com"><rect x="1" y="1" width="5" height="5"/></a>
            """.trimIndent(),
        )

        // Only the one inside the link, which is just a container here.
        assertEquals("M1 1 L6 1 L6 6 L1 6 Z", shape.path?.data)
    }

    @Test
    fun theProloguesAndCommentsAnEditorWritesAreSteppedOver() {
        val shape: ShapeElement = shape(
            """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <!-- Generator: Some Editor 1.0 -->
            <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd" [
              <!ENTITY ns_svg "http://www.w3.org/2000/svg">
            ]>
            <svg:svg xmlns:svg="&ns_svg;" viewBox='0 0 24 24'>
              <!-- <rect width="24" height="24"/> -->
              <svg:rect x='2' y='2' width = "20" height="20" fill="&#x23;ff0000"
                        id="a &amp; b &lt;&gt; &quot;c&quot; &apos;d&apos;"/>
            </svg:svg>
            """.trimIndent(),
        )

        assertEquals("M2 2 L22 2 L22 22 L2 22 Z", shape.path?.data)
        assertEquals(0xFFFF0000, shape.fill)
    }

    @Test
    fun aFileCutOffMidTagIsWhatWasReadBeforeIt() {
        val cut = """<svg viewBox="0 0 24 24"><g><rect width="10" height="10"/><path d="M0 0 L5"""
        assertEquals("M0 0 L10 0 L10 10 L0 10 Z", shape(cut).path?.data)

        // A close tag that matches nothing open is ignored.
        val stray = """<svg viewBox="0 0 24 24"><rect width="10" height="10"></g></rect></svg>"""
        assertEquals("M0 0 L10 0 L10 10 L0 10 Z", shape(stray).path?.data)
    }
}

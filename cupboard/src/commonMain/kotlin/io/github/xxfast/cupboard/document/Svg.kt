package io.github.xxfast.cupboard.document

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * [svg] as something a slide can hold: its outlines as [ShapeKind.Path] shapes.
 * Null for text that is not an SVG or draws nothing. Frame origin at 0,0, natural
 * size scaled down (never up) to fit [maxWidth] by [maxHeight].
 *
 * An SVG becomes shapes rather than a picture so it stays what Keynote makes of
 * one: something to recolour, restyle and break apart, at any size, with nothing
 * in the deck's assets behind it.
 *
 * Everything drawn in one paint is ONE shape, with every element's contours in a
 * single compound path: an icon is usually exactly that, and one shape is one
 * thing to select and one fill to change. Consecutive elements wearing the same
 * paint merge the same way, and an SVG in several paints comes back as a
 * [GroupElement] of those shapes in document order, later on top. An element
 * with nothing to see (no fill and no stroke) is left out altogether, which is
 * what drops the invisible bounding square icon sets ship around their artwork.
 *
 * Each shape's frame hugs its outline, stroke not included, so a viewBox's
 * padding is dropped and the handles sit on the drawing. The path data itself
 * stays in the SVG's user space: the scale to the natural size and the fit only
 * move frames and scale stroke widths.
 *
 * What is read: `path`, `rect`, `circle`, `ellipse`, `line`, `polyline` and
 * `polygon`, under `g` and nested `svg`, with `transform`, the fill and stroke
 * properties (as attributes, in `style`, and from `<style>` class rules), linear
 * and radial gradients, and a `filter` that is a drop shadow (see
 * [SvgSheet.shadow]). Everything else is skipped with its subtree: text, images,
 * `use`, clipping, masks, patterns and markers. A filter that is anything but a
 * drop shadow (a blur, a glow, an inner shadow, a turbulence) is skipped too,
 * and what it was on draws as if it had none.
 *
 * Merging contours from separate elements under one nonzero fill can differ from
 * the SVG where they overlap with opposite winding: the overlap comes out as a
 * hole. Known simplification, as are a group `opacity` multiplied into each
 * shape rather than composited once, a group's shadow cast by each shape under
 * it rather than by the group as one, and a gradient kept as its first and last
 * stop: along an angle read off its unit box for a linear one, as a circle with
 * no focal point for a radial one.
 */
fun svgElement(svg: String, maxWidth: Float, maxHeight: Float): Element? {
    val root: SvgNode = parseXml(svg).firstNamed("svg") ?: return null
    val nodes: List<SvgNode> = root.subtree()
    val sheet = SvgSheet(
        rules = nodes.filter { it.name == "style" }.flatMap { cssRules(it.text.toString()) },
        gradients = nodes
            .filter { it.name == "linearGradient" || it.name == "radialGradient" }
            .mapNotNull { node -> node.attributes["id"]?.let { id -> id to node } }
            .toMap(),
        filters = nodes
            .filter { it.name == "filter" }
            .mapNotNull { node -> node.attributes["id"]?.let { id -> id to node } }
            .toMap(),
    )

    val drawn: MutableList<SvgDrawn> = mutableListOf()
    root.draw(SvgStyle(), SvgMatrix(), sheet, drawn, outermost = true)

    // Only neighbours merge: a shape further down the file may be drawn over
    // whatever sits between, and joining it to an earlier one would pull it under.
    val shapes: MutableList<SvgDrawn> = mutableListOf()
    for (next in drawn) {
        val last: SvgDrawn? = shapes.lastOrNull()
        if (last != null && last.look == next.look) last.outline += next.outline
        else shapes += next
    }
    if (shapes.isEmpty()) return null

    val tight: List<Frame> = shapes.map { it.outline.tightBounds() }
    val content: Frame = boundingFrame(tight)
    val viewport: SvgViewport = root.viewport()
    val naturalWidth: Float = viewport.width ?: content.width
    val naturalHeight: Float = viewport.height ?: content.height

    // Nothing here clips to the viewBox, so artwork spilling out of it is still
    // on the slide and has to fit too.
    val fit: Float = minOf(
        1f,
        fitting(maxWidth, max(naturalWidth, content.width * viewport.scaleX)),
        fitting(maxHeight, max(naturalHeight, content.height * viewport.scaleY)),
    )
    val scaleX: Float = viewport.scaleX * fit
    val scaleY: Float = viewport.scaleY * fit
    val strokeScale: Float = sqrt(scaleX * scaleY)

    // Grown before anything is placed, so the origin every frame is measured
    // from is the one the grown boxes share.
    val bounds: List<Frame> = shapes.mapIndexed { index, shape ->
        val minimum: Float = max(shape.look.strokeWidth * strokeScale, 1f)
        tight[index].atLeast(minimum / scaleX, minimum / scaleY)
    }
    val origin: Frame = boundingFrame(bounds)

    val elements: List<ShapeElement> = shapes.mapIndexed { index, shape ->
        val box: Frame = bounds[index]
        ShapeElement(
            frame = Frame(
                x = (box.x - origin.x) * scaleX,
                y = (box.y - origin.y) * scaleY,
                width = box.width * scaleX,
                height = box.height * scaleY,
            ),
            opacity = shape.look.opacity,
            kind = ShapeKind.Path,
            cornerRadius = 0f,
            fill = shape.look.fill,
            gradient = shape.look.gradient,
            strokeColor = shape.look.strokeColor,
            strokeWidth = shape.look.strokeWidth * strokeScale,
            shadow = shape.look.shadow?.cast(scaleX, scaleY, strokeScale),
            label = "",
            path = ShapePath(shape.outline.toPathData(), box, shape.look.evenOdd),
        )
    }

    return elements.singleOrNull()
        ?: GroupElement(frame = boundingFrame(elements.map { it.frame }), children = elements)
}

/** How far [size] has to shrink to fit in [limit]; 1 for a size with nothing to shrink. */
private fun fitting(limit: Float, size: Float): Float =
    if (limit > 0f && size > 0f) limit / size else 1f

// region Drawing: the tree walked into outlines

/** One drawn element: its outline in the root's user space, and what it is painted with. */
private class SvgDrawn(val outline: MutableList<PathCommand>, val look: SvgLook)

/** What the walk reads besides the tree itself: class rules, and gradients and filters by id. */
private class SvgSheet(
    val rules: List<SvgRule>,
    val gradients: Map<String, SvgNode>,
    val filters: Map<String, SvgNode>,
)

/** Elements that draw nothing this reader can keep, skipped with everything under them. */
private val SkippedElements: Set<String> = setOf(
    "defs", "clipPath", "mask", "symbol", "pattern", "marker", "filter", "text", "image", "use",
    "foreignObject", "linearGradient", "radialGradient", "style", "title", "desc", "metadata",
    "script",
)

/**
 * Walks this element and everything under it, adding what it draws to [into] in
 * document order. [inherited] and [parent] are what the elements above it handed
 * down; both are flattened into each outline and its look, so nothing downstream
 * has to know there was a tree.
 */
private fun SvgNode.draw(
    inherited: SvgStyle,
    parent: SvgMatrix,
    sheet: SvgSheet,
    into: MutableList<SvgDrawn>,
    outermost: Boolean = false,
) {
    if (name in SkippedElements) return
    val properties: Map<String, String> = properties(sheet.rules)
    if (properties["display"] == "none") return
    if (properties["visibility"] == "hidden" || properties["visibility"] == "collapse") return

    var matrix: SvgMatrix = parent * svgTransform(attributes["transform"])

    // A nested `svg` is a viewport of its own. The outermost one's is applied to
    // the frames instead, by the caller, so the path data stays in user space.
    if (name == "svg" && !outermost) {
        val viewport: SvgViewport = viewport()
        matrix = matrix *
            SvgMatrix(e = length("x"), f = length("y")) *
            SvgMatrix(a = viewport.scaleX, d = viewport.scaleY) *
            SvgMatrix(e = -viewport.originX, f = -viewport.originY)
    }

    // A filter is not inherited: SVG draws the whole subtree through it. The
    // nearest a list of shapes comes is every shape under it casting the shadow
    // itself. One that is no shadow this can read leaves whatever was cast above.
    val style: SvgStyle = inherited.with(properties).let { style ->
        val shadow: SvgShadow? = properties["filter"]?.let { sheet.shadow(it, matrix) }
        if (shadow == null) style else style.copy(shadow = shadow)
    }

    val outline: List<PathCommand> = when (name) {
        "path" -> svgPathCommands(attributes["d"].orEmpty())

        "rect" -> rectOutline(
            x = length("x"),
            y = length("y"),
            width = length("width"),
            height = length("height"),
            radiusX = attributes["rx"]?.let(::svgLength),
            radiusY = attributes["ry"]?.let(::svgLength),
        )

        "circle" -> ellipseOutline(length("cx"), length("cy"), length("r"), length("r"))

        "ellipse" -> ellipseOutline(length("cx"), length("cy"), length("rx"), length("ry"))

        "line" -> listOf(
            PathCommand.MoveTo(length("x1"), length("y1")),
            PathCommand.LineTo(length("x2"), length("y2")),
        )

        "polyline", "polygon" -> pointsOutline(attributes["points"].orEmpty(), name == "polygon")

        // A `g`, and anything this reader has no name for: nothing drawn here,
        // but whatever is inside still is.
        else -> {
            children.forEach { it.draw(style, matrix, sheet, into) }
            return
        }
    }
    if (outline.isEmpty()) return

    // A line has no inside, so SVG's default black fill has nothing to paint.
    val placed: MutableList<PathCommand> =
        outline.mapTo(mutableListOf()) { it.transformed(matrix) }
    val look: SvgLook =
        style.look(sheet, matrix, filled = name != "line", bounds = placed.tightBounds()) ?: return
    into += SvgDrawn(placed, look)
}

private fun SvgNode.length(name: String): Float = attributes[name]?.let(::svgLength) ?: 0f

private const val Kappa: Float = 0.5522848f

/** A `rect`, clockwise from its top edge, corners rounded when it has radii. */
private fun rectOutline(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radiusX: Float?,
    radiusY: Float?,
): List<PathCommand> {
    if (width <= 0f || height <= 0f) return emptyList()

    // One radius given stands for both, and neither reaches past the middle.
    val rx: Float = (radiusX ?: radiusY ?: 0f).coerceIn(0f, width / 2)
    val ry: Float = (radiusY ?: radiusX ?: 0f).coerceIn(0f, height / 2)
    val right: Float = x + width
    val bottom: Float = y + height

    if (rx == 0f || ry == 0f) {
        return listOf(
            PathCommand.MoveTo(x, y),
            PathCommand.LineTo(right, y),
            PathCommand.LineTo(right, bottom),
            PathCommand.LineTo(x, bottom),
            PathCommand.Close,
        )
    }

    val kx: Float = rx * Kappa
    val ky: Float = ry * Kappa
    return listOf(
        PathCommand.MoveTo(x + rx, y),
        PathCommand.LineTo(right - rx, y),
        PathCommand.CubicTo(right - rx + kx, y, right, y + ry - ky, right, y + ry),
        PathCommand.LineTo(right, bottom - ry),
        PathCommand.CubicTo(right, bottom - ry + ky, right - rx + kx, bottom, right - rx, bottom),
        PathCommand.LineTo(x + rx, bottom),
        PathCommand.CubicTo(x + rx - kx, bottom, x, bottom - ry + ky, x, bottom - ry),
        PathCommand.LineTo(x, y + ry),
        PathCommand.CubicTo(x, y + ry - ky, x + rx - kx, y, x + rx, y),
        PathCommand.Close,
    )
}

/** A `circle` or an `ellipse`: four quarter turns, clockwise from three o'clock. */
private fun ellipseOutline(cx: Float, cy: Float, rx: Float, ry: Float): List<PathCommand> {
    if (rx <= 0f || ry <= 0f) return emptyList()

    val kx: Float = rx * Kappa
    val ky: Float = ry * Kappa
    return listOf(
        PathCommand.MoveTo(cx + rx, cy),
        PathCommand.CubicTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry),
        PathCommand.CubicTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy),
        PathCommand.CubicTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry),
        PathCommand.CubicTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy),
        PathCommand.Close,
    )
}

/** A `polyline`'s or a `polygon`'s `points`; an odd number left over is dropped. */
private fun pointsOutline(points: String, closed: Boolean): List<PathCommand> {
    val numbers: List<Float> = svgNumbers(points)
    if (numbers.size < 4) return emptyList()

    val outline: MutableList<PathCommand> = mutableListOf(PathCommand.MoveTo(numbers[0], numbers[1]))
    for (at in 2 until numbers.size - 1 step 2) {
        outline += PathCommand.LineTo(numbers[at], numbers[at + 1])
    }
    if (closed) outline += PathCommand.Close
    return outline
}

// endregion

// region Path data: SVG's twenty commands down to four

/**
 * SVG path data as the four absolute commands a [ShapePath] is written in.
 *
 * Relative commands are resolved, `H` and `V` become lines, `S` and `T` get the
 * control point they imply, `Q` is raised to the cubic that traces exactly the
 * same curve, and an arc is split into cubics of a quarter turn at most. Where
 * the data stops making sense the rest is dropped, which is SVG's own rule: draw
 * up to the last segment that parsed.
 *
 * A move nothing is drawn from is not written, and a contour picked back up
 * after its `Z` gets the move SVG implies, so every contour here opens with its
 * own `M` and `subpaths` can split on that alone.
 */
internal fun svgPathCommands(data: String): List<PathCommand> {
    val scanner = PathScanner(data)
    val commands: MutableList<PathCommand> = mutableListOf()

    // Where the pen is, where its subpath began, and whether that subpath's
    // move has been written yet.
    var x = 0f
    var y = 0f
    var startX = 0f
    var startY = 0f
    var opened = false

    // The last control point, for the `S` or `T` that reflects it, and the
    // command it belonged to: only a curve of the same family counts.
    var controlX = 0f
    var controlY = 0f
    var previous = ' '

    fun open() {
        if (opened) return
        commands += PathCommand.MoveTo(startX, startY)
        opened = true
    }

    fun lineTo(toX: Float, toY: Float) {
        open()
        commands += PathCommand.LineTo(toX, toY)
        x = toX
        y = toY
    }

    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, toX: Float, toY: Float) {
        open()
        commands += PathCommand.CubicTo(x1, y1, x2, y2, toX, toY)
        x = toX
        y = toY
    }

    // Two thirds of the way from each end to the quadratic's control point is
    // the cubic that is the same curve, not an approximation of it.
    fun quadTo(qx: Float, qy: Float, toX: Float, toY: Float) {
        cubicTo(
            x1 = x + (qx - x) * 2f / 3f,
            y1 = y + (qy - y) * 2f / 3f,
            x2 = toX + (qx - toX) * 2f / 3f,
            y2 = toY + (qy - toY) * 2f / 3f,
            toX = toX,
            toY = toY,
        )
        controlX = qx
        controlY = qy
    }

    // Endpoint to centre, per the SVG implementation notes, in doubles: the
    // conversion subtracts nearly equal numbers and a float loses the arc.
    fun arcTo(
        radiusX: Float,
        radiusY: Float,
        rotation: Float,
        large: Boolean,
        sweep: Boolean,
        toX: Float,
        toY: Float,
    ) {
        // The out-of-range rules: an arc to where the pen already is draws
        // nothing, and one with no radius is the straight line between its ends.
        if (toX == x && toY == y) return
        if (radiusX == 0f || radiusY == 0f) return lineTo(toX, toY)

        val phi: Double = rotation * PI / 180.0
        val cosPhi: Double = cos(phi)
        val sinPhi: Double = sin(phi)
        val halfX: Double = (x - toX) / 2.0
        val halfY: Double = (y - toY) / 2.0
        val px: Double = cosPhi * halfX + sinPhi * halfY
        val py: Double = -sinPhi * halfX + cosPhi * halfY

        // Radii too small to span the ends are scaled up until they just do.
        var rx: Double = abs(radiusX).toDouble()
        var ry: Double = abs(radiusY).toDouble()
        val lambda: Double = px * px / (rx * rx) + py * py / (ry * ry)
        if (lambda > 1.0) {
            rx *= sqrt(lambda)
            ry *= sqrt(lambda)
        }

        val numerator: Double = rx * rx * ry * ry - rx * rx * py * py - ry * ry * px * px
        val denominator: Double = rx * rx * py * py + ry * ry * px * px
        val side: Double = if (large != sweep) 1.0 else -1.0
        val factor: Double = side * sqrt(max(0.0, numerator / denominator))
        val centerXp: Double = factor * rx * py / ry
        val centerYp: Double = -factor * ry * px / rx
        val centerX: Double = cosPhi * centerXp - sinPhi * centerYp + (x + toX) / 2.0
        val centerY: Double = sinPhi * centerXp + cosPhi * centerYp + (y + toY) / 2.0

        val startAngle: Double = atan2((py - centerYp) / ry, (px - centerXp) / rx)
        var delta: Double = atan2((-py - centerYp) / ry, (-px - centerXp) / rx) - startAngle
        if (sweep && delta < 0.0) delta += 2.0 * PI
        if (!sweep && delta > 0.0) delta -= 2.0 * PI

        // A cubic follows a quarter turn to within a thousandth of the radius
        // and falls off quickly past it.
        val pieces: Int = max(1, ceil(abs(delta) / (PI / 2.0) - 1e-6).toInt())
        val step: Double = delta / pieces
        val handle: Double = 4.0 / 3.0 * tan(step / 4.0)

        // A point of the unit circle, out on the ellipse.
        fun ellipseX(ux: Double, uy: Double): Float =
            (centerX + rx * ux * cosPhi - ry * uy * sinPhi).toFloat()
        fun ellipseY(ux: Double, uy: Double): Float =
            (centerY + rx * ux * sinPhi + ry * uy * cosPhi).toFloat()

        for (piece in 0 until pieces) {
            val from: Double = startAngle + step * piece
            val to: Double = from + step
            val x1: Double = cos(from) - handle * sin(from)
            val y1: Double = sin(from) + handle * cos(from)
            val x2: Double = cos(to) + handle * sin(to)
            val y2: Double = sin(to) - handle * cos(to)

            // The last piece lands on the endpoint as written, not as recomputed.
            val last: Boolean = piece == pieces - 1
            cubicTo(
                x1 = ellipseX(x1, y1),
                y1 = ellipseY(x1, y1),
                x2 = ellipseX(x2, y2),
                y2 = ellipseY(x2, y2),
                toX = if (last) toX else ellipseX(cos(to), sin(to)),
                toY = if (last) toY else ellipseY(cos(to), sin(to)),
            )
        }
    }

    // Path data has to open with a move; anything else draws nothing at all.
    var letter: Char = scanner.letter()?.takeIf { it == 'M' || it == 'm' } ?: return commands

    while (true) {
        val command: Char = letter.uppercaseChar()
        val relative: Boolean = letter != command
        val baseX: Float = if (relative) x else 0f
        val baseY: Float = if (relative) y else 0f

        when (command) {
            'M' -> {
                val (toX, toY) = scanner.numbers(2) ?: break
                x = baseX + toX
                y = baseY + toY
                startX = x
                startY = y
                opened = false
            }

            'L' -> {
                val (toX, toY) = scanner.numbers(2) ?: break
                lineTo(baseX + toX, baseY + toY)
            }

            'H' -> lineTo(baseX + (scanner.number() ?: break), y)

            'V' -> lineTo(x, baseY + (scanner.number() ?: break))

            'C' -> {
                val at: List<Float> = scanner.numbers(6) ?: break
                controlX = baseX + at[2]
                controlY = baseY + at[3]
                cubicTo(
                    baseX + at[0], baseY + at[1], controlX, controlY, baseX + at[4], baseY + at[5],
                )
            }

            'S' -> {
                val at: List<Float> = scanner.numbers(4) ?: break
                val smooth: Boolean = previous == 'C' || previous == 'S'
                val x1: Float = if (smooth) 2 * x - controlX else x
                val y1: Float = if (smooth) 2 * y - controlY else y
                controlX = baseX + at[0]
                controlY = baseY + at[1]
                cubicTo(x1, y1, controlX, controlY, baseX + at[2], baseY + at[3])
            }

            'Q' -> {
                val at: List<Float> = scanner.numbers(4) ?: break
                quadTo(baseX + at[0], baseY + at[1], baseX + at[2], baseY + at[3])
            }

            'T' -> {
                val (toX, toY) = scanner.numbers(2) ?: break
                val smooth: Boolean = previous == 'Q' || previous == 'T'
                quadTo(
                    qx = if (smooth) 2 * x - controlX else x,
                    qy = if (smooth) 2 * y - controlY else y,
                    toX = baseX + toX,
                    toY = baseY + toY,
                )
            }

            'A' -> {
                val (radiusX, radiusY, rotation) = scanner.numbers(3) ?: break
                val large: Boolean = scanner.flag() ?: break
                val sweep: Boolean = scanner.flag() ?: break
                val (toX, toY) = scanner.numbers(2) ?: break
                arcTo(radiusX, radiusY, rotation, large, sweep, baseX + toX, baseY + toY)
            }

            'Z' -> {
                if (opened) commands += PathCommand.Close
                x = startX
                y = startY
                opened = false
            }

            else -> break
        }

        previous = command

        // More numbers with no letter repeat the command, except that the pairs
        // after a move are lines to, in the move's own absolute or relative.
        letter = when {
            command == 'Z' || !scanner.hasNumber() -> scanner.letter() ?: break
            command == 'M' -> if (relative) 'l' else 'L'
            else -> letter
        }
    }

    return commands
}

// endregion

// region Transforms

/** An SVG transform: `x' = a x + c y + e`, `y' = b x + d y + f`. Identity by default. */
private data class SvgMatrix(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val e: Float = 0f,
    val f: Float = 0f,
) {
    /** How much this grows a length on average: what a stroke width scales by. */
    val scale: Float get() = sqrt(abs(a * d - b * c))

    /** This after [inner]: a point goes through [inner] first, the way nesting reads. */
    operator fun times(inner: SvgMatrix): SvgMatrix = SvgMatrix(
        a = a * inner.a + c * inner.b,
        b = b * inner.a + d * inner.b,
        c = a * inner.c + c * inner.d,
        d = b * inner.c + d * inner.d,
        e = a * inner.e + c * inner.f + e,
        f = b * inner.e + d * inner.f + f,
    )

    fun x(x: Float, y: Float): Float = a * x + c * y + e
    fun y(x: Float, y: Float): Float = b * x + d * y + f
}

/** A cubic under an affine map is the cubic through the mapped points, so this is exact. */
private fun PathCommand.transformed(matrix: SvgMatrix): PathCommand = when (this) {
    is PathCommand.MoveTo -> PathCommand.MoveTo(matrix.x(x, y), matrix.y(x, y))
    is PathCommand.LineTo -> PathCommand.LineTo(matrix.x(x, y), matrix.y(x, y))
    is PathCommand.CubicTo -> PathCommand.CubicTo(
        x1 = matrix.x(x1, y1),
        y1 = matrix.y(x1, y1),
        x2 = matrix.x(x2, y2),
        y2 = matrix.y(x2, y2),
        x = matrix.x(x, y),
        y = matrix.y(x, y),
    )
    PathCommand.Close -> this
}

/**
 * A `transform` attribute's list, composed left to right into one matrix. A
 * function this doesn't know, or one short of arguments, is the identity rather
 * than the end of the list.
 */
private fun svgTransform(value: String?): SvgMatrix {
    var matrix = SvgMatrix()
    var rest: String = value ?: return matrix

    while (true) {
        val open: Int = rest.indexOf('(')
        val close: Int = rest.indexOf(')', open + 1)
        if (open < 0 || close < 0) break

        val at: List<Float> = svgNumbers(rest.substring(open + 1, close))
        fun radians(index: Int): Float = at[index] * PI.toFloat() / 180f

        val function: String = rest.substring(0, open).trim { it.isWhitespace() || it == ',' }
        matrix *= when {
            function == "matrix" && at.size >= 6 -> SvgMatrix(at[0], at[1], at[2], at[3], at[4], at[5])
            function == "translate" && at.isNotEmpty() -> SvgMatrix(e = at[0], f = at.getOrElse(1) { 0f })
            function == "scale" && at.isNotEmpty() -> SvgMatrix(a = at[0], d = at.getOrElse(1) { at[0] })

            // Around a centre is there, turn, and back.
            function == "rotate" && at.isNotEmpty() -> {
                val turn = SvgMatrix(
                    a = cos(radians(0)),
                    b = sin(radians(0)),
                    c = -sin(radians(0)),
                    d = cos(radians(0)),
                )
                if (at.size < 3) turn
                else SvgMatrix(e = at[1], f = at[2]) * turn * SvgMatrix(e = -at[1], f = -at[2])
            }

            function == "skewX" && at.isNotEmpty() -> SvgMatrix(c = tan(radians(0)))
            function == "skewY" && at.isNotEmpty() -> SvgMatrix(b = tan(radians(0)))
            else -> SvgMatrix()
        }

        rest = rest.substring(close + 1)
    }

    return matrix
}

/**
 * What an `svg` element's own attributes say about size: the box it asks to be
 * shown in, null where it doesn't say, and the scale from its user space to that.
 *
 * `preserveAspectRatio` is read for `none` and nothing else: the default fits the
 * viewBox uniformly, and where it would centre the slack is an offset this reader
 * drops anyway, since every frame is measured from the artwork's own corner.
 */
private class SvgViewport(
    val width: Float?,
    val height: Float?,
    val scaleX: Float,
    val scaleY: Float,
    val originX: Float,
    val originY: Float,
)

private fun SvgNode.viewport(): SvgViewport {
    val given: List<Float> = svgNumbers(attributes["viewBox"].orEmpty())
    val viewBox: Frame? = given
        .takeIf { it.size == 4 && it[2] > 0f && it[3] > 0f }
        ?.let { Frame(it[0], it[1], it[2], it[3]) }
    val width: Float? = attributes["width"]?.let(::svgLength)?.takeIf { it > 0f }
    val height: Float? = attributes["height"]?.let(::svgLength)?.takeIf { it > 0f }
    if (viewBox == null) return SvgViewport(width, height, 1f, 1f, 0f, 0f)

    // One side given sizes the other by the viewBox's aspect; neither is the
    // viewBox at one user unit to the pixel.
    val shownWidth: Float = width ?: height?.let { it * viewBox.width / viewBox.height } ?: viewBox.width
    val shownHeight: Float = height ?: width?.let { it * viewBox.height / viewBox.width } ?: viewBox.height
    val scaleX: Float = shownWidth / viewBox.width
    val scaleY: Float = shownHeight / viewBox.height
    val stretched: Boolean = attributes["preserveAspectRatio"]?.startsWith("none") == true
    val uniform: Float = min(scaleX, scaleY)

    return SvgViewport(
        width = shownWidth,
        height = shownHeight,
        scaleX = if (stretched) scaleX else uniform,
        scaleY = if (stretched) scaleY else uniform,
        originX = viewBox.x,
        originY = viewBox.y,
    )
}

// endregion

// region Paint: properties, colours, gradients

private sealed interface SvgPaint {
    data object None : SvgPaint
    data class Solid(val color: Long) : SvgPaint
    data class Reference(val id: String) : SvgPaint
}

/**
 * The presentation an element inherits and hands on, at SVG's defaults: filled
 * black, no stroke.
 *
 * [opacity] is the odd one out. SVG does not inherit it, it composites the whole
 * subtree at that alpha, and the nearest thing a flat list of shapes has is the
 * product of every `opacity` on the way down. [shadow] is the other, for the same
 * reason: the nearest filter above, cast by each shape under it.
 */
private data class SvgStyle(
    val fill: SvgPaint = SvgPaint.Solid(SvgBlack),
    val stroke: SvgPaint = SvgPaint.None,
    val strokeWidth: Float = 1f,
    val fillOpacity: Float = 1f,
    val strokeOpacity: Float = 1f,
    val opacity: Float = 1f,
    val evenOdd: Boolean = false,
    val shadow: SvgShadow? = null,
)

/** A value this can't read leaves the inherited one standing, as an absent one does. */
private fun SvgStyle.with(properties: Map<String, String>): SvgStyle = copy(
    fill = properties["fill"]?.let(::svgPaint) ?: fill,
    stroke = properties["stroke"]?.let(::svgPaint) ?: stroke,
    strokeWidth = properties["stroke-width"]?.let(::svgLength) ?: strokeWidth,
    fillOpacity = properties["fill-opacity"]?.let(::svgFraction) ?: fillOpacity,
    strokeOpacity = properties["stroke-opacity"]?.let(::svgFraction) ?: strokeOpacity,
    opacity = opacity * (properties["opacity"]?.let(::svgFraction) ?: 1f),
    evenOdd = when (properties["fill-rule"]) {
        "evenodd" -> true
        "nonzero" -> false
        else -> evenOdd
    },
)

/**
 * What a drawn element is painted with, in a [ShapeElement]'s own terms. Two
 * elements with equal looks are one compound shape, which is why everything that
 * can tell them apart on the slide is in here and nothing else is.
 */
private data class SvgLook(
    val fill: Long,
    val gradient: ShapeGradient?,
    val strokeColor: Long,
    val strokeWidth: Float,
    val opacity: Float,
    val evenOdd: Boolean,
    val shadow: SvgShadow?,
)

/**
 * This style as a look, null for one that shows nothing at all. [matrix] is the
 * element's, for the stroke it scales; [filled] is false for an element with no
 * inside to fill; [bounds] is its outline's box in the root's user space, what a
 * radial gradient in user space is measured against.
 */
private fun SvgStyle.look(
    sheet: SvgSheet,
    matrix: SvgMatrix,
    filled: Boolean,
    bounds: Frame,
): SvgLook? {
    if (opacity <= 0f) return null

    var fillColor: Long = SvgClear
    var gradient: ShapeGradient? = null
    when (val paint: SvgPaint = if (filled) fill else SvgPaint.None) {
        SvgPaint.None -> Unit
        is SvgPaint.Solid -> fillColor = paint.color.faded(fillOpacity)

        // A gradient with two ends to it is a gradient here too. A single stop
        // or an id that names nothing is its first colour, or black: something
        // visible to restyle rather than a shape that vanished.
        is SvgPaint.Reference -> {
            val found: SvgGradient? = sheet.gradient(paint.id)
            val first: Long = (found?.stops?.firstOrNull() ?: SvgBlack).faded(fillOpacity)
            fillColor = first
            if (found != null && found.stops.size > 1) {
                val last: Long = found.stops.last().faded(fillOpacity)
                gradient =
                    if (found.linear) ShapeGradient(first, last, found.angle)
                    else found.radial(first, last, matrix, bounds)
            }
        }
    }

    val strokeColor: Long = when (val paint: SvgPaint = stroke) {
        SvgPaint.None -> SvgClear
        is SvgPaint.Solid -> paint.color.faded(strokeOpacity)
        is SvgPaint.Reference ->
            (sheet.gradient(paint.id)?.stops?.firstOrNull() ?: SvgBlack).faded(strokeOpacity)
    }
    val width: Float = strokeWidth * matrix.scale

    val painted: Boolean = gradient != null || fillColor.alpha > 0
    val stroked: Boolean = width > 0f && strokeColor.alpha > 0
    if (!painted && !stroked) return null

    // No stroke is a width of zero, the document's "no border". Its colour is
    // left at black, so a border switched on later has something to show.
    return SvgLook(
        fill = fillColor,
        gradient = gradient,
        strokeColor = if (stroked) strokeColor else SvgBlack,
        strokeWidth = if (stroked) width else 0f,
        opacity = opacity,
        evenOdd = evenOdd,
        shadow = shadow,
    )
}

private const val SvgBlack: Long = 0xFF000000
private const val SvgClear: Long = 0x00000000

private val Long.alpha: Int get() = ((this ushr 24) and 0xFF).toInt()

/** This colour with its alpha multiplied by [opacity]: how `fill-opacity` lands in a packed ARGB. */
private fun Long.faded(opacity: Float): Long {
    val alpha: Int = (alpha * opacity).roundToInt().coerceIn(0, 255)
    return (this and 0x00FFFFFF) or (alpha.toLong() shl 24)
}

/** A `fill` or `stroke` value; null for one this can't read, `inherit` included. */
private fun svgPaint(value: String): SvgPaint? {
    if (value.equals("none", ignoreCase = true)) return SvgPaint.None

    // Ids are case-sensitive, unlike everything else a paint can say.
    if (value.startsWith("url(", ignoreCase = true)) {
        val id: String = value
            .substringAfter('#', missingDelimiterValue = "")
            .substringBefore(')')
            .trim { it.isWhitespace() || it == '"' || it == '\'' }
        return SvgPaint.Reference(id)
    }

    return svgColor(value)?.let { SvgPaint.Solid(it) }
}

/** The 16 basic CSS colour names, and `orange`, which every icon set uses anyway. */
private val NamedColors: Map<String, Long> = mapOf(
    "black" to 0xFF000000,
    "silver" to 0xFFC0C0C0,
    "gray" to 0xFF808080,
    "white" to 0xFFFFFFFF,
    "maroon" to 0xFF800000,
    "red" to 0xFFFF0000,
    "purple" to 0xFF800080,
    "fuchsia" to 0xFFFF00FF,
    "green" to 0xFF008000,
    "lime" to 0xFF00FF00,
    "olive" to 0xFF808000,
    "yellow" to 0xFFFFFF00,
    "navy" to 0xFF000080,
    "blue" to 0xFF0000FF,
    "teal" to 0xFF008080,
    "aqua" to 0xFF00FFFF,
    "orange" to 0xFFFFA500,
)

/**
 * A CSS colour as packed ARGB, null for one this can't read. Hex in all four
 * lengths, `rgb()` and `rgba()` in numbers or percentages, the names above,
 * `transparent`, and `currentColor`, which with no text colour to inherit is
 * black.
 */
internal fun svgColor(value: String): Long? {
    val text: String = value.trim().lowercase()

    if (text.startsWith("#")) {
        val digits: String = text.drop(1)
        // The short forms double each digit; CSS writes alpha last, the document first.
        val full: String = when (digits.length) {
            3, 4 -> digits.map { "$it$it" }.joinToString("")
            6, 8 -> digits
            else -> return null
        }
        val rgb: Long = full.take(6).toLongOrNull(16) ?: return null
        val alpha: Long = if (full.length == 8) full.drop(6).toLongOrNull(16) ?: return null else 0xFF
        return (alpha shl 24) or rgb
    }

    if (text.startsWith("rgb")) {
        val parts: List<String> = text
            .substringAfter('(', missingDelimiterValue = "")
            .substringBefore(')')
            .split(',', ' ', '/')
            .filter { it.isNotBlank() }
        if (parts.size != 3 && parts.size != 4) return null

        // A channel is 0 to 255 or a percentage of it; alpha is 0 to 1 or a percentage.
        fun channel(part: String, whole: Float): Long? {
            val number: Float = PathScanner(part).number() ?: return null
            val scaled: Float = if (part.endsWith("%")) number / 100f * 255f else number * whole
            return scaled.roundToInt().coerceIn(0, 255).toLong()
        }

        val red: Long = channel(parts[0], 1f) ?: return null
        val green: Long = channel(parts[1], 1f) ?: return null
        val blue: Long = channel(parts[2], 1f) ?: return null
        val alpha: Long = if (parts.size == 4) channel(parts[3], 255f) ?: return null else 0xFF
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    return when (text) {
        "transparent" -> SvgClear
        "currentcolor" -> SvgBlack
        else -> NamedColors[text]
    }
}

/**
 * A length in user units: a bare number or `px` as written, the absolute units
 * at CSS's 96 to the inch. Null for a percentage or a font-relative unit, which
 * need a box or a font this reader doesn't have.
 */
private fun svgLength(value: String): Float? {
    val scanner = PathScanner(value)
    val number: Float = scanner.number() ?: return null
    return when (scanner.rest.trim().lowercase()) {
        "", "px" -> number
        "pt" -> number * 96f / 72f
        "pc" -> number * 16f
        "mm" -> number * 96f / 25.4f
        "cm" -> number * 96f / 2.54f
        "in" -> number * 96f
        else -> null
    }
}

/** An opacity: 0 to 1, or a percentage of it. */
private fun svgFraction(value: String): Float? {
    val scanner = PathScanner(value)
    val number: Float = scanner.number() ?: return null
    val fraction: Float = if (scanner.rest.trim() == "%") number / 100f else number
    return fraction.coerceIn(0f, 1f)
}

/** Every number in [text], however they are separated. Stops at the first thing that is not one. */
private fun svgNumbers(text: String): List<Float> {
    val scanner = PathScanner(text)
    return generateSequence { scanner.number() }.toList()
}

/**
 * A gradient boiled down to what a [ShapeGradient] can keep. [angle] is a linear
 * one's; [centerX], [centerY] and [radius] are a radial one's circle through its
 * `gradientTransform`, in the unit box or, when [userSpace], the user space of
 * the element it fills.
 */
private class SvgGradient(
    val linear: Boolean,
    val stops: List<Long>,
    val angle: Float,
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val userSpace: Boolean,
)

/**
 * This radial gradient from [start] to [end] over [bounds], the filled outline's
 * box in the root's user space. In the unit box its circle is already in
 * fractions of the box; in user space it goes through [matrix], the element's,
 * and is measured against the box.
 *
 * The radius is a fraction of the box's longer side, the most a circular fill
 * can say of the ellipse SVG stretches a unit box circle into.
 */
private fun SvgGradient.radial(
    start: Long,
    end: Long,
    matrix: SvgMatrix,
    bounds: Frame,
): ShapeGradient {
    val circle = ShapeGradient(start, end, kind = GradientKind.Radial)
    if (!userSpace) return circle.copy(centerX = centerX, centerY = centerY, radius = radius)
    if (bounds.width <= 0f || bounds.height <= 0f) return circle

    return circle.copy(
        centerX = (matrix.x(centerX, centerY) - bounds.x) / bounds.width,
        centerY = (matrix.y(centerX, centerY) - bounds.y) / bounds.height,
        radius = radius * matrix.scale / max(bounds.width, bounds.height),
    )
}

/**
 * The gradient [id] names, null when nothing does.
 *
 * Its stops are its own or, for one that has none, those of the gradient its
 * `href` points at: one hop, which is how every editor writes a gradient that
 * shares its colours with another. [SvgGradient.angle] is the direction of its
 * vector in CSS degrees, 0 pointing up and turning clockwise.
 */
private fun SvgSheet.gradient(id: String): SvgGradient? {
    val node: SvgNode = gradients[id] ?: return null

    fun SvgNode.stops(): List<Long> = children.filter { it.name == "stop" }.map { stop ->
        val properties: Map<String, String> = stop.properties(rules)
        val color: Long = properties["stop-color"]?.let(::svgColor) ?: SvgBlack
        color.faded(properties["stop-opacity"]?.let(::svgFraction) ?: 1f)
    }

    val shared: SvgNode? = (node.attributes["href"] ?: node.attributes["xlink:href"])
        ?.removePrefix("#")
        ?.let { gradients[it] }
    val stops: List<Long> = node.stops().ifEmpty { shared?.stops().orEmpty() }

    // In the unit box by default, left to right: a percentage is a fraction of it.
    // In user space a percentage would be of the viewport, which nothing exports.
    fun coordinate(name: String, default: Float): Float {
        val value: String = node.attributes[name] ?: return default
        if (value.endsWith("%")) return PathScanner(value).number()?.div(100f) ?: default
        return svgLength(value) ?: default
    }

    val turn: SvgMatrix = svgTransform(node.attributes["gradientTransform"])
    val alongX: Float = coordinate("x2", 1f) - coordinate("x1", 0f)
    val alongY: Float = coordinate("y2", 0f) - coordinate("y1", 0f)
    val dx: Float = turn.a * alongX + turn.c * alongY
    val dy: Float = turn.b * alongX + turn.d * alongY
    val degrees: Float = atan2(dx, -dy) * 180f / PI.toFloat()

    // A radial one's circle, which defaults to filling the unit box.
    val cx: Float = coordinate("cx", 0.5f)
    val cy: Float = coordinate("cy", 0.5f)

    return SvgGradient(
        linear = node.name == "linearGradient",
        stops = stops,
        angle = (degrees + 360f) % 360f,
        centerX = turn.x(cx, cy),
        centerY = turn.y(cx, cy),
        radius = coordinate("r", 0.5f) * turn.scale,
        userSpace = node.attributes["gradientUnits"] == "userSpaceOnUse",
    )
}

/**
 * A drop shadow read off a filter, in the root's user space: the step it falls
 * by, its blur's standard deviation, and its colour with the opacity apart.
 */
private data class SvgShadow(
    val dx: Float,
    val dy: Float,
    val deviation: Float,
    val color: Long,
    val opacity: Float,
)

/**
 * This shadow on a slide, the walk's [scaleX] and [scaleY] to the natural size
 * and fit applied, [strokeScale] for the blur. A standard deviation is half a
 * blur radius, CSS's own reading of the two; an alpha in the colour folds into
 * the opacity, which is where the editor keeps it.
 */
private fun SvgShadow.cast(scaleX: Float, scaleY: Float, strokeScale: Float): ShapeShadow {
    val (offset: Float, angle: Float) = shadowOffsetAngle(dx * scaleX, dy * scaleY)
    return ShapeShadow(
        kind = ShadowKind.Drop,
        color = color or SvgBlack,
        blur = deviation * 2f * strokeScale,
        offset = offset,
        angle = angle,
        opacity = (opacity * color.alpha / 255f).coerceIn(0f, 1f),
    )
}

/**
 * The drop shadow the `filter` [value] names, in the root's user space: [matrix]
 * is the filtered element's, which its numbers are in. Null for a filter that
 * names nothing or is not a drop shadow this can read.
 *
 * Two spellings are read. `feDropShadow`, which says it all in one. And the
 * recipe from before it, which design tools still export: the source's alpha
 * stepped by `feOffset` and blurred by `feGaussianBlur`, coloured by an `feFlood`
 * or by an `feColorMatrix` that throws the source's colour away (Figma's), then
 * put back under `SourceGraphic` with `feMerge`, `feBlend` or `feComposite`.
 *
 * A filter that never puts the source back over what it made is doing something
 * to the shape itself, a blur or a glow, and an arithmetic composite is how an
 * inner shadow cuts itself out: neither is a drop shadow.
 */
private fun SvgSheet.shadow(value: String, matrix: SvgMatrix): SvgShadow? {
    val id: String = (svgPaint(value) as? SvgPaint.Reference)?.id ?: return null
    val primitives: List<SvgNode> = filters[id]?.children ?: return null

    fun SvgNode.number(name: String, default: Float): Float =
        attributes[name]?.let(::svgLength) ?: default

    // One deviation or two, along x then y; two are as near as one blur gets by
    // their mean.
    fun SvgNode.deviation(default: Float): Float {
        val given: List<Float> = svgNumbers(attributes["stdDeviation"].orEmpty())
        return if (given.isEmpty()) default else given.take(2).average().toFloat()
    }

    fun SvgNode.flood(): Pair<Long, Float> {
        val flood: Map<String, String> = properties(rules)
        val color: Long = flood["flood-color"]?.let(::svgColor) ?: SvgBlack
        return color to (flood["flood-opacity"]?.let(::svgFraction) ?: 1f)
    }

    val drop: SvgNode? = primitives.firstOrNull { it.name == "feDropShadow" }
    val shadow: SvgShadow = if (drop != null) {
        val (color: Long, opacity: Float) = drop.flood()
        SvgShadow(drop.number("dx", 2f), drop.number("dy", 2f), drop.deviation(2f), color, opacity)
    } else {
        val offset: SvgNode? = primitives.firstOrNull { it.name == "feOffset" }
        val blur: SvgNode? = primitives.firstOrNull { it.name == "feGaussianBlur" }
        if (offset == null && blur == null) return null

        val restored: Boolean = primitives.any { node ->
            when (node.name) {
                "feMerge" -> node.children.any { it.attributes["in"] == "SourceGraphic" }
                "feBlend", "feComposite" -> node.attributes["in"] == "SourceGraphic" ||
                    node.attributes["in2"] == "SourceGraphic"
                else -> false
            }
        }
        val cutOut: Boolean = primitives.any {
            it.name == "feComposite" && it.attributes["operator"] == "arithmetic"
        }
        if (!restored || cutOut) return null

        // The last recolouring wins: Figma's first colour matrix only pulls the
        // alpha out, the one after the blur is the shadow's. A flood of nothing
        // is a backdrop being cleared rather than a colour.
        val recoloured: Pair<Long, Float>? = primitives
            .filter { it.name == "feColorMatrix" }
            .lastOrNull { (it.attributes["type"] ?: "matrix") == "matrix" }
            ?.let { svgRecolour(it.attributes["values"].orEmpty()) }
        val flooded: Pair<Long, Float>? = primitives
            .filter { it.name == "feFlood" }
            .map { it.flood() }
            .lastOrNull { (_, opacity) -> opacity > 0f }
        val (color: Long, opacity: Float) = recoloured ?: flooded ?: (SvgBlack to 1f)

        SvgShadow(
            dx = offset?.number("dx", 0f) ?: 0f,
            dy = offset?.number("dy", 0f) ?: 0f,
            deviation = blur?.deviation(0f) ?: 0f,
            color = color,
            opacity = opacity,
        )
    }

    // Into the root's space: the step turns and stretches with the element, the
    // blur grows with it.
    return shadow.copy(
        dx = matrix.a * shadow.dx + matrix.c * shadow.dy,
        dy = matrix.b * shadow.dx + matrix.d * shadow.dy,
        deviation = shadow.deviation * matrix.scale,
    )
}

/**
 * The colour and opacity an `feColorMatrix` [values] paints everything in, null
 * for a matrix that keeps any of the source's colour, which is a tint rather than
 * a shadow's colour. The colour is the RGB rows' constant column; the opacity is
 * what the alpha row multiplies the source's alpha by.
 */
private fun svgRecolour(values: String): Pair<Long, Float>? {
    val m: List<Float> = svgNumbers(values)
    if (m.size != 20) return null

    val keepsColour: Boolean =
        (0..2).any { row -> (0..2).any { column -> m[row * 5 + column] != 0f } }
    if (keepsColour) return null

    fun channel(value: Float): Long = (value.coerceIn(0f, 1f) * 255f).roundToInt().toLong()
    val color: Long = SvgBlack or (channel(m[4]) shl 16) or (channel(m[9]) shl 8) or channel(m[14])
    return color to m[18].coerceIn(0f, 1f)
}

// endregion

// region Stylesheets

/** One class selector and what it sets. `.a, .b { }` is two of these. */
private class SvgRule(val className: String, val declarations: Map<String, String>)

/**
 * Everything this element sets, by name: its attributes, then the class rules
 * that apply to it in stylesheet order, then its own `style`, each over the one
 * before. That is CSS's order for the three, with specificity left out, since
 * one class selector is as specific as another.
 */
private fun SvgNode.properties(rules: List<SvgRule>): Map<String, String> {
    val classes: List<String> = attributes["class"]?.split(' ', '\t', '\n', '\r').orEmpty()
    val properties: MutableMap<String, String> = attributes.toMutableMap()
    for (rule in rules) {
        if (rule.className in classes) properties += rule.declarations
    }
    attributes["style"]?.let { properties += cssDeclarations(it) }
    return properties
}

/**
 * The rules of a `<style>` block that are plain class selectors, alone or in a
 * comma list. Anything cleverer (a descendant, an element name, an id, an
 * at-rule) is skipped rather than half-applied.
 */
private fun cssRules(css: String): List<SvgRule> {
    var text: String = css
    while (true) {
        val open: Int = text.indexOf("/*")
        if (open < 0) break
        val close: Int = text.indexOf("*/", open + 2)
        text = text.substring(0, open) + if (close < 0) "" else text.substring(close + 2)
    }

    val rules: MutableList<SvgRule> = mutableListOf()
    var index = 0
    while (true) {
        val open: Int = text.indexOf('{', index)
        if (open < 0) break
        val close: Int = text.indexOf('}', open).takeIf { it >= 0 } ?: text.length
        val declarations: Map<String, String> = cssDeclarations(text.substring(open + 1, close))

        for (selector in text.substring(index, open).split(',')) {
            val name: String = selector.trim().removePrefix(".")
            val simple: Boolean = selector.trim().startsWith(".") && name.isNotEmpty() &&
                name.all { it.isLetterOrDigit() || it == '-' || it == '_' }
            if (simple) rules += SvgRule(name, declarations)
        }

        index = min(close + 1, text.length)
    }

    return rules
}

/** `name: value; name: value`, names lowercased. */
private fun cssDeclarations(text: String): Map<String, String> = text
    .split(';')
    .filter { ':' in it }
    .associate { declaration ->
        val name: String = declaration.substringBefore(':').trim().lowercase()
        val value: String = declaration.substringAfter(':').trim().removeSuffix("!important").trim()
        name to value
    }

// endregion

// region XML: just enough of it

/** An element: its local name, its attributes, and the text directly inside it. */
private class SvgNode(val name: String, val attributes: Map<String, String>) {
    val children: MutableList<SvgNode> = mutableListOf()
    val text: StringBuilder = StringBuilder()
}

/** This element and everything under it, in document order. */
private fun SvgNode.subtree(): List<SvgNode> = listOf(this) + children.flatMap { it.subtree() }

private fun SvgNode.firstNamed(name: String): SvgNode? = subtree().firstOrNull { it.name == name }

/**
 * [source] as a tree, under a nameless node standing for the document.
 *
 * Hand-rolled because the core has no XML library on any of its targets and an
 * SVG needs very little of one: tags, attributes, and the text inside `<style>`.
 * Namespace prefixes come off element names, so `svg:rect` is a `rect`. Comments,
 * processing instructions and the doctype are stepped over, and nothing here
 * throws: a file cut off mid-tag is the tree up to where it stopped.
 */
private fun parseXml(source: String): SvgNode {
    val document = SvgNode("", emptyMap())
    val open: MutableList<SvgNode> = mutableListOf(document)
    var index = 0

    fun past(marker: String, from: Int): Int {
        val at: Int = source.indexOf(marker, from)
        return if (at < 0) source.length else at + marker.length
    }

    while (index < source.length) {
        val tag: Int = source.indexOf('<', index)
        if (tag < 0) break
        open.last().text.append(decodeEntities(source.substring(index, tag)))

        index = when {
            source.startsWith("<!--", tag) -> past("-->", tag + 4)

            source.startsWith("<![CDATA[", tag) -> {
                val end: Int = past("]]>", tag + 9)
                val close: Int = if (source.startsWith("]]>", end - 3)) end - 3 else end
                open.last().text.append(source.substring(min(tag + 9, close), close))
                end
            }

            source.startsWith("<?", tag) -> past("?>", tag + 2)

            // A doctype can carry an internal subset in brackets, with tags of
            // its own inside: it ends at the first `>` outside them.
            source.startsWith("<!", tag) -> {
                var at: Int = tag
                var depth = 0
                while (at < source.length && !(source[at] == '>' && depth == 0)) {
                    if (source[at] == '[') depth++
                    if (source[at] == ']') depth--
                    at++
                }
                min(at + 1, source.length)
            }

            // A close tag closes the nearest open element of that name and
            // everything left open inside it; one that names nothing is ignored.
            source.startsWith("</", tag) -> {
                val end: Int = past(">", tag + 2)
                val name: String = source.substring(tag + 2, end).trim { it.isWhitespace() || it == '>' }
                val at: Int = open.indexOfLast { it.name == name.substringAfter(':') }
                if (at > 0) while (open.size > at) open.removeAt(open.lastIndex)
                end
            }

            else -> {
                var at: Int = tag + 1
                while (at < source.length && !source[at].isWhitespace() && source[at] !in "/>") at++
                val name: String = source.substring(tag + 1, at).substringAfter(':')

                // A `<` that opens nothing is a stray one in text.
                if (name.isEmpty()) {
                    index = tag + 1
                    continue
                }

                val attributes: MutableMap<String, String> = mutableMapOf()
                var selfClosing = false
                while (at < source.length) {
                    while (at < source.length && source[at].isWhitespace()) at++
                    if (at >= source.length) break
                    if (source[at] == '>') {
                        at++
                        break
                    }
                    if (source[at] == '/') {
                        selfClosing = true
                        at++
                        continue
                    }

                    val keyStart: Int = at
                    while (at < source.length && !source[at].isWhitespace() && source[at] !in "=/>") at++
                    val key: String = source.substring(keyStart, at)
                    while (at < source.length && source[at].isWhitespace()) at++
                    if (at >= source.length || source[at] != '=') continue

                    at++
                    while (at < source.length && source[at].isWhitespace()) at++
                    val quote: Char? = source.getOrNull(at)?.takeIf { it == '"' || it == '\'' }
                    val valueStart: Int = if (quote == null) at else at + 1
                    var valueEnd: Int = valueStart
                    while (valueEnd < source.length) {
                        val char: Char = source[valueEnd]
                        if (quote != null && char == quote) break
                        if (quote == null && (char.isWhitespace() || char == '>')) break
                        valueEnd++
                    }

                    attributes[key] = decodeEntities(source.substring(valueStart, valueEnd)).trim()
                    at = if (quote == null) valueEnd else min(valueEnd + 1, source.length)
                }

                val node = SvgNode(name, attributes)
                open.last().children += node
                if (!selfClosing) open += node
                at
            }
        }
    }

    return document
}

/** The five entities XML defines and numeric references; anything else is left as written. */
private fun decodeEntities(text: String): String {
    if ('&' !in text) return text

    val decoded = StringBuilder()
    var index = 0
    while (index < text.length) {
        val ampersand: Int = text.indexOf('&', index)
        if (ampersand < 0) {
            decoded.append(text.substring(index))
            break
        }
        decoded.append(text.substring(index, ampersand))

        val semicolon: Int = text.indexOf(';', ampersand)
        val entity: String = if (semicolon < 0) "" else text.substring(ampersand + 1, semicolon)
        val character: Char? = when {
            entity == "amp" -> '&'
            entity == "lt" -> '<'
            entity == "gt" -> '>'
            entity == "quot" -> '"'
            entity == "apos" -> '\''
            entity.startsWith("#x") -> entity.drop(2).toIntOrNull(16)?.toChar()
            entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.toChar()
            else -> null
        }

        if (character == null) {
            decoded.append('&')
            index = ampersand + 1
        } else {
            decoded.append(character)
            index = semicolon + 1
        }
    }

    return decoded.toString()
}

// endregion

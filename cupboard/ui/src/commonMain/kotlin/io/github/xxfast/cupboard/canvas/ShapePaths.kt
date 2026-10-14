package io.github.xxfast.cupboard.canvas

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.xxfast.cupboard.document.PathCommand
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.ShapePath
import io.github.xxfast.cupboard.document.commands
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The outline a shape draws, filled and stroked as one so the border traces
 * exactly what the fill covers.
 *
 * Every kind but the two that predate the catalog is a [GenericShape] over the
 * element's box: the paths below are written in fractions of that box, so a
 * shape stretched by a resize handle stretches rather than re-laying itself out.
 *
 * [ShapeKind.Path] is the exception to "a kind decides": its outline is data on
 * the element, so it is answered here and not by the kind.
 */
internal fun ShapeElement.shape(): Shape {
    val path: ShapePath? = path
    if (kind == ShapeKind.Path && path != null) return PathShape(path)
    return kind.shape(cornerRadius)
}

/**
 * The same outline for a bare kind, which is what a mask has: an image cropped to
 * a star is clipped by the star the catalog draws, so the two never drift apart.
 *
 * [cornerRadius] means nothing to any kind but [ShapeKind.Rectangle], and defaults
 * to square because a mask carries no radius of its own.
 */
internal fun ShapeKind.shape(cornerRadius: Float = 0f): Shape = when (this) {
    ShapeKind.Rectangle -> RoundedCornerShape(cornerRadius.dp)
    ShapeKind.Ellipse -> CircleShape
    ShapeKind.Triangle -> TriangleShape
    ShapeKind.Arrow -> ArrowShape
    ShapeKind.Diamond -> DiamondShape
    ShapeKind.Star -> StarShape
    ShapeKind.Polygon -> HexagonShape
    ShapeKind.QuoteBubble -> QuoteBubbleShape
    ShapeKind.Callout -> CalloutShape
    // A path's outline is the element's own (see [ShapeElement.shape]); a bare
    // kind has none to draw, so it reads as the box it sits in.
    ShapeKind.Path -> RoundedCornerShape(0.dp)
}

/**
 * A [ShapePath] as a [Shape]. A data class so that two recompositions of the
 * same element compare equal and nothing downstream re-reads the path.
 */
private data class PathShape(val path: ShapePath) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(path.toComposePath(size))
}

/**
 * This outline as a Compose path in a box of [size]: each point goes through
 * [ShapePath.bounds], so the box shows exactly that slice of path space.
 *
 * A side of the bounds with no extent (a straight line's) has nothing to scale
 * by, so it collapses onto the box's edge rather than dividing by zero.
 */
internal fun ShapePath.toComposePath(size: Size): Path {
    val scaleX: Float = if (bounds.width > 0f) size.width / bounds.width else 0f
    val scaleY: Float = if (bounds.height > 0f) size.height / bounds.height else 0f

    fun x(value: Float): Float = (value - bounds.x) * scaleX
    fun y(value: Float): Float = (value - bounds.y) * scaleY

    return Path().apply {
        fillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
        for (command in commands) {
            when (command) {
                is PathCommand.MoveTo -> moveTo(x(command.x), y(command.y))
                is PathCommand.LineTo -> lineTo(x(command.x), y(command.y))
                is PathCommand.CubicTo -> cubicTo(
                    x(command.x1), y(command.y1),
                    x(command.x2), y(command.y2),
                    x(command.x), y(command.y),
                )
                PathCommand.Close -> close()
            }
        }
    }
}

private val TriangleShape = GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

private val DiamondShape = GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width / 2f, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

/** How much of a block arrow's length the head takes, and of its height the shaft. */
private const val ArrowHeadFraction: Float = 0.36f
private const val ArrowShaftFraction: Float = 0.5f

private val ArrowShape = GenericShape { size, _ ->
    val headStart: Float = size.width * (1f - ArrowHeadFraction)
    val shaftTop: Float = size.height * (1f - ArrowShaftFraction) / 2f
    val shaftBottom: Float = size.height - shaftTop

    moveTo(0f, shaftTop)
    lineTo(headStart, shaftTop)
    lineTo(headStart, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(headStart, size.height)
    lineTo(headStart, shaftBottom)
    lineTo(0f, shaftBottom)
    close()
}

/** How far in the star's inner points sit, as a fraction of the outer ones. */
private const val StarInnerFraction: Float = 0.42f

private const val StarPoints: Int = 5

private val StarShape = GenericShape { size, _ ->
    // Radii per axis rather than one: the star fills whatever box it is given,
    // so a wide frame gets a wide star instead of a circular one in a gap.
    val radiusX: Float = size.width / 2f
    val radiusY: Float = size.height / 2f

    repeat(StarPoints * 2) { index ->
        val fraction: Float = if (index % 2 == 0) 1f else StarInnerFraction
        val angle: Float = -PI.toFloat() / 2f + index * PI.toFloat() / StarPoints
        val x: Float = radiusX + cos(angle) * radiusX * fraction
        val y: Float = radiusY + sin(angle) * radiusY * fraction
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private const val HexagonSides: Int = 6

/** Flat top and bottom, points left and right: the shape a wide box wants. */
private val HexagonShape = GenericShape { size, _ ->
    val radiusX: Float = size.width / 2f
    val radiusY: Float = size.height / 2f

    repeat(HexagonSides) { index ->
        val angle: Float = index * 2f * PI.toFloat() / HexagonSides
        val x: Float = radiusX + cos(angle) * radiusX
        val y: Float = radiusY + sin(angle) * radiusY
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** How much of a bubble's height the body takes; the rest is the tail. */
private const val BubbleBodyFraction: Float = 0.78f

/** The body's corner radius, against its shorter side. */
private const val BubbleCornerFraction: Float = 0.16f

private val QuoteBubbleShape = GenericShape { size, _ ->
    addBubble(size, tailLeft = 0.18f, tailTip = 0.1f, tailRight = 0.32f)
}

private val CalloutShape = GenericShape { size, _ ->
    addBubble(size, tailLeft = 0.44f, tailTip = 0.5f, tailRight = 0.56f)
}

/**
 * A rounded body across the top of [size] with a tail hanging off its bottom
 * edge, as one continuous outline: two subpaths would leave a seam for the
 * border to trace across the join.
 *
 * The three tail arguments are fractions of the width: where its base starts and
 * ends on the bottom edge, and where its point sits at the very bottom of the box.
 * Drawn clockwise from the top-left corner, so the bottom edge is walked right to
 * left and the tail's right-hand base comes first.
 */
private fun Path.addBubble(size: Size, tailLeft: Float, tailTip: Float, tailRight: Float) {
    val bottom: Float = size.height * BubbleBodyFraction
    val radius: Float = min(size.width, bottom) * BubbleCornerFraction

    moveTo(radius, 0f)
    lineTo(size.width - radius, 0f)
    quadraticTo(size.width, 0f, size.width, radius)
    lineTo(size.width, bottom - radius)
    quadraticTo(size.width, bottom, size.width - radius, bottom)
    lineTo(size.width * tailRight, bottom)
    lineTo(size.width * tailTip, size.height)
    lineTo(size.width * tailLeft, bottom)
    lineTo(radius, bottom)
    quadraticTo(0f, bottom, 0f, bottom - radius)
    lineTo(0f, radius)
    quadraticTo(0f, 0f, radius, 0f)
    close()
}

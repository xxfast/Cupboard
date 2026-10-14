package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.xxfast.cupboard.document.LineEnd
import io.github.xxfast.cupboard.document.StrokeStyle
import kotlin.math.hypot

/**
 * How big a line's heads draw at [strokeWidth], in the same units: Keynote's,
 * measured on its filled arrow at 6pt for a 1pt line, 13pt at 3pt and 38pt at
 * 10pt. Heads grow with the stroke, but not in proportion.
 */
internal fun lineHeadSize(strokeWidth: Float): Float = 3.5f * strokeWidth + 3f

/**
 * The dash pattern this style lays along a stroke [weight] wide, null for a solid
 * one. Every length is in stroke widths, so a heavier line has longer dashes,
 * eyeballed against Keynote's own popup. A round dot is a dash of next to nothing
 * that [cap] rounds out into a dot one stroke wide.
 */
internal fun StrokeStyle.pathEffect(weight: Float): PathEffect? = when (this) {
    StrokeStyle.Solid -> null
    StrokeStyle.SquareDots -> PathEffect.dashPathEffect(floatArrayOf(weight, weight))
    StrokeStyle.ShortDash -> PathEffect.dashPathEffect(floatArrayOf(3 * weight, 2 * weight))
    StrokeStyle.LongDash -> PathEffect.dashPathEffect(floatArrayOf(8 * weight, 3 * weight))
    StrokeStyle.RoundDots -> PathEffect.dashPathEffect(floatArrayOf(0.01f, 4 * weight))
}

/** Butt for everything but round dots, whose dots are the caps. */
internal val StrokeStyle.cap: StrokeCap
    get() = if (this == StrokeStyle.RoundDots) StrokeCap.Round else StrokeCap.Butt

/**
 * How far back from the endpoint the stroke stops under this head, so a dash
 * pattern never pokes through a filled head and a hollow one stays hollow. A
 * filled head takes the stroke some way into itself, where it is wider than the
 * stroke, so no seam shows between the two.
 */
internal fun LineEnd.inset(size: Float, weight: Float): Float = when (this) {
    LineEnd.None, LineEnd.Bar -> 0f
    LineEnd.Arrow -> 0.8f * size
    LineEnd.SweptArrow -> 0.5f * size
    LineEnd.OpenArrow -> weight
    LineEnd.Circle -> CircleScale * size / 2
    LineEnd.Diamond -> size / 2
    LineEnd.Square -> SquareScale * size / 2
    LineEnd.OpenSquare -> SquareScale * size - weight / 2
    LineEnd.OpenCircle -> CircleScale * size - weight / 2
    LineEnd.ReversedArrow -> 0.3f * size
}

/**
 * [end] drawn at [tip], the line running back towards [from]: the head's own
 * outermost point sits on [tip], and it is [size] long. Hollow heads are
 * stroked at [weight], the line's own.
 */
internal fun DrawScope.drawLineEnd(
    end: LineEnd,
    color: Color,
    tip: Offset,
    from: Offset,
    weight: Float,
    size: Float,
) {
    if (end == LineEnd.None) return
    val length: Float = hypot(tip.x - from.x, tip.y - from.y)
    if (length == 0f) return

    // Along points back down the line from the tip; across is square to it.
    val alongX: Float = (from.x - tip.x) / length
    val alongY: Float = (from.y - tip.y) / length
    fun at(back: Float, side: Float): Offset =
        Offset(tip.x + alongX * back - alongY * side, tip.y + alongY * back + alongX * side)

    fun polygon(points: List<Offset>): Path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (point in points.drop(1)) lineTo(point.x, point.y)
        close()
    }

    val outline = Stroke(width = weight, join = StrokeJoin.Miter)
    when (end) {
        LineEnd.None -> Unit

        LineEnd.Arrow ->
            drawPath(polygon(listOf(at(0f, 0f), at(size, size / 2), at(size, -size / 2))), color)

        LineEnd.SweptArrow -> drawPath(
            polygon(
                listOf(at(0f, 0f), at(size, size / 2), at(SweptNotch * size, 0f), at(size, -size / 2)),
            ),
            color,
        )

        LineEnd.OpenArrow -> drawPath(
            path = Path().apply {
                val left: Offset = at(size, size / 2)
                val point: Offset = at(weight, 0f)
                val right: Offset = at(size, -size / 2)
                moveTo(left.x, left.y)
                lineTo(point.x, point.y)
                lineTo(right.x, right.y)
            },
            color = color,
            style = Stroke(width = weight, cap = StrokeCap.Butt, join = StrokeJoin.Miter),
        )

        LineEnd.ReversedArrow -> {
            val back: Float = ReversedLength * size
            val half: Float = CircleScale * size / 2
            drawPath(polygon(listOf(at(0f, half), at(0f, -half), at(back, 0f))), color)
        }

        LineEnd.Diamond -> {
            val half: Float = CircleScale * size / 2
            drawPath(
                polygon(listOf(at(0f, 0f), at(size / 2, half), at(size, 0f), at(size / 2, -half))),
                color,
            )
        }

        LineEnd.Circle -> {
            val radius: Float = CircleScale * size / 2
            drawCircle(color, radius, at(radius, 0f))
        }

        LineEnd.OpenCircle -> {
            val radius: Float = CircleScale * size / 2
            drawCircle(color, radius - weight / 2, at(radius, 0f), style = outline)
        }

        LineEnd.Square, LineEnd.OpenSquare -> {
            val half: Float = SquareScale * size / 2
            val hollow: Boolean = end == LineEnd.OpenSquare
            val inset: Float = if (hollow) weight / 2 else 0f
            val square: Path = polygon(
                listOf(
                    at(inset, half - inset),
                    at(2 * half - inset, half - inset),
                    at(2 * half - inset, -half + inset),
                    at(inset, -half + inset),
                ),
            )
            if (hollow) drawPath(square, color, style = outline) else drawPath(square, color)
        }

        LineEnd.Bar -> {
            val half: Float = CircleScale * size / 2
            drawLine(color, at(0f, half), at(0f, -half), strokeWidth = weight)
        }
    }
}

/**
 * The picker's preview of [end]: a short stretch of line across [size] with the
 * head on its right, or on its left when [mirrored], which is how Keynote draws
 * the start's popup.
 */
internal fun DrawScope.drawLineEndPreview(
    end: LineEnd,
    color: Color,
    weight: Float,
    mirrored: Boolean,
) {
    val y: Float = size.height / 2
    val left = Offset(0f, y)
    val right = Offset(size.width, y)
    val tip: Offset = if (mirrored) left else right
    val tail: Offset = if (mirrored) right else left
    val head: Float = lineHeadSize(weight)
    val back: Float = end.inset(head, weight)
    val stop = Offset(tip.x + (if (mirrored) back else -back), y)
    drawLine(color, tail, stop, strokeWidth = weight)
    drawLineEnd(end, color, tip, tail, weight, head)
}

/** The picker's preview of [style]: a stretch of line across the middle of the box. */
internal fun DrawScope.drawStrokeStylePreview(style: StrokeStyle, color: Color, weight: Float) {
    val y: Float = size.height / 2
    drawLine(
        color = color,
        start = Offset(weight, y),
        end = Offset(size.width - weight, y),
        strokeWidth = weight,
        cap = style.cap,
        pathEffect = style.pathEffect(weight),
    )
}

/** A shape's outline stroked in [style], inset by half the stroke the way a solid border is. */
internal fun DrawScope.drawStyledOutline(
    outline: (Size) -> Path,
    style: StrokeStyle,
    color: Color,
    weight: Float,
) {
    if (weight <= 0f) return
    val inner: Size = Size(size.width - weight, size.height - weight)
    if (inner.width <= 0f || inner.height <= 0f) return
    val path: Path = outline(inner)
    path.translate(Offset(weight / 2, weight / 2))
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = weight, cap = style.cap, pathEffect = style.pathEffect(weight)),
    )
}

/** How wide a circle, a diamond, a reversed arrow and a bar are against an arrow's [lineHeadSize]. */
private const val CircleScale: Float = 0.85f

/** A square's side against an arrow's length. */
private const val SquareScale: Float = 0.8f

/** How far back a swept arrow's notch sits, against its length. */
private const val SweptNotch: Float = 0.6f

/** A reversed arrow's length against an arrow's: Keynote's is stubbier. */
private const val ReversedLength: Float = 0.6f

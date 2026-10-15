package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import io.github.xxfast.cupboard.document.GradientKind
import io.github.xxfast.cupboard.document.ShapeGradient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * One end of a CSS gradient line at [angle] degrees across a [size] box,
 * [direction] -1 for the start and 1 for the end.
 *
 * The line runs through the box's center, 0 degrees pointing up and the angle
 * turning clockwise, and is long enough that the corners project onto its ends,
 * which is what makes a 45-degree gradient reach corner to corner rather than
 * stopping short of it.
 *
 * Shared by the slide's background and a shape's gradient fill: both are the
 * document's same two-stop angle, and a shape filled at 140 degrees has to match
 * the deck it sits on.
 */
internal fun gradientStop(size: Size, angle: Float, direction: Float): Offset {
    val radians: Float = angle * PI.toFloat() / 180f
    val dx: Float = sin(radians)
    val dy: Float = -cos(radians)
    val length: Float = abs(size.width * dx) + abs(size.height * dy)
    return Offset(
        size.width / 2 + dx * length / 2 * direction,
        size.height / 2 + dy * length / 2 * direction,
    )
}

/**
 * What a shape's gradient paints with: along its angle's line for a linear one,
 * out from its centre for a radial one, the stops at the two ends of either.
 *
 * A shader brush rather than [Brush.linearGradient] or [Brush.radialGradient]
 * themselves: the line's ends and the circle's centre are fractions of the box,
 * and the box isn't known until the fill is drawn.
 */
internal fun ShapeGradient.brush(): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val colors: List<Color> = listOf(start.toComposeColor(), end.toComposeColor())
        return when (kind) {
            GradientKind.Linear -> LinearGradientShader(
                from = gradientStop(size, angle, -1f),
                to = gradientStop(size, angle, 1f),
                colors = colors,
            )

            // A circle of no size is no shader at all to some backends, so the
            // smallest one is a pixel's worth: the end colour, everywhere.
            GradientKind.Radial -> RadialGradientShader(
                center = Offset(size.width * centerX, size.height * centerY),
                radius = (max(size.width, size.height) * radius).coerceAtLeast(MinRadius),
                colors = colors,
            )
        }
    }
}

private const val MinRadius: Float = 0.5f

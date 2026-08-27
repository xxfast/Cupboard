package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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

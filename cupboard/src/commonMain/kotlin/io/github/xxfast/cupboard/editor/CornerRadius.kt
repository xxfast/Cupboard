package io.github.xxfast.cupboard.editor

import io.github.xxfast.cupboard.document.Point
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import kotlin.math.min

/**
 * The most a rectangle's corners round: half its shorter side, where it has
 * turned into a pill. Past this the renderer draws the same pill anyway.
 */
val ShapeElement.maxCornerRadius: Float get() = min(frame.width, frame.height) / 2

/**
 * Where Keynote's green radius dot sits: on the top edge, [ShapeElement.cornerRadius]
 * in from the left, where the top-left corner's arc meets the edge. So it moves
 * with the radius, right as it grows. A radius past the pill sits where the
 * pill's arc does.
 *
 * Never nearer the corner than [inset], though: a small radius would put the dot
 * under the corner's resize handle, where it could be seen and never grabbed, so
 * it waits just right of it until the arc catches up. Keynote's does the same.
 *
 * Null when there is no dot: only a [ShapeKind.Rectangle] rounds, a locked one
 * has nothing to drag, and a turned one has no top edge running along x.
 */
fun ShapeElement.radiusHandle(inset: Float = 0f): Point? {
    if (kind != ShapeKind.Rectangle || locked || rotation != 0f) return null
    val radius: Float = cornerRadius.coerceIn(0f, maxCornerRadius)
    return Point(frame.x + radius.coerceAtLeast(inset), frame.y)
}

/**
 * This rectangle with its radius dot dragged to [x] on the slide: the radius is
 * how far that is in from the left edge, from square corners to the pill and no
 * further either way. [snapped] is a Shift drag, which settles on whichever of
 * the two ends is nearer.
 */
fun ShapeElement.withCornerRadiusAt(x: Float, snapped: Boolean = false): ShapeElement {
    val max: Float = maxCornerRadius
    val radius: Float = (x - frame.x).coerceIn(0f, max)
    if (!snapped) return copy(cornerRadius = radius)
    return copy(cornerRadius = if (radius < max / 2) 0f else max)
}

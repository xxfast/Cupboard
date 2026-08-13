package io.github.xxfast.cupboard.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class Handle {
    TopLeft, Top, TopRight,
    Left, Right,
    BottomLeft, Bottom, BottomRight;

    val affectsLeft: Boolean get() = this == TopLeft || this == Left || this == BottomLeft
    val affectsRight: Boolean get() = this == TopRight || this == Right || this == BottomRight
    val affectsTop: Boolean get() = this == TopLeft || this == Top || this == TopRight
    val affectsBottom: Boolean get() = this == BottomLeft || this == Bottom || this == BottomRight
}

/**
 * The axis a handle drags along, as drawn. [DiagonalDown] is the "\" axis and
 * [DiagonalUp] the "/" one, named for where they point in the slide's y-down
 * space. Four, not eight: a resize axis has no direction, both ends of it pull
 * the same way.
 */
enum class ResizeDirection { Horizontal, Vertical, DiagonalDown, DiagonalUp }

/**
 * Which way [handle] pulls on an element drawn at [rotation] degrees: the
 * handle's own axis turned by the rotation, then bucketed to the nearest 45.
 * A box turned 45 degrees therefore offers diagonal cursors on the handles that
 * are horizontal in its own space, which is where the pointer sees them.
 */
fun resizeDirection(handle: Handle, rotation: Float): ResizeDirection {
    val axis = when (handle) {
        Handle.Left, Handle.Right -> 0f
        Handle.TopLeft, Handle.BottomRight -> 45f
        Handle.Top, Handle.Bottom -> 90f
        Handle.TopRight, Handle.BottomLeft -> 135f
    }
    // mod, not rem: a negative rotation has to land in 0..180 like any other.
    val degrees = (axis + rotation).mod(180f)
    return when ((degrees / 45f).roundToInt() % 4) {
        0 -> ResizeDirection.Horizontal
        1 -> ResizeDirection.DiagonalDown
        2 -> ResizeDirection.Vertical
        else -> ResizeDirection.DiagonalUp
    }
}

/** Handle center positions for a frame, in doc units. */
fun handlePositions(frame: Frame): Map<Handle, Pair<Float, Float>> = mapOf(
    Handle.TopLeft to (frame.x to frame.y),
    Handle.Top to (frame.centerX to frame.y),
    Handle.TopRight to (frame.x + frame.width to frame.y),
    Handle.Left to (frame.x to frame.centerY),
    Handle.Right to (frame.x + frame.width to frame.centerY),
    Handle.BottomLeft to (frame.x to frame.y + frame.height),
    Handle.Bottom to (frame.centerX to frame.y + frame.height),
    Handle.BottomRight to (frame.x + frame.width to frame.y + frame.height),
)

fun hitTestHandle(frame: Frame, x: Float, y: Float, tolerance: Float = 8f): Handle? =
    handlePositions(frame).entries.firstOrNull { (_, pos) ->
        val (hx, hy) = pos
        x in (hx - tolerance)..(hx + tolerance) && y in (hy - tolerance)..(hy + tolerance)
    }?.key

fun Frame.contains(x: Float, y: Float): Boolean =
    x in this.x..(this.x + width) && y in this.y..(this.y + height)

/** The vector ([dx], [dy]) rotated by [degrees], clockwise-positive like the canvas. */
private fun rotateVector(dx: Float, dy: Float, degrees: Float): Pair<Float, Float> {
    val radians = degrees * PI.toFloat() / 180f
    val cos = cos(radians)
    val sin = sin(radians)
    return (dx * cos - dy * sin) to (dx * sin + dy * cos)
}

/**
 * The slide-space point ([x], [y]) mapped into the element's unrotated frame
 * space: rotation happens around the frame's center, so hit-testing rotates the
 * point back instead of trying to rotate the frame.
 */
fun Element.toLocal(x: Float, y: Float): Pair<Float, Float> {
    val (dx, dy) = rotateVector(x - frame.centerX, y - frame.centerY, -rotation)
    return (frame.centerX + dx) to (frame.centerY + dy)
}

/** Inverse of [toLocal]: a point in the element's frame space, as drawn on the slide. */
fun Element.toSlide(x: Float, y: Float): Pair<Float, Float> {
    val (dx, dy) = rotateVector(x - frame.centerX, y - frame.centerY, rotation)
    return (frame.centerX + dx) to (frame.centerY + dy)
}

/**
 * Whether a point lands on the element as drawn: the frame test in the frame's
 * own space, so rotation is honoured; hit-testing the raw frame instead loses
 * the visible box the further rotation takes it from the unrotated one. Flips
 * need no counterpart: mirroring a rectangle about its own center leaves its
 * footprint where it was.
 */
fun Element.contains(x: Float, y: Float): Boolean {
    if (rotation == 0f) return frame.contains(x, y)
    val (localX, localY) = toLocal(x, y)
    return frame.contains(localX, localY)
}

fun resizeFrame(frame: Frame, handle: Handle, dx: Float, dy: Float, minSize: Float = 40f): Frame {
    var left = frame.x
    var top = frame.y
    var right = frame.x + frame.width
    var bottom = frame.y + frame.height

    if (handle.affectsLeft) left = (left + dx).coerceAtMost(right - minSize)
    if (handle.affectsRight) right = (right + dx).coerceAtLeast(left + minSize)
    if (handle.affectsTop) top = (top + dy).coerceAtMost(bottom - minSize)
    if (handle.affectsBottom) bottom = (bottom + dy).coerceAtLeast(top + minSize)

    return Frame(left, top, right - left, bottom - top)
}

/**
 * [resizeFrame] for a frame drawn at [rotation] degrees. The deltas arrive in
 * slide space, so they are rotated into the frame's own space before resizing,
 * and the result is shifted so the edge or corner opposite the handle stays put
 * on screen: resizing moves the frame's center, rotation pivots on the center,
 * so an uncorrected resize swings the whole element around the slide.
 */
fun resizeFrame(
    frame: Frame,
    rotation: Float,
    handle: Handle,
    dx: Float,
    dy: Float,
    minSize: Float = 40f,
): Frame {
    if (rotation == 0f) return resizeFrame(frame, handle, dx, dy, minSize)
    val (localDx, localDy) = rotateVector(dx, dy, -rotation)
    val resized = resizeFrame(frame, handle, localDx, localDy, minSize)
    val shiftX = resized.centerX - frame.centerX
    val shiftY = resized.centerY - frame.centerY
    val (drawnShiftX, drawnShiftY) = rotateVector(shiftX, shiftY, rotation)
    return resized.translate(drawnShiftX - shiftX, drawnShiftY - shiftY)
}

data class SnapResult(
    val frame: Frame,
    val snappedX: Boolean,
    val snappedY: Boolean,
)

/**
 * Snaps the frame's center to the slide center when within [threshold] doc units,
 * per the design's alignment guides.
 */
fun snapToSlideCenter(
    frame: Frame,
    slideWidth: Float = Document.SLIDE_WIDTH,
    slideHeight: Float = Document.SLIDE_HEIGHT,
    threshold: Float = 10f,
): SnapResult {
    var result = frame
    var snappedX = false
    var snappedY = false

    val slideCenterX = slideWidth / 2
    if (kotlin.math.abs(frame.centerX - slideCenterX) <= threshold) {
        result = result.copy(x = slideCenterX - frame.width / 2)
        snappedX = true
    }
    val slideCenterY = slideHeight / 2
    if (kotlin.math.abs(frame.centerY - slideCenterY) <= threshold) {
        result = result.copy(y = slideCenterY - frame.height / 2)
        snappedY = true
    }
    return SnapResult(result, snappedX, snappedY)
}

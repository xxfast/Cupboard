package io.github.xxfast.cupboard.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame

enum class Handle {
    TopLeft, Top, TopRight,
    Left, Right,
    BottomLeft, Bottom, BottomRight;

    val affectsLeft: Boolean get() = this == TopLeft || this == Left || this == BottomLeft
    val affectsRight: Boolean get() = this == TopRight || this == Right || this == BottomRight
    val affectsTop: Boolean get() = this == TopLeft || this == Top || this == TopRight
    val affectsBottom: Boolean get() = this == BottomLeft || this == Bottom || this == BottomRight
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

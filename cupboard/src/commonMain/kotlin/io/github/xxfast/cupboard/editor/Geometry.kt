package io.github.xxfast.cupboard.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.GuideAxis
import io.github.xxfast.cupboard.document.boundingFrame
import io.github.xxfast.cupboard.document.rotateVector
import kotlin.math.abs
import kotlin.math.roundToInt

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
 *
 * A group is its children, not its box: the empty space inside a group's bounds
 * is not the group, so a click there falls through to whatever is behind it.
 */
fun Element.contains(x: Float, y: Float): Boolean {
    val (localX: Float, localY: Float) = if (rotation == 0f) x to y else toLocal(x, y)
    return when (this) {
        is GroupElement -> children.any { it.contains(localX, localY) }
        else -> frame.contains(localX, localY)
    }
}

/** Which edge, or center line, [alignFrames] lines elements up on. */
enum class AlignEdge { Left, CenterX, Right, Top, CenterY, Bottom }

/** The axis [distributeFrames] spreads elements along. */
enum class Axis { Horizontal, Vertical }

/**
 * Lines [elements] up on [edge]: two or more align to their own bounding box, a
 * lone one aligns to the slide, which is what Keynote does and the only reading
 * of "align left" that means anything for a single element.
 *
 * Stored frames, not drawn ones, so a rotated element lines up by its unrotated
 * box. Known v1 simplification.
 *
 * Only the elements that actually moved come back, so an align that changes
 * nothing comes back empty and the caller can skip the edit outright.
 */
fun alignFrames(
    elements: List<Element>,
    edge: AlignEdge,
    slideWidth: Float = Document.SLIDE_WIDTH,
    slideHeight: Float = Document.SLIDE_HEIGHT,
): List<Element> {
    if (elements.isEmpty()) return emptyList()

    val bounds: Frame =
        if (elements.size == 1) Frame(0f, 0f, slideWidth, slideHeight)
        else boundingFrame(elements.map { it.frame })

    return elements.mapNotNull { element ->
        val frame: Frame = element.frame
        val aligned: Frame = when (edge) {
            AlignEdge.Left -> frame.copy(x = bounds.x)
            AlignEdge.CenterX -> frame.copy(x = bounds.centerX - frame.width / 2)
            AlignEdge.Right -> frame.copy(x = bounds.x + bounds.width - frame.width)
            AlignEdge.Top -> frame.copy(y = bounds.y)
            AlignEdge.CenterY -> frame.copy(y = bounds.centerY - frame.height / 2)
            AlignEdge.Bottom -> frame.copy(y = bounds.y + bounds.height - frame.height)
        }
        if (aligned == frame) null else element.update(frame = aligned)
    }
}

/**
 * Spreads [elements] along [axis] so the gaps between neighbours are equal. The
 * outermost two stay where they are: they are the span everything else shares.
 *
 * Fewer than three elements have no gap to equalize, so nothing comes back, and
 * as with [alignFrames] only the elements that moved do.
 */
fun distributeFrames(elements: List<Element>, axis: Axis): List<Element> {
    if (elements.size < 3) return emptyList()

    fun start(frame: Frame): Float = if (axis == Axis.Horizontal) frame.x else frame.y
    fun extent(frame: Frame): Float = if (axis == Axis.Horizontal) frame.width else frame.height

    val ordered: List<Element> = elements.sortedBy { start(it.frame) }
    val first: Frame = ordered.first().frame
    val last: Frame = ordered.last().frame
    val span: Float = start(last) + extent(last) - start(first)
    val gap: Float = (span - ordered.map { extent(it.frame) }.sum()) / (ordered.size - 1)

    var cursor: Float = start(first)
    return ordered.mapNotNull { element ->
        val frame: Frame = element.frame
        val spread: Frame =
            if (axis == Axis.Horizontal) frame.copy(x = cursor) else frame.copy(y = cursor)
        cursor += extent(frame) + gap
        if (spread == frame) null else element.update(frame = spread)
    }
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

/**
 * Where a snap line came from, which is both the setting that switches it on and
 * what the guide drawn over it is called.
 */
enum class SnapKind { Center, Edges, Objects, Guides }

/**
 * One line a dragged frame may settle on. [position] is an x for a
 * [GuideAxis.Vertical] line and a y for a horizontal one.
 */
data class SnapLine(val axis: GuideAxis, val position: Float, val kind: SnapKind)

data class SnapResult(
    val frame: Frame,
    /** The line each axis settled on, null when that axis came down free. */
    val snappedX: SnapLine?,
    val snappedY: SnapLine?,
)

/**
 * Slides [frame] onto whichever of [lines] is nearest, per axis, when one is
 * within [threshold] doc units.
 *
 * Three edges snap on each axis, not just the center: left, center and right
 * against every vertical line, top, middle and bottom against every horizontal
 * one. Nearest wins, so a frame near two lines settles on the one it is closest
 * to rather than on whichever came first in the list.
 */
fun snapFrame(frame: Frame, lines: List<SnapLine>, threshold: Float = 10f): SnapResult {
    // The shift that axis wants, and the line asking for it. Written as one pass
    // per axis so a tie is broken by distance alone, never by list order.
    fun settle(axis: GuideAxis, edges: List<Float>): Pair<Float, SnapLine?> {
        var shift = 0f
        var nearest: SnapLine? = null
        var best: Float = threshold

        for (line in lines) {
            if (line.axis != axis) continue
            for (edge in edges) {
                val distance: Float = line.position - edge
                if (abs(distance) > best) continue
                best = abs(distance)
                shift = distance
                nearest = line
            }
        }

        return shift to nearest
    }

    val (dx: Float, snappedX: SnapLine?) =
        settle(GuideAxis.Vertical, listOf(frame.centerX, frame.x, frame.x + frame.width))
    val (dy: Float, snappedY: SnapLine?) =
        settle(GuideAxis.Horizontal, listOf(frame.centerY, frame.y, frame.y + frame.height))

    return SnapResult(frame.translate(dx, dy), snappedX, snappedY)
}

/**
 * [snapFrame] against the slide's two center lines and nothing else: the narrow
 * case the canvas had before user guides, kept for callers that want only it.
 */
fun snapToSlideCenter(
    frame: Frame,
    slideWidth: Float = Document.SLIDE_WIDTH,
    slideHeight: Float = Document.SLIDE_HEIGHT,
    threshold: Float = 10f,
): SnapResult = snapFrame(
    frame = frame,
    lines = listOf(
        SnapLine(GuideAxis.Vertical, slideWidth / 2, SnapKind.Center),
        SnapLine(GuideAxis.Horizontal, slideHeight / 2, SnapKind.Center),
    ),
    threshold = threshold,
)

package io.github.xxfast.cupboard.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.GuideAxis
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.boundingFrame
import io.github.xxfast.cupboard.document.elementById
import io.github.xxfast.cupboard.document.isLocked
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

/**
 * How near two measurements have to be to read as the same one, in doc units.
 * Snapping lands frames on exact values, so this only has to absorb float noise.
 */
private const val Coincident: Float = 0.01f

/**
 * One gap the overlay marks as equal to another: a double-headed arrow from
 * [from] to [to] along [axis], drawn at [at] on the other axis, which is the
 * middle of where the two frames bounding the gap overlap. Keynote's relative
 * spacing indicator.
 */
data class SpacingMark(val axis: Axis, val from: Float, val to: Float, val at: Float)

/**
 * One frame whose extent along [axis] matches the frame being resized: a
 * double-headed arrow over its top (width) or beside its left (height).
 * Keynote's relative sizing indicator.
 */
data class SizeMark(val axis: Axis, val frame: Frame)

/** What a canvas drag knows that its elements don't: where the pointer is, in doc units, and what the gesture is. */
data class DragSample(val pointerX: Float, val pointerY: Float, val resizing: Boolean)

/**
 * Everything the canvas draws over a move or resize in flight, derived from the
 * frames it previewed: the alignment guides, the equal-spacing and equal-size
 * arrows, and the position (or size) readout that follows the pointer.
 *
 * [frame] is what the readout reports: the dragged element, or the selection's
 * bounding box when several move together, which is also what the guides are
 * measured against.
 */
data class AlignmentOverlay(
    val frame: Frame,
    val guides: List<SnapLine>,
    val spacing: List<SpacingMark>,
    val sizes: List<SizeMark>,
    val drag: DragSample,
)

/** [frame] shifted along [axis]. */
private fun Frame.along(axis: Axis, by: Float): Frame =
    if (axis == Axis.Horizontal) translate(by, 0f) else translate(0f, by)

private fun Frame.start(axis: Axis): Float = if (axis == Axis.Horizontal) x else y
private fun Frame.extent(axis: Axis): Float = if (axis == Axis.Horizontal) width else height
private fun Frame.end(axis: Axis): Float = start(axis) + extent(axis)

/** Whether the two frames share any run on the axis across [axis]: a gap only reads as a gap between neighbours. */
private fun Frame.besides(other: Frame, axis: Axis): Boolean {
    val cross: Axis = if (axis == Axis.Horizontal) Axis.Vertical else Axis.Horizontal
    return start(cross) < other.end(cross) && other.start(cross) < end(cross)
}

/** The middle of the run two neighbours share on the axis across [axis], where their gap's arrow sits. */
private fun overlapMiddle(a: Frame, b: Frame, axis: Axis): Float {
    val cross: Axis = if (axis == Axis.Horizontal) Axis.Vertical else Axis.Horizontal
    return (maxOf(a.start(cross), b.start(cross)) + minOf(a.end(cross), b.end(cross))) / 2
}

/** The nearest of [others] wholly before [frame] along [axis] and beside it, if any. */
private fun before(frame: Frame, others: List<Frame>, axis: Axis): Frame? =
    others.filter { it.end(axis) <= frame.start(axis) + Coincident && it.besides(frame, axis) }
        .maxByOrNull { it.end(axis) }

/** The nearest of [others] wholly after [frame] along [axis] and beside it, if any. */
private fun after(frame: Frame, others: List<Frame>, axis: Axis): Frame? =
    others.filter { it.start(axis) >= frame.end(axis) - Coincident && it.besides(frame, axis) }
        .minByOrNull { it.start(axis) }

/** One way [frame] could sit at an equal distance from its neighbours: the shift that gets it there and the two gaps it would match. */
private data class SpacingFit(val shift: Float, val marks: List<SpacingMark>)

/**
 * The equal-spacing positions near [frame] along [axis], Keynote's three: midway
 * between the neighbour on each side, as far past the one before it as that one
 * is from its own predecessor, and the mirror of that on the other side.
 */
private fun spacingFits(frame: Frame, others: List<Frame>, axis: Axis): List<SpacingFit> {
    val fits: MutableList<SpacingFit> = mutableListOf()
    val previous: Frame? = before(frame, others, axis)
    val next: Frame? = after(frame, others, axis)

    fun mark(a: Frame, b: Frame): SpacingMark =
        SpacingMark(axis, a.end(axis), b.start(axis), overlapMiddle(a, b, axis))

    if (previous != null && next != null) {
        val gap: Float = (next.start(axis) - previous.end(axis) - frame.extent(axis)) / 2
        val moved: Frame = frame.along(axis, previous.end(axis) + gap - frame.start(axis))
        fits += SpacingFit(moved.start(axis) - frame.start(axis), listOf(mark(previous, moved), mark(moved, next)))
    }
    if (previous != null) {
        val earlier: Frame? = before(previous, others, axis)
        if (earlier != null) {
            val gap: Float = previous.start(axis) - earlier.end(axis)
            val moved: Frame = frame.along(axis, previous.end(axis) + gap - frame.start(axis))
            fits += SpacingFit(moved.start(axis) - frame.start(axis), listOf(mark(earlier, previous), mark(previous, moved)))
        }
    }
    if (next != null) {
        val later: Frame? = after(next, others, axis)
        if (later != null) {
            val gap: Float = later.start(axis) - next.end(axis)
            val moved: Frame = frame.along(axis, next.start(axis) - gap - frame.end(axis))
            fits += SpacingFit(moved.start(axis) - frame.start(axis), listOf(mark(moved, next), mark(next, later)))
        }
    }
    return fits
}

/**
 * [snapFrame], then equal spacing on whichever axis came down free: a frame
 * within [threshold] of sitting as far from one neighbour as that neighbour is
 * from the next settles there, the way Keynote's relative spacing does. Lines
 * win over spacing, so a frame on a guide stays on it. [others] are the frames
 * left in place; [frame] is the one, or the bounding box of the ones, moving.
 */
fun snapMove(
    frame: Frame,
    lines: List<SnapLine>,
    others: List<Frame>,
    threshold: Float = 10f,
): SnapResult {
    val onLines: SnapResult = snapFrame(frame, lines, threshold)
    var settled: Frame = onLines.frame
    if (onLines.snappedX == null) {
        spacingFits(settled, others, Axis.Horizontal)
            .filter { abs(it.shift) <= threshold }
            .minByOrNull { abs(it.shift) }
            ?.let { settled = settled.along(Axis.Horizontal, it.shift) }
    }
    if (onLines.snappedY == null) {
        spacingFits(settled, others, Axis.Vertical)
            .filter { abs(it.shift) <= threshold }
            .minByOrNull { abs(it.shift) }
            ?.let { settled = settled.along(Axis.Vertical, it.shift) }
    }
    return SnapResult(settled, onLines.snappedX, onLines.snappedY)
}

/**
 * [frame] mid-resize by [handle], its width and height each pulled to the
 * nearest of [others]' when one is within [threshold]: Keynote's relative sizing.
 * The side opposite the handle stays put, so a left handle grows leftwards. A
 * corner handle matches both; an edge handle only the axis it moves.
 */
fun snapResize(frame: Frame, handle: Handle, others: List<Frame>, threshold: Float = 10f): Frame {
    var settled: Frame = frame
    if (handle.affectsLeft || handle.affectsRight) {
        val width: Float? = others.map { it.width }
            .filter { abs(it - frame.width) <= threshold }
            .minByOrNull { abs(it - frame.width) }
        if (width != null) {
            settled = if (handle.affectsLeft) settled.copy(x = settled.x + settled.width - width, width = width)
            else settled.copy(width = width)
        }
    }
    if (handle.affectsTop || handle.affectsBottom) {
        val height: Float? = others.map { it.height }
            .filter { abs(it - frame.height) <= threshold }
            .minByOrNull { abs(it - frame.height) }
        if (height != null) {
            settled = if (handle.affectsTop) settled.copy(y = settled.y + settled.height - height, height = height)
            else settled.copy(height = height)
        }
    }
    return settled
}

/**
 * What to draw over [frame] where a drag has left it: every line of [lines] one
 * of its edges or centers sits on, every gap it makes equal to a neighbouring
 * one, and, mid-resize, every one of [others] whose width or height it matches.
 * Pure recognition, no snapping: the frame is already where the gesture put it,
 * and the marks say what it coincides with there.
 */
fun alignmentOverlay(
    frame: Frame,
    lines: List<SnapLine>,
    others: List<Frame>,
    drag: DragSample,
): AlignmentOverlay {
    val xs: List<Float> = listOf(frame.x, frame.centerX, frame.x + frame.width)
    val ys: List<Float> = listOf(frame.y, frame.centerY, frame.y + frame.height)
    val guides: List<SnapLine> = lines
        .filter { line ->
            val edges: List<Float> = if (line.axis == GuideAxis.Vertical) xs else ys
            edges.any { abs(it - line.position) < Coincident }
        }
        // Two settings can offer the same line (the slide's center and an
        // element centered on it): one line drawn, whichever offered it first.
        .distinctBy { it.axis to it.position }

    val spacing: List<SpacingMark> =
        if (drag.resizing) emptyList()
        else Axis.entries.flatMap { axis ->
            spacingFits(frame, others, axis)
                .filter { abs(it.shift) < Coincident }
                .flatMap { it.marks }
        }.distinct()

    val sizes: List<SizeMark> =
        if (!drag.resizing) emptyList()
        else Axis.entries.flatMap { axis ->
            val matching: List<Frame> = others.filter { abs(it.extent(axis) - frame.extent(axis)) < Coincident }
            if (matching.isEmpty()) emptyList()
            else (listOf(frame) + matching).map { SizeMark(axis, it) }
        }

    return AlignmentOverlay(frame, guides, spacing, sizes, drag)
}

/**
 * The elements of [ids] on this slide moved [dx], [dy] together: an arrow key's
 * nudge. The selection moves as one box, which a plain shift already is, and a
 * group carries its children through its own `update`. Locked ones, a group's
 * lock included, stay where they are, the way a drag leaves them. No clamping,
 * since a drag doesn't either: an element may sit partly off the slide.
 */
fun Slide.nudged(ids: List<String>, dx: Float, dy: Float): List<Element> = ids
    .filter { id -> !isLocked(id) }
    .mapNotNull { id -> elementById(id) }
    .map { element -> element.update(frame = element.frame.translate(dx, dy)) }

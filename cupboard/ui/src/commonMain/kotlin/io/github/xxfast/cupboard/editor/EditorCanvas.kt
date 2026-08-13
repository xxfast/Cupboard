package io.github.xxfast.cupboard.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.canvas.ElementView
import io.github.xxfast.cupboard.canvas.LocalCanvasScale
import io.github.xxfast.cupboard.canvas.SlideSurface
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.Slide
import kotlin.math.abs
import kotlin.math.min

private val Accent = Color(0xFF7F52FF)
private val GuideYellow = Color(0xFFF5C518)

private sealed interface DragTarget {
    /**
     * A move of the whole selection. [draggedId] is the one under the pointer:
     * it is the frame that snaps, and the rest take the delta the snap settled on
     * so the selection keeps its shape.
     */
    data class Move(val draggedId: String) : DragTarget
    data class Resize(val elementId: String, val handle: Handle) : DragTarget
    /** A sweep over empty slide space. [start] is in doc units. */
    data class Marquee(val start: Offset) : DragTarget
}

/** The rectangle two corners span, in either order, so a rect always has positive extent. */
private fun rectBetween(a: Offset, b: Offset): Frame =
    Frame(min(a.x, b.x), min(a.y, b.y), abs(b.x - a.x), abs(b.y - a.y))

/**
 * The editable slide canvas: renders the slide plus a selection ring per selected
 * element, the 8 resize handles for a lone selection, drag-to-move/resize,
 * marquee selection, and center alignment guides with snapping. All hit-testing
 * happens in doc units (1dp == 1 unit inside [SlideSurface]).
 *
 * The canvas renders purely from its arguments: a drag streams [onPreviewElements]
 * (or [onPreviewMarquee]) per pointer sample through the state loop and draws
 * whatever comes back. It never renders from local gesture state, because snapshot
 * writes from pointer handlers proved unreliable for repaint on desktop.
 * [onUpdateElements] fires exactly once, on release: that's the undo and autosave
 * boundary.
 */
@Composable
fun EditorCanvas(
    slide: Slide,
    selectedElementIds: List<String>,
    marquee: Frame?,
    onSelectElement: (String?) -> Unit,
    onToggleElementSelection: (String) -> Unit,
    onPreviewMarquee: (Frame) -> Unit,
    onEndMarquee: () -> Unit,
    onUpdateElements: (List<Element>) -> Unit,
    onPreviewElements: (List<Element>) -> Unit,
    onPreviewCancel: () -> Unit,
    modifier: Modifier = Modifier,
    zoom: Float? = null,
) {
    val currentSlide by rememberUpdatedState(slide)
    val currentSelection by rememberUpdatedState(selectedElementIds)
    var guideX by remember { mutableStateOf(false) }
    var guideY by remember { mutableStateOf(false) }
    // Cursor only. These two are written from pointer handlers, which the canvas
    // may not do for anything it draws, but the cursor is the shell's to paint.
    var hovered by remember { mutableStateOf<ResizeDirection?>(null) }
    var resizing by remember { mutableStateOf<ResizeDirection?>(null) }
    // Whether the press in flight had shift down, latched by the click loop and
    // read by the drag handler (whose callbacks never see modifiers): a shift
    // gesture edits the selection and nothing else, so drags sit it out.
    var shiftDown by remember { mutableStateOf(false) }

    SlideSurface(modifier, zoom = zoom) {
        for (element in slide.elements) ElementView(element)

        // Editing affordances hold constant screen size at any zoom: authored
        // sizes are divided by the canvas scale, positions stay in doc units.
        val canvasScale = LocalCanvasScale.current
        val docDensity = LocalDensity.current.density
        fun toDoc(position: Offset) = Offset(position.x / docDensity, position.y / docDensity)

        fun elementAt(p: Offset): Element? =
            currentSlide.elements.lastOrNull { it.contains(p.x, p.y) }

        fun selectedElements(): List<Element> = currentSelection.mapNotNull { id ->
            currentSlide.elements.firstOrNull { it.id == id }
        }

        // Handles belong to a lone selection: with two or more selected there is
        // no one frame to resize, so every drag there is a move.
        fun soleSelected(): Element? =
            currentSelection.singleOrNull()?.let { id ->
                currentSlide.elements.firstOrNull { it.id == id }
            }

        // A locked element has no handles to hit: it neither resizes nor moves.
        // Selection still happens either way, so the inspector can reach it to
        // unlock it. Handles live where they are drawn, so the pointer maps into
        // the element's own space before the test, and [position] converts to doc
        // units first or the tolerance would shrink with the display's density.
        // [tolerance] is in screen dp: hovering is the more forgiving of the two,
        // so the cursor hints just before the grab starts working, never after.
        fun handleAt(position: Offset, tolerance: Float = 8f): Handle? {
            val element = soleSelected()?.takeIf { !it.locked } ?: return null
            val p = toDoc(position)
            val (localX, localY) = element.toLocal(p.x, p.y)
            return hitTestHandle(element.frame, localX, localY, tolerance / canvasScale)
        }

        // The dragged handle wins over the hovered one: mid-resize the frame
        // moves under a still pointer, and the cursor must not flicker with it.
        val cursors: ResizeCursors? = LocalResizeCursors.current
        val cursorModifier: Modifier = cursors?.cursor(resizing ?: hovered) ?: Modifier

        // Input overlay covering the whole slide
        Box(
            Modifier
                .fillMaxSize()
                .then(cursorModifier)
                .pointerInput(Unit) {
                    // Hover for the cursor, and the click that selects. One loop
                    // because the click needs the modifiers of its own press,
                    // which detectTapGestures doesn't hand out. Nothing is
                    // consumed here, so the drag handler below still sees every
                    // event; a press that becomes a drag has its changes consumed
                    // there, which is what takes it out of the running as a click.
                    val slop: Float = viewConfiguration.touchSlop
                    awaitPointerEventScope {
                        var pressedAt: Offset? = null
                        while (true) {
                            val event: PointerEvent = awaitPointerEvent()
                            val position: Offset? = event.changes.firstOrNull()?.position
                            if (event.changes.any { it.isConsumed }) pressedAt = null

                            when (event.type) {
                                // A shift press settles at press time, the way
                                // editors do: toggling on release let a jitter
                                // past the drag slop swallow the click, and the
                                // drag it became would replace the selection the
                                // user was building. Shift over empty space is a
                                // no-op, not a clear: a near-miss while adding
                                // must not cost the whole selection. Plain
                                // clicks keep deciding on release, where a click
                                // and a drag can still be told apart.
                                PointerEventType.Press -> {
                                    shiftDown = event.keyboardModifiers.isShiftPressed
                                    if (shiftDown) {
                                        position
                                            ?.let { elementAt(toDoc(it)) }
                                            ?.let { onToggleElementSelection(it.id) }
                                        pressedAt = null
                                    } else {
                                        pressedAt = position
                                    }
                                }

                                PointerEventType.Move -> {
                                    hovered = position
                                        ?.let { handleAt(it, tolerance = 12f) }
                                        ?.let { resizeDirection(it, soleSelected()?.rotation ?: 0f) }
                                }

                                PointerEventType.Release -> {
                                    val start: Offset? = pressedAt
                                    pressedAt = null
                                    // A click, not a drag: the pointer came back
                                    // up where it went down.
                                    if (start != null && position != null &&
                                        (position - start).getDistance() <= slop
                                    ) {
                                        val hit: Element? = elementAt(toDoc(start))
                                        if (hit == null) onSelectElement(null)
                                        else onSelectElement(hit.id)
                                    }
                                }

                                PointerEventType.Exit -> hovered = null
                                else -> {}
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    // Plain vars, not snapshot state: each sample recomputes from
                    // the frames the gesture started with, so every emission
                    // carries the whole gesture and a slow roundtrip can delay a
                    // repaint but never lose movement.
                    var target: DragTarget? = null
                    var startFrames: Map<String, Frame> = emptyMap()
                    var startFrame: Frame? = null
                    var startRotation = 0f
                    var totalDx = 0f
                    var totalDy = 0f
                    var marqueeOrigin: Offset? = null
                    fun reset() {
                        target = null
                        startFrames = emptyMap()
                        startFrame = null
                        startRotation = 0f
                        totalDx = 0f
                        totalDy = 0f
                        marqueeOrigin = null
                        guideX = false
                        guideY = false
                        resizing = null
                    }

                    // The whole selection moved by this gesture: the dragged
                    // element snaps to the slide center and hands the delta it
                    // settled on to the rest. The guides ride that same snap.
                    fun moved(draggedId: String): List<Element> {
                        val start: Frame = startFrames[draggedId] ?: return emptyList()
                        val snapped: SnapResult =
                            snapToSlideCenter(start.translate(totalDx, totalDy))
                        guideX = snapped.snappedX
                        guideY = snapped.snappedY
                        val dx: Float = snapped.frame.x - start.x
                        val dy: Float = snapped.frame.y - start.y
                        return currentSlide.elements
                            .filter { it.id in startFrames }
                            .map { it.update(frame = startFrames.getValue(it.id).translate(dx, dy)) }
                    }

                    fun resized(t: DragTarget.Resize): List<Element> {
                        val start: Frame = startFrame ?: return emptyList()
                        val element: Element = currentSlide.elements
                            .firstOrNull { it.id == t.elementId }
                            ?: return emptyList()
                        val frame: Frame =
                            resizeFrame(start, startRotation, t.handle, totalDx, totalDy)
                        return listOf(element.update(frame = frame))
                    }

                    detectDragGestures(
                        onDragStart = { position ->
                            // The selection was already edited at press time.
                            if (shiftDown) return@detectDragGestures
                            val p = toDoc(position)
                            val sole: Element? = soleSelected()
                            val handle: Handle? = handleAt(position)
                            val hit: Element? = elementAt(p)
                            target = when {
                                sole != null && handle != null -> {
                                    startFrame = sole.frame
                                    startRotation = sole.rotation
                                    DragTarget.Resize(sole.id, handle)
                                }

                                // Empty space sweeps a marquee instead.
                                hit == null -> DragTarget.Marquee(p)

                                else -> {
                                    // A press on something outside the selection
                                    // takes the selection with it, exactly as a
                                    // plain click would; a press inside it drags
                                    // everything already selected.
                                    val selected: Boolean = hit.id in currentSelection
                                    if (!selected) onSelectElement(hit.id)
                                    val movers: List<Element> =
                                        if (selected) selectedElements() else listOf(hit)
                                    startFrames = movers
                                        .filter { !it.locked }
                                        .associate { it.id to it.frame }
                                    if (hit.locked) null else DragTarget.Move(hit.id)
                                }
                            }
                            resizing = (target as? DragTarget.Resize)
                                ?.let { resizeDirection(it.handle, startRotation) }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val delta = Offset(dragAmount.x / docDensity, dragAmount.y / docDensity)
                            totalDx += delta.x
                            totalDy += delta.y
                            when (val t = target) {
                                is DragTarget.Move -> onPreviewElements(moved(t.draggedId))
                                is DragTarget.Resize -> onPreviewElements(resized(t))

                                is DragTarget.Marquee -> {
                                    // The rectangle starts at the press, not at
                                    // where the slop was crossed: the first
                                    // sample carries that gap, so it comes back
                                    // off the start position once.
                                    val origin: Offset = marqueeOrigin
                                        ?: (t.start - delta).also { marqueeOrigin = it }
                                    val corner = Offset(origin.x + totalDx, origin.y + totalDy)
                                    onPreviewMarquee(rectBetween(origin, corner))
                                }

                                null -> {}
                            }
                        },
                        // Commit only when this gesture had a target: an empty
                        // drag must never publish anything.
                        onDragEnd = {
                            when (val t = target) {
                                is DragTarget.Move -> onUpdateElements(moved(t.draggedId))
                                is DragTarget.Resize -> onUpdateElements(resized(t))
                                // What a half-swept marquee caught, it keeps.
                                is DragTarget.Marquee -> onEndMarquee()
                                null -> {}
                            }
                            reset()
                        },
                        // A cancelled gesture never happened: the presenter rolls
                        // the document back to its pre-gesture state. A marquee
                        // changed no document, so it only has its rectangle to
                        // put away.
                        onDragCancel = {
                            when (target) {
                                is DragTarget.Marquee -> onEndMarquee()
                                null -> {}
                                else -> onPreviewCancel()
                            }
                            reset()
                        },
                    )
                }
        )

        // Selection rings, one per selected element; handles only for a lone one.
        val selected: List<Element> = slide.elements.filter { it.id in selectedElementIds }
        for (element in selected) {
            SelectionOverlay(element, canvasScale, handles = selected.size == 1)
        }

        // The marquee, drawn from state rather than from the gesture's own vars.
        if (marquee != null) MarqueeOverlay(marquee, canvasScale)

        // Alignment guides
        if (guideX) VerticalCenterGuide(canvasScale)
        if (guideY) HorizontalCenterGuide(canvasScale)
    }
}

/**
 * The selection ring, and the 8 handles when [handles] and [element] is unlocked:
 * a lock means what it says. Ring and handles both ride the element's rotation,
 * the handles by sitting at their corner's drawn position and turning with it.
 */
@Composable
private fun SelectionOverlay(element: Element, scale: Float, handles: Boolean) {
    val frame = element.frame
    Box(
        Modifier
            .offset(frame.x.dp, frame.y.dp)
            .size(frame.width.dp, frame.height.dp)
            .graphicsLayer {
                rotationZ = element.rotation
                transformOrigin = TransformOrigin.Center
            }
            .border((1.5f / scale).dp, Accent)
    )
    if (!handles || element.locked) return

    val handleSize = 9f / scale
    for ((_, position) in handlePositions(frame)) {
        val (hx, hy) = position
        val (drawnX, drawnY) = element.toSlide(hx, hy)
        Box(
            Modifier
                .offset((drawnX - handleSize / 2).dp, (drawnY - handleSize / 2).dp)
                .size(handleSize.dp)
                .graphicsLayer { rotationZ = element.rotation }
                .background(Color.White, RoundedCornerShape((2f / scale).dp))
                .border((1.5f / scale).dp, Accent, RoundedCornerShape((2f / scale).dp))
        )
    }
}

/** The sweep rectangle: a hairline accent border over a wash of the same accent. */
@Composable
private fun MarqueeOverlay(rect: Frame, scale: Float) {
    Box(
        Modifier
            .offset(rect.x.dp, rect.y.dp)
            .size(rect.width.dp, rect.height.dp)
            .background(Accent.copy(alpha = 0.1f))
            .border((1f / scale).dp, Accent)
    )
}

@Composable
private fun VerticalCenterGuide(scale: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val x = size.width / 2
        drawLine(
            color = GuideYellow,
            start = Offset(x, -(12f / scale).dp.toPx()),
            end = Offset(x, size.height + (12f / scale).dp.toPx()),
            strokeWidth = (1f / scale).dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf((5f / scale).dp.toPx(), (4f / scale).dp.toPx())),
        )
    }
    Box(
        Modifier.offset((Document.SLIDE_WIDTH / 2 - 24f / scale).dp, (8f / scale).dp)
            .background(GuideYellow, RoundedCornerShape((3f / scale).dp))
            .padding(horizontal = (6f / scale).dp, vertical = (1f / scale).dp)
    ) {
        Text("center x", color = Color(0xFF17181C), fontSize = (10f / scale).sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun HorizontalCenterGuide(scale: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val y = size.height / 2
        drawLine(
            color = GuideYellow,
            start = Offset(-(12f / scale).dp.toPx(), y),
            end = Offset(size.width + (12f / scale).dp.toPx(), y),
            strokeWidth = (1f / scale).dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf((5f / scale).dp.toPx(), (4f / scale).dp.toPx())),
        )
    }
    Box(
        Modifier.offset((8f / scale).dp, (Document.SLIDE_HEIGHT / 2 - 18f / scale).dp)
            .background(GuideYellow, RoundedCornerShape((3f / scale).dp))
            .padding(horizontal = (6f / scale).dp, vertical = (1f / scale).dp)
    ) {
        Text("center y", color = Color(0xFF17181C), fontSize = (10f / scale).sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

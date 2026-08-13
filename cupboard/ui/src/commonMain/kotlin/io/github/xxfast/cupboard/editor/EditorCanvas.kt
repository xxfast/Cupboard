package io.github.xxfast.cupboard.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import io.github.xxfast.cupboard.canvas.ElementView
import io.github.xxfast.cupboard.canvas.LocalCanvasScale
import io.github.xxfast.cupboard.canvas.SlideSurface
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.Slide

private val Accent = Color(0xFF7F52FF)
private val GuideYellow = Color(0xFFF5C518)

private sealed interface DragTarget {
    data class Move(val elementId: String) : DragTarget
    data class Resize(val elementId: String, val handle: Handle) : DragTarget
}

/**
 * The editable slide canvas: renders the slide plus selection ring, 8 resize
 * handles, drag-to-move/resize, and center alignment guides with snapping.
 * All hit-testing happens in doc units (1dp == 1 unit inside [SlideSurface]).
 *
 * The canvas renders purely from [slide]: a drag streams [onSlidePreview] per
 * pointer sample through the state loop and draws whatever comes back. It never
 * renders from local gesture state, because snapshot writes from pointer
 * handlers proved unreliable for repaint on desktop. [onSlideChange] fires
 * exactly once, on release: that's the undo and autosave boundary.
 */
@Composable
fun EditorCanvas(
    slide: Slide,
    selectedElementId: String?,
    onSelectElement: (String?) -> Unit,
    onSlideChange: (Slide) -> Unit,
    onSlidePreview: (Slide) -> Unit,
    onPreviewCancel: () -> Unit,
    modifier: Modifier = Modifier,
    zoom: Float? = null,
) {
    val currentSlide by rememberUpdatedState(slide)
    val currentSelection by rememberUpdatedState(selectedElementId)
    var guideX by remember { mutableStateOf(false) }
    var guideY by remember { mutableStateOf(false) }

    SlideSurface(modifier, zoom = zoom) {
        for (element in slide.elements) ElementView(element)

        // Editing affordances hold constant screen size at any zoom: authored
        // sizes are divided by the canvas scale, positions stay in doc units.
        val canvasScale = LocalCanvasScale.current
        val docDensity = LocalDensity.current.density
        fun toDoc(position: Offset) = Offset(position.x / docDensity, position.y / docDensity)

        fun elementAt(p: Offset): Element? =
            currentSlide.elements.lastOrNull { it.contains(p.x, p.y) }

        fun placed(elementId: String, frame: Frame): Slide = currentSlide.copy(
            elements = currentSlide.elements.map {
                if (it.id == elementId) it.update(frame = frame) else it
            }
        )

        // Input overlay covering the whole slide
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        onSelectElement(elementAt(toDoc(position))?.id)
                    }
                }
                .pointerInput(Unit) {
                    // Plain vars, not snapshot state: each sample accumulates
                    // into an absolute frame, so every emission carries the
                    // whole gesture and a slow roundtrip can delay a repaint
                    // but never lose movement.
                    var target: DragTarget? = null
                    var draggedFrame: Frame? = null
                    var startFrame: Frame? = null
                    var startRotation = 0f
                    var totalDx = 0f
                    var totalDy = 0f
                    fun reset() {
                        target = null
                        draggedFrame = null
                        startFrame = null
                        startRotation = 0f
                        totalDx = 0f
                        totalDy = 0f
                        guideX = false
                        guideY = false
                    }
                    detectDragGestures(
                        onDragStart = { position ->
                            val p = toDoc(position)
                            val selected = currentSlide.elements.firstOrNull { it.id == currentSelection }
                            // A locked element has no handles to hit: it neither
                            // resizes nor moves. Selection still happens either
                            // way, so the inspector can reach it to unlock it.
                            // Handles live where they are drawn, so the pointer
                            // maps into the element's own space before the test.
                            val handle = selected
                                ?.takeIf { !it.locked }
                                ?.let { element ->
                                    val (lx, ly) = element.toLocal(p.x, p.y)
                                    hitTestHandle(element.frame, lx, ly, tolerance = 8f / canvasScale)
                                }
                            target = when {
                                selected != null && handle != null -> DragTarget.Resize(selected.id, handle)
                                else -> elementAt(p)
                                    ?.also { onSelectElement(it.id) }
                                    ?.takeIf { !it.locked }
                                    ?.let { DragTarget.Move(it.id) }
                            }
                            val targetId = when (val t = target) {
                                is DragTarget.Move -> t.elementId
                                is DragTarget.Resize -> t.elementId
                                null -> null
                            }
                            val element = currentSlide.elements.firstOrNull { it.id == targetId }
                            draggedFrame = element?.frame
                            startFrame = element?.frame
                            startRotation = element?.rotation ?: 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val delta = Offset(dragAmount.x / docDensity, dragAmount.y / docDensity)
                            when (val t = target) {
                                is DragTarget.Move -> {
                                    // The raw frame accumulates, the snapped one
                                    // is emitted: snapping must not compound.
                                    val moved = draggedFrame?.translate(delta.x, delta.y)
                                        ?: return@detectDragGestures
                                    draggedFrame = moved
                                    val snapped = snapToSlideCenter(moved)
                                    guideX = snapped.snappedX
                                    guideY = snapped.snappedY
                                    onSlidePreview(placed(t.elementId, snapped.frame))
                                }
                                is DragTarget.Resize -> {
                                    val start = startFrame ?: return@detectDragGestures
                                    totalDx += delta.x
                                    totalDy += delta.y
                                    // Always from the start frame with running
                                    // totals: same math as iterating, and the
                                    // min-size clamp holds across the gesture.
                                    onSlidePreview(placed(t.elementId, resizeFrame(start, startRotation, t.handle, totalDx, totalDy)))
                                }
                                null -> {}
                            }
                        },
                        // Commit only when this gesture had a target: an empty
                        // drag must never publish anything.
                        onDragEnd = {
                            when (val t = target) {
                                is DragTarget.Move -> draggedFrame?.let { frame ->
                                    onSlideChange(placed(t.elementId, snapToSlideCenter(frame).frame))
                                }
                                is DragTarget.Resize -> startFrame?.let { start ->
                                    onSlideChange(placed(t.elementId, resizeFrame(start, startRotation, t.handle, totalDx, totalDy)))
                                }
                                null -> {}
                            }
                            reset()
                        },
                        // A cancelled gesture never happened: the presenter
                        // rolls the document back to its pre-gesture state.
                        onDragCancel = {
                            if (target != null) onPreviewCancel()
                            reset()
                        },
                    )
                }
        )

        // Selection ring + handles
        slide.elements.firstOrNull { it.id == selectedElementId }?.let { selected ->
            SelectionOverlay(selected, canvasScale)
        }

        // Alignment guides
        if (guideX) VerticalCenterGuide(canvasScale)
        if (guideY) HorizontalCenterGuide(canvasScale)
    }
}

/**
 * The selection ring, and the 8 handles unless [element] is locked: a lock
 * means what it says. Ring and handles both ride the element's rotation, the
 * handles by sitting at their corner's drawn position and turning with it.
 */
@Composable
private fun SelectionOverlay(element: Element, scale: Float) {
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
    if (element.locked) return

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

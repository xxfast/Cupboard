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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
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
 * A drag in progress is this composable's own business: the moving element is
 * rendered from a local [preview] slide and [onSlideChange] fires exactly once,
 * on release, with the final slide. The event stream upstream then carries
 * intent-sized facts (one edit per gesture), which is also one autosave write
 * and, later, one undo entry per gesture instead of one per pointer sample.
 */
@Composable
fun EditorCanvas(
    slide: Slide,
    selectedElementId: String?,
    onSelectElement: (String?) -> Unit,
    onSlideChange: (Slide) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The canvas trusts its own hands first: gestures render from local state
    // immediately and [onSlideChange] is write-behind (persistence, undo), so
    // no visual behavior ever waits on the state roundtrip. An incoming slide
    // only takes over when it differs from what we last sent (undo, external
    // edits), which keeps the canvas correct without being dependent.
    var preview: Slide? by remember(slide.id) { mutableStateOf(null) }
    var committed: Slide? by remember(slide.id) { mutableStateOf(null) }
    var lastSent: Slide? by remember(slide.id) { mutableStateOf(null) }
    remember(slide) {
        if (lastSent != null && slide != lastSent) {
            committed = null
            lastSent = null
        }
    }

    // Everything below draws and hit-tests against this, so the gesture and what
    // you see stay the same thing.
    val shownSlide: Slide = preview ?: committed ?: slide

    val currentSlide by rememberUpdatedState(shownSlide)
    val currentSelection by rememberUpdatedState(selectedElementId)
    var guideX by remember { mutableStateOf(false) }
    var guideY by remember { mutableStateOf(false) }

    SlideSurface(modifier) {
        for (element in shownSlide.elements) ElementView(element)

        // Editing affordances hold constant screen size at any zoom: authored
        // sizes are divided by the canvas scale, positions stay in doc units.
        val canvasScale = LocalCanvasScale.current
        val docDensity = LocalDensity.current.density
        fun toDoc(position: Offset) = Offset(position.x / docDensity, position.y / docDensity)

        fun elementAt(p: Offset): Element? =
            currentSlide.elements.lastOrNull { it.frame.contains(p.x, p.y) }

        // Local only: the gesture edits the preview, release publishes it.
        fun previewUpdate(element: Element, frame: Frame) {
            preview = currentSlide.copy(
                elements = currentSlide.elements.map {
                    if (it.id == element.id) it.withFrame(frame) else it
                }
            )
        }

        fun commit() {
            preview?.let { done ->
                committed = done
                lastSent = done
                onSlideChange(done)
            }
            preview = null
            guideX = false
            guideY = false
        }

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
                    var target: DragTarget? = null
                    detectDragGestures(
                        onDragStart = { position ->
                            val p = toDoc(position)
                            val selected = currentSlide.elements.firstOrNull { it.id == currentSelection }
                            val handle = selected?.let {
                                hitTestHandle(it.frame, p.x, p.y, tolerance = 8f / canvasScale)
                            }
                            target = when {
                                selected != null && handle != null -> DragTarget.Resize(selected.id, handle)
                                else -> elementAt(p)?.also { onSelectElement(it.id) }?.let { DragTarget.Move(it.id) }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val t = target ?: return@detectDragGestures
                            val delta = Offset(dragAmount.x / docDensity, dragAmount.y / docDensity)
                            val targetId = when (t) {
                                is DragTarget.Move -> t.elementId
                                is DragTarget.Resize -> t.elementId
                            }
                            val element = currentSlide.elements.firstOrNull { it.id == targetId }
                                ?: return@detectDragGestures
                            when (t) {
                                is DragTarget.Move -> {
                                    val moved = element.frame.translate(delta.x, delta.y)
                                    val snapped = snapToSlideCenter(moved)
                                    guideX = snapped.snappedX
                                    guideY = snapped.snappedY
                                    previewUpdate(element, snapped.frame)
                                }
                                is DragTarget.Resize -> {
                                    previewUpdate(element, resizeFrame(element.frame, t.handle, delta.x, delta.y))
                                }
                            }
                        },
                        // Publish only when this gesture had a target: an empty
                        // drag must never commit a leftover preview.
                        onDragEnd = {
                            val hadTarget = target != null
                            target = null
                            if (hadTarget) commit() else preview = null
                        },
                        // A cancelled gesture never happened: drop the preview
                        // and let the element fall back to the committed slide.
                        onDragCancel = { target = null; preview = null; guideX = false; guideY = false },
                    )
                }
        )

        // Selection ring + handles
        shownSlide.elements.firstOrNull { it.id == selectedElementId }?.let { selected ->
            SelectionOverlay(selected.frame, canvasScale)
        }

        // Alignment guides
        if (guideX) VerticalCenterGuide(canvasScale)
        if (guideY) HorizontalCenterGuide(canvasScale)
    }
}

@Composable
private fun SelectionOverlay(frame: Frame, scale: Float) {
    Box(
        Modifier
            .offset(frame.x.dp, frame.y.dp)
            .size(frame.width.dp, frame.height.dp)
            .border((1.5f / scale).dp, Accent)
    )
    val handleSize = 9f / scale
    for ((_, position) in handlePositions(frame)) {
        val (hx, hy) = position
        Box(
            Modifier
                .offset((hx - handleSize / 2).dp, (hy - handleSize / 2).dp)
                .size(handleSize.dp)
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

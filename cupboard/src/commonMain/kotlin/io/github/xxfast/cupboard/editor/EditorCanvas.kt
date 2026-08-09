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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import io.github.xxfast.cupboard.canvas.ElementView
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
 */
@Composable
fun EditorCanvas(
    slide: Slide,
    selectedElementId: String?,
    onSelectElement: (String?) -> Unit,
    onSlideChange: (Slide) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSlide by rememberUpdatedState(slide)
    val currentSelection by rememberUpdatedState(selectedElementId)
    var guideX by remember { mutableStateOf(false) }
    var guideY by remember { mutableStateOf(false) }

    SlideSurface(modifier) {
        for (element in slide.elements) ElementView(element)

        val docDensity = LocalDensity.current.density
        fun toDoc(position: Offset) = Offset(position.x / docDensity, position.y / docDensity)

        fun elementAt(p: Offset): Element? =
            currentSlide.elements.lastOrNull { it.frame.contains(p.x, p.y) }

        fun update(element: Element, frame: Frame) {
            onSlideChange(
                currentSlide.copy(
                    elements = currentSlide.elements.map {
                        if (it.id == element.id) it.withFrame(frame) else it
                    }
                )
            )
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
                            val handle = selected?.let { hitTestHandle(it.frame, p.x, p.y) }
                            target = when {
                                handle != null && selected != null -> DragTarget.Resize(selected.id, handle)
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
                                    update(element, snapped.frame)
                                }
                                is DragTarget.Resize -> {
                                    update(element, resizeFrame(element.frame, t.handle, delta.x, delta.y))
                                }
                            }
                        },
                        onDragEnd = { target = null; guideX = false; guideY = false },
                        onDragCancel = { target = null; guideX = false; guideY = false },
                    )
                }
        )

        // Selection ring + handles
        slide.elements.firstOrNull { it.id == selectedElementId }?.let { selected ->
            SelectionOverlay(selected.frame)
        }

        // Alignment guides
        if (guideX) VerticalCenterGuide()
        if (guideY) HorizontalCenterGuide()
    }
}

@Composable
private fun SelectionOverlay(frame: Frame) {
    Box(
        Modifier
            .offset(frame.x.dp, frame.y.dp)
            .size(frame.width.dp, frame.height.dp)
            .border(1.5.dp, Accent)
    )
    for ((_, position) in handlePositions(frame)) {
        val (hx, hy) = position
        Box(
            Modifier
                .offset((hx - 4.5f).dp, (hy - 4.5f).dp)
                .size(9.dp)
                .background(Color.White, RoundedCornerShape(2.dp))
                .border(1.5.dp, Accent, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun VerticalCenterGuide() {
    Canvas(Modifier.fillMaxSize()) {
        val x = size.width / 2
        drawLine(
            color = GuideYellow,
            start = Offset(x, -12.dp.toPx()),
            end = Offset(x, size.height + 12.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
    }
    Box(
        Modifier.offset((Document.SLIDE_WIDTH / 2 - 24).dp, 8.dp)
            .background(GuideYellow, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text("center x", color = Color(0xFF17181C), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun HorizontalCenterGuide() {
    Canvas(Modifier.fillMaxSize()) {
        val y = size.height / 2
        drawLine(
            color = GuideYellow,
            start = Offset(-12.dp.toPx(), y),
            end = Offset(size.width + 12.dp.toPx(), y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
    }
    Box(
        Modifier.offset(8.dp, (Document.SLIDE_HEIGHT / 2 - 18).dp)
            .background(GuideYellow, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text("center y", color = Color(0xFF17181C), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

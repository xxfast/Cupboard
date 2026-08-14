package io.github.xxfast.cupboard.screens.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.canvas.SlideThumbnail
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTokens

/**
 * The 224dp slide navigator: nested thumbnails with disclosure chevrons,
 * selection ring on the selected slide. Colors come from [LocalChromeTokens];
 * [thumbnailRadius] is the theme's thumbR (Linux 10dp).
 *
 * [onContextClick] is a right-click on a row, with the row's id and where the
 * press landed in window coordinates: this panel only reports it, what opens
 * there is the screen's business.
 */
@Composable
fun EditorNavigator(
    document: Document,
    entries: List<OutlineEntry>,
    selectedSlideId: String,
    onSelectSlide: (String) -> Unit,
    onToggleCollapsed: (String) -> Unit,
    thumbnailRadius: Dp,
    onContextClick: (slideId: String, positionInWindow: Offset) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(LocalChromeTokens.current.panel)
            .verticalScroll(rememberScrollState())
            // Rows carry their own 6.dp bottom gap (so it collapses away with
            // them); the last one plus this padding lands on the design's 14.
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
    ) {
        for (entry in entries) {
            val slide: Slide = document.slides[entry.slideIndex]
            val selected: Boolean = entry.slideId == selectedSlideId
            // Hidden rows stay in the tree so collapsing animates them out.
            AnimatedVisibility(
                visible = entry.visible,
                enter = expandVertically(tween(durationMillis = 140)) + fadeIn(tween(durationMillis = 140)),
                exit = shrinkVertically(tween(durationMillis = 140)) + fadeOut(tween(durationMillis = 140)),
            ) {
                NavigatorRow(
                    slide = slide,
                    entry = entry,
                    selected = selected,
                    thumbnailRadius = thumbnailRadius,
                    onSelectSlide = onSelectSlide,
                    onToggleCollapsed = onToggleCollapsed,
                    onContextClick = onContextClick,
                )
            }
        }
    }
}

@Composable
private fun NavigatorRow(
    slide: Slide,
    entry: OutlineEntry,
    selected: Boolean,
    thumbnailRadius: Dp,
    onSelectSlide: (String) -> Unit,
    onToggleCollapsed: (String) -> Unit,
    onContextClick: (slideId: String, positionInWindow: Offset) -> Unit,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    // Each row places itself, so the press it reports is already in the
    // coordinates a menu anywhere in the window can be hung from.
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) tokens.rowHov else Color.Transparent)
            .onGloballyPositioned { coordinates = it }
            .pointerInput(entry.slideId) {
                awaitPointerEventScope {
                    while (true) {
                        // Classified the way the canvas classifies one: the chord
                        // is all a common pointer event carries, and a press with
                        // the primary held is a left click whatever the stale rest
                        // of the chord says, so a bit left down by a native menu's
                        // tracking loop can never reclassify ordinary clicks.
                        val event: PointerEvent = awaitPointerEvent(PointerEventPass.Initial)
                        val secondary: Boolean = event.type == PointerEventType.Press &&
                            event.buttons.isSecondaryPressed &&
                            !event.buttons.isPrimaryPressed
                        if (!secondary) continue

                        val change: PointerInputChange = event.changes.firstOrNull() ?: continue
                        // Taken on the initial pass so the row's clickable and the
                        // chevron's never see it: a right-click starts nothing,
                        // it reports what it hit and settles there.
                        change.consume()
                        onContextClick(
                            entry.slideId,
                            coordinates?.localToWindow(change.position) ?: change.position,
                        )
                    }
                }
            }
            .clickable { onSelectSlide(entry.slideId) }
            .padding(top = 5.dp, bottom = 5.dp, end = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Fixed gutter, outside the indent, so every chevron shares one left rail.
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(16.dp)
                .clip(RoundedCornerShape(3.dp))
                .let {
                    if (entry.hasChildren) it.clickable { onToggleCollapsed(entry.slideId) }
                    else it
                },
            contentAlignment = Alignment.Center,
        ) {
            if (entry.hasChildren) DisclosureChevron(collapsed = entry.collapsed)
        }
        Row(
            modifier = Modifier.padding(start = (entry.depth * 12).dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${entry.slideIndex + 1}",
                color = tokens.subtle,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(14.dp).padding(top = 2.dp),
            )
            SlideThumbnail(
                slide = slide,
                width = (136 - 12 * minOf(entry.depth, 3)).dp,
                cornerRadius = thumbnailRadius,
                modifier = if (selected) {
                    Modifier.border(2.dp, tokens.accent, RoundedCornerShape(thumbnailRadius))
                } else {
                    Modifier
                },
            )
        }
    }
}

/**
 * The navigator's disclosure control: a stroked chevron in a 9x9 dp space,
 * pointing right when collapsed and rotating down when the children show.
 */
@Composable
private fun DisclosureChevron(collapsed: Boolean) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val rotation: Float by animateFloatAsState(
        targetValue = if (collapsed) 0f else 90f,
        animationSpec = tween(durationMillis = 140),
    )
    Canvas(Modifier.size(9.dp).rotate(rotation)) {
        val scale: Float = size.width / 9f
        val chevron: Path = Path().apply {
            moveTo(2.6f * scale, 1.1f * scale)
            lineTo(6.4f * scale, 4.5f * scale)
            lineTo(2.6f * scale, 7.9f * scale)
        }
        drawPath(
            path = chevron,
            color = tokens.icon,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

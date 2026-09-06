package io.github.xxfast.cupboard.screens.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.canvas.SlideThumbnail
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.layoutOf
import io.github.xxfast.cupboard.document.slideById
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
 *
 * Rows reorder by drag, Keynote's way: the picked-up row travels with the
 * pointer, a line or ring marks where it will land, and on release every row
 * animates into its new place. A row's body nests the drop under it, its top and
 * bottom quarters name the gaps either side. What the drag *shows* comes from
 * [slideDrag] alone: the gesture reports the spot it is over and how far it has
 * come through [onPreviewSlideDrag] and the answer arrives back through the
 * state, because a navigator that drew from its
 * own pointer-handler writes is exactly the drag-freeze bug (see ROADMAP.md).
 */
@Composable
fun EditorNavigator(
    document: Document,
    entries: List<OutlineEntry>,
    selectedSlideId: String,
    onSelectSlide: (String) -> Unit,
    onToggleCollapsed: (String) -> Unit,
    thumbnailRadius: Dp,
    /**
     * Layout mode: the rows are the deck's layouts rather than its slides, which
     * the outline has already decided. All this panel does with it is head the
     * list with the strip that says so and give each row its name, since one
     * layout looks much like another in a thumbnail.
     */
    isEditingLayouts: Boolean = false,
    onExitSlideLayouts: () -> Unit = {},
    onContextClick: (slideId: String, positionInWindow: Offset) -> Unit = { _, _ -> },
    /** The row on the move and the gap it is over, null when nothing is dragging. */
    slideDrag: SlideDrag? = null,
    onPreviewSlideDrag: (slideId: String, afterId: String?, nest: Boolean, translationY: Float) -> Unit =
        { _, _, _, _ -> },
    onMoveSlide: (slideId: String, afterId: String?, nest: Boolean) -> Unit = { _, _, _ -> },
    onEndSlideDrag: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Layout data, not gesture state: where each visible row sits, so a drag can
    // name the spot under the pointer. A plain map on purpose, nothing draws from
    // it, and a snapshot map here would invalidate the whole panel every layout.
    val bounds: MutableMap<String, ClosedFloatingPointRange<Float>> = remember { mutableMapOf() }
    // Only visible rows anchor a drop: a row inside a collapsed group has no
    // place on screen to drop next to.
    val rows: List<OutlineEntry> = entries.filter { it.visible }

    /**
     * The spot [windowY] is over, measured against the rows that are staying
     * put: the ones on the move travel with the pointer and can't anchor
     * anything. Inside the middle half of a row, the row: the drop nests under
     * it. Otherwise the gap after the last row whose midpoint it has passed,
     * null above the first.
     */
    fun spotAt(windowY: Float, draggedId: String): DropSpot {
        val dragged: Set<String> = draggedRun(entries, draggedId)
        var afterId: String? = null
        for (row in rows) {
            if (row.slideId in dragged) continue
            val range: ClosedFloatingPointRange<Float> = bounds[row.slideId] ?: continue
            val quarter: Float = (range.endInclusive - range.start) / 4f
            if (windowY in range.start + quarter..range.endInclusive - quarter) {
                return DropSpot(row.slideId, nest = true)
            }
            if (range.start + 2 * quarter >= windowY) break
            afterId = row.slideId
        }
        return DropSpot(afterId, nest = false)
    }

    val firstVisibleId: String? = rows.firstOrNull()?.slideId
    // The row on the move and everything nested under it: a parent drags as a
    // group (Document.moveSlide lands it as one), so the whole run reads as
    // picked up and no gap inside it is a place to drop.
    val draggedIds: Set<String> = draggedRun(entries, slideDrag?.slideId)

    // Keyed by slide so a row keeps its identity across a reorder or a
    // collapse, which is what lets animateItem slide it to where it now sits
    // rather than redraw every row in place.
    LazyColumn(
        modifier = modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(LocalChromeTokens.current.panel),
        // Rows carry their own 6.dp bottom gap; the last one plus this padding
        // lands on the design's 14.
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
    ) {
        // The one thing that tells layout mode from ordinary editing at a glance,
        // and the way back out of it. Scrolls with the rows rather than pinning:
        // it heads the list it names.
        if (isEditingLayouts) item(key = "slide-layouts-header") {
            LayoutsHeader(onDone = onExitSlideLayouts)
        }

        items(rows, key = { it.slideId }) { entry ->
            // By id, not by index: in layout mode the rows are the deck's
            // layouts, which are a list of their own.
            val slide: Slide = document.slideById(entry.slideId) ?: return@items
            val selected: Boolean = entry.slideId == selectedSlideId
            val dragged: Boolean = entry.slideId in draggedIds
            // On release the displacement and the placement animate with one
            // spec, so the row glides from under the pointer straight into its
            // new slot instead of snapping home first.
            val translationY: Float by animateFloatAsState(
                targetValue = if (dragged) slideDrag?.translationY ?: 0f else 0f,
                animationSpec = if (dragged) snap() else tween(durationMillis = 220),
            )
            Box(
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = tween(durationMillis = 140),
                        fadeOutSpec = tween(durationMillis = 140),
                        placementSpec = tween(durationMillis = 220),
                    )
                    // Lifted above the rows it travels over.
                    .zIndex(if (dragged || translationY != 0f) 1f else 0f)
                    .graphicsLayer { this.translationY = translationY },
            ) {
                NavigatorRow(
                    slide = slide,
                    // Null on a layout, and for free: a layout is on no layout, so
                    // its thumbnail draws itself and nothing behind it.
                    layout = document.layoutOf(slide),
                    background = document.background,
                    // A deck of layouts is a deck of near-identical thumbnails, so
                    // there the name is the row. Ordinary rows keep the number alone.
                    title = entry.title.takeIf { isEditingLayouts },
                    entry = entry,
                    selected = selected,
                    thumbnailRadius = thumbnailRadius,
                    dragged = dragged,
                    // The gap sits between rows, so the row above it draws its
                    // half and the topmost row draws the one above itself.
                    dropAbove = slideDrag != null &&
                        !slideDrag.nest &&
                        slideDrag.afterId == null &&
                        entry.slideId == firstVisibleId &&
                        entry.slideId !in draggedIds,
                    dropBelow = slideDrag != null &&
                        !slideDrag.nest &&
                        slideDrag.afterId == entry.slideId &&
                        entry.slideId !in draggedIds,
                    dropOnto = slideDrag != null &&
                        slideDrag.nest &&
                        slideDrag.afterId == entry.slideId &&
                        entry.slideId !in draggedIds,
                    onSelectSlide = onSelectSlide,
                    onToggleCollapsed = onToggleCollapsed,
                    onContextClick = onContextClick,
                    onBounds = { range -> bounds[entry.slideId] = range },
                    onSpotAt = ::spotAt,
                    onPreviewSlideDrag = onPreviewSlideDrag,
                    onMoveSlide = onMoveSlide,
                    onEndSlideDrag = onEndSlideDrag,
                )
            }
        }
    }
}

/**
 * The strip that heads the navigator in layout mode: what the rows are, and the
 * button that puts them away. The label is the speaker notes' label, since it
 * says the same kind of thing about the panel under it.
 */
@Composable
private fun LayoutsHeader(onDone: () -> Unit) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "SLIDE LAYOUTS",
            color = tokens.faint,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tokens.tonal)
                .clickable(onClick = onDone)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Done",
                color = tokens.tonalText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Where a drag is: the gap under [afterId] (null for the one above the first
 * row), or [afterId]'s own row when [nest], which drops the slide under it.
 */
private data class DropSpot(val afterId: String?, val nest: Boolean)

/** [slideId]'s entry and the run of deeper entries after it; empty for null. */
private fun draggedRun(entries: List<OutlineEntry>, slideId: String?): Set<String> {
    val start: Int = entries.indexOfFirst { it.slideId == slideId }
    if (start == -1) return emptySet()
    val depth: Int = entries[start].depth
    return buildSet {
        add(slideId!!)
        for (entry in entries.drop(start + 1)) {
            if (entry.depth <= depth) break
            add(entry.slideId)
        }
    }
}

/**
 * One navigator row. [dropAbove] and [dropBelow] are the drag's drop line,
 * [dropOnto] its nest ring, and [dragged] the picked-up row: all four come from
 * the state, never from this row's own reading of the pointer.
 *
 * [onSpotAt] answers what spot a window y is over for a given dragged slide;
 * [onBounds] reports where this row sits so it can.
 */
@Composable
private fun NavigatorRow(
    slide: Slide,
    layout: Slide?,
    /** The deck's background, which is what a slide on this layout will sit on. */
    background: SlideBackground?,
    /** The name under the thumbnail, null for a row that shows none. */
    title: String?,
    entry: OutlineEntry,
    selected: Boolean,
    thumbnailRadius: Dp,
    dragged: Boolean,
    dropAbove: Boolean,
    dropBelow: Boolean,
    dropOnto: Boolean,
    onSelectSlide: (String) -> Unit,
    onToggleCollapsed: (String) -> Unit,
    onContextClick: (slideId: String, positionInWindow: Offset) -> Unit,
    onBounds: (windowRange: ClosedFloatingPointRange<Float>) -> Unit,
    onSpotAt: (windowY: Float, draggedId: String) -> DropSpot,
    onPreviewSlideDrag: (slideId: String, afterId: String?, nest: Boolean, translationY: Float) -> Unit,
    onMoveSlide: (slideId: String, afterId: String?, nest: Boolean) -> Unit,
    onEndSlideDrag: () -> Unit,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val gap: Float = with(LocalDensity.current) { 6.dp.toPx() }
    // Each row places itself, so the press it reports is already in the
    // coordinates a menu anywhere in the window can be hung from.
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
    // The gesture loop outlives the composition that started it, so it reads the
    // freshest callbacks rather than the ones the drag began with.
    val spotAt: (Float, String) -> DropSpot by rememberUpdatedState(onSpotAt)
    val preview: (String, String?, Boolean, Float) -> Unit by rememberUpdatedState(onPreviewSlideDrag)
    val commit: (String, String?, Boolean) -> Unit by rememberUpdatedState(onMoveSlide)
    val cancel: () -> Unit by rememberUpdatedState(onEndSlideDrag)
    // A skipped row keeps its place in the deck but not in the presentation, so
    // it reads as half-there; a picked-up one is lifted, so it goes a touch
    // translucent to show the rows it passes over.
    val alpha: Float = when {
        entry.skipped -> 0.4f
        dragged -> 0.85f
        else -> 1f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Outside the bottom gap's padding, so the drop line can be drawn in
            // the gap it names without taking a pixel of layout to do it.
            .drawWithContent {
                drawContent()
                val stroke: Float = 2.dp.toPx()
                if (dropAbove) drawDropLine(tokens.accent, stroke / 2f, stroke)
                if (dropBelow) drawDropLine(tokens.accent, size.height - 3.dp.toPx(), stroke)
                // The nest ring hugs the capsule, inside the bottom gap.
                if (dropOnto) {
                    drawRoundRect(
                        color = tokens.accent,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - 6.dp.toPx() - stroke),
                        cornerRadius = CornerRadius(9.dp.toPx()),
                        style = Stroke(stroke),
                    )
                }
            }
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) tokens.rowHov else Color.Transparent)
            .onGloballyPositioned {
                coordinates = it
                val top: Float = it.positionInWindow().y
                // The capsule's extent, without the bottom gap the row carries.
                onBounds(top..top + it.size.height - gap)
            }
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
            // Straight to the drag, no long press: this is a pointer app first.
            .pointerInput(entry.slideId) {
                // Plain locals, deliberately: what the drag draws comes back
                // through the state, this only remembers what it last said, so
                // there is no snapshot write in a pointer handler to lose.
                var dragging: Boolean = false
                var spot: DropSpot? = null
                // Where the press landed, in window pixels, plus what the
                // pointer has moved since: the row travels with it, so its own
                // coordinates are no fixed place to measure from.
                var startY: Float = 0f
                var translationY: Float = 0f
                detectDragGestures(
                    onDragStart = { press ->
                        startY = coordinates?.localToWindow(press)?.y ?: 0f
                        translationY = 0f
                    },
                    onDragEnd = {
                        spot?.let { commit(entry.slideId, it.afterId, it.nest) }
                        dragging = false
                        spot = null
                    },
                    onDragCancel = {
                        if (dragging) cancel()
                        dragging = false
                        spot = null
                    },
                ) { change, dragAmount ->
                    change.consume()
                    translationY += dragAmount.y
                    val here: DropSpot = spotAt(startY + translationY, entry.slideId)
                    dragging = true
                    spot = here
                    // Every sample, the way the canvas previews an element drag:
                    // the row follows the pointer, and it follows through the
                    // loop.
                    preview(entry.slideId, here.afterId, here.nest, translationY)
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
            modifier = Modifier.padding(start = (entry.depth * 12).dp).alpha(alpha),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                // The presentation number, so a skipped row shows none at all.
                // The gutter keeps its width either way, or every row below a
                // skipped one would step sideways.
                text = entry.number?.toString().orEmpty(),
                color = tokens.subtle,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(14.dp).padding(top = 2.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SlideThumbnail(
                    slide = slide,
                    layout = layout,
                    background = background,
                    width = (136 - 12 * minOf(entry.depth, 3)).dp,
                    cornerRadius = thumbnailRadius,
                    modifier = if (selected) {
                        Modifier.border(2.dp, tokens.accent, RoundedCornerShape(thumbnailRadius))
                    } else {
                        Modifier
                    },
                )
                if (title != null) Text(
                    text = title,
                    color = if (selected) tokens.text else tokens.dim,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The drop indicator: a 2dp accent rule across the row at [y]. */
private fun DrawScope.drawDropLine(color: Color, y: Float, stroke: Float) {
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
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

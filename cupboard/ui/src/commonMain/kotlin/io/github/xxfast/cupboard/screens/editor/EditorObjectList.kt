package io.github.xxfast.cupboard.screens.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTokens
import kotlin.math.abs

private val ObjectRowHeight = 34.dp

/** Where a top-level row's content starts. */
private val RowInset = 6.dp

/** How far one level of nesting indents a row. */
private val ObjectIndent = 16.dp

/** A hidden object's row reads as half-there, like a skipped slide in the navigator. */
private const val HiddenAlpha: Float = 0.4f

/**
 * Keynote's Object List: every object on the slide, front to back, one 34dp row
 * each, groups folding open to show their children.
 *
 * The panel only reports. Each verb goes out through a callback and the answer
 * comes back as new [rows]; the only state kept here is what has no business in
 * the document: which row the pointer is over, the name being typed, and the
 * search box's text buffer.
 *
 * A click selects, a ctrl, cmd or shift click adds to or removes from the
 * selection, a double click renames in place (Enter or leaving the field
 * commits, Escape cancels, a blank name resets to the default). The lock and
 * eye show on hover, and stay once they are set. A right-click goes to
 * [onContextClick] with the press in window coordinates, the same as the
 * navigator's rows.
 *
 * Rows reorder by drag, the navigator's way: the picked-up row travels with the
 * pointer and a line marks the gap it lands in. Only gaps among the dragged
 * row's siblings count, since a drag never takes an object in or out of a
 * group: the line goes to the nearest of those. What the drag shows comes from
 * [objectDrag] alone, fed through [onPreviewObjectDrag] and landed by
 * [onMoveElement]. A filtered list is flat, so it has no siblings to tell apart
 * and doesn't drag.
 */
@Composable
fun EditorObjectList(
    rows: List<ObjectRow>,
    filter: String,
    onFilterChange: (String) -> Unit,
    onSelect: (id: String) -> Unit,
    onToggleSelection: (id: String) -> Unit,
    onToggleExpanded: (id: String) -> Unit,
    onSetHidden: (id: String, hidden: Boolean) -> Unit,
    onSetLocked: (id: String, locked: Boolean) -> Unit,
    onRename: (id: String, name: String?) -> Unit,
    onContextClick: (id: String, positionInWindow: Offset) -> Unit,
    /** The row on the move and the gap it would land in, null when nothing is dragging. */
    objectDrag: ObjectDrag? = null,
    onPreviewObjectDrag: (id: String, beforeId: String?, translationY: Float) -> Unit =
        { _, _, _ -> },
    onMoveElement: (id: String, beforeId: String?) -> Unit = { _, _ -> },
    onEndObjectDrag: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    // Layout data, not gesture state, the navigator's way: where each row sits in
    // window pixels, so a drag can find the gaps. A plain map: the drop line only
    // reads it when [objectDrag] changes, which recomposes this anyway.
    val bounds: MutableMap<String, ClosedFloatingPointRange<Float>> = remember { mutableMapOf() }
    val parents: Map<String, String?> = remember(rows) { parentsOf(rows) }
    val draggedIds: List<String> = runOf(rows, objectDrag?.id)
    val dragDepth: Int = rows.firstOrNull { it.id == objectDrag?.id }?.depth ?: 0
    // Where the list sits in the window, so the drop line can be drawn in its space.
    var listTop: Float by remember { mutableStateOf(0f) }
    // The gap the drag would land in, in the list's pixels: the top of the row it
    // goes in front of, or the bottom of the last sibling's run for the back.
    val dropY: Float? = objectDrag?.let { drag ->
        val edge: Float? =
            if (drag.beforeId != null) bounds[drag.beforeId]?.start
            else backRowOf(rows, parents, drag.id)?.let { id -> bounds[id]?.endInclusive }
        edge?.minus(listTop)
    }

    // The gap nearest [windowY] that [draggedId] may land in, null when it has no
    // sibling to land next to. Measured against the rows staying put: the dragged
    // run travels with the pointer and anchors nothing.
    fun gapAt(windowY: Float, draggedId: String): ObjectGap? {
        val parent: String? = parents[draggedId]
        val dragged: List<String> = runOf(rows, draggedId)
        val front: List<Pair<ObjectGap, Float>> = rows
            .filter { row -> row.id !in dragged && parents[row.id] == parent }
            .mapNotNull { row -> bounds[row.id]?.let { ObjectGap(row.id) to it.start } }
        val back: Pair<ObjectGap, Float>? = backRowOf(rows, parents, draggedId)
            ?.let { id -> bounds[id] }
            ?.let { range -> ObjectGap(beforeId = null) to range.endInclusive }

        return (front + listOfNotNull(back))
            .minByOrNull { (_, y) -> abs(y - windowY) }
            ?.first
    }

    Column(
        modifier = modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(tokens.panel),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { listTop = it.positionInWindow().y },
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (rows.isEmpty()) item(key = "empty") {
                    Text(
                        text = if (filter.isBlank()) "No Objects" else "No Results",
                        color = tokens.subtle,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }

                items(rows, key = { it.id }) { row ->
                    val dragged: Boolean = row.id in draggedIds
                    // On release the displacement and the new placement animate with
                    // one spec, so the row glides from under the pointer into its slot.
                    val translationY: Float by animateFloatAsState(
                        targetValue = if (dragged) objectDrag?.translationY ?: 0f else 0f,
                        animationSpec = if (dragged) snap() else tween(durationMillis = 220),
                    )
                    // A lock anywhere above the row holds it where it is.
                    val locked: Boolean = generateSequence(row.id) { id -> parents[id] }
                        .any { id -> rows.firstOrNull { it.id == id }?.locked == true }

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
                        ObjectListRow(
                            row = row,
                            dragged = dragged,
                            draggable = filter.isBlank() && !locked,
                            onBounds = { range -> bounds[row.id] = range },
                            onGapAt = ::gapAt,
                            onPreviewObjectDrag = onPreviewObjectDrag,
                            onMoveElement = onMoveElement,
                            onEndObjectDrag = onEndObjectDrag,
                            onSelect = onSelect,
                            onToggleSelection = onToggleSelection,
                            onToggleExpanded = onToggleExpanded,
                            onSetHidden = onSetHidden,
                            onSetLocked = onSetLocked,
                            onRename = onRename,
                            onContextClick = onContextClick,
                        )
                    }
                }
            }

            // Over every row, the lifted one included: the gap a drag names is
            // usually right under the row it is carrying.
            if (dropY != null) Canvas(Modifier.matchParentSize()) {
                val stroke: Float = 2.dp.toPx()
                val y: Float = dropY.coerceIn(stroke / 2f, size.height - stroke / 2f)
                drawLine(
                    color = tokens.accent,
                    start = Offset((RowInset + ObjectIndent * dragDepth).toPx(), y),
                    end = Offset(size.width - RowInset.toPx(), y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.div))
        ObjectSearchField(filter = filter, onFilterChange = onFilterChange)
    }
}

/** Where a drag lands: in front of [beforeId]'s element, the back of its siblings for null. */
private data class ObjectGap(val beforeId: String?)

/** Each row's group, by id: the nearest row above it one level out, null at the top level. */
private fun parentsOf(rows: List<ObjectRow>): Map<String, String?> {
    val open: MutableList<ObjectRow> = mutableListOf()
    return rows.associate { row ->
        while (open.isNotEmpty() && open.last().depth >= row.depth) open.removeAt(open.lastIndex)
        val parent: String? = open.lastOrNull()?.id
        open.add(row)
        row.id to parent
    }
}

/** [id]'s row and the deeper rows after it, which travel with it; empty for null. */
private fun runOf(rows: List<ObjectRow>, id: String?): List<String> {
    val start: Int = rows.indexOfFirst { it.id == id }
    if (start == -1) return emptyList()

    val depth: Int = rows[start].depth
    val below: List<ObjectRow> = rows.drop(start + 1).takeWhile { it.depth > depth }
    return listOf(rows[start].id) + below.map { it.id }
}

/**
 * The row the back of [draggedId]'s siblings sits under: the last row of the
 * last sibling's run, the dragged run left out. Null with no sibling at all.
 */
private fun backRowOf(
    rows: List<ObjectRow>,
    parents: Map<String, String?>,
    draggedId: String,
): String? {
    val parent: String? = parents[draggedId]
    val dragged: List<String> = runOf(rows, draggedId)
    val last: ObjectRow = rows
        .lastOrNull { row -> row.id !in dragged && parents[row.id] == parent }
        ?: return null

    return runOf(rows, last.id).last()
}

/**
 * One Object List row. [dragged] is the picked-up row, which comes from the
 * state, never from this row's own reading of the pointer. [onGapAt] answers
 * which gap a window y is over, [onBounds] reports where this row sits so it can.
 */
@Composable
private fun ObjectListRow(
    row: ObjectRow,
    dragged: Boolean,
    draggable: Boolean,
    onBounds: (windowRange: ClosedFloatingPointRange<Float>) -> Unit,
    onGapAt: (windowY: Float, draggedId: String) -> ObjectGap?,
    onPreviewObjectDrag: (id: String, beforeId: String?, translationY: Float) -> Unit,
    onMoveElement: (id: String, beforeId: String?) -> Unit,
    onEndObjectDrag: () -> Unit,
    onSelect: (id: String) -> Unit,
    onToggleSelection: (id: String) -> Unit,
    onToggleExpanded: (id: String) -> Unit,
    onSetHidden: (id: String, hidden: Boolean) -> Unit,
    onSetLocked: (id: String, locked: Boolean) -> Unit,
    onRename: (id: String, name: String?) -> Unit,
    onContextClick: (id: String, positionInWindow: Offset) -> Unit,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val hover: MutableInteractionSource = remember { MutableInteractionSource() }
    val hovered: Boolean by hover.collectIsHoveredAsState()
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
    // The name being typed lives in the field; this only says a field is open.
    var renaming: Boolean by remember(row.id) { mutableStateOf(false) }
    // The gesture loops outlive the composition that started them.
    val select: (String) -> Unit by rememberUpdatedState(onSelect)
    val toggleSelection: (String) -> Unit by rememberUpdatedState(onToggleSelection)
    val contextClick: (String, Offset) -> Unit by rememberUpdatedState(onContextClick)
    val gapAt: (Float, String) -> ObjectGap? by rememberUpdatedState(onGapAt)
    val preview: (String, String?, Float) -> Unit by rememberUpdatedState(onPreviewObjectDrag)
    val commit: (String, String?) -> Unit by rememberUpdatedState(onMoveElement)
    val cancel: () -> Unit by rememberUpdatedState(onEndObjectDrag)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ObjectRowHeight)
            .hoverable(hover)
            // A row on the move covers the ones it passes over.
            .background(if (dragged) tokens.panel else Color.Transparent)
            .background(if (row.selected) tokens.rowHov else Color.Transparent)
            .alpha(if (dragged) 0.85f else 1f)
            .drawBehind {
                val y: Float = size.height - 0.5.dp.toPx()
                drawLine(tokens.div, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            .onGloballyPositioned {
                coordinates = it
                val top: Float = it.positionInWindow().y
                onBounds(top..top + it.size.height)
            }
            .pointerInput(row.id) {
                awaitPointerEventScope {
                    while (true) {
                        // Classified the way the navigator and the canvas do it: a
                        // press with the primary held is a left click, whatever the
                        // rest of the chord says.
                        val event: PointerEvent = awaitPointerEvent(PointerEventPass.Initial)
                        val secondary: Boolean = event.type == PointerEventType.Press &&
                            event.buttons.isSecondaryPressed &&
                            !event.buttons.isPrimaryPressed
                        if (!secondary) continue

                        val change: PointerInputChange = event.changes.firstOrNull() ?: continue
                        // Taken on the initial pass so nothing under the row sees it.
                        change.consume()
                        contextClick(
                            row.id,
                            coordinates?.localToWindow(change.position) ?: change.position,
                        )
                    }
                }
            }
            // Past the touch slop and no sooner, so a click, a double click and
            // the hover buttons all stay what they were.
            .pointerInput(row.id, draggable, renaming) {
                if (!draggable || renaming) return@pointerInput

                // Plain locals, deliberately: what the drag draws comes back
                // through the state, this only remembers what it last said.
                var dragging: Boolean = false
                var gap: ObjectGap? = null
                // Where the press landed, in window pixels, plus what the pointer
                // has moved since: the row travels with it, so its own
                // coordinates are no fixed place to measure from.
                var startY: Float = 0f
                var translationY: Float = 0f
                detectDragGestures(
                    onDragStart = { press ->
                        startY = coordinates?.localToWindow(press)?.y ?: 0f
                        translationY = 0f
                    },
                    onDragEnd = {
                        val landed: ObjectGap? = gap
                        if (landed != null) commit(row.id, landed.beforeId)
                        else if (dragging) cancel()
                        dragging = false
                        gap = null
                    },
                    onDragCancel = {
                        if (dragging) cancel()
                        dragging = false
                        gap = null
                    },
                ) { change, dragAmount ->
                    change.consume()
                    translationY += dragAmount.y
                    val here: ObjectGap? = gapAt(startY + translationY, row.id)
                    dragging = true
                    gap = here
                    preview(row.id, here?.beforeId, translationY)
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(RowInset + ObjectIndent * row.depth))

        // Fixed gutter so every chevron at a depth shares one rail, group or not.
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(3.dp))
                .let {
                    if (row.isGroup) it.clickable { onToggleExpanded(row.id) }
                    else it
                },
            contentAlignment = Alignment.Center,
        ) {
            if (row.isGroup) DisclosureChevron(collapsed = !row.expanded)
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(row.id, renaming) {
                    if (renaming) return@pointerInput

                    // Two clicks this close are a double click: the platform's own
                    // window, the same one the canvas goes by.
                    val doubleClick: Long = viewConfiguration.doubleTapTimeoutMillis
                    var lastUp: Long? = null
                    awaitEachGesture {
                        val down: PointerInputChange = awaitFirstDown()
                        val chord: PointerKeyboardModifiers = currentEvent.keyboardModifiers
                        val secondary: Boolean = currentEvent.buttons.isSecondaryPressed &&
                            !currentEvent.buttons.isPrimaryPressed
                        val up: PointerInputChange = waitForUpOrCancellation() ?: return@awaitEachGesture
                        if (secondary) return@awaitEachGesture

                        val toggles: Boolean =
                            chord.isCtrlPressed || chord.isMetaPressed || chord.isShiftPressed
                        val previous: Long? = lastUp
                        when {
                            toggles -> {
                                lastUp = null
                                toggleSelection(row.id)
                            }

                            previous != null && up.uptimeMillis - previous <= doubleClick -> {
                                lastUp = null
                                renaming = true
                            }

                            else -> {
                                lastUp = up.uptimeMillis
                                select(row.id)
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).alpha(if (row.hidden) HiddenAlpha else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                    KindGlyph(kind = row.kind, color = tokens.icon)
                }
                Spacer(Modifier.width(6.dp))

                if (renaming) RenameField(
                    name = row.name,
                    onCommit = { text ->
                        renaming = false
                        // Typing the default back in is not naming it.
                        if (text != row.name) onRename(row.id, text.ifBlank { null })
                    },
                    onCancel = { renaming = false },
                    modifier = Modifier.weight(1f),
                ) else Text(
                    text = row.name,
                    color = tokens.text,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Always laid out, so a name ellipsises at one width whether or not the
        // pointer is over the row.
        RowToggle(
            visible = row.locked || hovered,
            onClick = { onSetLocked(row.id, !row.locked) },
        ) { LockGlyph(locked = row.locked, color = tokens.icon) }
        RowToggle(
            visible = row.hidden || hovered,
            onClick = { onSetHidden(row.id, !row.hidden) },
        ) { EyeGlyph(hidden = row.hidden, color = tokens.icon) }
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun RowToggle(visible: Boolean, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) glyph()
    }
}

/** The name in a field, committed by Enter or by leaving it, dropped by Escape. */
@Composable
private fun RenameField(
    name: String,
    onCommit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val focusRequester: FocusRequester = remember { FocusRequester() }
    var value: TextFieldValue by remember {
        mutableStateOf(TextFieldValue(name, TextRange(0, name.length)))
    }
    var focused: Boolean by remember { mutableStateOf(false) }
    // One way out only: Enter removes the field, which can report a focus loss too.
    var settled: Boolean by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BasicTextField(
        value = value,
        onValueChange = { value = it },
        singleLine = true,
        textStyle = TextStyle(color = tokens.text, fontSize = 12.sp),
        cursorBrush = SolidColor(tokens.accent),
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (focused && !state.isFocused && !settled) {
                    settled = true
                    onCommit(value.text)
                }
                focused = state.isFocused
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> {
                        settled = true
                        onCommit(value.text)
                        true
                    }

                    Key.Escape -> {
                        settled = true
                        onCancel()
                        true
                    }

                    else -> false
                }
            },
    )
}

@Composable
private fun ObjectSearchField(filter: String, onFilterChange: (String) -> Unit) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    // The buffer is the field's own, so a keystroke is never waiting on the loop
    // to hand it back; it follows [filter] when something else changes it.
    var value: TextFieldValue by remember { mutableStateOf(TextFieldValue(filter)) }
    LaunchedEffect(filter) { if (filter != value.text) value = TextFieldValue(filter) }

    val shape: RoundedCornerShape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .height(26.dp)
            .clip(shape)
            .border(1.dp, tokens.outline, shape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchGlyph(color = tokens.subtle)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.text.isEmpty()) Text("Search", color = tokens.faint, fontSize = 12.sp)
            BasicTextField(
                value = value,
                onValueChange = { next ->
                    val changed: Boolean = next.text != value.text
                    value = next
                    if (changed) onFilterChange(next.text)
                },
                singleLine = true,
                textStyle = TextStyle(color = tokens.text, fontSize = 12.sp),
                cursorBrush = SolidColor(tokens.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.text.isNotEmpty()) Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    value = TextFieldValue("")
                    onFilterChange("")
                },
            contentAlignment = Alignment.Center,
        ) { ClearGlyph(color = tokens.subtle) }
    }
}

/** The row's icon for [kind], one of [io.github.xxfast.cupboard.document.kindName]'s words. */
@Composable
private fun KindGlyph(kind: String, color: Color) {
    when (kind) {
        "Text" -> TextGlyph(color)
        "Shape" -> ShapeGlyph(color)
        "Line" -> LineGlyph(color)
        "Image" -> ImageGlyph(color)
        "Gallery" -> GalleryGlyph(color)
        "Group" -> GroupGlyph(color)
        "Code" -> CodeGlyph(color)
        "Terminal" -> TerminalGlyph(color)
        "Diagram" -> DiagramGlyph(color)
        "Equation" -> EquationGlyph(color)
        "Video" -> MediaGlyph(color)
        "Audio" -> AudioGlyph(color)
        else -> ShapeGlyph(color)
    }
}

// Glyphs the toolbar has no use for, drawn by hand the same way (see EditorToolbar).

/** A rising diagonal, the way a fresh line inserts. */
@Composable
private fun LineGlyph(color: Color) {
    Canvas(Modifier.size(15.dp)) {
        val s: Float = size.width / 14f
        drawLine(color, Offset(2f * s, 12f * s), Offset(12f * s, 2f * s), 1.4.dp.toPx(), StrokeCap.Round)
    }
}

/** Two sheets, one behind the other, which is what a stack of pictures looks like. */
@Composable
private fun GalleryGlyph(color: Color) {
    Canvas(Modifier.size(15.dp)) {
        val s: Float = size.width / 14f
        val corner: CornerRadius = CornerRadius(1.2f * s)
        drawRoundRect(
            color = color,
            topLeft = Offset(3.8f * s, 0.9f * s),
            size = Size(9.3f * s, 8.2f * s),
            cornerRadius = corner,
            style = glyphStroke(),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(0.9f * s, 4.9f * s),
            size = Size(9.3f * s, 8.2f * s),
            cornerRadius = corner,
            style = glyphStroke(),
        )
    }
}

/** A frame with two members inside it. */
@Composable
private fun GroupGlyph(color: Color) {
    Canvas(Modifier.size(15.dp)) {
        val s: Float = size.width / 14f
        drawRoundRect(
            color = color,
            topLeft = Offset(0.8f * s, 0.8f * s),
            size = Size(12.4f * s, 12.4f * s),
            cornerRadius = CornerRadius(1.5f * s),
            style = glyphStroke(),
        )
        drawRect(color, Offset(3.2f * s, 3.2f * s), Size(3.4f * s, 3.4f * s))
        drawRect(color, Offset(7.4f * s, 7.4f * s), Size(3.4f * s, 3.4f * s))
    }
}

/** A quaver. */
@Composable
private fun AudioGlyph(color: Color) {
    Canvas(Modifier.size(15.dp)) {
        val s: Float = size.width / 14f
        drawCircle(color, radius = 2.2f * s, center = Offset(4.4f * s, 10.6f * s), style = glyphStroke())
        val stem: Path = Path().apply {
            moveTo(6.6f * s, 10.6f * s)
            lineTo(6.6f * s, 2.2f * s)
            lineTo(11.6f * s, 4.4f * s)
        }
        drawPath(stem, color, style = glyphStroke())
    }
}

/** A padlock, its shackle open when [locked] is false. */
@Composable
private fun LockGlyph(locked: Boolean, color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val s: Float = size.width / 14f
        drawRoundRect(
            color = color,
            topLeft = Offset(2.6f * s, 6.4f * s),
            size = Size(8.8f * s, 6.4f * s),
            cornerRadius = CornerRadius(1.2f * s),
            style = glyphStroke(),
        )
        val shackle: Path = Path().apply {
            moveTo(4.6f * s, 6.4f * s)
            lineTo(4.6f * s, 4.6f * s)
            cubicTo(4.6f * s, 1.2f * s, 9.4f * s, 1.2f * s, 9.4f * s, 4.6f * s)
            lineTo(9.4f * s, if (locked) 6.4f * s else 3.4f * s)
        }
        drawPath(shackle, color, style = glyphStroke())
    }
}

/** An eye, struck through when [hidden]. */
@Composable
private fun EyeGlyph(hidden: Boolean, color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val s: Float = size.width / 14f
        val eye: Path = Path().apply {
            moveTo(0.8f * s, 7f * s)
            quadraticTo(7f * s, 0.2f * s, 13.2f * s, 7f * s)
            quadraticTo(7f * s, 13.8f * s, 0.8f * s, 7f * s)
        }
        drawPath(eye, color, style = glyphStroke())
        drawCircle(color, radius = 1.9f * s, center = Offset(7f * s, 7f * s), style = glyphStroke())
        if (hidden) drawLine(
            color = color,
            start = Offset(2.4f * s, 12f * s),
            end = Offset(11.6f * s, 2f * s),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun SearchGlyph(color: Color) {
    Canvas(Modifier.size(12.dp)) {
        val s: Float = size.width / 12f
        drawCircle(color, radius = 3.8f * s, center = Offset(5f * s, 5f * s), style = glyphStroke())
        drawLine(color, Offset(8f * s, 8f * s), Offset(11f * s, 11f * s), 1.4.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun ClearGlyph(color: Color) {
    Canvas(Modifier.size(8.dp)) {
        val s: Float = size.width / 8f
        drawLine(color, Offset(1f * s, 1f * s), Offset(7f * s, 7f * s), 1.4.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(7f * s, 1f * s), Offset(1f * s, 7f * s), 1.4.dp.toPx(), StrokeCap.Round)
    }
}

package io.github.xxfast.cupboard.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.canvas.ElementView
import io.github.xxfast.cupboard.canvas.LocalCanvasScale
import io.github.xxfast.cupboard.canvas.SlideNumberView
import io.github.xxfast.cupboard.canvas.SlideSurface
import io.github.xxfast.cupboard.canvas.alignment
import io.github.xxfast.cupboard.canvas.textStyle
import io.github.xxfast.cupboard.canvas.toComposeColor
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
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
 *
 * A secondary press only reports itself, through [onContextClick]: the menu it
 * opens is the shell's, and what the click does to the selection is the state
 * loop's.
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
    /**
     * A right-click, carrying the topmost element under it (null over empty slide
     * space) and where it landed in this composable's own space, in pixels, for a
     * shell to anchor its menu at. Defaulted so a shell that has no menu yet still
     * builds; every shell that grows one passes it.
     */
    onContextClick: (elementId: String?, position: Offset) -> Unit = { _, _ -> },
    /**
     * The element the caret is in, null when none is: it draws as a text field
     * in place of its text, on top of everything, and takes the keys.
     */
    editingElementId: String? = null,
    /** A double click on a text element: the caret goes in it. */
    onBeginTextEdit: (String) -> Unit = {},
    /** Escape, or a press anywhere else on the slide: the caret leaves. */
    onEndTextEdit: () -> Unit = {},
    modifier: Modifier = Modifier,
    zoom: Float? = null,
    /** The slide's place in the presentation, drawn only when the slide asks for it. */
    number: Int? = null,
) {
    val currentSlide by rememberUpdatedState(slide)
    val currentSelection by rememberUpdatedState(selectedElementIds)
    // Read from the pointer handlers, which are set up once: a press outside the
    // field has to know whether there is a caret to take away.
    val currentEditingId by rememberUpdatedState(editingElementId)
    // Whatever the id resolves to right now, and only when there is text to edit.
    val editing: TextElement? =
        slide.elements.firstOrNull { it.id == editingElementId } as? TextElement
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
    // The same latch for the secondary button, which the drag handler can't tell
    // from the primary one either: a right press is a click and never a gesture.
    var secondaryDown by remember { mutableStateOf(false) }
    // Where the slide sits inside this composable, so a right-click can be
    // reported in the canvas's space rather than the slide's: at any zoom but Fit
    // the slide is letterboxed, and a menu anchors to the canvas.
    var canvasBounds by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var slideBounds by remember { mutableStateOf<LayoutCoordinates?>(null) }

    SlideSurface(
        modifier.onGloballyPositioned { canvasBounds = it },
        slideBackground = slide.background,
        zoom = zoom,
    ) {
        // The element under the caret keeps its place in the layout but paints
        // nothing: the text field below draws it, and two copies of the same
        // text half a pixel apart is what an editor must never show.
        for (element in slide.elements) {
            ElementView(element, modifier = if (element.id == editing?.id) Modifier.alpha(0f) else Modifier)
        }
        if (slide.showsSlideNumber && number != null) SlideNumberView(number)

        // Editing affordances hold constant screen size at any zoom: authored
        // sizes are divided by the canvas scale, positions stay in doc units.
        val canvasScale = LocalCanvasScale.current
        val docDensity = LocalDensity.current.density
        fun toDoc(position: Offset) = Offset(position.x / docDensity, position.y / docDensity)

        fun elementAt(p: Offset): Element? =
            currentSlide.elements.lastOrNull { it.contains(p.x, p.y) }

        // A pointer position, which arrives in the input overlay's space (the
        // slide rectangle), moved into the canvas composable's own. Untranslated
        // until both layouts have reported, which is before the first press.
        fun toCanvas(position: Offset): Offset {
            val canvas: LayoutCoordinates = canvasBounds ?: return position
            val slideRect: LayoutCoordinates = slideBounds ?: return position
            return canvas.localPositionOf(slideRect, position)
        }

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
                .onGloballyPositioned { slideBounds = it }
                .then(cursorModifier)
                .pointerInput(Unit) {
                    // Hover for the cursor, and the click that selects. One loop
                    // because the click needs the modifiers of its own press,
                    // which detectTapGestures doesn't hand out. Nothing is
                    // consumed here, so the drag handler below still sees every
                    // event; a press that becomes a drag has its changes consumed
                    // there, which is what takes it out of the running as a click.
                    val slop: Float = viewConfiguration.touchSlop
                    val doubleClick: Long = viewConfiguration.doubleTapTimeoutMillis
                    awaitPointerEventScope {
                        var pressedAt: Offset? = null
                        // What the last click landed on and when, so the next one
                        // can tell itself a second click on the same element.
                        var clickedId: String? = null
                        var clickedAt = 0L
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
                                //
                                // A secondary press settles at press time too,
                                // and settles there for good: it reports what it
                                // hit and starts nothing. Ctrl-click is left to
                                // mean ctrl-click; the platforms that spell a
                                // right-click that way say so in their own
                                // events, not in this button.
                                PointerEventType.Press -> {
                                    // A caret in flight ends here, before the
                                    // press is classified: the field sits above
                                    // this overlay, so anything that reaches it
                                    // landed somewhere else on the slide.
                                    if (currentEditingId != null) onEndTextEdit()
                                    // The chord, not the changed button, is all a
                                    // common pointer event carries, and a native
                                    // menu's tracking loop can eat a secondary
                                    // release and leave its bit stuck down. A
                                    // press with the primary held is a left
                                    // click whatever the stale rest of the chord
                                    // says, so a stuck bit can never reclassify
                                    // ordinary clicks.
                                    secondaryDown = event.buttons.isSecondaryPressed &&
                                        !event.buttons.isPrimaryPressed
                                    shiftDown =
                                        !secondaryDown && event.keyboardModifiers.isShiftPressed
                                    pressedAt = null
                                    when {
                                        secondaryDown -> position?.let {
                                            onContextClick(elementAt(toDoc(it))?.id, toCanvas(it))
                                        }

                                        shiftDown -> position
                                            ?.let { elementAt(toDoc(it)) }
                                            ?.let { onToggleElementSelection(it.id) }

                                        else -> pressedAt = position
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
                                        val now: Long =
                                            event.changes.firstOrNull()?.uptimeMillis ?: 0L
                                        // A second click on the same text
                                        // element opens it for typing, the way
                                        // every editor's does. Anything else
                                        // selects, first click or not.
                                        val again: Boolean = hit != null &&
                                            hit.id == clickedId && now - clickedAt <= doubleClick
                                        clickedId = hit?.id
                                        clickedAt = now
                                        when {
                                            hit == null -> onSelectElement(null)
                                            again && hit is TextElement && !hit.locked ->
                                                onBeginTextEdit(hit.id)

                                            else -> onSelectElement(hit.id)
                                        }
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
                            // The selection was already edited at press time, and
                            // a secondary press moves nothing at all: no marquee,
                            // no move, no resize. Both leave [target] null, so
                            // there is nothing to commit either.
                            if (shiftDown || secondaryDown) return@detectDragGestures
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
            // The ring stays while the caret is in the element, the handles go:
            // the field covers them, so a handle there would be a target you
            // can see and never hit.
            SelectionOverlay(element, canvasScale, handles = selected.size == 1 && editing == null)
        }

        // The marquee, drawn from state rather than from the gesture's own vars.
        if (marquee != null) MarqueeOverlay(marquee, canvasScale)

        // Alignment guides
        if (guideX) VerticalCenterGuide(canvasScale)
        if (guideY) HorizontalCenterGuide(canvasScale)

        // Last, and so on top of the input overlay: the field has to see its own
        // clicks and drags to place a caret and sweep a selection with them.
        if (editing != null) {
            TextEditor(editing, onPreviewElements = onPreviewElements, onEndTextEdit = onEndTextEdit)
        }
    }
}

/**
 * [element] as an editable text field, sitting exactly where its text draws.
 *
 * The value is held here rather than read back out of the state loop: a caret,
 * a selection and an in-flight composition have to move with the very key that
 * moved them, and a field that waited a roundtrip for them would drop
 * characters. Every change still goes through the loop as an ordinary element
 * preview, so the thumbnails, the inspector and every other shell see the text
 * as it is typed, and the commit is whatever the last preview left, settled by
 * [onEndTextEdit].
 *
 * This is not the drag-freeze pattern (see ROADMAP.md). That was a pointer
 * handler writing snapshot state the canvas then drew from, where the
 * invalidation could go missing. This is a composable callback driven by key
 * events, on the same path any text field in Compose takes.
 */
@Composable
private fun TextEditor(
    element: TextElement,
    onPreviewElements: (List<Element>) -> Unit,
    onEndTextEdit: () -> Unit,
) {
    // Re-seeded when the caret moves to another element, with everything
    // selected: entering an edit and typing replaces the text, Keynote-style.
    var value: TextFieldValue by remember(element.id) {
        mutableStateOf(TextFieldValue(element.text, TextRange(0, element.text.length)))
    }
    val focusRequester: FocusRequester = remember { FocusRequester() }
    LaunchedEffect(element.id) { focusRequester.requestFocus() }

    Box(
        Modifier
            .offset(element.frame.x.dp, element.frame.y.dp)
            .size(element.frame.width.dp, element.frame.height.dp)
            .graphicsLayer {
                alpha = element.opacity
                rotationZ = element.rotation
                scaleX = if (element.flippedHorizontally) -1f else 1f
                scaleY = if (element.flippedVertically) -1f else 1f
                transformOrigin = TransformOrigin.Center
            }
    ) {
        BasicTextField(
            value = value,
            onValueChange = { edited ->
                val typed: Boolean = edited.text != value.text
                value = edited
                if (typed) onPreviewElements(listOf(element.copy(text = edited.text)))
            },
            textStyle = element.textStyle(),
            cursorBrush = SolidColor(element.color.toComposeColor()),
            modifier = Modifier
                .align(element.alignment())
                .fillMaxWidth()
                .focusRequester(focusRequester)
                // Escape leaves the text where it is and the caret behind.
                // Enter is the field's, it inserts a newline.
                .onPreviewKeyEvent { key ->
                    if (key.type == KeyEventType.KeyDown && key.key == Key.Escape) {
                        onEndTextEdit()
                        true
                    } else {
                        false
                    }
                },
        )
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

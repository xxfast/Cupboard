package io.github.xxfast.cupboard.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import io.github.xxfast.cupboard.canvas.CodeChrome
import io.github.xxfast.cupboard.canvas.CodeCorner
import io.github.xxfast.cupboard.canvas.CodeGutterGap
import io.github.xxfast.cupboard.canvas.CodeLineHeight
import io.github.xxfast.cupboard.canvas.CodePadding
import io.github.xxfast.cupboard.canvas.ElementView
import io.github.xxfast.cupboard.canvas.LocalCanvasScale
import io.github.xxfast.cupboard.canvas.SlideNumberView
import io.github.xxfast.cupboard.canvas.SlideSurface
import io.github.xxfast.cupboard.canvas.TerminalBackground
import io.github.xxfast.cupboard.canvas.TerminalBorder
import io.github.xxfast.cupboard.canvas.TerminalCommand
import io.github.xxfast.cupboard.canvas.TerminalCorner
import io.github.xxfast.cupboard.canvas.TerminalLineHeight
import io.github.xxfast.cupboard.canvas.TerminalPadding
import io.github.xxfast.cupboard.canvas.TerminalTitleBarView
import io.github.xxfast.cupboard.canvas.alignment
import io.github.xxfast.cupboard.canvas.chrome
import io.github.xxfast.cupboard.canvas.highlightCode
import io.github.xxfast.cupboard.canvas.textStyle
import io.github.xxfast.cupboard.canvas.toComposeColor
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.DiagramElement
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.EquationElement
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.Guide
import io.github.xxfast.cupboard.document.GuideAxis
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.PlaceholderRole
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.TerminalElement
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.effectiveBackground
import io.github.xxfast.cupboard.document.indentLine
import io.github.xxfast.cupboard.document.inheritedElements
import io.github.xxfast.cupboard.document.lineIndexOf
import io.github.xxfast.cupboard.document.listBody
import io.github.xxfast.cupboard.document.listIndentLevel
import io.github.xxfast.cupboard.document.listMarkers
import io.github.xxfast.cupboard.document.outdentLine
import io.github.xxfast.cupboard.document.placeholderRole
import io.github.xxfast.cupboard.document.takesCaret
import io.github.xxfast.cupboard.screens.editor.GuideDrag
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTokens
import kotlin.math.abs
import kotlin.math.min

private val Accent = Color(0xFF7F52FF)
private val GuideYellow = Color(0xFFF5C518)

/**
 * The placeholder annotation's one colour, outline and tag alike: white at a
 * quarter, so it reads on the dark slide without competing with what is drawn on
 * it. Not the accent, which means selection everywhere else on this canvas.
 */
private val PlaceholderMark = Color(0x66FFFFFF)

/**
 * The sheet a source is typed on, and what is typed on it. Shared by every
 * element whose content is written rather than drawn: a diagram's chart, an
 * equation's math.
 *
 * Sheer rather than opaque on purpose: the element carries on drawing
 * underneath, so the source and the picture it makes are read in the one place.
 * Everything here is the editor's own rather than the renderer's, since none of
 * it survives the edit: what the element looks like is the renderer's business
 * alone.
 */
private val SourceSheet = Color(0xE616171D)
private val SourceBorder = Color(0xFF2C2E36)
private val SourceText = Color(0xFFF1F1F1)
private val SourceCorner = RoundedCornerShape(8.dp)
private val SourcePadding = 12.dp

/**
 * The source is set at this much of the element's own size, and never smaller
 * than [SourceMinSize]: a description runs to more lines than the picture it
 * describes, and it has to fit inside the same box.
 */
private const val SourceScale: Float = 0.6f
private const val SourceMinSize: Float = 12f

/**
 * What one nesting level of a list indents by inside the text field. A field
 * cannot lay out the canvas' 24 doc units of tab stop, so it counts in spaces.
 */
private const val SpacesPerLevel: Int = 4

/** How wide the ruler strips are, in screen dp: constant size at every zoom. */
private val RulerThickness: Dp = 18.dp

/** Doc units between ruler ticks; every second one is drawn long and labelled. */
private const val RulerMinorStep: Float = 50f
private const val RulerMajorStep: Float = 100f

/** How close a press has to come to a guide to grab it, in screen dp. */
private const val GuideGrab: Float = 4f

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
    /** A user guide on the move, [id] being the one it has in the document. */
    data class GuideMove(val id: String, val axis: GuideAxis) : DragTarget
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
    /**
     * The layout the slide is built on, null for a slide on none. Its static
     * objects draw behind the slide's own and are background-locked: they hit-test
     * as empty slide space, so nothing here selects, moves or resizes one. Editing
     * them is editing the layout, which is what layout mode is for.
     */
    layout: Slide? = null,
    /**
     * Whether the slide on the canvas is a layout being edited. It only annotates:
     * the placeholders wear a dashed outline and their role, so a slot on a layout
     * reads as a slot. On an ordinary slide nothing is drawn, where a placeholder
     * is simply an element the slide owns.
     */
    isEditingLayouts: Boolean = false,
    /** The deck's background, behind a slide that has none of its own or its layout's. */
    background: SlideBackground? = null,
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
    /**
     * A shell's way into the caret's own Cut/Copy/Paste/Select All, and its cue
     * that it wants them: passed one, the fields hand it their verbs and a
     * right-click inside an edit session is caught here rather than opening the
     * menu compose draws itself (see [onFieldContextClick]). Left null, which is
     * every compose shell, nothing about editing changes.
     */
    fieldMenuBridge: FieldMenuBridge? = null,
    /**
     * A right-click inside an edit session, in this composable's own space, in
     * pixels, exactly as [onContextClick] reports one. Only ever fires when
     * [fieldMenuBridge] is set. Nothing about the selection or the document
     * changed, the caret is where it was: this is a menu cue and no more.
     */
    onFieldContextClick: (position: Offset) -> Unit = {},
    modifier: Modifier = Modifier,
    zoom: Float? = null,
    /**
     * The deck's slide size, `Document.slideWidth` and `Document.slideHeight`:
     * what the surface lays out at, what the rulers graduate in, and what tells a
     * dragged guide whether it is still over the slide.
     */
    slideWidth: Float = Document.SLIDE_WIDTH,
    slideHeight: Float = Document.SLIDE_HEIGHT,
    /** The slide's place in the presentation, drawn only when the slide asks for it. */
    number: Int? = null,
    /** The deck's user guides. Document-owned and shared by every slide. */
    guides: List<Guide> = emptyList(),
    /** The strips around the slide, and whether [guides] draw and can be grabbed. */
    showRulers: Boolean = false,
    showGuides: Boolean = true,
    /** The guide the pointer is carrying, drawn full strength over the settled ones. */
    guideDrag: GuideDrag? = null,
    /** The lines a move may settle on, the dragged elements' own excluded. */
    snapTargets: (exclude: Set<String>) -> List<SnapLine> = { emptyList() },
    onPreviewGuide: (id: String?, axis: GuideAxis, position: Float) -> Unit = { _, _, _ -> },
    onCommitGuide: (id: String?, axis: GuideAxis, position: Float) -> Unit = { _, _, _ -> },
    onRemoveGuide: (id: String) -> Unit = {},
    onEndGuideDrag: () -> Unit = {},
) {
    val currentSlide by rememberUpdatedState(slide)
    val currentSelection by rememberUpdatedState(selectedElementIds)
    // Read from the pointer handlers, which are set up once: a press outside the
    // field has to know whether there is a caret to take away.
    val currentEditingId by rememberUpdatedState(editingElementId)
    // Whatever the id resolves to right now, and only when there is text or code
    // to edit. Which of the two it is picks the field below.
    val editing: Element? = slide.elements
        .firstOrNull { it.id == editingElementId }
        ?.takeIf { it.takesCaret }
    val currentGuides by rememberUpdatedState(guides)
    val currentShowGuides by rememberUpdatedState(showGuides)
    val currentSnapTargets by rememberUpdatedState(snapTargets)
    // The lines the move in flight has settled on, one per axis, null when that
    // axis is free. Written from the drag the way the two center flags were.
    var snappedX by remember { mutableStateOf<SnapLine?>(null) }
    var snappedY by remember { mutableStateOf<SnapLine?>(null) }
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
    // Where the field under the caret sits, for the same reason: a right-click in
    // it is reported in the canvas's space too, so a shell has one convention.
    var fieldBounds by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentFieldContextClick by rememberUpdatedState(onFieldContextClick)
    // The slide rectangle in this composable's own space, in pixels: what the
    // rulers graduate against, and what tells a guide dragged out of one where it
    // has landed. A plain value rather than the coordinates themselves, which
    // arrive as the same instance every pass and so would notify nothing.
    var slideInCanvas by remember { mutableStateOf<Rect?>(null) }
    fun measured() {
        val canvas: LayoutCoordinates = canvasBounds ?: return
        val slideRect: LayoutCoordinates = slideBounds ?: return
        if (!canvas.isAttached || !slideRect.isAttached) return
        slideInCanvas =
            Rect(canvas.localPositionOf(slideRect, Offset.Zero), slideRect.size.toSize())
    }

    // A wrapper, so the rulers have somewhere to sit that is not the slide: the
    // surface below is the slide rectangle itself, and clips to it.
    Box(modifier.onGloballyPositioned { canvasBounds = it; measured() }) {
        val rulerInset: Dp = if (showRulers) RulerThickness else 0.dp

        SlideSurface(
            modifier = Modifier.fillMaxSize().padding(start = rulerInset, top = rulerInset),
            slideBackground = slide.effectiveBackground(layout, background),
            zoom = zoom,
            slideWidth = slideWidth,
            slideHeight = slideHeight,
        ) {
            // Behind everything the slide owns, and out of every hit test below:
            // the gesture code only ever looks at `slide.elements`.
            for (element in slide.inheritedElements(layout)) ElementView(element)

            // The element under the caret keeps its place in the layout but paints
            // nothing: the text field below draws it, and two copies of the same
            // text half a pixel apart is what an editor must never show.
            for (element in slide.elements) {
                // Not composed at all rather than drawn at alpha 0: elements
                // are absolutely positioned so nothing shifts, and a code
                // block composed invisibly would still re-highlight on every
                // keystroke the field streams through the loop.
                if (element.id == editing?.id) continue
                ElementView(element)
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

            // The guide under the pointer, when guides are showing at all: a press
            // that lands on one grabs it, and only a handle outranks that. The
            // tolerance is screen dp, converted into doc units so the target is the
            // same size at every zoom, exactly as [handleAt] does it.
            fun guideAt(p: Offset): Guide? {
                if (!currentShowGuides) return null
                val tolerance: Float = GuideGrab / canvasScale
                return currentGuides.firstOrNull { guide ->
                    val distance: Float =
                        if (guide.axis == GuideAxis.Vertical) abs(p.x - guide.position)
                        else abs(p.y - guide.position)
                    distance <= tolerance
                }
            }

            // The dragged handle wins over the hovered one: mid-resize the frame
            // moves under a still pointer, and the cursor must not flicker with it.
            val cursors: ResizeCursors? = LocalResizeCursors.current
            val cursorModifier: Modifier = cursors?.cursor(resizing ?: hovered) ?: Modifier

            // Input overlay covering the whole slide
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { slideBounds = it; measured() }
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
                                                val at: Element? = elementAt(toDoc(it))
                                                onContextClick(at?.id, toCanvas(it))
                                            }

                                            shiftDown -> position
                                                ?.let { elementAt(toDoc(it)) }
                                                ?.let { onToggleElementSelection(it.id) }

                                            else -> pressedAt = position
                                        }
                                    }

                                    PointerEventType.Move -> {
                                        val rotation: Float = soleSelected()?.rotation ?: 0f
                                        hovered = position
                                            ?.let { handleAt(it, tolerance = 12f) }
                                            ?.let { resizeDirection(it, rotation) }
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
                                            // A second click on anything that takes
                                            // a caret opens it for typing, the way
                                            // every editor's does. Anything else
                                            // selects, first click or not.
                                            val again: Boolean = hit != null &&
                                                hit.id == clickedId &&
                                                now - clickedAt <= doubleClick
                                            clickedId = hit?.id
                                            clickedAt = now
                                            when {
                                                hit == null -> onSelectElement(null)
                                                again && hit.takesCaret && !hit.locked ->
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
                        // Where a guide drag was picked up, in doc units.
                        var guideStart: Offset? = null
                        fun reset() {
                            target = null
                            startFrames = emptyMap()
                            startFrame = null
                            startRotation = 0f
                            totalDx = 0f
                            totalDy = 0f
                            marqueeOrigin = null
                            guideStart = null
                            snappedX = null
                            snappedY = null
                            resizing = null
                        }

                        // The whole selection moved by this gesture: the dragged
                        // element snaps to whatever the settings offer and hands the
                        // delta it settled on to the rest. The guides ride that same
                        // snap. The dragged elements' own lines are left out, or the
                        // selection would stick to where it started.
                        fun moved(draggedId: String): List<Element> {
                            val start: Frame = startFrames[draggedId] ?: return emptyList()
                            val snapped: SnapResult = snapFrame(
                                frame = start.translate(totalDx, totalDy),
                                lines = currentSnapTargets(startFrames.keys),
                            )
                            snappedX = snapped.snappedX
                            snappedY = snapped.snappedY
                            val dx: Float = snapped.frame.x - start.x
                            val dy: Float = snapped.frame.y - start.y
                            return currentSlide.elements
                                .filter { it.id in startFrames }
                                .map { element ->
                                    val from: Frame = startFrames.getValue(element.id)
                                    element.update(frame = from.translate(dx, dy))
                                }
                        }

                        // Where the guide drag has got to, in doc units, and whether
                        // that is still on the slide: one let go off the slide is one
                        // thrown away, the way every editor's rulers work.
                        fun guidePoint(): Offset {
                            val start: Offset = guideStart ?: Offset.Zero
                            return Offset(start.x + totalDx, start.y + totalDy)
                        }

                        fun guidePosition(axis: GuideAxis): Float =
                            if (axis == GuideAxis.Vertical) guidePoint().x else guidePoint().y

                        fun onSlide(): Boolean {
                            val at: Offset = guidePoint()
                            return at.x in 0f..slideWidth && at.y in 0f..slideHeight
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
                                val guide: Guide? = guideAt(p)
                                target = when {
                                    sole != null && handle != null -> {
                                        startFrame = sole.frame
                                        startRotation = sole.rotation
                                        DragTarget.Resize(sole.id, handle)
                                    }

                                    // A guide outranks whatever is drawn under it,
                                    // but never a handle: a handle is the smaller
                                    // target and the one you went looking for.
                                    guide != null -> {
                                        guideStart = p
                                        DragTarget.GuideMove(guide.id, guide.axis)
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
                                val delta =
                                    Offset(dragAmount.x / docDensity, dragAmount.y / docDensity)
                                totalDx += delta.x
                                totalDy += delta.y
                                when (val t = target) {
                                    is DragTarget.Move -> onPreviewElements(moved(t.draggedId))
                                    is DragTarget.Resize -> onPreviewElements(resized(t))
                                    is DragTarget.GuideMove ->
                                        onPreviewGuide(t.id, t.axis, guidePosition(t.axis))

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

                                    is DragTarget.GuideMove -> {
                                        val at: Float = guidePosition(t.axis)
                                        if (onSlide()) onCommitGuide(t.id, t.axis, at)
                                        else onRemoveGuide(t.id)
                                    }
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
                                    is DragTarget.GuideMove -> onEndGuideDrag()
                                    null -> {}
                                    else -> onPreviewCancel()
                                }
                                reset()
                            },
                        )
                    }
            )

            // Under the selection ring and over everything drawn: an annotation on
            // the layout, not a part of it. Nothing here is hit-tested, the same
            // way the rings above aren't.
            if (isEditingLayouts) {
                for (element in slide.elements) {
                    val role: PlaceholderRole = element.placeholderRole ?: continue
                    PlaceholderOverlay(element, role, canvasScale)
                }
            }

            // Selection rings, one per selected element; handles only for a lone one.
            val selected: List<Element> = slide.elements.filter { it.id in selectedElementIds }
            for (element in selected) {
                // The ring stays while the caret is in the element, the handles go:
                // the field covers them, so a handle there would be a target you
                // can see and never hit.
                val handles: Boolean = selected.size == 1 && editing == null
                SelectionOverlay(element, canvasScale, handles = handles)
            }

            // The marquee, drawn from state rather than from the gesture's own vars.
            if (marquee != null) MarqueeOverlay(marquee, canvasScale)

            // The user's guides, settled ones first and the one the pointer is
            // carrying over them. The dragged line comes back through the loop like
            // everything else a gesture shows.
            if (showGuides) {
                for (guide in guides) {
                    GuideLine(guide.axis, guide.position, canvasScale, alpha = 0.7f)
                }
            }
            if (guideDrag != null) {
                GuideLine(guideDrag.axis, guideDrag.position, canvasScale, alpha = 1f)
            }

            // Alignment guides: drawn only while the move in flight sits on one,
            // which is what makes the layout guides layout guides rather than lines.
            snappedX?.let { SnapGuide(it, canvasScale) }
            snappedY?.let { SnapGuide(it, canvasScale) }

            // The secondary press inside an edit session, for a shell that draws
            // its own menu. It has to be caught on the field's own rectangle:
            // the field is above the input overlay and so the press never
            // reaches the click loop, and it has to be caught in the Initial
            // pass, which is the parent's turn, before compose's text field can
            // open the menu it draws itself. Without a bridge this is Modifier
            // and everything stays exactly as it was.
            val fieldContextClick: Modifier =
                if (fieldMenuBridge == null) Modifier
                else Modifier
                    .onGloballyPositioned { fieldBounds = it }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event: PointerEvent =
                                    awaitPointerEvent(PointerEventPass.Initial)
                                // The primary button is left entirely alone:
                                // placing a caret and sweeping a selection are
                                // the field's own, and a press consumed here
                                // would take both. Only a secondary press with
                                // nothing else held is ours, the same test the
                                // click loop makes for the same reason.
                                val secondary: Boolean =
                                    event.type == PointerEventType.Press &&
                                        event.buttons.isSecondaryPressed &&
                                        !event.buttons.isPrimaryPressed
                                if (!secondary) continue

                                val position: Offset? =
                                    event.changes.firstOrNull()?.position
                                // Consumed whether or not it can be reported:
                                // swallowing it is what keeps the built-in menu
                                // shut, and a half-handled right-click that
                                // opens both menus is the worst of the three.
                                event.changes.forEach { it.consume() }
                                if (position == null) continue

                                val field: LayoutCoordinates? = fieldBounds
                                val canvas: LayoutCoordinates? = canvasBounds
                                currentFieldContextClick(
                                    if (field != null && canvas != null &&
                                        field.isAttached && canvas.isAttached
                                    ) canvas.localPositionOf(field, position)
                                    else position
                                )
                            }
                        }
                    }

            // Last, and so on top of the input overlay: the field has to see its own
            // clicks and drags to place a caret and sweep a selection with them.
            when (editing) {
                is TextElement -> TextEditor(
                    element = editing,
                    onPreviewElements = onPreviewElements,
                    onEndTextEdit = onEndTextEdit,
                    fieldMenuBridge = fieldMenuBridge,
                    modifier = fieldContextClick,
                )

                is CodeElement -> CodeEditor(
                    element = editing,
                    onPreviewElements = onPreviewElements,
                    onEndTextEdit = onEndTextEdit,
                    fieldMenuBridge = fieldMenuBridge,
                    modifier = fieldContextClick,
                )

                is TerminalElement -> TerminalEditor(
                    element = editing,
                    onPreviewElements = onPreviewElements,
                    onEndTextEdit = onEndTextEdit,
                    fieldMenuBridge = fieldMenuBridge,
                    modifier = fieldContextClick,
                )

                is DiagramElement -> DiagramEditor(
                    element = editing,
                    onPreviewElements = onPreviewElements,
                    onEndTextEdit = onEndTextEdit,
                    fieldMenuBridge = fieldMenuBridge,
                    modifier = fieldContextClick,
                )

                is EquationElement -> EquationEditor(
                    element = editing,
                    onPreviewElements = onPreviewElements,
                    onEndTextEdit = onEndTextEdit,
                    fieldMenuBridge = fieldMenuBridge,
                    modifier = fieldContextClick,
                )

                else -> {}
            }
        }

        val ruled: Rect? = slideInCanvas
        if (showRulers && ruled != null) Rulers(
            slide = ruled,
            slideWidth = slideWidth,
            slideHeight = slideHeight,
            onPreviewGuide = onPreviewGuide,
            onCommitGuide = onCommitGuide,
            onEndGuideDrag = onEndGuideDrag,
        )
    }
}

/**
 * The two ruler strips, in the canvas' own space around the slide: the top one
 * graduated in x, the left one in y, ticked every [RulerMinorStep] doc units and
 * labelled every [RulerMajorStep].
 *
 * Pressing a strip and dragging into the slide pulls a fresh guide out of it, the
 * way every editor's rulers do. Only the strips take pointers: the box they sit
 * in has none of its own, so the slide underneath still sees its own clicks.
 *
 * [slide] is the slide rectangle in that same space, in pixels, which is all the
 * ticks and the drag need to convert between the two.
 */
@Composable
private fun Rulers(
    slide: Rect,
    slideWidth: Float,
    slideHeight: Float,
    onPreviewGuide: (id: String?, axis: GuideAxis, position: Float) -> Unit,
    onCommitGuide: (id: String?, axis: GuideAxis, position: Float) -> Unit,
    onEndGuideDrag: () -> Unit,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Box(Modifier.fillMaxSize()) {
        RulerStrip(
            horizontal = true,
            slide = slide,
            slideWidth = slideWidth,
            slideHeight = slideHeight,
            tokens = tokens,
            onPreviewGuide = onPreviewGuide,
            onCommitGuide = onCommitGuide,
            onEndGuideDrag = onEndGuideDrag,
            modifier = Modifier.fillMaxWidth().height(RulerThickness),
        )
        RulerStrip(
            horizontal = false,
            slide = slide,
            slideWidth = slideWidth,
            slideHeight = slideHeight,
            tokens = tokens,
            onPreviewGuide = onPreviewGuide,
            onCommitGuide = onCommitGuide,
            onEndGuideDrag = onEndGuideDrag,
            modifier = Modifier.fillMaxHeight().width(RulerThickness),
        )
    }
}

/**
 * One ruler strip. [horizontal] is the top one: it graduates in x and pulls
 * horizontal guides down out of itself, which is the pairing rulers have
 * everywhere. The left strip is the mirror of it.
 */
@Composable
private fun RulerStrip(
    horizontal: Boolean,
    slide: Rect,
    slideWidth: Float,
    slideHeight: Float,
    tokens: ChromeTokens,
    onPreviewGuide: (id: String?, axis: GuideAxis, position: Float) -> Unit,
    onCommitGuide: (id: String?, axis: GuideAxis, position: Float) -> Unit,
    onEndGuideDrag: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density: Density = LocalDensity.current
    // What the strip graduates: the span it covers in pixels, and in doc units.
    val span: Float = if (horizontal) slide.width else slide.height
    val units: Float = if (horizontal) slideWidth else slideHeight
    val origin: Float = if (horizontal) slide.left else slide.top
    // What it pulls out, which is the other axis: a horizontal ruler makes
    // horizontal guides, and a horizontal guide is placed by its y.
    val pulls: GuideAxis = if (horizontal) GuideAxis.Horizontal else GuideAxis.Vertical

    fun tickAt(unit: Float): Float = origin + unit / units * span

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(slide) {
                    var pointer: Offset = Offset.Zero
                    fun position(): Float = if (horizontal) {
                        (pointer.y - slide.top) / slide.height * slideHeight
                    } else {
                        (pointer.x - slide.left) / slide.width * slideWidth
                    }

                    detectDragGestures(
                        onDragStart = { pointer = it },
                        onDrag = { change, amount ->
                            change.consume()
                            pointer += amount
                            onPreviewGuide(null, pulls, position())
                        },
                        // A guide dropped anywhere but on the slide was never
                        // pulled out at all.
                        onDragEnd = {
                            if (slide.contains(pointer)) onCommitGuide(null, pulls, position())
                            else onEndGuideDrag()
                        },
                        onDragCancel = { onEndGuideDrag() },
                    )
                }
        ) {
            val hair: Float = 1.dp.toPx()
            val minor: Float = 5.dp.toPx()
            val major: Float = 9.dp.toPx()
            drawRect(tokens.panel)

            if (horizontal) {
                drawLine(
                    color = tokens.border,
                    start = Offset(0f, size.height - hair),
                    end = Offset(size.width, size.height - hair),
                    strokeWidth = hair,
                )
            } else {
                drawLine(
                    color = tokens.border,
                    start = Offset(size.width - hair, 0f),
                    end = Offset(size.width - hair, size.height),
                    strokeWidth = hair,
                )
            }

            var unit = 0f
            while (unit <= units) {
                val at: Float = tickAt(unit)
                val length: Float = if (unit % RulerMajorStep == 0f) major else minor
                if (horizontal) {
                    drawLine(
                        color = tokens.dim,
                        start = Offset(at, size.height - length),
                        end = Offset(at, size.height),
                        strokeWidth = hair,
                    )
                } else {
                    drawLine(
                        color = tokens.dim,
                        start = Offset(size.width - length, at),
                        end = Offset(size.width, at),
                        strokeWidth = hair,
                    )
                }
                unit += RulerMinorStep
            }
        }

        var unit = 0f
        while (unit <= units) {
            val at: Dp = with(density) { tickAt(unit).toDp() }
            Text(
                text = unit.toInt().toString(),
                color = tokens.dim,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                modifier =
                    if (horizontal) Modifier.offset(x = at + 2.dp, y = 1.dp)
                    else Modifier.offset(x = 1.dp, y = at + 2.dp),
            )
            unit += RulerMajorStep
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
    fieldMenuBridge: FieldMenuBridge? = null,
    modifier: Modifier = Modifier,
) {
    // Re-seeded when the caret moves to another element, with everything
    // selected: entering an edit and typing replaces the text, Keynote-style.
    var value: TextFieldValue by remember(element.id) {
        mutableStateOf(TextFieldValue(element.text, TextRange(0, element.text.length)))
    }
    val focusRequester: FocusRequester = remember { FocusRequester() }
    LaunchedEffect(element.id) { focusRequester.requestFocus() }

    val markers: VisualTransformation = remember(element.listStyle) {
        if (element.listStyle == ListStyle.None) VisualTransformation.None
        else ListMarkerTransformation(element.listStyle)
    }

    val clipboard: FieldClipboard = rememberFieldClipboard()

    // Every way the value can change goes through here: typing, and the keys
    // handled below that edit the text themselves.
    val update: (TextFieldValue) -> Unit = { edited ->
        val typed: Boolean = edited.text != value.text
        value = edited
        if (typed) onPreviewElements(listOf(element.copy(text = edited.text)))
    }

    // The same [update] the chords go through, so a shell's menu item and a Cmd
    // chord are one act as far as the loop can tell.
    RegisterFieldMenu(fieldMenuBridge, element.id, { value }, clipboard, update)

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
            .then(modifier)
    ) {
        BasicTextField(
            value = value,
            onValueChange = update,
            textStyle = element.textStyle(),
            cursorBrush = SolidColor(element.color.toComposeColor()),
            visualTransformation = markers,
            modifier = Modifier
                .align(element.alignment())
                .fillMaxWidth()
                .focusRequester(focusRequester)
                // Escape leaves the text where it is and the caret behind.
                // Enter is the field's, it inserts a newline.
                .onPreviewKeyEvent { key ->
                    if (key.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    // Mid-edit the app's Cut/Copy/Paste grey out, so the chord
                    // is the text's. Ahead of the field's own handling of it,
                    // which is the only way the edit streams as a preview.
                    if (handleClipboardKey(key, value, clipboard, update)) {
                        return@onPreviewKeyEvent true
                    }

                    when {
                        key.key == Key.Escape -> {
                            onEndTextEdit()
                            true
                        }

                        // In a list, Tab is nesting rather than focus traversal.
                        // The line the caret sits on moves; a selection across
                        // lines moves the line it started on.
                        key.key == Key.Tab && element.listStyle != ListStyle.None -> {
                            val line: Int = value.text.lineIndexOf(value.selection.start)
                            val edited: String =
                                if (key.isShiftPressed) value.text.outdentLine(line)
                                else value.text.indentLine(line)

                            if (edited != value.text) {
                                val moved: Int = value.selection.start +
                                    (edited.length - value.text.length)
                                value = TextFieldValue(
                                    text = edited,
                                    selection = TextRange(moved.coerceIn(0, edited.length)),
                                )
                                onPreviewElements(listOf(element.copy(text = edited)))
                            }
                            true
                        }

                        else -> false
                    }
                },
        )
    }
}

/** What a Tab puts in the code, rather than a tab stop the field cannot lay out. */
private const val CodeIndent: String = "    "

/**
 * [element] as an editable code block, sitting exactly where its code draws.
 *
 * [TextEditor]'s pattern throughout: the value is held here so the caret moves
 * with the key that moved it, and every change goes back through the loop as an
 * ordinary element preview, so one session is one undo entry and every other
 * shell sees the code as it is typed.
 *
 * The chrome is the renderer's, built from the same constants rather than from
 * numbers repeated here, so opening a block for typing moves nothing. The
 * highlighting is recomputed on every keystroke against the field's own text: a
 * slide-sized snippet is small enough that a re-parse per key costs nothing, and
 * an identity mapping is honest because the pass only colours, never rewrites.
 */
@Composable
private fun CodeEditor(
    element: CodeElement,
    onPreviewElements: (List<Element>) -> Unit,
    onEndTextEdit: () -> Unit,
    fieldMenuBridge: FieldMenuBridge? = null,
    modifier: Modifier = Modifier,
) {
    // Re-seeded when the caret moves to another block, everything selected the
    // way [TextEditor] seeds a text box.
    var value: TextFieldValue by remember(element.id) {
        mutableStateOf(TextFieldValue(element.code, TextRange(0, element.code.length)))
    }
    val focusRequester: FocusRequester = remember { FocusRequester() }
    LaunchedEffect(element.id) { focusRequester.requestFocus() }

    val chrome: CodeChrome = element.theme.chrome
    val style: TextStyle = TextStyle(
        color = chrome.text,
        fontSize = element.fontSize.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = (element.fontSize * CodeLineHeight).sp,
    )

    val clipboard: FieldClipboard = rememberFieldClipboard()

    // Every way the value can change goes through here: typing, and the keys
    // handled below that edit the code themselves.
    val update: (TextFieldValue) -> Unit = { edited ->
        val typed: Boolean = edited.text != value.text
        value = edited
        if (typed) onPreviewElements(listOf(element.copy(code = edited.text)))
    }

    // As the text box: the menu's verbs are the chords' verbs.
    RegisterFieldMenu(fieldMenuBridge, element.id, { value }, clipboard, update)

    // Colouring only, so every offset in the text is its own offset in what is
    // drawn: no mapping to keep, unlike the list markers above. Remembered, and
    // caching its last parse inside, because the field re-applies a
    // transformation on every recomposition: handed a fresh instance each pass
    // it would re-tokenize on every cursor blink, not just on every keystroke.
    val highlighted: VisualTransformation = remember(element.language, element.theme) {
        var lastText = ""
        var lastParse = AnnotatedString("")
        VisualTransformation { text ->
            if (text.text != lastText || text.text.isEmpty()) {
                lastText = text.text
                lastParse = highlightCode(text.text, element.language, element.theme)
            }
            TransformedText(lastParse, OffsetMapping.Identity)
        }
    }

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
            .then(modifier)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(chrome.background, CodeCorner)
                .border(1.dp, chrome.border, CodeCorner)
                .clip(CodeCorner)
                .padding(CodePadding)
        ) {
            Row {
                // Counted off the field rather than off the element, so a line
                // added or taken away is numbered as it is typed.
                if (element.showLineNumbers) {
                    Text(
                        text = (1..value.text.count { it == '\n' } + 1).joinToString("\n"),
                        color = chrome.gutter,
                        fontSize = element.fontSize.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = (element.fontSize * CodeLineHeight).sp,
                        softWrap = false,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.padding(end = CodeGutterGap),
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = update,
                    textStyle = style,
                    cursorBrush = SolidColor(chrome.text),
                    visualTransformation = highlighted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        // Escape leaves the code where it is and the caret
                        // behind. Enter is the field's, it inserts a newline.
                        .onPreviewKeyEvent { key ->
                            if (key.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                            // As the text box: mid-edit the chord is the code's.
                            if (handleClipboardKey(key, value, clipboard, update)) {
                                return@onPreviewKeyEvent true
                            }

                            when (key.key) {
                                Key.Escape -> {
                                    onEndTextEdit()
                                    true
                                }

                                // In code, Tab is an indent rather than focus
                                // traversal: four spaces where the caret is, and
                                // a selection is replaced by them like any typing
                                // would replace it.
                                Key.Tab -> {
                                    val start: Int = value.selection.min
                                    val end: Int = value.selection.max
                                    val edited: String =
                                        value.text.replaceRange(start, end, CodeIndent)
                                    value = TextFieldValue(
                                        text = edited,
                                        selection = TextRange(start + CodeIndent.length),
                                    )
                                    onPreviewElements(listOf(element.copy(code = edited)))
                                    true
                                }

                                else -> false
                            }
                        },
                )
            }
        }
    }
}

/**
 * [element] as an editable terminal, sitting exactly where its transcript draws.
 *
 * [CodeEditor]'s pattern throughout, minus the colouring: the transcript is
 * typed as one plain string, prompts and all, because that is what the document
 * holds and what the renderer classifies at draw time. Nothing here is a command
 * or an output line yet, so nothing here is green or dim; what is typed goes
 * back through the loop as an ordinary element preview and comes out coloured on
 * the next repaint of the block behind the field.
 *
 * The chrome is the renderer's own title bar and its own constants, so opening a
 * terminal for typing moves nothing. Tab is left to the field as plain focus
 * traversal: a shell transcript is not indented the way code is.
 */
@Composable
private fun TerminalEditor(
    element: TerminalElement,
    onPreviewElements: (List<Element>) -> Unit,
    onEndTextEdit: () -> Unit,
    fieldMenuBridge: FieldMenuBridge? = null,
    modifier: Modifier = Modifier,
) {
    // Re-seeded when the caret moves to another terminal, everything selected
    // the way [TextEditor] seeds a text box.
    var value: TextFieldValue by remember(element.id) {
        mutableStateOf(TextFieldValue(element.text, TextRange(0, element.text.length)))
    }
    val focusRequester: FocusRequester = remember { FocusRequester() }
    LaunchedEffect(element.id) { focusRequester.requestFocus() }

    val style: TextStyle = TextStyle(
        color = TerminalCommand,
        fontSize = element.fontSize.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = (element.fontSize * TerminalLineHeight).sp,
    )

    val clipboard: FieldClipboard = rememberFieldClipboard()

    // Every way the value can change goes through here, as the code block's does.
    val update: (TextFieldValue) -> Unit = { edited ->
        val typed: Boolean = edited.text != value.text
        value = edited
        if (typed) onPreviewElements(listOf(element.copy(text = edited.text)))
    }

    // As the text box: the menu's verbs are the chords' verbs.
    RegisterFieldMenu(fieldMenuBridge, element.id, { value }, clipboard, update)

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
            .then(modifier)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(TerminalBackground, TerminalCorner)
                .border(1.dp, TerminalBorder, TerminalCorner)
                .clip(TerminalCorner)
        ) {
            Column {
                if (element.showTitleBar) TerminalTitleBarView(element)

                BasicTextField(
                    value = value,
                    onValueChange = update,
                    textStyle = style,
                    cursorBrush = SolidColor(TerminalCommand),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(TerminalPadding)
                        .focusRequester(focusRequester)
                        // Escape leaves the transcript where it is and the caret
                        // behind. Enter is the field's, it inserts a newline.
                        .onPreviewKeyEvent { key ->
                            if (key.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                            // As the text box: mid-edit the chord is the transcript's.
                            if (handleClipboardKey(key, value, clipboard, update)) {
                                return@onPreviewKeyEvent true
                            }

                            if (key.key != Key.Escape) return@onPreviewKeyEvent false
                            onEndTextEdit()
                            true
                        },
                )
            }
        }
    }
}

/**
 * [element] as its editable source, on a sheer sheet over the chart it draws.
 *
 * [TerminalEditor]'s pattern, with the one difference the kind forces: what is
 * typed here is not what is drawn. A diagram's source is a description of the
 * picture rather than the picture, so a field styled like the element would be
 * unreadable and a field beside it would put the two out of sight of each other.
 * The sheet is laid over the element's own frame instead, dark enough to read
 * the source on and sheer enough to watch the chart redraw through it as the
 * source is typed, which is the whole point of a diagram you write.
 *
 * The chart under the sheet is drawn here rather than by the canvas' element
 * loop, which skips whatever is being edited. Each keystroke goes back through
 * the loop as an ordinary element preview, so the drawing behind the field is
 * the parse of what has been typed so far, badly-formed lines and all.
 *
 * Text is set well under the chart's own size so a source longer than the chart
 * is tall still fits. Tab is left to the field as plain focus traversal: the
 * indentation a diagram's source carries is typed, not inserted for you.
 */
@Composable
private fun DiagramEditor(
    element: DiagramElement,
    onPreviewElements: (List<Element>) -> Unit,
    onEndTextEdit: () -> Unit,
    fieldMenuBridge: FieldMenuBridge? = null,
    modifier: Modifier = Modifier,
) {
    // Re-seeded when the caret moves to another diagram, everything selected
    // the way [TextEditor] seeds a text box.
    var value: TextFieldValue by remember(element.id) {
        mutableStateOf(TextFieldValue(element.source, TextRange(0, element.source.length)))
    }
    val focusRequester: FocusRequester = remember { FocusRequester() }
    LaunchedEffect(element.id) { focusRequester.requestFocus() }

    val style: TextStyle = TextStyle(
        color = SourceText,
        fontSize = (element.fontSize * SourceScale).coerceAtLeast(SourceMinSize).sp,
        fontFamily = FontFamily.Monospace,
    )

    val clipboard: FieldClipboard = rememberFieldClipboard()

    // Every way the value can change goes through here, as the terminal's does.
    val update: (TextFieldValue) -> Unit = { edited ->
        val typed: Boolean = edited.text != value.text
        value = edited
        if (typed) onPreviewElements(listOf(element.copy(source = edited.text)))
    }

    // As the text box: the menu's verbs are the chords' verbs.
    RegisterFieldMenu(fieldMenuBridge, element.id, { value }, clipboard, update)

    // The chart, still drawing, since the sheet over it lets it through.
    ElementView(element)

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
            .then(modifier)
    ) {
        BasicTextField(
            value = value,
            onValueChange = update,
            textStyle = style,
            cursorBrush = SolidColor(SourceText),
            modifier = Modifier
                .fillMaxSize()
                .background(SourceSheet, SourceCorner)
                .border(1.dp, SourceBorder, SourceCorner)
                .clip(SourceCorner)
                .padding(SourcePadding)
                .focusRequester(focusRequester)
                // Escape leaves the source where it is and the caret behind.
                // Enter is the field's, it starts the next line of the chart.
                .onPreviewKeyEvent { key ->
                    if (key.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    // As the text box: mid-edit the chord is the source's.
                    if (handleClipboardKey(key, value, clipboard, update)) {
                        return@onPreviewKeyEvent true
                    }

                    if (key.key != Key.Escape) return@onPreviewKeyEvent false
                    onEndTextEdit()
                    true
                },
        )
    }
}

/**
 * [element] as its editable LaTeX, on a sheer sheet over the math it sets.
 *
 * [DiagramEditor]'s pattern exactly, for the same reason: the source is a
 * description of the picture rather than the picture, so it is typed over the
 * element on a sheet the setting shows through. Watching `\frac` become a
 * fraction as it is typed is what makes the syntax learnable without a manual.
 *
 * The math under the sheet is drawn here rather than by the canvas' element
 * loop, which skips whatever is being edited. Each keystroke goes back through
 * the loop as an ordinary element preview, so what is set behind the field is
 * the parse of what has been typed so far, half-finished commands and all.
 *
 * Monospace and well under the equation's own size: the source is read as code
 * (braces, backslashes) while the setting behind it is read as math, and an
 * equation's size is a display size that would run a couple of terms off the box
 * if the source were set at it.
 */
@Composable
private fun EquationEditor(
    element: EquationElement,
    onPreviewElements: (List<Element>) -> Unit,
    onEndTextEdit: () -> Unit,
    fieldMenuBridge: FieldMenuBridge? = null,
    modifier: Modifier = Modifier,
) {
    // Re-seeded when the caret moves to another equation, everything selected
    // the way [TextEditor] seeds a text box.
    var value: TextFieldValue by remember(element.id) {
        mutableStateOf(TextFieldValue(element.latex, TextRange(0, element.latex.length)))
    }
    val focusRequester: FocusRequester = remember { FocusRequester() }
    LaunchedEffect(element.id) { focusRequester.requestFocus() }

    val style: TextStyle = TextStyle(
        color = SourceText,
        fontSize = (element.fontSize * SourceScale).coerceAtLeast(SourceMinSize).sp,
        fontFamily = FontFamily.Monospace,
    )

    val clipboard: FieldClipboard = rememberFieldClipboard()

    // Every way the value can change goes through here, as the diagram's does.
    val update: (TextFieldValue) -> Unit = { edited ->
        val typed: Boolean = edited.text != value.text
        value = edited
        if (typed) onPreviewElements(listOf(element.copy(latex = edited.text)))
    }

    // As the text box: the menu's verbs are the chords' verbs.
    RegisterFieldMenu(fieldMenuBridge, element.id, { value }, clipboard, update)

    // The math, still setting, since the sheet over it lets it through.
    ElementView(element)

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
            .then(modifier)
    ) {
        BasicTextField(
            value = value,
            onValueChange = update,
            textStyle = style,
            cursorBrush = SolidColor(SourceText),
            modifier = Modifier
                .fillMaxSize()
                .background(SourceSheet, SourceCorner)
                .border(1.dp, SourceBorder, SourceCorner)
                .clip(SourceCorner)
                .padding(SourcePadding)
                .focusRequester(focusRequester)
                // Escape leaves the source where it is and the caret behind.
                // Enter is the field's: a long equation is typed over lines even
                // though the setting reads as one.
                .onPreviewKeyEvent { key ->
                    if (key.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    // As the text box: mid-edit the chord is the source's.
                    if (handleClipboardKey(key, value, clipboard, update)) {
                        return@onPreviewKeyEvent true
                    }

                    if (key.key != Key.Escape) return@onPreviewKeyEvent false
                    onEndTextEdit()
                    true
                },
        )
    }
}

/**
 * The markers the canvas draws in front of a list's lines, drawn into the field
 * the same way without ever being in the text.
 *
 * The text stays plain: a line's nesting is its leading tabs, and a marker is
 * worked out from them by [listMarkers], the very call the renderer makes. Tabs
 * come out as spaces here because a field cannot lay out a tab stop, which is
 * the one place the caret's text and the drawn text part company.
 *
 * The mapping is the fiddly half: every offset in the text has to land somewhere
 * in what is drawn and back again, or the caret ends up a marker's width away
 * from the character it is on.
 */
internal class ListMarkerTransformation(private val style: ListStyle) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val lines: List<String> = text.text.split("\n")
        val markers: List<String> = listMarkers(text.text, style)

        // Per line: where it starts in each string, how many tabs it opens with,
        // and how long everything before its body is once drawn.
        val levels: List<Int> = lines.map { it.listIndentLevel() }
        val prefixes: List<String> = lines.mapIndexed { index, line ->
            val indent: String = " ".repeat(line.listIndentLevel() * SpacesPerLevel)
            if (markers[index].isEmpty()) indent else "$indent${markers[index]} "
        }
        val drawn: List<String> = lines.mapIndexed { index, line ->
            "${prefixes[index]}${line.listBody()}"
        }

        val starts: List<Int> = lines.runningFold(0) { start, line -> start + line.length + 1 }
        val drawnStarts: List<Int> = drawn.runningFold(0) { start, line -> start + line.length + 1 }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val line: Int = lineAt(starts, lines, offset)
                val within: Int = offset.coerceIn(0, text.text.length) - starts[line]
                if (within < levels[line]) return drawnStarts[line] + within * SpacesPerLevel

                val body: Int = within - levels[line]
                return drawnStarts[line] + prefixes[line].length + body
            }

            override fun transformedToOriginal(offset: Int): Int {
                val line: Int = lineAt(drawnStarts, drawn, offset)
                val within: Int = offset.coerceIn(0, drawn.sumLength()) - drawnStarts[line]
                val indent: Int = levels[line] * SpacesPerLevel
                if (within < indent) return starts[line] + within / SpacesPerLevel
                if (within <= prefixes[line].length) return starts[line] + levels[line]

                return starts[line] + levels[line] + (within - prefixes[line].length)
            }
        }

        return TransformedText(AnnotatedString(drawn.joinToString("\n")), mapping)
    }

    /** The line an offset falls on: the first whose end it has not passed. */
    private fun lineAt(starts: List<Int>, lines: List<String>, offset: Int): Int {
        for (index in lines.indices) {
            if (offset <= starts[index] + lines[index].length) return index
        }
        return lines.lastIndex
    }

    private fun List<String>.sumLength(): Int = sumOf { it.length } + size - 1
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

/**
 * What a placeholder wears while its layout is edited: a dashed hairline round
 * its frame and its role in the top-left corner.
 *
 * Every measurement is divided by [scale], the way the selection ring's is, so
 * the annotation stays the same size on screen at every zoom rather than growing
 * with the slide it sits on.
 */
@Composable
private fun PlaceholderOverlay(element: Element, role: PlaceholderRole, scale: Float) {
    val frame: Frame = element.frame

    Box(
        Modifier
            .offset(frame.x.dp, frame.y.dp)
            .size(frame.width.dp, frame.height.dp)
            .graphicsLayer {
                rotationZ = element.rotation
                transformOrigin = TransformOrigin.Center
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val dash: Float = (4f / scale).dp.toPx()
            drawRect(
                color = PlaceholderMark,
                style = Stroke(
                    width = (1f / scale).dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                ),
            )
        }
        Text(
            text = role.name,
            color = PlaceholderMark,
            fontSize = (10f / scale).sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = (4f / scale).dp, top = (2f / scale).dp),
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

/**
 * One user guide across the slide: a hairline in the document accent, drawn at
 * constant screen width whatever the zoom. [alpha] separates the settled guides
 * from the one the pointer is carrying.
 */
@Composable
private fun GuideLine(axis: GuideAxis, position: Float, scale: Float, alpha: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke: Float = (1f / scale).dp.toPx()
        val at: Float = position.dp.toPx()
        if (axis == GuideAxis.Vertical) {
            drawLine(
                color = Accent.copy(alpha = alpha),
                start = Offset(at, 0f),
                end = Offset(at, size.height),
                strokeWidth = stroke,
            )
        } else {
            drawLine(
                color = Accent.copy(alpha = alpha),
                start = Offset(0f, at),
                end = Offset(size.width, at),
                strokeWidth = stroke,
            )
        }
    }
}

/** What the chip over a snapped line says. Short: it sits on the slide. */
private fun SnapLine.label(): String = when (kind) {
    SnapKind.Center -> if (axis == GuideAxis.Vertical) "center x" else "center y"
    SnapKind.Edges -> "edge"
    SnapKind.Objects -> "object"
    SnapKind.Guides -> "guide"
}

/**
 * The alignment guide over the line a drag has settled on: 1px dashed yellow
 * running 12dp past both slide edges, with a label chip at the near edge. Screen
 * space, like every other editing affordance, so it holds its size at any zoom.
 */
@Composable
private fun SnapGuide(line: SnapLine, scale: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke: Float = (1f / scale).dp.toPx()
        val over: Float = (12f / scale).dp.toPx()
        val dash: PathEffect = PathEffect.dashPathEffect(
            floatArrayOf((5f / scale).dp.toPx(), (4f / scale).dp.toPx()),
        )
        val at: Float = line.position.dp.toPx()
        if (line.axis == GuideAxis.Vertical) {
            drawLine(
                color = GuideYellow,
                start = Offset(at, -over),
                end = Offset(at, size.height + over),
                strokeWidth = stroke,
                pathEffect = dash,
            )
        } else {
            drawLine(
                color = GuideYellow,
                start = Offset(-over, at),
                end = Offset(size.width + over, at),
                strokeWidth = stroke,
                pathEffect = dash,
            )
        }
    }

    val chip: Modifier =
        if (line.axis == GuideAxis.Vertical) {
            Modifier.offset((line.position - 24f / scale).dp, (8f / scale).dp)
        } else {
            Modifier.offset((8f / scale).dp, (line.position - 18f / scale).dp)
        }

    Box(
        chip
            .background(GuideYellow, RoundedCornerShape((3f / scale).dp))
            .padding(horizontal = (6f / scale).dp, vertical = (1f / scale).dp)
    ) {
        Text(
            text = line.label(),
            color = Color(0xFF17181C),
            fontSize = (10f / scale).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

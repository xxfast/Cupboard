@file:OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)

package io.github.xxfast.cupboard.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.dp
import io.github.xxfast.cupboard.Cupboard
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.editor.AlignEdge
import io.github.xxfast.cupboard.editor.Axis
import io.github.xxfast.cupboard.editor.EditorCanvas
import io.github.xxfast.cupboard.editor.LocalResizeCursors
import io.github.xxfast.cupboard.editor.ResizeCursors
import io.github.xxfast.cupboard.editor.ResizeDirection
import io.github.xxfast.cupboard.editor
import io.github.xxfast.cupboard.play.PresentationPlayer
import io.github.xxfast.cupboard.screens.editor.EditorState
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import io.github.xxfast.cupboard.screens.editor.FlipAxis
import io.github.xxfast.cupboard.screens.editor.InspectorTab
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.jetbrains.skia.EncodedImageFormat
import platform.AppKit.NSCursor
import platform.AppKit.NSCursorFrameResizeDirectionsAll
import platform.AppKit.NSCursorFrameResizePosition
import platform.AppKit.NSCursorFrameResizePositionBottomRight
import platform.AppKit.NSCursorFrameResizePositionTopRight
import platform.AppKit.NSImage
import platform.AppKit.NSView
import platform.Foundation.NSData
import platform.Foundation.NSMakeSize
import platform.Foundation.NSProcessInfo
import platform.Foundation.dataWithBytes

/**
 * The chrome the shell floats over the canvas, in canvas coordinates. Fit has to
 * agree with the shell about them, so they are stated once, here.
 */
private val SIDEBAR = 212.dp
private val INSPECTOR = 282.dp
private val TOOLBAR = 52.dp
private val NOTES = 122.dp

/** macOS 15 is where the frame-resize cursors landed; the app still runs on 14. */
private val macOsMajorVersion: Long =
    NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion }

private fun resizeCursor(direction: ResizeDirection?): NSCursor = when (direction) {
    ResizeDirection.Horizontal -> NSCursor.resizeLeftRightCursor
    ResizeDirection.Vertical -> NSCursor.resizeUpDownCursor
    // Named for a corner, not an axis: BottomRight draws "\", TopRight "/".
    ResizeDirection.DiagonalDown -> frameResizeCursor(NSCursorFrameResizePositionBottomRight)
    ResizeDirection.DiagonalUp -> frameResizeCursor(NSCursorFrameResizePositionTopRight)
    null -> NSCursor.arrowCursor
}

private fun frameResizeCursor(position: NSCursorFrameResizePosition): NSCursor =
    if (macOsMajorVersion < 15) NSCursor.crosshairCursor
    else NSCursor.frameResizeCursorFromPosition(position, NSCursorFrameResizeDirectionsAll)

/**
 * Resize cursors set straight on AppKit. Compose's macOS backend can only show
 * its own internal cursor type, so a `PointerIcon` built out here would resolve
 * to a plain arrow; [NSCursor] is the way in. Putting the arrow back on dispose
 * is what covers the pointer leaving a handle, since that arrives as a new
 * direction (null) and disposes the old effect.
 */
private val AppKitResizeCursors = ResizeCursors { direction ->
    DisposableEffect(direction) {
        resizeCursor(direction).set()
        onDispose { NSCursor.arrowCursor.set() }
    }
    Modifier
}

/** One row of the navigator outline, for the native (SwiftUI) sidebar. */
class OutlineRow(
    val title: String,
    val depth: Int,
    val slideIndex: Int,
    val hasChildren: Boolean,
    val collapsed: Boolean,
)

/**
 * The primary selected element's shared properties, flattened for the native
 * inspector: the first of a selection that may hold many, which is what every
 * single-element control speaks for. The setters it feeds edit the whole
 * selection, so this is what the inspector shows, not what it edits.
 *
 * A value, not a handle: the shell re-reads it whenever [EditorHost.onChange]
 * fires and sends edits back through the setters, so nothing here can drift out
 * of step with the document. [Element] itself never crosses the boundary; a
 * shell holding one could edit its way around a lock.
 */
class ElementProps(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val opacity: Float,
    /** Degrees clockwise, applied around the frame's center. */
    val rotation: Float,
    val flippedHorizontally: Boolean,
    val flippedVertically: Boolean,
    val locked: Boolean,
    /** "Text", "Shape", "Image", "Code" or "Group": what the inspector titles itself. */
    val kind: String,
)

/**
 * A running presentation: a Compose view playing a snapshot of the document.
 * The host shows [view] full screen and calls [dispose] when it tears it down.
 * Playback keys are the player's own business; it only calls back on exit.
 */
class PlaySession internal constructor(
    document: Document,
    startIndex: Int,
    onExit: () -> Unit,
) {
    private val composeView = ComposeNSView {
        PresentationPlayer(
            document = document,
            startIndex = startIndex,
            modifier = Modifier.fillMaxSize(),
            onExit = onExit,
        )
    }

    val view: NSView = composeView

    fun dispose() {
        composeView.dispose()
    }
}

/**
 * Adapter between a native macOS shell and the shared [EditorViewModel]: exposes
 * the Compose canvas as an NSView, and gives the SwiftUI sidebar its outline,
 * selection and change-notification API in ObjC-friendly shapes.
 * Only the canvas is Compose; the chrome around it is the host's business.
 * No state lives here, it all belongs to the view model.
 */
class EditorHost {
    // Private: the framework only exports this file's types, so the view model
    // stays a Kotlin-side detail. Swift talks to it through the methods below.
    // The factory owns the store and the path, which is deliberately the one the
    // Compose Desktop shell uses: two front ends onto one document, not two apps.
    private val viewModel = Cupboard.editor()

    /** The state the sidebar reads right now. Never stale: the canvas and this
     * are the same flow, so an edit made in Compose shows up here too. */
    private val state: EditorState get() = viewModel.states.value

    private class Thumbnail(val slide: Slide, val width: Int, val image: NSImage)

    private val thumbnails = mutableMapOf<String, Thumbnail>()

    private val scope = CoroutineScope(Dispatchers.Main)

    /** View-local, not document state: null is Fit, otherwise a scale factor. */
    private val zoom = MutableStateFlow<Float?>(null)

    /** The well follows the host's appearance; slide content never does. */
    private val darkChrome = MutableStateFlow(true)

    /** The full-bleed content layer: the shell floats its glass panels over this. */
    val view: NSView = ComposeNSView {
        val state: EditorState by viewModel.states.collectAsState()
        val scale: Float? by zoom.collectAsState()
        val dark: Boolean by darkChrome.collectAsState()

        // Paint the canvas well ourselves: unpainted scene regions are undefined
        // (white) instead of showing the SwiftUI background through.
        val well = if (dark) Color(0xFF17181C) else Color(0xFFDCDCDA)
        // Fit measures against the space the slide may actually occupy, so the
        // gutters are the panels that are open right now, not a constant. The
        // canvas layer is still the whole window: at any fixed zoom the slide is
        // window-centred and runs under the glass, which is what sells it.
        val gutters = PaddingValues(
            start = if (state.sidebarOpen) SIDEBAR else 0.dp,
            top = TOOLBAR,
            end = if (state.inspectorOpen) INSPECTOR else 0.dp,
            bottom = if (state.showNotes) NOTES else 0.dp,
        )
        CompositionLocalProvider(LocalResizeCursors provides AppKitResizeCursors) {
            Box(Modifier.fillMaxSize().background(well), contentAlignment = Alignment.Center) {
                EditorCanvas(
                    slide = state.selectedSlide,
                    selectedElementIds = state.selectedElementIds,
                    marquee = state.marquee,
                    onSelectElement = viewModel::onSelectElement,
                    onToggleElementSelection = viewModel::onToggleElementSelection,
                    onPreviewMarquee = viewModel::onPreviewMarquee,
                    onEndMarquee = viewModel::onEndMarquee,
                    onUpdateElements = viewModel::onUpdateElements,
                    onPreviewElements = viewModel::onPreviewElements,
                    onPreviewCancel = viewModel::onCancelPreview,
                    modifier = if (scale == null) Modifier.fillMaxSize().padding(gutters)
                    else Modifier.fillMaxSize(),
                    zoom = scale,
                )
            }
        }
    }

    /** Zoom as a whole percentage, 0 meaning Fit. Kept ObjC-friendly on purpose. */
    fun zoomPercent(): Int = zoom.value?.let { (it * 100).roundToInt() } ?: 0

    fun setZoomPercent(percent: Int) {
        zoom.value = if (percent <= 0) null else percent / 100f
    }

    /** Follows the host's appearance. Chrome only: slide content stays dark. */
    fun setDarkChrome(dark: Boolean) {
        darkChrome.value = dark
    }

    fun outline(): List<OutlineRow> = state.outline().map { entry ->
        OutlineRow(
            title = entry.title,
            depth = entry.depth,
            slideIndex = entry.slideIndex,
            hasChildren = entry.hasChildren,
            collapsed = entry.collapsed,
        )
    }

    fun toggleCollapsed(index: Int) {
        val slide = state.document.slides.getOrNull(index) ?: return
        viewModel.onToggleCollapsed(slide.id)
    }

    fun undo() {
        viewModel.onUndo()
    }

    fun redo() {
        viewModel.onRedo()
    }

    /** What the Edit menu greys out. Fresh whenever [onChange] has just fired. */
    fun canUndo(): Boolean = state.canUndo

    fun canRedo(): Boolean = state.canRedo

    fun selectedSlideIndex(): Int = state.selectedSlideIndex()

    fun selectSlide(index: Int) {
        viewModel.onSelectSlideAt(index)
    }

    /**
     * Panel visibility, straight off the shared state. The shell reads these
     * rather than keeping its own copies: chrome that a menu item, a click and
     * the canvas can all move needs one owner, and it is the view model.
     */
    fun sidebarOpen(): Boolean = state.sidebarOpen

    fun inspectorOpen(): Boolean = state.inspectorOpen

    fun inspectorTab(): InspectorTab = state.inspectorTab

    fun showNotes(): Boolean = state.showNotes

    /** Notes for the selected slide, what the speaker-notes strip shows. */
    fun slideNotes(): String = state.selectedSlide.notes

    fun toggleSidebar() {
        viewModel.onToggleSidebar()
    }

    fun toggleNotes() {
        viewModel.onToggleNotes()
    }

    fun selectInspectorTab(tab: InspectorTab) {
        viewModel.onSelectInspectorTab(tab)
    }

    fun closeInspector() {
        viewModel.onCloseInspector()
    }

    /**
     * What the Format inspector shows, or null when nothing is selected: the
     * primary element, with the rest of the selection behind it.
     *
     * Every setter below resolves the selection the same way, at call time, so a
     * click that lands after the selection moved edits nothing rather than the
     * wrong elements.
     */
    fun selectedElement(): ElementProps? = state.primaryElement?.let { element ->
        ElementProps(
            x = element.frame.x,
            y = element.frame.y,
            width = element.frame.width,
            height = element.frame.height,
            opacity = element.opacity,
            rotation = element.rotation,
            flippedHorizontally = element.flippedHorizontally,
            flippedVertically = element.flippedVertically,
            locked = element.locked,
            kind = when (element) {
                is TextElement -> "Text"
                is ShapeElement -> "Shape"
                is ImageElement -> "Image"
                is CodeElement -> "Code"
                is GroupElement -> "Group"
            },
        )
    }

    /** How many elements are selected, for the inspector's "N selected" line. */
    fun selectionCount(): Int = state.selectedElements.size

    /** Two unlocked elements are what a group is made of. */
    fun canGroup(): Boolean {
        val elements: List<Element> = state.selectedElements
        return elements.size >= 2 && elements.count { !it.locked } >= 2
    }

    /**
     * Ungrouping is a single-group act: two groups selected is a batch nothing
     * else in the app does, so the menu item goes dead rather than guessing.
     */
    fun canUngroup(): Boolean {
        val group: Element = state.selectedElements.singleOrNull() ?: return false
        return group is GroupElement && !group.locked
    }

    // The selection an edit may touch. A locked element answers to nothing but
    // the unlock, which the presenter enforces too; this keeps the shell from
    // sending edits it knows will be dropped.
    private fun editable(): List<Element> = state.selectedElements.filter { !it.locked }

    /**
     * Commits a typed frame onto every selected element, the way Keynote's
     * inspector does: typing 40 into X puts them all at x = 40 rather than moving
     * them as a block. Sizes floor at one document unit: an element with no
     * extent has nothing left to click, so there is no way to select it back out.
     */
    fun setSelectedElementFrame(x: Float, y: Float, width: Float, height: Float) {
        val frame = Frame(
            x = x,
            y = y,
            width = width.coerceAtLeast(1f),
            height = height.coerceAtLeast(1f),
        )
        val edits: List<Element> = editable().map { it.update(frame = frame) }
        if (edits.isEmpty()) return
        viewModel.onUpdateElements(edits)
    }

    /**
     * [commit] false is a slider still under the thumb: it folds into the document
     * so the canvas redraws, but makes no history entry. True is the release, and
     * the whole drag lands as one undo step however many elements it moved.
     */
    fun setSelectedElementOpacity(opacity: Float, commit: Boolean) {
        val edits: List<Element> = editable().map { it.update(opacity = opacity.coerceIn(0f, 1f)) }
        if (edits.isEmpty()) return
        if (commit) viewModel.onUpdateElements(edits) else viewModel.onPreviewElements(edits)
    }

    /** Degrees clockwise. Typed, so it commits: the shell has no rotate gesture yet. */
    fun setSelectedElementRotation(degrees: Float) {
        val edits: List<Element> = editable().map { it.update(rotation = degrees) }
        if (edits.isEmpty()) return
        viewModel.onUpdateElements(edits)
    }

    fun flipSelectedElement(axis: FlipAxis) {
        val ids: List<String> = state.selectedElementIds
        if (ids.isEmpty()) return
        viewModel.onFlipElements(ids, axis)
    }

    fun reorderSelectedElement(move: ZOrderMove) {
        val ids: List<String> = state.selectedElementIds
        if (ids.isEmpty()) return
        viewModel.onReorderElements(ids, move)
    }

    /** The one edit a locked element still answers to. The primary decides which way. */
    fun toggleSelectedElementLock() {
        val primary: Element = state.primaryElement ?: return
        viewModel.onSetElementsLocked(state.selectedElementIds, !primary.locked)
    }

    /** Wraps the selection into one group. Does nothing unless [canGroup]. */
    fun groupSelection() {
        if (!canGroup()) return
        viewModel.onGroupElements(state.selectedElementIds)
    }

    /** Breaks the selected group apart. Does nothing unless [canUngroup]. */
    fun ungroupSelection() {
        if (!canUngroup()) return
        val group: Element = state.primaryElement ?: return
        viewModel.onUngroupElements(group.id)
    }

    /** Two or more line up on their own bounds, a lone one on the slide. */
    fun alignSelection(edge: AlignEdge) {
        viewModel.onAlignElements(edge)
    }

    /** Equalizes the gaps across the selection. Needs three unlocked members. */
    fun distributeSelection(axis: Axis) {
        viewModel.onDistributeElements(axis)
    }

    /**
     * One unlocked element in the selection is enough: the delete carries the
     * whole selection and the presenter skips the locked ones, so the item stays
     * live as long as it has something to take.
     */
    fun canDelete(): Boolean = editable().isNotEmpty()

    /** Takes the unlocked part of the selection off the slide. One undo entry. */
    fun deleteSelection() {
        if (!canDelete()) return
        viewModel.onDeleteElements(state.selectedElementIds)
    }

    /** A slide of nothing but locked elements has nothing left to clear. */
    fun canClearAll(): Boolean = state.selectedSlide.elements.any { !it.locked }

    /** Empties the selected slide of everything unlocked. Locked elements stay. */
    fun clearAll() {
        if (!canClearAll()) return
        viewModel.onClearAll()
    }

    /**
     * Removes the selected slide, and the run it was hiding if it was collapsed.
     * Always available: the last slide out leaves a fresh blank one behind, so
     * there is no state where this has nothing to do.
     */
    fun deleteSelectedSlide() {
        viewModel.onDeleteSlide(state.selectedSlide.id)
    }

    /**
     * The clipboard, as the Edit menu sees it. It is the app's own, not
     * NSPasteboard: elements carry style, builds and grouping that no system
     * flavour describes, so [canPaste] answers off the shared state rather than
     * off what some other app last copied.
     *
     * Cut and duplicate need something unlocked, the same rule delete follows.
     * Copy only needs a selection: reading a locked element is always allowed.
     *
     * `copy` is a reserved ObjC method family, so the exporter renames every
     * `copyX` to `doCopyX`, which is what the shell calls. `@ObjCName` doesn't
     * buy the name back: the rename is applied after it.
     */
    fun canCut(): Boolean = editable().isNotEmpty()

    fun cutSelection() {
        if (!canCut()) return
        viewModel.onCutElements(state.selectedElementIds)
    }

    fun canCopy(): Boolean = state.selectedElements.isNotEmpty()

    fun copySelection() {
        if (!canCopy()) return
        viewModel.onCopyElements(state.selectedElementIds)
    }

    fun canPaste(): Boolean = state.canPaste

    fun paste() {
        if (!canPaste()) return
        viewModel.onPaste()
    }

    fun canDuplicate(): Boolean = editable().isNotEmpty()

    fun duplicateSelection() {
        if (!canDuplicate()) return
        viewModel.onDuplicateElements(state.selectedElementIds)
    }

    /** Style comes off the primary alone, the one the inspector speaks for. */
    fun canCopyStyle(): Boolean = state.primaryElement != null

    fun copyStyle() {
        val primary: Element = state.primaryElement ?: return
        viewModel.onCopyStyle(primary.id)
    }

    /** Needs a style on the clipboard and something unlocked to wear it. */
    fun canPasteStyle(): Boolean = state.canPasteStyle && editable().isNotEmpty()

    fun pasteStyle() {
        if (!canPasteStyle()) return
        viewModel.onPasteStyle(state.selectedElementIds)
    }

    /**
     * Slide-level clipboard. Always available for the same reason the delete is:
     * there is always a selected slide, and cutting the last one leaves a blank
     * one behind.
     */
    fun cutSelectedSlide() {
        viewModel.onCutSlide(state.selectedSlide.id)
    }

    fun copySelectedSlide() {
        viewModel.onCopySlide(state.selectedSlide.id)
    }

    fun duplicateSelectedSlide() {
        viewModel.onDuplicateSlide(state.selectedSlide.id)
    }

    /**
     * Registers [callback], fired whenever the editor state changes (including
     * edits made inside the Compose canvas), and returns the unsubscribe for the
     * host to call when it goes away. Swift can't observe a Kotlin StateFlow, so
     * this is how the sidebar learns to re-pull its outline and thumbnails.
     *
     * Collecting is also what starts the presenter: the state flow is lazily
     * shared, so the host subscribing at launch is what gets the editor running.
     */
    fun onChange(callback: () -> Unit): () -> Unit {
        val job = scope.launch { viewModel.states.collect { callback() } }
        return { job.cancel() }
    }

    /**
     * Starts playing the document as it stands, from the selected slide.
     * [onExit] fires on the main thread when the player asks to stop (Escape).
     */
    fun startPlay(onExit: () -> Unit): PlaySession =
        PlaySession(state.document, state.selectedSlideIndex().coerceAtLeast(0), onExit)

    /**
     * Rasterizes a slide with the shared Compose renderer for native chrome to
     * display (navigator thumbs). Rendered at 2x for retina, sized in points.
     *
     * Cached by slide value: the host re-pulls every row on every state
     * emission, and a drag emits one per pointer sample, so rendering each row
     * every time starved the main thread. Only the slide that actually changed
     * misses the cache.
     *
     * Mid-gesture the cache answers even for the slide being dragged, stale on
     * purpose: one render costs 20-30ms, and paying that per pointer sample ate
     * three quarters of the main thread. The commit clears [EditorState.isPreviewing]
     * and the row catches up then, one render per gesture.
     */
    fun thumbnail(index: Int, width: Int): NSImage? {
        val slide = state.document.allSlides().getOrNull(index) ?: return null
        val cached = thumbnails[slide.id]
        // Settled, the cache has to match the slide; mid-gesture any render of it will do.
        val usable = cached != null && cached.width == width &&
            (cached.slide == slide || state.isPreviewing)
        if (usable) return cached.image

        val height = (width * Document.SLIDE_HEIGHT / Document.SLIDE_WIDTH).toInt()
        val skiaImage = renderComposeScene(width * 2, height * 2) {
            SlideView(slide)
        }
        val png = skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes ?: return null
        val nsData = png.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), png.size.toULong())
        }
        val image = NSImage(data = nsData)?.apply {
            setSize(NSMakeSize(width.toDouble(), height.toDouble()))
        } ?: return null

        thumbnails[slide.id] = Thumbnail(slide, width, image)
        return image
    }

    /**
     * Stops the editor (autosave included) and tears the scope down. Optional: a
     * document app keeps one editor for its whole life, so a host that never
     * closes the editor can leave this alone and let process exit do it.
     */
    fun close() {
        scope.cancel()
        viewModel.close()
    }
}

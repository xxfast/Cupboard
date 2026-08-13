@file:OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)

package io.github.xxfast.cupboard.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.renderComposeScene
import io.github.xxfast.cupboard.Cupboard
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.editor.EditorCanvas
import io.github.xxfast.cupboard.editor
import io.github.xxfast.cupboard.play.PresentationPlayer
import io.github.xxfast.cupboard.screens.editor.EditorState
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.jetbrains.skia.EncodedImageFormat
import platform.AppKit.NSImage
import platform.AppKit.NSView
import platform.Foundation.NSData
import platform.Foundation.NSMakeSize
import platform.Foundation.dataWithBytes

/** One row of the navigator outline, for the native (SwiftUI) sidebar. */
class OutlineRow(
    val title: String,
    val depth: Int,
    val slideIndex: Int,
    val hasChildren: Boolean,
    val collapsed: Boolean,
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
        Box(Modifier.fillMaxSize().background(well), contentAlignment = Alignment.Center) {
            EditorCanvas(
                slide = state.selectedSlide,
                selectedElementId = state.selectedElementId,
                onSelectElement = viewModel::onSelectElement,
                onSlideChange = viewModel::onUpdateSlide,
                onSlidePreview = viewModel::onPreviewSlide,
                onPreviewCancel = viewModel::onCancelPreview,
                // Fit is 84% of the window on macOS: the canvas layer is the whole
                // window, so at Fit the slide runs under both glass panels.
                modifier = if (scale == null) Modifier.fillMaxSize(0.84f) else Modifier.fillMaxSize(),
                zoom = scale,
            )
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

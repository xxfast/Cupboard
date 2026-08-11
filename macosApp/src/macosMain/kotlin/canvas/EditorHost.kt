@file:OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)

package io.github.xxfast.cupboard.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.renderComposeScene
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.editor.EditorCanvas
import io.github.xxfast.cupboard.editor.EditorStore
import io.github.xxfast.cupboard.editor.autosaveTo
import io.github.xxfast.cupboard.play.PresentationPlayer
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.skia.EncodedImageFormat
import platform.AppKit.NSImage
import platform.AppKit.NSView
import platform.Foundation.NSData
import platform.Foundation.NSHomeDirectory
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

    val view: NSView = ComposeHostView(composeView)

    fun dispose() {
        composeView.dispose()
    }
}

/**
 * Adapter between a native macOS shell and the shared [EditorStore]: exposes the
 * Compose canvas as an NSView, and gives the SwiftUI sidebar its outline,
 * selection and change-notification API in ObjC-friendly shapes.
 * Only the canvas is Compose; the chrome around it is the host's business.
 * No state lives here, it all belongs to [store].
 */
class EditorHost {
    // Deliberately the same path the Compose Desktop shell uses: the two shells
    // are front ends onto one document on this machine, not two apps.
    private val documentStore: KStore<Document> = run {
        val file = Path(NSHomeDirectory(), ".cupboard", "document.json")
        file.parent?.let { SystemFileSystem.createDirectories(it) }
        storeOf(file = file, default = sampleDocument())
    }

    // Private: the framework only exports this file's types, so the store stays
    // a Kotlin-side detail. Swift talks to it through the methods below.
    // Blocking is right here: there is no editor to show until the document loads.
    private val store = EditorStore(runBlocking { documentStore.get() } ?: sampleDocument())

    private val scope = CoroutineScope(Dispatchers.Main)
    private val stopAutosave: () -> Unit = store.autosaveTo(documentStore, scope)

    val view: NSView = ComposeHostView(ComposeNSView {
        // Paint the canvas well ourselves: unpainted scene regions are undefined
        // (white) instead of showing the SwiftUI background through.
        Box(Modifier.fillMaxSize().background(Color(0xFF17181C))) {
            EditorCanvas(
                slide = store.selectedSlide,
                selectedElementId = store.selectedElementId,
                onSelectElement = { store.selectElement(it) },
                onSlideChange = { store.updateSlide(it) },
            )
        }
    })

    fun outline(): List<OutlineRow> = store.outline().map { entry ->
        OutlineRow(
            title = entry.title,
            depth = entry.depth,
            slideIndex = entry.slideIndex,
            hasChildren = entry.hasChildren,
            collapsed = entry.collapsed,
        )
    }

    fun toggleCollapsed(index: Int) {
        val slide = store.document.slides.getOrNull(index) ?: return
        store.toggleCollapsed(slide.id)
    }

    fun selectedSlideIndex(): Int = store.selectedSlideIndex()

    fun selectSlide(index: Int) {
        store.selectSlideAt(index)
    }

    /**
     * Registers [callback], fired on every store change (including edits made
     * inside the Compose canvas), and returns the unsubscribe for the host to
     * call when it goes away. Swift can't observe snapshot state, so this is how
     * the sidebar learns to re-pull its outline and thumbnails.
     */
    fun onChange(callback: () -> Unit): () -> Unit = store.subscribe(callback)

    /**
     * Starts playing the document as it stands, from the selected slide.
     * [onExit] fires on the main thread when the player asks to stop (Escape).
     */
    fun startPlay(onExit: () -> Unit): PlaySession =
        PlaySession(store.document, store.selectedSlideIndex().coerceAtLeast(0), onExit)

    /**
     * Rasterizes a slide with the shared Compose renderer for native chrome to
     * display (navigator thumbs). Rendered at 2x for retina, sized in points.
     */
    fun thumbnail(index: Int, width: Int): NSImage? {
        val slide = store.document.allSlides().getOrNull(index) ?: return null
        val height = (width * Document.SLIDE_HEIGHT / Document.SLIDE_WIDTH).toInt()
        val skiaImage = renderComposeScene(width * 2, height * 2) {
            SlideView(slide)
        }
        val png = skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes ?: return null
        val nsData = png.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), png.size.toULong())
        }
        return NSImage(data = nsData)?.apply {
            setSize(NSMakeSize(width.toDouble(), height.toDouble()))
        }
    }

    /**
     * Stops autosaving and tears the scope down. Optional: a document app keeps
     * one editor for its whole life, so a host that never closes the editor can
     * leave this alone and let the process exit do it.
     */
    fun close() {
        stopAutosave()
        scope.cancel()
    }
}

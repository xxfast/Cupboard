@file:OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)

package io.github.xxfast.cupboard.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.renderComposeScene
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.hasChildren
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.document.toggleCollapsed
import io.github.xxfast.cupboard.document.updateSlide
import io.github.xxfast.cupboard.editor.EditorCanvas
import io.github.xxfast.cupboard.play.PresentationPlayer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
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

    val view: NSView = ComposeHostView(composeView)

    fun dispose() {
        composeView.dispose()
    }
}

/**
 * Bridge for a native macOS shell: owns the document state, exposes the Compose
 * canvas as an NSView, and gives the SwiftUI sidebar its outline + selection API.
 * Only the canvas is Compose; the chrome around it is the host's business.
 */
class EditorHost {
    private var document by mutableStateOf(sampleDocument())
    private var selectedSlideId by mutableStateOf(
        sampleDocument().allSlides().first { it.elements.isNotEmpty() }.id
    )
    private var selectedElementId by mutableStateOf<String?>(null)

    val view: NSView = ComposeHostView(ComposeNSView {
        val slides = document.allSlides()
        val slide = slides.firstOrNull { it.id == selectedSlideId } ?: slides.first()
        // Paint the canvas well ourselves: unpainted scene regions are undefined
        // (white) instead of showing the SwiftUI background through.
        Box(Modifier.fillMaxSize().background(Color(0xFF17181C))) {
            EditorCanvas(
                slide = slide,
                selectedElementId = selectedElementId,
                onSelectElement = { selectedElementId = it },
                onSlideChange = { document = document.updateSlide(it) },
            )
        }
    })

    fun outline(): List<OutlineRow> = document.slides.mapIndexed { index, slide ->
        OutlineRow(
            title = slide.title,
            depth = slide.depth,
            slideIndex = index,
            hasChildren = document.hasChildren(index),
            collapsed = slide.collapsed,
        )
    }

    fun toggleCollapsed(index: Int) {
        val slide = document.slides.getOrNull(index) ?: return
        document = document.toggleCollapsed(slide.id)
    }

    fun selectedSlideIndex(): Int =
        document.allSlides().indexOfFirst { it.id == selectedSlideId }

    fun selectSlide(index: Int) {
        val slide = document.allSlides().getOrNull(index) ?: return
        selectedSlideId = slide.id
        selectedElementId = null
    }

    /**
     * Starts playing the document as it stands, from the selected slide.
     * [onExit] fires on the main thread when the player asks to stop (Escape).
     */
    fun startPlay(onExit: () -> Unit): PlaySession =
        PlaySession(document, selectedSlideIndex().coerceAtLeast(0), onExit)

    /**
     * Rasterizes a slide with the shared Compose renderer for native chrome to
     * display (navigator thumbs). Rendered at 2x for retina, sized in points.
     */
    fun thumbnail(index: Int, width: Int): NSImage? {
        val slide = document.allSlides().getOrNull(index) ?: return null
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
}

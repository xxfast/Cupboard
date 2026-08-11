package io.github.xxfast.cupboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.editor.EditorStore
import io.github.xxfast.cupboard.editor.autosaveTo
import io.github.xxfast.cupboard.play.PresentationPlayer
import io.github.xxfast.cupboard.play.rememberPlayerController
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

private data class PlayRequest(val document: Document, val slideIndex: Int)

/**
 * Every shell on this machine points at the same file on purpose: the macOS
 * SwiftUI host and this one are two front ends onto one document, not two apps.
 */
private val documentFile: Path = Path(System.getProperty("user.home"), ".cupboard", "document.json")

fun main() {
    documentFile.parent?.let { SystemFileSystem.createDirectories(it) }
    val documentStore: KStore<Document> = storeOf(file = documentFile, default = sampleDocument())
    // Blocking is right here: there is no editor to show until the document loads.
    val initial: Document = runBlocking { documentStore.get() } ?: sampleDocument()
    val editorStore = EditorStore(initial)

    application {
        var playing by remember { mutableStateOf<PlayRequest?>(null) }

        // The scope is the application composition's, so autosave runs for as
        // long as the app does and stops when it exits.
        val scope = rememberCoroutineScope()
        DisposableEffect(Unit) {
            val stopAutosave = editorStore.autosaveTo(documentStore, scope)
            onDispose { stopAutosave() }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "Cupboard",
        ) {
            App(
                onPlay = { document, index -> playing = PlayRequest(document, index) },
                store = editorStore,
            )
        }

        playing?.let { request ->
            val controller = rememberPlayerController()
            val close = { playing = null }
            // Window-level fallback for when focus wanders off the player's own
            // key handler; consumed events never reach here, so no double-advance.
            Window(
                onCloseRequest = close,
                title = "Cupboard Play",
                state = rememberWindowState(placement = WindowPlacement.Maximized),
                onKeyEvent = { event ->
                    if (event.type != KeyEventType.KeyDown) false
                    else when (event.key) {
                        Key.Escape -> { close(); true }
                        Key.DirectionRight, Key.DirectionDown, Key.Spacebar, Key.Enter -> {
                            if (event.isShiftPressed) controller.nextSlide() else controller.next()
                            true
                        }
                        Key.DirectionLeft, Key.DirectionUp, Key.Backspace -> {
                            if (event.isShiftPressed) controller.previousSlide() else controller.previous()
                            true
                        }
                        else -> false
                    }
                },
            ) {
                PresentationPlayer(
                    document = request.document,
                    startIndex = request.slideIndex,
                    modifier = Modifier.fillMaxSize(),
                    onExit = close,
                    controller = controller,
                )
            }
        }
    }
}

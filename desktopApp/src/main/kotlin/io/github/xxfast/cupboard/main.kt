package io.github.xxfast.cupboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import io.github.xxfast.cupboard.play.PresentationPlayer
import io.github.xxfast.cupboard.play.rememberPlayerController

private data class PlayRequest(val document: Document, val slideIndex: Int)

fun main() = application {
    var playing by remember { mutableStateOf<PlayRequest?>(null) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Cupboard",
    ) {
        App(onPlay = { document, index -> playing = PlayRequest(document, index) })
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

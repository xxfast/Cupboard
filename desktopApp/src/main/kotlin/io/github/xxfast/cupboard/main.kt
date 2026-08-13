@file:OptIn(InternalComposeUiApi::class)

package io.github.xxfast.cupboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.play.PresentationPlayer
import io.github.xxfast.cupboard.play.rememberPlayerController
import io.github.xxfast.cupboard.screens.editor.EditorScreen
import io.github.xxfast.cupboard.screens.editor.EditorState
import kotlinx.coroutines.delay
import org.jetbrains.skiko.currentSystemTheme
import org.jetbrains.skiko.SystemTheme as SkikoSystemTheme

private data class PlayRequest(val document: Document, val slideIndex: Int)

/**
 * This shell is the Linux app but runs everywhere, so the Edit menu takes the
 * accelerator of whatever it's running on: Cmd on a mac, Ctrl elsewhere.
 */
private val isMacOs: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")

private fun editShortcut(shift: Boolean = false): KeyShortcut =
    KeyShortcut(Key.Z, shift = shift, meta = isMacOs, ctrl = !isMacOs)

/** The OS dark/light setting as a live value, re-read every second. */
@Composable
private fun pollSystemTheme(): SystemTheme {
    val theme: SystemTheme by produceState(initialValue = systemTheme()) {
        while (true) {
            delay(1_000)
            value = systemTheme()
        }
    }
    return theme
}

private fun systemTheme(): SystemTheme = when (currentSystemTheme) {
    SkikoSystemTheme.DARK -> SystemTheme.Dark
    SkikoSystemTheme.LIGHT -> SystemTheme.Light
    SkikoSystemTheme.UNKNOWN -> SystemTheme.Unknown
}

fun main() {
    // AWT title bars on macOS stay light aqua regardless of the OS appearance
    // unless the app opts into following it. JetBrains Runtime honours this;
    // other JVMs and platforms ignore it. Must be set before the first window.
    System.setProperty("apple.awt.application.appearance", "system")

    // One view model for the whole app: the editor window and the play window are
    // two views onto it, not two editors. Autosave lives inside it. Where the
    // document lives and how it loads is the factory's business, not this shell's.
    val viewModel = Cupboard.editor()

    application {
        var playing by remember { mutableStateOf<PlayRequest?>(null) }

        Window(
            onCloseRequest = { viewModel.close(); exitApplication() },
            title = "Cupboard",
        ) {
            val state: EditorState by viewModel.states.collectAsState()

            MenuBar {
                Menu("Edit", mnemonic = 'E') {
                    Item(
                        text = "Undo",
                        shortcut = editShortcut(),
                        enabled = state.canUndo,
                        onClick = viewModel::onUndo,
                    )
                    Item(
                        text = "Redo",
                        shortcut = editShortcut(shift = true),
                        enabled = state.canRedo,
                        onClick = viewModel::onRedo,
                    )
                }
            }

            // Desktop Compose reads the OS theme once, lazily (LocalSystemTheme's
            // default is a one-shot skiko read), so a running app never sees the
            // system switch. Re-providing it from a poll keeps the shared shell's
            // isSystemInDarkTheme() live.
            CompositionLocalProvider(LocalSystemTheme provides pollSystemTheme()) {
                EditorScreen(
                    viewModel = viewModel,
                    // The document and index the editor has right now: play is a
                    // snapshot, later edits don't reach the running presentation.
                    onPlay = { document, index -> playing = PlayRequest(document, index) },
                )
            }
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

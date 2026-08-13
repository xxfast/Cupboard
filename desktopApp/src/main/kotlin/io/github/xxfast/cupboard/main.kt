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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.ZOrderMove.Backward
import io.github.xxfast.cupboard.document.ZOrderMove.Forward
import io.github.xxfast.cupboard.document.ZOrderMove.ToBack
import io.github.xxfast.cupboard.document.ZOrderMove.ToFront
import io.github.xxfast.cupboard.editor.LocalResizeCursors
import io.github.xxfast.cupboard.editor.ResizeCursors
import io.github.xxfast.cupboard.editor.ResizeDirection
import io.github.xxfast.cupboard.play.PresentationPlayer
import io.github.xxfast.cupboard.play.rememberPlayerController
import io.github.xxfast.cupboard.screens.editor.EditorScreen
import io.github.xxfast.cupboard.screens.editor.EditorState
import io.github.xxfast.cupboard.screens.editor.FlipAxis.Horizontal
import io.github.xxfast.cupboard.screens.editor.FlipAxis.Vertical
import java.awt.BasicStroke
import java.awt.Cursor
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
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

/**
 * A double-headed resize arrow drawn at [degrees], as a custom AWT cursor.
 *
 * Drawn rather than predefined: the macOS JDK has no diagonal resize cursors at
 * all (NW/NE silently fall back to the plain arrow) and its edge cursors are the
 * single-headed variants, so the eight handle positions read as a grab-bag next
 * to the native shell's cursors. One drawn arrow, rotated per axis, keeps all
 * eight consistent on every OS. Black fill with a white outline, like the
 * system's own artwork, so it survives light and dark canvases alike.
 */
private fun drawnResizeCursor(degrees: Double, name: String): Cursor? {
    val toolkit = Toolkit.getDefaultToolkit()
    val best = toolkit.getBestCursorSize(24, 24)
    if (best.width == 0 || best.height == 0) return null

    val image = BufferedImage(best.width, best.height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    // The arrow is authored on a 24-unit grid; scale to whatever AWT wants.
    g.scale(best.width / 24.0, best.height / 24.0)
    g.rotate(Math.toRadians(degrees), 12.0, 12.0)
    val arrow = Path2D.Float().apply {
        moveTo(3f, 12f); lineTo(8f, 7f); lineTo(8f, 10f)
        lineTo(16f, 10f); lineTo(16f, 7f); lineTo(21f, 12f)
        lineTo(16f, 17f); lineTo(16f, 14f); lineTo(8f, 14f); lineTo(8f, 17f)
        closePath()
    }
    g.color = java.awt.Color.WHITE
    g.stroke = BasicStroke(2f)
    g.draw(arrow)
    g.color = java.awt.Color.BLACK
    g.fill(arrow)
    g.dispose()

    return toolkit.createCustomCursor(image, Point(best.width / 2, best.height / 2), name)
}

/** Falls back to AWT's nearest predefined cursor where custom cursors aren't supported. */
private fun resizeCursor(degrees: Double, name: String, fallback: Int): Cursor =
    drawnResizeCursor(degrees, name) ?: Cursor.getPredefinedCursor(fallback)

private val ResizeCursorIcons: Map<ResizeDirection, PointerIcon> by lazy {
    mapOf(
        ResizeDirection.Horizontal to PointerIcon(resizeCursor(0.0, "resize-ew", Cursor.E_RESIZE_CURSOR)),
        ResizeDirection.Vertical to PointerIcon(resizeCursor(90.0, "resize-ns", Cursor.S_RESIZE_CURSOR)),
        ResizeDirection.DiagonalDown to PointerIcon(resizeCursor(45.0, "resize-nwse", Cursor.NW_RESIZE_CURSOR)),
        ResizeDirection.DiagonalUp to PointerIcon(resizeCursor(135.0, "resize-nesw", Cursor.NE_RESIZE_CURSOR)),
    )
}

/**
 * The modifier stays attached with no handle under the pointer, showing the
 * arrow, rather than detaching: adding and removing the hover-icon node
 * mid-hover leaves AWT holding a stale cursor, which shows up as the cursor
 * vanishing over the canvas.
 */
private val AwtResizeCursors = ResizeCursors { direction ->
    Modifier.pointerHoverIcon(direction?.let { ResizeCursorIcons.getValue(it) } ?: PointerIcon.Default)
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

                // Everything here needs something selected, and everything but
                // the unlock needs it unlocked: the same rule the presenter
                // applies, so a greyed item is never a silently dropped event.
                val element: Element? = state.selectedElement
                val editable: Boolean = element != null && !element.locked

                Menu("Arrange", mnemonic = 'A') {
                    Item(
                        text = "Bring Forward",
                        enabled = editable,
                        onClick = { element?.let { viewModel.onReorderElement(it.id, Forward) } },
                    )
                    Item(
                        text = "Send Backward",
                        enabled = editable,
                        onClick = { element?.let { viewModel.onReorderElement(it.id, Backward) } },
                    )
                    Item(
                        text = "Bring to Front",
                        enabled = editable,
                        onClick = { element?.let { viewModel.onReorderElement(it.id, ToFront) } },
                    )
                    Item(
                        text = "Send to Back",
                        enabled = editable,
                        onClick = { element?.let { viewModel.onReorderElement(it.id, ToBack) } },
                    )

                    Separator()

                    Item(
                        text = "Flip Horizontally",
                        enabled = editable,
                        onClick = { element?.let { viewModel.onFlipElement(it.id, Horizontal) } },
                    )
                    Item(
                        text = "Flip Vertically",
                        enabled = editable,
                        onClick = { element?.let { viewModel.onFlipElement(it.id, Vertical) } },
                    )

                    Separator()

                    Item(
                        text = if (element?.locked == true) "Unlock" else "Lock",
                        enabled = element != null,
                        onClick = { element?.let { viewModel.onToggleElementLock(it.id) } },
                    )
                }
            }

            // Desktop Compose reads the OS theme once, lazily (LocalSystemTheme's
            // default is a one-shot skiko read), so a running app never sees the
            // system switch. Re-providing it from a poll keeps the shared shell's
            // isSystemInDarkTheme() live.
            CompositionLocalProvider(
                LocalSystemTheme provides pollSystemTheme(),
                LocalResizeCursors provides AwtResizeCursors,
            ) {
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

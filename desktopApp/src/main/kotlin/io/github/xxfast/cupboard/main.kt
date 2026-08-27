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
import androidx.compose.ui.window.MenuScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.editor.LocalResizeCursors
import io.github.xxfast.cupboard.editor.ResizeCursors
import io.github.xxfast.cupboard.editor.ResizeDirection
import io.github.xxfast.cupboard.editor.SnapKind
import io.github.xxfast.cupboard.play.PresentationPlayer
import io.github.xxfast.cupboard.play.rememberPlayerController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import io.github.xxfast.cupboard.screens.editor.EditorMenuItem
import io.github.xxfast.cupboard.screens.editor.EditorMenuSection
import io.github.xxfast.cupboard.screens.editor.EditorScreen
import io.github.xxfast.cupboard.screens.editor.EditorState
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import io.github.xxfast.cupboard.screens.editor.arrangeSections
import io.github.xxfast.cupboard.screens.editor.canvasMenuSections
import io.github.xxfast.cupboard.screens.editor.formatSections
import io.github.xxfast.cupboard.screens.editor.insertSections
import io.github.xxfast.cupboard.screens.editor.slideSections
import java.awt.BasicStroke
import java.awt.Component
import java.awt.Cursor
import java.awt.EventQueue
import java.awt.Point
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.awt.Menu as AwtMenu
import java.awt.MenuItem as AwtMenuItem
import kotlinx.coroutines.delay
import org.jetbrains.skiko.currentSystemTheme
import org.jetbrains.skiko.SystemTheme as SkikoSystemTheme

private data class PlayRequest(val document: Document, val slideIndex: Int)

/**
 * This shell is the Linux app but runs everywhere, so the Edit menu takes the
 * accelerator of whatever it's running on: Cmd on a mac, Ctrl elsewhere.
 */
private val isMacOs: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")

private fun editShortcut(key: Key, shift: Boolean = false, alt: Boolean = false): KeyShortcut =
    KeyShortcut(key, shift = shift, alt = alt, meta = isMacOs, ctrl = !isMacOs)

private val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")

/**
 * A context menu as the OS draws it. AWT popups are native menus on macOS and
 * Windows, which is where the m3 dropdown looked foreign; Linux keeps the
 * dropdown, M3 being that shell's design language (and AWT's Linux menus being
 * nobody's).
 *
 * The verbs come in already built: the canvas and the navigator each have their
 * own idea of what a right-click means, this only fills the popup and shows it.
 */
private fun showNativeMenu(
    popup: PopupMenu,
    parent: Component,
    density: Float,
    positionInWindow: Offset,
    sections: List<EditorMenuSection>,
) {
    popup.removeAll()
    for ((index, section) in sections.withIndex()) {
        if (index > 0) popup.addSeparator()
        for (item in section.items) popup.add(item.toAwtItem())
    }

    // Deferred a turn so the menu's native tracking loop doesn't start from
    // inside Compose's handling of the very click that asked for it.
    EventQueue.invokeLater {
        popup.show(
            parent,
            (positionInWindow.x / density).toInt(),
            (positionInWindow.y / density).toInt(),
        )
    }
}

/**
 * What a right-click on the canvas offers, built one step ahead of the loop:
 * the ContextClick event this menu rides in on has not roundtripped when the
 * menu is built, so the specs derive from the selection that click settles on,
 * by the reducer's own rule. One `state.copy` covers both the enablement and
 * the ids the actions carry.
 */
private fun canvasContextSections(
    state: EditorState,
    viewModel: EditorViewModel,
    elementId: String?,
): List<EditorMenuSection> {
    val selection: List<String> =
        if (elementId != null && elementId in state.selectedElementIds) state.selectedElementIds
        else listOfNotNull(elementId)

    return canvasMenuSections(state.copy(selectedElementIds = selection), viewModel)
}

/** [EditorMenuItem] rendered into AWT, children as a real submenu. */
private fun EditorMenuItem.toAwtItem(): AwtMenuItem =
    if (children.isEmpty()) AwtMenuItem(label).also { item ->
        item.isEnabled = enabled
        item.addActionListener { onPick() }
    } else AwtMenu(label).also { submenu ->
        submenu.isEnabled = enabled
        for (child in children) submenu.add(child.toAwtItem())
    }

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

/** The Snap submenu's rows, each the label for the [SnapKind] it switches. */
private val SnapLabels: List<Pair<SnapKind, String>> = listOf(
    SnapKind.Center to "Center",
    SnapKind.Edges to "Edges",
    SnapKind.Objects to "Objects",
    SnapKind.Guides to "Guides",
)

/** Whether a dragged element currently settles onto [kind]'s lines. */
private fun EditorState.snapsTo(kind: SnapKind): Boolean = when (kind) {
    SnapKind.Center -> snapToCenter
    SnapKind.Edges -> snapToEdges
    SnapKind.Objects -> snapToObjects
    SnapKind.Guides -> snapToGuides
}

/**
 * Menu specs as menu-bar entries: a [Separator] between sections, a nested
 * [Menu] for anything carrying children.
 *
 * Accelerators stay out of the specs, which are shared with the context menus
 * and have nowhere to put a Compose [KeyShortcut]. [shortcut] hangs them back on
 * per menu, so the Format menu renders from the same spec the canvas will.
 */
@Composable
private fun MenuScope.MenuItems(
    sections: List<EditorMenuSection>,
    shortcut: (EditorMenuItem) -> KeyShortcut? = { null },
) {
    sections.forEachIndexed { index, section ->
        if (index > 0) Separator()

        for (item in section.items) {
            if (item.children.isEmpty()) {
                Item(
                    text = item.label,
                    shortcut = shortcut(item),
                    enabled = item.enabled,
                    onClick = item.onPick,
                )
            } else {
                Menu(item.label, enabled = item.enabled) {
                    for (child in item.children) Item(text = child.label, onClick = child.onPick)
                }
            }
        }
    }
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

        val state: EditorState by viewModel.states.collectAsState()

        Window(
            onCloseRequest = { viewModel.close(); exitApplication() },
            title = "Cupboard",
            // Forward delete, the half the menu accelerator can't carry: Delete
            // shows as Backspace there, which is the delete key on a mac board.
            // Dispatches exactly like the menu item does, focus and all: with the
            // navigator focused this takes the slide away, which is Keynote's
            // behaviour. Guarded on the same canDelete, so the key is only ours
            // when there is something to take away. Nothing in the window takes
            // typing yet, so there is no field to steal from.
            onKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Delete && state.canDelete) {
                    viewModel.onDelete()
                    true
                } else {
                    false
                }
            },
        ) {
            MenuBar {
                // What's left of the element-scoped enablement, for the items
                // that stay element-scoped: the style pair below. The label
                // follows the primary element, the events carry the whole
                // selection, and the edit needs something unlocked, the same
                // rule the presenter applies.
                val primary: Element? = state.primaryElement
                val ids: List<String> = state.selectedElementIds
                val editable: Boolean = state.selectedElements.any { !it.locked }
                // Clear All speaks for the slide rather than the selection, so
                // it asks the slide the same question: is there anything
                // unlocked left to take away.
                val clearable: Boolean = state.selectedSlide.elements.any { !it.locked }

                Menu("Edit", mnemonic = 'E') {
                    Item(
                        text = "Undo",
                        shortcut = editShortcut(Key.Z),
                        enabled = state.canUndo,
                        onClick = viewModel::onUndo,
                    )
                    Item(
                        text = "Redo",
                        shortcut = editShortcut(Key.Z, shift = true),
                        enabled = state.canRedo,
                        onClick = viewModel::onRedo,
                    )

                    Separator()

                    // These four follow the focus, like Keynote's: the slide in
                    // the navigator, the elements on the canvas. The core
                    // resolves which, both in the verb and in what greys it,
                    // so nothing here asks about the selection. Paste was
                    // always generic and stays so, it follows the clipboard.
                    Item(
                        text = "Cut",
                        shortcut = editShortcut(Key.X),
                        enabled = state.canCut,
                        onClick = viewModel::onCut,
                    )
                    Item(
                        text = "Copy",
                        shortcut = editShortcut(Key.C),
                        enabled = state.canCopy,
                        onClick = viewModel::onCopy,
                    )
                    Item(
                        text = "Paste",
                        shortcut = editShortcut(Key.V),
                        enabled = state.canPaste,
                        onClick = viewModel::onPaste,
                    )
                    Item(
                        text = "Duplicate",
                        shortcut = editShortcut(Key.D),
                        enabled = state.canDuplicate,
                        onClick = viewModel::onDuplicate,
                    )

                    Separator()

                    Item(
                        text = "Delete",
                        shortcut = KeyShortcut(Key.Backspace),
                        enabled = state.canDelete,
                        onClick = viewModel::onDelete,
                    )
                    Item(
                        text = "Clear All",
                        enabled = clearable,
                        onClick = viewModel::onClearAll,
                    )

                    Separator()

                    // The style clipboard is its own thing, so these two ask
                    // about it rather than about the one above.
                    Item(
                        text = "Copy Style",
                        shortcut = editShortcut(Key.C, alt = true),
                        enabled = primary != null,
                        onClick = { primary?.let { viewModel.onCopyStyle(it.id) } },
                    )
                    Item(
                        text = "Paste Style",
                        shortcut = editShortcut(Key.V, alt = true),
                        enabled = state.canPasteStyle && editable,
                        onClick = { viewModel.onPasteStyle(ids) },
                    )
                }

                // Insert takes no accelerators either: nothing here is a
                // verb the user reaches for mid-gesture, and the toolbar's
                // Text and Shape buttons render the same catalog.
                Menu("Insert", mnemonic = 'I') {
                    MenuItems(insertSections(state, viewModel))
                }

                // The slide verbs take no accelerators: Cmd+X/C/V/D belong to the
                // element ones next door, and Paste is dropped here for the same
                // reason, Edit already owns it. Rendered from the shared specs,
                // like Arrange: the navigator's context menu offers the same
                // verbs against whichever row it opened on.
                Menu("Slide", mnemonic = 'S') {
                    MenuItems(
                        slideSections(
                            state = state,
                            viewModel = viewModel,
                            slideId = state.selectedSlide.id,
                            includePaste = false,
                        ),
                    )
                }

                // Whole-box text styling. Bold, Italic and Underline take the
                // accelerators every editor gives them; Strikethrough has no
                // conventional one, so it goes without rather than inventing one.
                Menu("Format", mnemonic = 'F') {
                    MenuItems(formatSections(state, viewModel)) { item ->
                        when (item.label) {
                            "Bold" -> editShortcut(Key.B)
                            "Italic" -> editShortcut(Key.I)
                            "Underline" -> editShortcut(Key.U)
                            else -> null
                        }
                    }
                }

                // Rendered from the shared specs, not written here: the canvas
                // context menu offers the same verbs, and a rule written twice
                // is a rule that drifts.
                Menu("Arrange", mnemonic = 'A') {
                    MenuItems(arrangeSections(state, viewModel))
                }

                // Written here rather than as a shared spec: every entry is a
                // switch showing its own state, and [EditorMenuItem] has no
                // checkmark to carry. Nothing is ever greyed, a view toggle
                // asks nothing of the selection. The state these read is the
                // editor's, not this window's, so a reopened window comes back
                // the way it was left.
                Menu("View", mnemonic = 'V') {
                    CheckboxItem(
                        text = "Show Navigator",
                        checked = state.sidebarOpen,
                        onCheckedChange = { viewModel.onToggleSidebar() },
                    )
                    CheckboxItem(
                        text = "Show Presenter Notes",
                        checked = state.showNotes,
                        onCheckedChange = { viewModel.onToggleNotes() },
                    )

                    Separator()

                    CheckboxItem(
                        text = "Show Rulers",
                        checked = state.showRulers,
                        shortcut = editShortcut(Key.R),
                        onCheckedChange = { viewModel.onToggleRulers() },
                    )
                    CheckboxItem(
                        text = "Show Guides",
                        checked = state.showGuides,
                        onCheckedChange = { viewModel.onToggleGuides() },
                    )

                    Separator()

                    // The parent already says Snap, so the rows don't repeat it.
                    // Driven off [SnapLabels] so a fifth kind is one line there
                    // and nothing here.
                    Menu("Snap") {
                        for ((kind, label) in SnapLabels) {
                            CheckboxItem(
                                text = label,
                                checked = state.snapsTo(kind),
                                onCheckedChange = { enabled -> viewModel.onSetSnap(kind, enabled) },
                            )
                        }
                    }
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
                // Added to the window once; each right-click rebuilds its items.
                // Null on Linux, which keeps the shared m3 dropdown.
                val nativeMenu: PopupMenu? = remember {
                    if (isMacOs || isWindows) PopupMenu().also(window.contentPane::add)
                    else null
                }
                val density: Float = LocalDensity.current.density

                EditorScreen(
                    viewModel = viewModel,
                    // The document and index the editor has right now: play is a
                    // snapshot, later edits don't reach the running presentation.
                    onPlay = { document, index -> playing = PlayRequest(document, index) },
                    onShowContextMenu = nativeMenu?.let { menu ->
                        { elementId, positionInWindow ->
                            showNativeMenu(
                                popup = menu,
                                parent = window.contentPane,
                                density = density,
                                positionInWindow = positionInWindow,
                                sections = canvasContextSections(state, viewModel, elementId),
                            )
                        }
                    },
                    // The same popup: two menus can't be open at once anyway, and
                    // the row's verbs carry its id, so nothing here has to guess
                    // what the click did to the selection.
                    onShowSlideContextMenu = nativeMenu?.let { menu ->
                        { slideId, positionInWindow ->
                            showNativeMenu(
                                popup = menu,
                                parent = window.contentPane,
                                density = density,
                                positionInWindow = positionInWindow,
                                sections = slideSections(
                                    state = state,
                                    viewModel = viewModel,
                                    slideId = slideId,
                                    includePaste = true,
                                ),
                            )
                        }
                    },
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

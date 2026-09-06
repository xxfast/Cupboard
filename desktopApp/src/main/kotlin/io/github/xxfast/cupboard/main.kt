@file:OptIn(InternalComposeUiApi::class)

package io.github.xxfast.cupboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.previewOf
import io.github.xxfast.cupboard.editor.LocalResizeCursors
import io.github.xxfast.cupboard.editor.ResizeCursors
import io.github.xxfast.cupboard.editor.ResizeDirection
import io.github.xxfast.cupboard.editor.SnapKind
import io.github.xxfast.cupboard.play.PlayerController
import io.github.xxfast.cupboard.play.PresentationPlayer
import io.github.xxfast.cupboard.play.PresenterView
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
import io.github.xxfast.cupboard.screens.editor.layoutSections
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.awt.Menu as AwtMenu
import java.awt.MenuItem as AwtMenuItem
import kotlinx.coroutines.delay
import org.jetbrains.skiko.currentSystemTheme
import org.jetbrains.skiko.SystemTheme as SkikoSystemTheme

/**
 * A show to open: the deck, the slide it starts on, and whether it is a Preview
 * rather than the talk. Preview is the same player over a one-slide deck, so all
 * the flag decides is how the window wears it: windowed and named a preview,
 * where the talk takes the whole screen.
 */
private data class PlayRequest(
    val document: Document,
    val slideIndex: Int,
    val preview: Boolean = false,
)

/** Half of 1080p: big enough to read a slide, small enough to leave the editor behind it. */
private val PreviewWindowWidth = 960.dp
private val PreviewWindowHeight = 540.dp

/**
 * Two thirds of 1080p, resizable: the presenter display is meant for the laptop
 * screen while the show takes the projector, and that is about the size of it.
 */
private val PresenterWindowWidth = 1280.dp
private val PresenterWindowHeight = 720.dp

/** The wall clock the presenter display shows, in the shell's own formatter. */
private val ClockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The keys that drive a running show, on whichever of its windows has focus.
 *
 * Window-level fallback for when focus wanders off the player's own key handler;
 * consumed events never reach here, so no double-advance, and typing into the
 * presenter's notes field doesn't advance the deck either. Escape on either
 * window ends the whole show, both windows with it.
 */
private fun showKeys(controller: PlayerController, onExit: () -> Unit): (KeyEvent) -> Boolean =
    { event ->
        if (event.type != KeyEventType.KeyDown) false
        else when (event.key) {
            Key.Escape -> { onExit(); true }
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
    }

/**
 * This shell is the Linux app but runs everywhere, so the Edit menu takes the
 * accelerator of whatever it's running on: Cmd on a mac, Ctrl elsewhere.
 */
internal val isMacOs: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")

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

        // Whether a show also puts up the presenter display. This shell's own
        // preference rather than the editor's: it is about the windows on this
        // machine, and nothing in the document has an opinion on it. Closing the
        // presenter window sets it back, which is why one flag covers both.
        var showPresenter by remember { mutableStateOf(true) }

        val state: EditorState by viewModel.states.collectAsState()

        // Preview is Play on the slide alone: the deck's furniture, one slide of
        // it, opened at its first step so the builds run from the top. Off in
        // layout mode for the same reason Play is, there is no slide of the talk
        // selected there, and null is what greys the button and the menu item.
        val previewSlide: (() -> Unit)? = if (state.isEditingLayouts) null else {
            {
                playing = PlayRequest(
                    document = state.document.previewOf(state.selectedSlide.id),
                    slideIndex = 0,
                    preview = true,
                )
            }
        }

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

                // The deck as a CuP project someone else can run: the generator
                // makes the files, this only asks where they go.
                Menu("File", mnemonic = 'F') {
                    Item(
                        text = "Export as CuP Project...",
                        onClick = { exportCupProject(state.document, window) },
                    )
                }

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
                        // The one accelerator here still live over a caret.
                        // Cut and Copy grey out mid-edit and a greyed item lets
                        // its key through to the field, which is how Delete's
                        // bare Backspace has always deleted characters rather
                        // than the element. Paste greys on an empty clipboard
                        // and nothing else, so its accelerator has to come off
                        // by hand for Cmd+V to be the field's paste.
                        text = "Paste",
                        shortcut = editShortcut(Key.V).takeIf { !state.isEditingText },
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

                    Separator()

                    // Written here rather than into the shared spec: it is a slide
                    // verb the navigator's menu has no business offering on a
                    // layout, and in layout mode there is no slide to reapply to.
                    Item(
                        text = "Reapply Layout",
                        enabled = !state.isEditingLayouts && state.selectedLayout != null,
                        onClick = { viewModel.onReapplyLayout(state.selectedSlide.id) },
                    )

                    // No accelerator: Play has the one people reach for, and a
                    // preview is a look at one slide rather than a mode you live
                    // in. The Animate tab's button is the other way to it.
                    Item(
                        text = "Preview Slide",
                        enabled = previewSlide != null,
                        onClick = { previewSlide?.invoke() },
                    )
                }

                // Whole-box text styling. Bold, Italic and Underline take the
                // accelerators every editor gives them; Strikethrough has no
                // conventional one, so it goes without rather than inventing one.
                Menu("Format", mnemonic = 'O') {
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
                    // The only way to put the inspector away on this shell: the
                    // tabs switch panels but never close one, which is the mac
                    // shell's behaviour and stays there (design/README.md).
                    CheckboxItem(
                        text = "Show Inspector",
                        checked = state.inspectorOpen,
                        onCheckedChange = { viewModel.onToggleInspector() },
                    )
                    CheckboxItem(
                        text = "Show Presenter Notes",
                        checked = state.showNotes,
                        onCheckedChange = { viewModel.onToggleNotes() },
                    )
                    // Live rather than a setting you arm beforehand: ticked
                    // mid-show the presenter display comes up on the spot, and
                    // unticked it goes away without touching the show.
                    CheckboxItem(
                        text = "Show Presenter Display",
                        checked = showPresenter,
                        onCheckedChange = { showPresenter = it },
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

                    Separator()

                    // A plain item rather than a checkbox: layout mode is a place
                    // the editor goes, not a thing it shows, so the entry says
                    // which way it is about to go.
                    Item(
                        text = if (state.isEditingLayouts) "Exit Slide Layouts"
                        else "Edit Slide Layouts",
                        onClick = {
                            if (state.isEditingLayouts) viewModel.onExitSlideLayouts()
                            else viewModel.onEditSlideLayouts()
                        },
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
                    //
                    // Off in layout mode: a layout is not a slide of the talk, and
                    // the index the player would start from names nothing there.
                    onPlay = if (state.isEditingLayouts) null else {
                        { document, index -> playing = PlayRequest(document, index) }
                    },
                    onPlayPreview = previewSlide,
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
                        { slideId, positionInWindow, onRename ->
                            showNativeMenu(
                                popup = menu,
                                parent = window.contentPane,
                                density = density,
                                positionInWindow = positionInWindow,
                                // In layout mode the row is a layout, so the verbs
                                // are the layout ones. Rename's dialog belongs to
                                // the editor view, which hands its opener in.
                                sections = if (state.isEditingLayouts) {
                                    layoutSections(viewModel, slideId, onRename)
                                } else {
                                    slideSections(
                                        state = state,
                                        viewModel = viewModel,
                                        slideId = slideId,
                                        includePaste = true,
                                    )
                                },
                            )
                        }
                    },
                )
            }
        }

        playing?.let { request ->
            val controller = rememberPlayerController()
            val close = { playing = null }
            Window(
                onCloseRequest = close,
                title = if (request.preview) "Cupboard Preview" else "Cupboard Play",
                // A preview sits in a window on top of the editor: you are still
                // working on the slide, so the deck should not take the screen
                // away to show it to you. Escape closes it either way.
                state = if (request.preview) {
                    rememberWindowState(
                        size = DpSize(PreviewWindowWidth, PreviewWindowHeight),
                        position = WindowPosition(Alignment.Center),
                    )
                } else {
                    rememberWindowState(placement = WindowPlacement.Maximized)
                },
                onKeyEvent = showKeys(controller, close),
            ) {
                // WindowState sizes the frame, title bar and all, so 960x540
                // asked for is a slide short by whatever the chrome takes. Hand
                // that back and re-centre on what the window actually became.
                // Insets read zero on a frame that isn't up yet, which just
                // leaves the window at the size it was asked for.
                if (request.preview) LaunchedEffect(Unit) {
                    EventQueue.invokeLater {
                        val chrome = window.insets
                        window.setSize(
                            window.width + chrome.left + chrome.right,
                            window.height + chrome.top + chrome.bottom,
                        )
                        window.setLocationRelativeTo(null)
                    }
                }

                PresentationPlayer(
                    document = request.document,
                    startIndex = request.slideIndex,
                    modifier = Modifier.fillMaxSize(),
                    onExit = close,
                    controller = controller,
                )
            }

            // The lectern's half of the show, following the same controller.
            // Never for a preview: a preview is one slide looked at from the
            // editor, there is nobody at a lectern.
            if (!request.preview && showPresenter) Window(
                // Closing this alone leaves the show up: it is a second screen,
                // not the show. The View menu brings it back.
                onCloseRequest = { showPresenter = false },
                title = "Cupboard Presenter",
                state = rememberWindowState(
                    size = DpSize(PresenterWindowWidth, PresenterWindowHeight),
                    position = WindowPosition(Alignment.Center),
                ),
                onKeyEvent = showKeys(controller, close),
            ) {
                PresenterView(
                    // The live document rather than the show's snapshot: notes
                    // typed here go through the editor's loop, and the snapshot
                    // would never show them coming back. The play order is the
                    // same either way, nothing edits the deck while a show is up.
                    document = state.document,
                    controller = controller,
                    onNotesChange = { slideId, notes ->
                        state.document.slides.firstOrNull { it.id == slideId }
                            ?.let { slide -> viewModel.onUpdateSlide(slide.copy(notes = notes)) }
                    },
                    modifier = Modifier.fillMaxSize(),
                    clock = { LocalTime.now().format(ClockFormat) },
                )
            }
        }
    }
}

package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.DefaultCodeBoxHeight
import io.github.xxfast.cupboard.document.DefaultCodeBoxWidth
import io.github.xxfast.cupboard.document.DefaultDiagramHeight
import io.github.xxfast.cupboard.document.DefaultDiagramWidth
import io.github.xxfast.cupboard.document.DefaultEquationHeight
import io.github.xxfast.cupboard.document.DefaultEquationWidth
import io.github.xxfast.cupboard.document.DefaultTerminalHeight
import io.github.xxfast.cupboard.document.DefaultTerminalWidth
import io.github.xxfast.cupboard.document.DefaultTextBoxHeight
import io.github.xxfast.cupboard.document.DefaultTextBoxWidth
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GuideAxis
import io.github.xxfast.cupboard.document.PlaceholderRole
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.SlideTransition
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.codeBoxElement
import io.github.xxfast.cupboard.document.diagramElement
import io.github.xxfast.cupboard.document.element
import io.github.xxfast.cupboard.document.equationElement
import io.github.xxfast.cupboard.document.slideById
import io.github.xxfast.cupboard.document.slideSizePreset
import io.github.xxfast.cupboard.document.terminalElement
import io.github.xxfast.cupboard.document.textBoxElement
import io.github.xxfast.cupboard.editor.EditorCanvas
import io.github.xxfast.cupboard.theme.ChromeTheme
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LinuxChrome
import io.github.xxfast.cupboard.theme.LocalChromeTheme
import io.github.xxfast.cupboard.theme.LocalChromeTokens
import io.github.xxfast.cupboard.theme.toColorScheme

/**
 * The Compose Desktop editor shell: the design's stacked layout (toolbar over
 * navigator | canvas well | inspector), Material 3 chrome. The Linux app and
 * the universal fallback; OS styling is all [ChromeTheme] tokens.
 *
 * [onPlay] non-null shows the toolbar Play pill, which receives the current
 * document and selected slide index; null hides play (android/web shells).
 */
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onPlay: ((Document, Int) -> Unit)? = null,
    /**
     * A shell that draws the context menu natively (the desktop app's AWT popup
     * on macOS and Windows). Called with the click's hit and its position in
     * window coordinates; when set, the m3 dropdown never composes.
     */
    onShowContextMenu: ((elementId: String?, positionInWindow: Offset) -> Unit)? = null,
    /**
     * The same for the navigator: the shell draws the slide menu for the row at
     * [slideId], at a position in window coordinates. Null keeps the m3 dropdown.
     *
     * The third argument is what a Rename verb picks. The dialog it opens is this
     * view's, whoever draws the menu, so a shell renaming a layout hands this back
     * rather than building a dialog of its own.
     */
    onShowSlideContextMenu: ((
        slideId: String,
        positionInWindow: Offset,
        onRename: () -> Unit,
    ) -> Unit)? = null,
) {
    val state: EditorState by viewModel.states.collectAsState()

    EditorView(
        state = state,
        onSelectSlide = viewModel::onSelectSlide,
        onToggleCollapsed = viewModel::onToggleCollapsed,
        onSelectElement = viewModel::onSelectElement,
        onToggleElementSelection = viewModel::onToggleElementSelection,
        // The click settles the selection; the view opens the menu at the
        // position it came with.
        onContextClick = { elementId, _ -> viewModel.onContextClick(elementId) },
        // Rebuilt from every state this screen sees, so the menu that is already
        // open re-reads its enablement as the click's selection settles. A shell
        // that draws its own menu gets none: two menus for one click is a bug.
        menuSections =
            if (onShowContextMenu == null) canvasMenuSections(state, viewModel) else emptyList(),
        onShowContextMenu = onShowContextMenu,
        // The row selects before its menu opens, through the loop, the same rule
        // the canvas click follows.
        onSlideContextClick = viewModel::onSelectSlide,
        // Built per row, since which slide the verbs carry is only known once a
        // row is clicked, and rebuilt from every state, so an open menu re-reads
        // its paste gate. A shell drawing its own menu gets none.
        // In layout mode the rows are layouts, so the verbs are the layout ones:
        // pasting a slide among the masters means nothing, and a layout has a name
        // where a slide has none.
        slideMenuSections = { slideId, onRename ->
            when {
                onShowSlideContextMenu != null -> emptyList()
                state.isEditingLayouts -> layoutSections(viewModel, slideId, onRename)
                else -> slideSections(state, viewModel, slideId, includePaste = true)
            }
        },
        onShowSlideContextMenu = onShowSlideContextMenu,
        onPreviewSlideDrag = viewModel::onPreviewSlideDrag,
        onMoveSlide = viewModel::onMoveSlide,
        onEndSlideDrag = viewModel::onEndSlideDrag,
        onUpdateSlide = viewModel::onUpdateSlide,
        onPreviewSlide = viewModel::onPreviewSlide,
        onSetSlideTransition = viewModel::onSetSlideTransition,
        onAddBuild = viewModel::onAddBuild,
        onUpdateBuild = viewModel::onUpdateBuild,
        onRemoveBuild = viewModel::onRemoveBuild,
        onMoveBuild = viewModel::onMoveBuild,
        onPreviewMarquee = viewModel::onPreviewMarquee,
        onEndMarquee = viewModel::onEndMarquee,
        onCancelPreview = viewModel::onCancelPreview,
        onUpdateElements = viewModel::onUpdateElements,
        onPreviewElements = viewModel::onPreviewElements,
        onInsertElement = viewModel::onInsertElement,
        onBeginTextEdit = viewModel::onBeginTextEdit,
        onEndTextEdit = viewModel::onEndTextEdit,
        onReorderElements = viewModel::onReorderElements,
        onSetElementsLocked = viewModel::onSetElementsLocked,
        onFlipElements = viewModel::onFlipElements,
        onGroupElements = viewModel::onGroupElements,
        onUngroupElements = viewModel::onUngroupElements,
        onSelectInspectorTab = viewModel::onSelectInspectorTab,
        onEditSlideLayouts = viewModel::onEditSlideLayouts,
        onExitSlideLayouts = viewModel::onExitSlideLayouts,
        onApplyLayout = viewModel::onApplyLayout,
        onReapplyLayout = viewModel::onReapplyLayout,
        onAddPlaceholder = viewModel::onAddPlaceholder,
        onRenameSlide = viewModel::onRenameSlide,
        onChangeTheme = viewModel::onChangeTheme,
        onSaveAsTheme = viewModel::onSaveAsTheme,
        onDeleteUserTheme = viewModel::onDeleteUserTheme,
        onApplyObjectStyle = viewModel::onApplyObjectStyle,
        onSaveObjectStyle = viewModel::onSaveObjectStyle,
        onRenameObjectStyle = viewModel::onRenameObjectStyle,
        onDeleteObjectStyle = viewModel::onDeleteObjectStyle,
        onSetDocumentBackground = viewModel::onSetDocumentBackground,
        onSetSlideSize = viewModel::onSetSlideSize,
        onPreviewGuide = viewModel::onPreviewGuide,
        onCommitGuide = viewModel::onCommitGuide,
        onRemoveGuide = viewModel::onRemoveGuide,
        onEndGuideDrag = viewModel::onEndGuideDrag,
        onPlay = onPlay,
    )
}

@Composable
fun EditorView(
    state: EditorState,
    onSelectSlide: (String) -> Unit,
    onToggleCollapsed: (String) -> Unit,
    onSelectElement: (String?) -> Unit,
    onToggleElementSelection: (String) -> Unit,
    /** A right-click on the canvas, and where in the canvas it landed: the screen
     * owns what happens next, the canvas only forwards it. */
    onContextClick: (elementId: String?, position: Offset) -> Unit,
    onPreviewMarquee: (Frame) -> Unit,
    onEndMarquee: () -> Unit,
    onCancelPreview: () -> Unit,
    onUpdateSlide: (Slide) -> Unit,
    /** An in-flight sample of a slide, the inspector's transition duration drag. */
    onPreviewSlide: (Slide) -> Unit,
    /** The transition the slide plays on its way out; null puts it back on the
     * deck's own. The Animate tab's transition section. */
    onSetSlideTransition: (slideId: String, transition: SlideTransition?) -> Unit,
    /** The selected slide's build order, all four verbs of the Animate tab's
     * list: what it appends, what its controls write, what its Remove drops, and
     * what a row dragged to a new place lands as. */
    onAddBuild: (Build) -> Unit = {},
    onUpdateBuild: (index: Int, build: Build) -> Unit = { _, _ -> },
    onRemoveBuild: (index: Int) -> Unit = {},
    onMoveBuild: (from: Int, to: Int) -> Unit = { _, _ -> },
    onUpdateElements: (List<Element>) -> Unit,
    onPreviewElements: (List<Element>) -> Unit,
    /** A toolbar insertion, already sized and placed: the element goes on the
     * selected slide and takes the selection. */
    onInsertElement: (Element) -> Unit,
    /** A double click on a text element on the canvas, and what ends that edit. */
    onBeginTextEdit: (String) -> Unit,
    onEndTextEdit: () -> Unit,
    onReorderElements: (List<String>, ZOrderMove) -> Unit,
    onSetElementsLocked: (List<String>, Boolean) -> Unit,
    onFlipElements: (List<String>, FlipAxis) -> Unit,
    onGroupElements: (List<String>) -> Unit,
    onUngroupElements: (String) -> Unit,
    onSelectInspectorTab: (InspectorTab) -> Unit,
    /** In and out of layout mode: the navigator swaps slides for layouts, and the
     * canvas edits one of them. */
    onEditSlideLayouts: () -> Unit = {},
    onExitSlideLayouts: () -> Unit = {},
    onApplyLayout: (slideId: String, layoutId: String?) -> Unit = { _, _ -> },
    onReapplyLayout: (slideId: String) -> Unit = {},
    onAddPlaceholder: (PlaceholderRole) -> Unit = {},
    /** Renames a slide, or a layout: the navigator's Rename and the Name field. */
    onRenameSlide: (id: String, title: String) -> Unit = { _, _ -> },
    /** The deck's look, all of it the inspector's Slide tab: the theme it is on,
     * the theme it is saved out as, the one it drops, and what sits behind every
     * slide that asks for none of its own. */
    onChangeTheme: (name: String) -> Unit = {},
    onSaveAsTheme: (name: String) -> Unit = {},
    onDeleteUserTheme: (name: String) -> Unit = {},
    /** The deck's saved shape looks, all four of them the shape section's style
     * strip: the one it puts on the selection, the one it saves off it, and the
     * two that keep the library tidy. */
    onApplyObjectStyle: (ids: List<String>, styleId: String) -> Unit = { _, _ -> },
    onSaveObjectStyle: (shapeId: String, name: String) -> Unit = { _, _ -> },
    onRenameObjectStyle: (styleId: String, name: String) -> Unit = { _, _ -> },
    onDeleteObjectStyle: (styleId: String) -> Unit = {},
    onSetDocumentBackground: (SlideBackground?) -> Unit = {},
    /** The shape every slide in the deck is laid out at, and whether the content
     * comes across with it. */
    onSetSlideSize: (width: Float, height: Float, scaleContent: Boolean) -> Unit = { _, _, _ -> },
    /** A guide drag on the canvas: its samples, its drop, the guide it throws
     * away off the slide, and its cancel. What the drag draws is
     * [EditorState.guideDrag], which these four feed. */
    onPreviewGuide: (id: String?, axis: GuideAxis, position: Float) -> Unit = { _, _, _ -> },
    onCommitGuide: (id: String?, axis: GuideAxis, position: Float) -> Unit = { _, _, _ -> },
    onRemoveGuide: (id: String) -> Unit = {},
    onEndGuideDrag: () -> Unit = {},
    /** What the canvas context menu shows, sections in order. Empty hides it. */
    menuSections: List<EditorMenuSection> = emptyList(),
    /** Takes over from [menuSections]: the shell draws the menu, this view only
     * hands it the click in window coordinates. */
    onShowContextMenu: ((elementId: String?, positionInWindow: Offset) -> Unit)? = null,
    /** A right-click on a navigator row, before its menu opens. */
    onSlideContextClick: (slideId: String) -> Unit = {},
    /** What the navigator's context menu shows for a given row. Empty hides it.
     * [onRename] is what a Rename verb picks: this view owns that dialog. */
    slideMenuSections: (slideId: String, onRename: () -> Unit) -> List<EditorMenuSection> =
        { _, _ -> emptyList() },
    /** [onShowContextMenu]'s counterpart for the navigator. */
    onShowSlideContextMenu: ((
        slideId: String,
        positionInWindow: Offset,
        onRename: () -> Unit,
    ) -> Unit)? = null,
    /** A navigator drag reporting the gap it is over, its drop, and its cancel.
     * What the drag draws is [EditorState.slideDrag], which these three feed. */
    onPreviewSlideDrag: (slideId: String, afterId: String?, nest: Boolean, translationY: Float) -> Unit = { _, _, _, _ -> },
    onMoveSlide: (slideId: String, afterId: String?, nest: Boolean) -> Unit = { _, _, _ -> },
    onEndSlideDrag: () -> Unit = {},
    onPlay: ((Document, Int) -> Unit)? = null,
    theme: ChromeTheme = LinuxChrome,
    modifier: Modifier = Modifier,
) {
    val dark: Boolean = isSystemInDarkTheme()
    val tokens: ChromeTokens = if (dark) theme.dark else theme.light

    CompositionLocalProvider(
        LocalChromeTokens provides tokens,
        LocalChromeTheme provides theme,
    ) {
        MaterialTheme(colorScheme = tokens.toColorScheme(dark)) {
            val selectedSlide: Slide = state.selectedSlide
            // Zoom is view-local, like the macOS shell: 0 means Fit.
            var zoomPercent: Int by remember { mutableStateOf(0) }
            // Where the context menu is open, null when it isn't. View-local like
            // zoom: what the click does to the selection rides the loop, where the
            // menu it opens sits is this shell's business alone. A second
            // right-click while one is open just moves it.
            var menuAt: Offset? by remember { mutableStateOf(null) }
            // The navigator's own pair: where its menu is open, and the row it
            // opened on, which is what the verbs in it carry.
            var slideMenuAt: Offset? by remember { mutableStateOf(null) }
            var slideMenuFor: String? by remember { mutableStateOf(null) }
            // The row a Rename verb was picked on, null when no dialog is open.
            // View-local like the menus: the name only reaches the loop on OK.
            var renamingId: String? by remember { mutableStateOf(null) }

            Column(modifier.fillMaxSize().background(tokens.chrome)) {
                EditorToolbar(
                    zoomPercent = zoomPercent,
                    onZoomPercentChange = { zoomPercent = it },
                    onPlay = if (onPlay == null) null else {
                        { onPlay(state.document, state.selectedSlideIndex().coerceAtLeast(0)) }
                    },
                    // The toolbar picks what to insert; where it lands and how
                    // big it starts is the state's and the catalog's business,
                    // and what it is dressed in is the deck's theme's.
                    onInsertText = {
                        onInsertElement(
                            textBoxElement(
                                state.insertionFrame(DefaultTextBoxWidth, DefaultTextBoxHeight),
                                state.defaults,
                            ),
                        )
                    },
                    onInsertCode = {
                        onInsertElement(
                            codeBoxElement(
                                state.insertionFrame(DefaultCodeBoxWidth, DefaultCodeBoxHeight),
                                state.defaults,
                            ),
                        )
                    },
                    onInsertTerminal = {
                        onInsertElement(
                            terminalElement(
                                state.insertionFrame(DefaultTerminalWidth, DefaultTerminalHeight),
                                state.defaults,
                            ),
                        )
                    },
                    onInsertDiagram = {
                        onInsertElement(
                            diagramElement(
                                state.insertionFrame(DefaultDiagramWidth, DefaultDiagramHeight),
                                state.defaults,
                            ),
                        )
                    },
                    onInsertEquation = {
                        onInsertElement(
                            equationElement(
                                state.insertionFrame(DefaultEquationWidth, DefaultEquationHeight),
                                state.defaults,
                            ),
                        )
                    },
                    onInsertShape = { entry ->
                        onInsertElement(
                            entry.element(
                                state.insertionFrame(entry.width, entry.height),
                                state.defaults,
                            ),
                        )
                    },
                )

                Row(Modifier.weight(1f).fillMaxWidth()) {
                    // The navigator gets its own anchor rather than the well's:
                    // the menu belongs over the rows, and a sibling box keeps the
                    // canvas anchor exactly what it was.
                    if (state.sidebarOpen) Box {
                        // The panel scrolls and the rows report in window
                        // coordinates, so the anchor converts back into this box,
                        // which sits still: the menu stays where it was clicked.
                        var navigatorCoords: LayoutCoordinates? by remember {
                            mutableStateOf(null)
                        }

                        EditorNavigator(
                            document = state.document,
                            entries = state.fullOutline(),
                            selectedSlideId = selectedSlide.id,
                            onSelectSlide = onSelectSlide,
                            onToggleCollapsed = onToggleCollapsed,
                            thumbnailRadius = theme.thumbR,
                            isEditingLayouts = state.isEditingLayouts,
                            onExitSlideLayouts = onExitSlideLayouts,
                            onContextClick = { slideId, positionInWindow ->
                                onSlideContextClick(slideId)
                                val native = onShowSlideContextMenu
                                if (native == null) {
                                    slideMenuFor = slideId
                                    slideMenuAt = navigatorCoords
                                        ?.windowToLocal(positionInWindow)
                                        ?: positionInWindow
                                } else {
                                    native(slideId, positionInWindow) { renamingId = slideId }
                                }
                            },
                            slideDrag = state.slideDrag,
                            onPreviewSlideDrag = onPreviewSlideDrag,
                            onMoveSlide = onMoveSlide,
                            onEndSlideDrag = onEndSlideDrag,
                            modifier = Modifier.onGloballyPositioned { navigatorCoords = it },
                        )

                        val rowSections: List<EditorMenuSection> = slideMenuFor
                            ?.let { id -> slideMenuSections(id) { renamingId = id } }
                            .orEmpty()

                        if (rowSections.isNotEmpty()) ContextMenu(
                            sections = rowSections,
                            position = slideMenuAt,
                            onDismiss = { slideMenuAt = null; slideMenuFor = null },
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                    }

                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(tokens.well)
                                // Past-Fit zoom clips at the well bounds instead of
                                // painting over the surrounding chrome.
                                .clipToBounds()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Where the canvas sits in the window, for the shell
                            // that draws its menu there rather than in here.
                            var canvasCoords: LayoutCoordinates? by remember {
                                mutableStateOf(null)
                            }

                            EditorCanvas(
                                slide = selectedSlide,
                                layout = state.selectedLayout,
                                isEditingLayouts = state.isEditingLayouts,
                                // The badges are the Animate tab's own reading
                                // of the slide, so they show exactly while that
                                // tab is the one open. A layout has no builds.
                                showBuildBadges = state.inspectorOpen &&
                                    state.inspectorTab == InspectorTab.Animate &&
                                    !state.isEditingLayouts,
                                background = state.document.background,
                                selectedElementIds = state.selectedElementIds,
                                marquee = state.marquee,
                                onSelectElement = onSelectElement,
                                onToggleElementSelection = onToggleElementSelection,
                                onContextClick = { elementId, position ->
                                    onContextClick(elementId, position)
                                    val native = onShowContextMenu
                                    if (native == null) menuAt = position
                                    else native(
                                        elementId,
                                        canvasCoords?.localToWindow(position) ?: position,
                                    )
                                },
                                onPreviewMarquee = onPreviewMarquee,
                                onEndMarquee = onEndMarquee,
                                onUpdateElements = onUpdateElements,
                                onPreviewElements = onPreviewElements,
                                onPreviewCancel = onCancelPreview,
                                editingElementId = state.editingElementId,
                                onBeginTextEdit = onBeginTextEdit,
                                onEndTextEdit = onEndTextEdit,
                                zoom = if (zoomPercent == 0) null else zoomPercent / 100f,
                                slideWidth = state.document.slideWidth,
                                slideHeight = state.document.slideHeight,
                                number = state.slideNumber(selectedSlide.id),
                                guides = state.document.guides,
                                showRulers = state.showRulers,
                                showGuides = state.showGuides,
                                guideDrag = state.guideDrag,
                                snapTargets = { ids -> state.snapTargets(ids) },
                                onPreviewGuide = onPreviewGuide,
                                onCommitGuide = onCommitGuide,
                                onRemoveGuide = onRemoveGuide,
                                onEndGuideDrag = onEndGuideDrag,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onGloballyPositioned { canvasCoords = it },
                            )

                            // The canvas reports in its own space and fills this
                            // well's content box, so a zero-size anchor pinned to
                            // the same corner puts the menu under the pointer.
                            if (menuSections.isNotEmpty()) ContextMenu(
                                sections = menuSections,
                                position = menuAt,
                                onDismiss = { menuAt = null },
                                modifier = Modifier.align(Alignment.TopStart),
                            )
                        }

                        if (state.showNotes) SpeakerNotes(notes = selectedSlide.notes)

                        // In layout mode the count is of layouts: the deck's slide
                        // count says nothing about where the canvas is.
                        val layouts: List<Slide> = state.document.layouts
                        EditorStatusBar(
                            noun = if (state.isEditingLayouts) "layout" else "slide",
                            slideNumber = if (state.isEditingLayouts) {
                                layouts.indexOfFirst { it.id == selectedSlide.id } + 1
                            } else {
                                state.selectedSlideIndex() + 1
                            },
                            slideCount = if (state.isEditingLayouts) layouts.size
                            else state.document.allSlides().size,
                            uiLabel = theme.uiLabel + if (dark) " · dark" else " · light",
                            slideWidth = state.document.slideWidth,
                            slideHeight = state.document.slideHeight,
                        )
                    }

                    if (state.inspectorOpen) EditorInspector(
                        tab = state.inspectorTab,
                        onSelectTab = onSelectInspectorTab,
                        slide = selectedSlide,
                        onUpdateSlide = onUpdateSlide,
                        onPreviewSlide = onPreviewSlide,
                        onSetSlideTransition = onSetSlideTransition,
                        onAddBuild = onAddBuild,
                        onUpdateBuild = onUpdateBuild,
                        onRemoveBuild = onRemoveBuild,
                        onMoveBuild = onMoveBuild,
                        onSelectElement = onSelectElement,
                        layouts = state.document.layouts,
                        isEditingLayouts = state.isEditingLayouts,
                        onApplyLayout = onApplyLayout,
                        onReapplyLayout = onReapplyLayout,
                        onEditSlideLayouts = onEditSlideLayouts,
                        onExitSlideLayouts = onExitSlideLayouts,
                        onAddPlaceholder = onAddPlaceholder,
                        onRenameSlide = onRenameSlide,
                        themes = state.themes,
                        userThemes = state.userThemes,
                        themeName = state.document.themeName,
                        documentBackground = state.document.background,
                        slideWidth = state.document.slideWidth,
                        slideHeight = state.document.slideHeight,
                        slideSizePreset = state.document.slideSizePreset(),
                        onChangeTheme = onChangeTheme,
                        onSaveAsTheme = onSaveAsTheme,
                        onDeleteUserTheme = onDeleteUserTheme,
                        onSetDocumentBackground = onSetDocumentBackground,
                        onSetSlideSize = onSetSlideSize,
                        objectStyles = state.objectStyles,
                        onApplyObjectStyle = onApplyObjectStyle,
                        onSaveObjectStyle = onSaveObjectStyle,
                        onRenameObjectStyle = onRenameObjectStyle,
                        onDeleteObjectStyle = onDeleteObjectStyle,
                        selectedElements = state.selectedElements,
                        onUpdateElements = onUpdateElements,
                        onPreviewElements = onPreviewElements,
                        onReorderElements = onReorderElements,
                        onSetElementsLocked = onSetElementsLocked,
                        onFlipElements = onFlipElements,
                        onGroupElements = onGroupElements,
                        onUngroupElements = onUngroupElements,
                    )
                }
            }

            // Outside the layout rather than in the navigator: a dialog belongs to
            // the window, and the row it was opened on can well have scrolled away
            // before it is answered. Keyed on the id, so it holds the name of the
            // row it opened on whatever the selection does meanwhile.
            renamingId?.let { id ->
                NameDialog(
                    title = "Rename",
                    confirmLabel = "Rename",
                    name = state.document.slideById(id)?.title.orEmpty(),
                    onDismiss = { renamingId = null },
                    onCommit = { name ->
                        renamingId = null
                        onRenameSlide(id, name)
                    },
                )
            }
        }
    }
}

/**
 * A name prompt: one field over the name it starts with, committed by the button
 * or by Enter. A blank name is no name, so it commits nothing.
 *
 * The navigator's Rename and the inspector's Save Theme are the same dialog with
 * different words on it, which is all [title] and [confirmLabel] are for.
 */
@Composable
internal fun NameDialog(
    title: String,
    confirmLabel: String,
    name: String,
    onDismiss: () -> Unit,
    onCommit: (String) -> Unit,
) {
    var text: String by remember(name) { mutableStateOf(name) }
    val focus: FocusRequester = remember { FocusRequester() }

    fun commit() {
        val entered: String = text.trim()
        if (entered.isEmpty()) onDismiss() else onCommit(entered)
    }

    // The name is what the dialog is for, so it is typed into straight away
    // rather than after a click.
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        val entered: Boolean = event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        if (entered) commit()
                        entered
                    },
            )
        },
        confirmButton = { TextButton(onClick = ::commit) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A context menu, the canvas's or the navigator's: [sections] divided in order,
 * anchored at [position] in its own anchor's space, in pixels, through a
 * zero-size box offset to it. A null [position] is a closed menu.
 *
 * Material 3 has no submenu, so a child-bearing item renders as a dim,
 * unclickable header with its children indented under it. The alternative is a
 * popup opening out of a popup on hover, which is a good deal more machinery
 * than eight align and distribute verbs are worth.
 */
@Composable
private fun ContextMenu(
    sections: List<EditorMenuSection>,
    position: Offset?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val density: Density = LocalDensity.current
    val anchor: Offset = position ?: Offset.Zero
    val x: Dp = with(density) { anchor.x.toDp() }
    val y: Dp = with(density) { anchor.y.toDp() }

    Box(modifier.offset(x = x, y = y)) {
        DropdownMenu(expanded = position != null, onDismissRequest = onDismiss) {
            sections.forEachIndexed { index, section ->
                if (index > 0) HorizontalDivider(thickness = 1.dp, color = tokens.div)

                for (item in section.items) {
                    if (item.children.isEmpty()) {
                        ContextMenuItem(item = item, onDismiss = onDismiss)
                    } else {
                        ContextMenuHeader(label = item.label, enabled = item.enabled)
                        for (child in item.children) {
                            ContextMenuItem(item = child, onDismiss = onDismiss, indent = 12.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextMenuItem(item: EditorMenuItem, onDismiss: () -> Unit, indent: Dp = 0.dp) {
    DropdownMenuItem(
        text = {
            Text(text = item.label, fontSize = 13.sp, modifier = Modifier.padding(start = indent))
        },
        enabled = item.enabled,
        onClick = {
            onDismiss()
            item.onPick()
        },
    )
}

/** The label of a submenu, greyed with the branch it heads. Picks nothing. */
@Composable
private fun ContextMenuHeader(label: String, enabled: Boolean) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Text(
        text = label,
        color = if (enabled) tokens.dim else tokens.dim.copy(alpha = 0.38f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
    )
}

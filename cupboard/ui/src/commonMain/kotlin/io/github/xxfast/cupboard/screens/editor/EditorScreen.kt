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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.editor.EditorCanvas
import io.github.xxfast.cupboard.theme.ChromeTheme
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LinuxChrome
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
        // open re-reads its enablement as the click's selection settles.
        menuSections = canvasMenuSections(state, viewModel),
        onPreviewMarquee = viewModel::onPreviewMarquee,
        onEndMarquee = viewModel::onEndMarquee,
        onCancelPreview = viewModel::onCancelPreview,
        onUpdateElements = viewModel::onUpdateElements,
        onPreviewElements = viewModel::onPreviewElements,
        onReorderElements = viewModel::onReorderElements,
        onSetElementsLocked = viewModel::onSetElementsLocked,
        onFlipElements = viewModel::onFlipElements,
        onGroupElements = viewModel::onGroupElements,
        onUngroupElements = viewModel::onUngroupElements,
        onSelectInspectorTab = viewModel::onSelectInspectorTab,
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
    onUpdateElements: (List<Element>) -> Unit,
    onPreviewElements: (List<Element>) -> Unit,
    onReorderElements: (List<String>, ZOrderMove) -> Unit,
    onSetElementsLocked: (List<String>, Boolean) -> Unit,
    onFlipElements: (List<String>, FlipAxis) -> Unit,
    onGroupElements: (List<String>) -> Unit,
    onUngroupElements: (String) -> Unit,
    onSelectInspectorTab: (InspectorTab) -> Unit,
    /** What the canvas context menu shows, sections in order. Empty hides it. */
    menuSections: List<EditorMenuSection> = emptyList(),
    onPlay: ((Document, Int) -> Unit)? = null,
    theme: ChromeTheme = LinuxChrome,
    modifier: Modifier = Modifier,
) {
    val dark: Boolean = isSystemInDarkTheme()
    val tokens: ChromeTokens = if (dark) theme.dark else theme.light

    CompositionLocalProvider(LocalChromeTokens provides tokens) {
        MaterialTheme(colorScheme = tokens.toColorScheme(dark)) {
            val selectedSlide: Slide = state.selectedSlide
            // Zoom is view-local, like the macOS shell: 0 means Fit.
            var zoomPercent: Int by remember { mutableStateOf(0) }
            // Where the context menu is open, null when it isn't. View-local like
            // zoom: what the click does to the selection rides the loop, where the
            // menu it opens sits is this shell's business alone. A second
            // right-click while one is open just moves it.
            var menuAt: Offset? by remember { mutableStateOf(null) }

            Column(modifier.fillMaxSize().background(tokens.chrome)) {
                EditorToolbar(
                    zoomPercent = zoomPercent,
                    onZoomPercentChange = { zoomPercent = it },
                    onPlay = if (onPlay == null) null else {
                        { onPlay(state.document, state.selectedSlideIndex().coerceAtLeast(0)) }
                    },
                )

                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (state.sidebarOpen) EditorNavigator(
                        document = state.document,
                        entries = state.fullOutline(),
                        selectedSlideId = selectedSlide.id,
                        onSelectSlide = onSelectSlide,
                        onToggleCollapsed = onToggleCollapsed,
                        thumbnailRadius = theme.thumbR,
                    )

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
                            EditorCanvas(
                                slide = selectedSlide,
                                selectedElementIds = state.selectedElementIds,
                                marquee = state.marquee,
                                onSelectElement = onSelectElement,
                                onToggleElementSelection = onToggleElementSelection,
                                onContextClick = { elementId, position ->
                                    onContextClick(elementId, position)
                                    menuAt = position
                                },
                                onPreviewMarquee = onPreviewMarquee,
                                onEndMarquee = onEndMarquee,
                                onUpdateElements = onUpdateElements,
                                onPreviewElements = onPreviewElements,
                                onPreviewCancel = onCancelPreview,
                                zoom = if (zoomPercent == 0) null else zoomPercent / 100f,
                                modifier = Modifier.fillMaxSize(),
                            )

                            // The canvas reports in its own space and fills this
                            // well's content box, so a zero-size anchor pinned to
                            // the same corner puts the menu under the pointer.
                            if (menuSections.isNotEmpty()) CanvasContextMenu(
                                sections = menuSections,
                                position = menuAt,
                                onDismiss = { menuAt = null },
                                modifier = Modifier.align(Alignment.TopStart),
                            )
                        }

                        if (state.showNotes) SpeakerNotes(notes = selectedSlide.notes)

                        EditorStatusBar(
                            slideNumber = state.selectedSlideIndex() + 1,
                            slideCount = state.document.allSlides().size,
                            uiLabel = theme.uiLabel + if (dark) " · dark" else " · light",
                        )
                    }

                    if (state.inspectorOpen) EditorInspector(
                        tab = state.inspectorTab,
                        onSelectTab = onSelectInspectorTab,
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
        }
    }
}

/**
 * The canvas context menu: [sections] divided in order, anchored at [position]
 * in the canvas's own space, in pixels, through a zero-size box offset to it.
 * A null [position] is a closed menu.
 *
 * Material 3 has no submenu, so a child-bearing item renders as a dim,
 * unclickable header with its children indented under it. The alternative is a
 * popup opening out of a popup on hover, which is a good deal more machinery
 * than eight align and distribute verbs are worth.
 */
@Composable
private fun CanvasContextMenu(
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

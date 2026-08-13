package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
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
        onUpdateSlide = viewModel::onUpdateSlide,
        onPreviewSlide = viewModel::onPreviewSlide,
        onCancelPreview = viewModel::onCancelPreview,
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
    onUpdateSlide: (Slide) -> Unit,
    onPreviewSlide: (Slide) -> Unit,
    onCancelPreview: () -> Unit,
    onSelectInspectorTab: (InspectorTab) -> Unit,
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
                                selectedElementId = state.selectedElementId,
                                onSelectElement = onSelectElement,
                                onSlideChange = onUpdateSlide,
                                onSlidePreview = onPreviewSlide,
                                onPreviewCancel = onCancelPreview,
                                zoom = if (zoomPercent == 0) null else zoomPercent / 100f,
                                modifier = Modifier.fillMaxSize(),
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
                    )
                }
            }
        }
    }
}

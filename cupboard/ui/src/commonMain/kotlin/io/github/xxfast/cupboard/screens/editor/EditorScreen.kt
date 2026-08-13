package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.canvas.SlideThumbnail
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.editor.EditorCanvas

/**
 * Phase 1 demo shell: navigator (flat outline, thumbnails) + editable canvas.
 * Real per-OS chrome replaces this in later phases.
 *
 * [onPlay] non-null shows a Play button that receives the current document and
 * selected slide index; null hides play entirely (android/web shells).
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
    onPlay: ((Document, Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val selectedSlide: Slide = state.selectedSlide

        Row(modifier.fillMaxSize().background(Color(0xFF17181C))) {
            Navigator(
                document = state.document,
                entries = state.outline(),
                selectedSlideId = selectedSlide.id,
                onSelectSlide = onSelectSlide,
                onToggleCollapsed = onToggleCollapsed,
            )
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(28.dp)) {
                    if (onPlay != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    onPlay(state.document, state.selectedSlideIndex().coerceAtLeast(0))
                                },
                            ) {
                                Text("▶ Play", color = Color(0xFFD9CFFF), fontSize = 13.sp)
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EditorCanvas(
                            slide = selectedSlide,
                            selectedElementId = state.selectedElementId,
                            onSelectElement = onSelectElement,
                            onSlideChange = onUpdateSlide,
                            onSlidePreview = onPreviewSlide,
                            onPreviewCancel = onCancelPreview,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (state.showNotes) SpeakerNotes(notes = selectedSlide.notes)
            }
        }
    }
}

@Composable
private fun SpeakerNotes(notes: String) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF1E1F26))) {
        HorizontalDivider(thickness = 1.dp, color = Color(0xFF33363D))
        Column(
            modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "SPEAKER NOTES",
                color = Color(0xFF8A8B94),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            if (notes.isNotEmpty()) Text(
                text = notes,
                color = Color(0xFFA0A0A8),
                fontSize = 13.5.sp,
                lineHeight = 20.25.sp,
            )
        }
    }
}

@Composable
private fun Navigator(
    document: Document,
    entries: List<OutlineEntry>,
    selectedSlideId: String,
    onSelectSlide: (String) -> Unit,
    onToggleCollapsed: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(Color(0xFF1E1F26))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (entry in entries) {
            val slide = document.slides[entry.slideIndex]
            val selected = entry.slideId == selectedSlideId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (entry.depth * 18).dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) {
                    if (entry.hasChildren) Text(
                        text = if (entry.collapsed) "▸" else "▾",
                        color = Color(0xFF8A8B94),
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onToggleCollapsed(entry.slideId) },
                    )
                }
                Text(
                    text = "${entry.slideIndex + 1}",
                    color = Color(0xFF8A8B94),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(16.dp),
                )
                SlideThumbnail(
                    slide = slide,
                    width = (150 - entry.depth * 18).dp,
                    modifier = Modifier
                        .clickable { onSelectSlide(entry.slideId) }
                        .let {
                            if (selected) it.border(2.dp, Color(0xFF7F52FF), RoundedCornerShape(5.dp))
                            else it
                        },
                )
            }
        }
    }
}

package io.github.xxfast.cupboard.screens.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                entries = state.fullOutline(),
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
            // Rows carry their own 6.dp bottom gap (so it collapses away with
            // them); the last one plus this padding lands on the design's 14.
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
    ) {
        for (entry in entries) {
            val slide: Slide = document.slides[entry.slideIndex]
            val selected: Boolean = entry.slideId == selectedSlideId
            // Hidden rows stay in the tree so collapsing animates them out.
            AnimatedVisibility(
                visible = entry.visible,
                enter = expandVertically(tween(durationMillis = 140)) + fadeIn(tween(durationMillis = 140)),
                exit = shrinkVertically(tween(durationMillis = 140)) + fadeOut(tween(durationMillis = 140)),
            ) {
                NavigatorRow(
                    slide = slide,
                    entry = entry,
                    selected = selected,
                    onSelectSlide = onSelectSlide,
                    onToggleCollapsed = onToggleCollapsed,
                )
            }
        }
    }
}

@Composable
private fun NavigatorRow(
    slide: Slide,
    entry: OutlineEntry,
    selected: Boolean,
    onSelectSlide: (String) -> Unit,
    onToggleCollapsed: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Color(0xFF38353F) else Color.Transparent)
            .clickable { onSelectSlide(entry.slideId) }
            .padding(top = 5.dp, bottom = 5.dp, end = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Fixed gutter, outside the indent, so every chevron shares one left rail.
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(16.dp)
                .clip(RoundedCornerShape(3.dp))
                .let {
                    if (entry.hasChildren) it.clickable { onToggleCollapsed(entry.slideId) }
                    else it
                },
            contentAlignment = Alignment.Center,
        ) {
            if (entry.hasChildren) DisclosureChevron(collapsed = entry.collapsed)
        }
        Row(
            modifier = Modifier.padding(start = (entry.depth * 12).dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${entry.slideIndex + 1}",
                color = Color(0xFF8A8B94),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.width(14.dp).padding(top = 2.dp),
            )
            SlideThumbnail(
                slide = slide,
                width = (136 - 12 * minOf(entry.depth, 3)).dp,
                modifier = if (selected) {
                    Modifier.border(2.dp, Color(0xFF7F52FF), RoundedCornerShape(5.dp))
                } else {
                    Modifier
                },
            )
        }
    }
}

/**
 * The navigator's disclosure control: a stroked chevron in a 9x9 dp space,
 * pointing right when collapsed and rotating down when the children show.
 */
@Composable
private fun DisclosureChevron(collapsed: Boolean) {
    val rotation: Float by animateFloatAsState(
        targetValue = if (collapsed) 0f else 90f,
        animationSpec = tween(durationMillis = 140),
    )
    Canvas(Modifier.size(9.dp).rotate(rotation)) {
        val scale: Float = size.width / 9f
        val chevron: Path = Path().apply {
            moveTo(2.6f * scale, 1.1f * scale)
            lineTo(6.4f * scale, 4.5f * scale)
            lineTo(2.6f * scale, 7.9f * scale)
        }
        drawPath(
            path = chevron,
            color = Color(0xFFCBC4D5),
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

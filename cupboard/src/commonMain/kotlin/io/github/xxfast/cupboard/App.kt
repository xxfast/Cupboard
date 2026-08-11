package io.github.xxfast.cupboard

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.canvas.SlideThumbnail
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.editor.EditorCanvas
import io.github.xxfast.cupboard.editor.EditorStore
import io.github.xxfast.cupboard.editor.OutlineEntry

/**
 * Phase 1 demo shell: navigator (flat outline, thumbnails) + editable canvas.
 * Real per-OS chrome replaces this in later phases.
 *
 * State lives in [store] so other windows (the desktop play window) can share
 * the same instance; screen logic is common, this is only the Compose shell.
 *
 * [onPlay] non-null shows a Play button that receives the current document and
 * selected slide index; null hides play entirely (android/web shells).
 */
@Composable
fun App(
    onPlay: ((Document, Int) -> Unit)? = null,
    store: EditorStore = remember { EditorStore(sampleDocument()) },
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val selectedSlide = store.selectedSlide

        Row(Modifier.fillMaxSize().background(Color(0xFF17181C))) {
            Navigator(
                document = store.document,
                entries = store.outline(),
                selectedSlideId = selectedSlide.id,
                onSelectSlide = { store.selectSlide(it) },
                onToggleCollapsed = { store.toggleCollapsed(it) },
            )
            Column(Modifier.weight(1f).fillMaxHeight().padding(28.dp)) {
                if (onPlay != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                onPlay(store.document, store.selectedSlideIndex().coerceAtLeast(0))
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
                        selectedElementId = store.selectedElementId,
                        onSelectElement = { store.selectElement(it) },
                        onSlideChange = { store.updateSlide(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
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

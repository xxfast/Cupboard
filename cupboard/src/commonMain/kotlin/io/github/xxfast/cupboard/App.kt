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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.canvas.SlideThumbnail
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.hasChildren
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.document.toggleCollapsed
import io.github.xxfast.cupboard.document.updateSlide
import io.github.xxfast.cupboard.document.visibleIndices
import io.github.xxfast.cupboard.editor.EditorCanvas

/**
 * Phase 1 demo shell: navigator (flat outline, thumbnails) + editable canvas.
 * Real per-OS chrome replaces this in later phases.
 *
 * [onPlay] non-null shows a Play button that receives the current document and
 * selected slide index; null hides play entirely (android/web shells).
 */
@Composable
fun App(onPlay: ((Document, Int) -> Unit)? = null) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        var document by remember { mutableStateOf(sampleDocument()) }
        val slides = document.allSlides()
        var selectedSlideId by remember {
            mutableStateOf(slides.first { it.elements.isNotEmpty() }.id)
        }
        var selectedElementId by remember { mutableStateOf<String?>(null) }
        val selectedSlide = slides.firstOrNull { it.id == selectedSlideId } ?: slides.first()

        Row(Modifier.fillMaxSize().background(Color(0xFF17181C))) {
            Navigator(
                document = document,
                selectedSlideId = selectedSlide.id,
                onSelectSlide = { selectedSlideId = it; selectedElementId = null },
                onToggleCollapsed = { document = document.toggleCollapsed(it) },
            )
            Column(Modifier.weight(1f).fillMaxHeight().padding(28.dp)) {
                if (onPlay != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                val index = document.slides
                                    .indexOfFirst { it.id == selectedSlide.id }
                                    .coerceAtLeast(0)
                                onPlay(document, index)
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
                        selectedElementId = selectedElementId,
                        onSelectElement = { selectedElementId = it },
                        onSlideChange = { document = document.updateSlide(it) },
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
        for (index in document.visibleIndices()) {
            val slide = document.slides[index]
            val selected = slide.id == selectedSlideId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (slide.depth * 18).dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) {
                    if (document.hasChildren(index)) Text(
                        text = if (slide.collapsed) "▸" else "▾",
                        color = Color(0xFF8A8B94),
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onToggleCollapsed(slide.id) },
                    )
                }
                Text(
                    text = "${index + 1}",
                    color = Color(0xFF8A8B94),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(16.dp),
                )
                SlideThumbnail(
                    slide = slide,
                    width = (150 - slide.depth * 18).dp,
                    modifier = Modifier
                        .clickable { onSelectSlide(slide.id) }
                        .let {
                            if (selected) it.border(2.dp, Color(0xFF7F52FF), RoundedCornerShape(5.dp))
                            else it
                        },
                )
            }
        }
    }
}

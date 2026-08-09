package io.github.xxfast.cupboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideGroup
import io.github.xxfast.cupboard.document.SlideNode
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.document.updateSlide
import io.github.xxfast.cupboard.editor.EditorCanvas

/**
 * Phase 1 demo shell: navigator (nested, thumbnails) + editable canvas.
 * Real per-OS chrome replaces this in later phases.
 */
@Composable
fun App() {
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
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(28.dp),
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

@Composable
private fun Navigator(
    document: Document,
    selectedSlideId: String,
    onSelectSlide: (String) -> Unit,
) {
    val slideNumbers = document.allSlides().withIndex().associate { (i, slide) -> slide.id to i + 1 }
    Column(
        modifier = Modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(Color(0xFF1E1F26))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NavigatorNodes(document.nodes, depth = 0, slideNumbers, selectedSlideId, onSelectSlide)
    }
}

@Composable
private fun NavigatorNodes(
    nodes: List<SlideNode>,
    depth: Int,
    slideNumbers: Map<String, Int>,
    selectedSlideId: String,
    onSelectSlide: (String) -> Unit,
) {
    for (node in nodes) {
        when (node) {
            is Slide -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (depth * 22).dp)
                    .clickable { onSelectSlide(node.id) },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${slideNumbers[node.id]}",
                    color = Color(0xFF8A8B94),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(16.dp),
                )
                SlideThumbnail(
                    slide = node,
                    selected = node.id == selectedSlideId,
                    width = (160 - depth * 22).dp,
                )
            }

            is SlideGroup -> {
                Text(
                    text = "▾ ${node.title}",
                    color = Color(0xFF8A8B94),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = (depth * 22).dp),
                )
                NavigatorNodes(node.children, depth + 1, slideNumbers, selectedSlideId, onSelectSlide)
            }
        }
    }
}

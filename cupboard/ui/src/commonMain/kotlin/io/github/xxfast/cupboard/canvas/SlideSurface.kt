package io.github.xxfast.cupboard.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.xxfast.cupboard.document.Document
import kotlin.math.min

/** The scale factor SlideSurface applied: screen px per document unit, over base density. */
val LocalCanvasScale = compositionLocalOf { 1f }

/**
 * Fixed-size slide space scaled to fit its container, CuP-style: the content is
 * laid out at [Document.SLIDE_WIDTH] x [Document.SLIDE_HEIGHT] dp and the density
 * is multiplied by the scale, so 1dp inside == 1 document unit at every zoom.
 *
 * [zoom] null fits the slide to the container; otherwise the slide renders at
 * that fraction of native size and the container clips it (no reflow).
 */
@Composable
fun SlideSurface(
    modifier: Modifier = Modifier,
    background: Boolean = true,
    zoom: Float? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val scale = (zoom ?: min(
            maxWidth.value / Document.SLIDE_WIDTH,
            maxHeight.value / Document.SLIDE_HEIGHT,
        )).coerceAtLeast(0.01f)
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * scale, density.fontScale),
            LocalCanvasScale provides scale,
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(Document.SLIDE_WIDTH.dp, Document.SLIDE_HEIGHT.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .let { if (background) it.drawBehind { drawSlideBackground() } else it },
                content = content,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSlideBackground() {
    // design: linear-gradient(140deg, #2a2452 0%, #171930 55%, #101223 100%)
    drawRect(
        brush = Brush.linearGradient(
            0.0f to Color(0xFF2A2452),
            0.55f to Color(0xFF171930),
            1.0f to Color(0xFF101223),
            start = Offset.Zero,
            end = Offset(size.width * 0.643f, size.height * 0.766f),
        )
    )
}

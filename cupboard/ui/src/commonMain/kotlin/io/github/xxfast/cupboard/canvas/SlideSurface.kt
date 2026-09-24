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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.SlideBackground
import kotlin.math.min

/** The scale factor SlideSurface applied: screen px per document unit, over base density. */
val LocalCanvasScale = compositionLocalOf { 1f }

/**
 * The ink for slide furniture that has no colour of its own in the document: an
 * image's caption, an empty image's dashes. White on a dark slide and black on a
 * light one, read off the background [SlideSurface] is painting.
 */
val LocalSlideInk = compositionLocalOf { Color.White }

/**
 * Fixed-size slide space scaled to fit its container, CuP-style: the content is
 * laid out at [slideWidth] x [slideHeight] dp and the density is multiplied by
 * the scale, so 1dp inside == 1 document unit at every zoom.
 *
 * [slideWidth] and [slideHeight] are the deck's own slide size, `Document.slideWidth`
 * and `Document.slideHeight`. Defaulted to the constants so a preview or a test
 * can draw a slide without a document in hand; every call site inside the app
 * passes the document's.
 *
 * [zoom] null fits the slide to the container; otherwise the slide renders at
 * that fraction of native size and the container clips it (no reflow).
 *
 * [background] is whether to paint one at all; [slideBackground] is which one,
 * null being the app's own dark gradient. [crossfade] paints another background
 * under it and [slideBackground] over that at the crossfade's progress: how a
 * Magic Move's arriving slide takes over from the one it is replacing.
 */
@Composable
fun SlideSurface(
    modifier: Modifier = Modifier,
    background: Boolean = true,
    slideBackground: SlideBackground? = null,
    crossfade: BackgroundCrossfade? = null,
    zoom: Float? = null,
    slideWidth: Float = Document.SLIDE_WIDTH,
    slideHeight: Float = Document.SLIDE_HEIGHT,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val scale = (zoom ?: min(
            maxWidth.value / slideWidth,
            maxHeight.value / slideHeight,
        )).coerceAtLeast(0.01f)
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * scale, density.fontScale),
            LocalCanvasScale provides scale,
            LocalSlideInk provides if (slideBackground.isLight()) Color.Black else Color.White,
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(slideWidth.dp, slideHeight.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .let {
                        if (background) it.drawBehind {
                            if (crossfade != null) drawSlideBackground(crossfade.from)
                            drawSlideBackground(slideBackground, crossfade?.progress ?: 1f)
                        }
                        else it
                    },
                content = content,
            )
        }
    }
}

/** Whether ink on this background wants to be dark: the mean luminance of its stops. */
private fun SlideBackground?.isLight(): Boolean = when (this) {
    null -> false
    is SlideBackground.Color -> color.toComposeColor().luminance() > 0.5f
    is SlideBackground.Gradient ->
        (start.toComposeColor().luminance() + end.toComposeColor().luminance()) / 2 > 0.5f
}

private fun DrawScope.drawSlideBackground(background: SlideBackground?, alpha: Float = 1f) {
    when (background) {
        // design: linear-gradient(140deg, #2a2452 0%, #171930 55%, #101223 100%)
        null -> drawRect(
            brush = Brush.linearGradient(
                0.0f to Color(0xFF2A2452),
                0.55f to Color(0xFF171930),
                1.0f to Color(0xFF101223),
                start = Offset.Zero,
                end = Offset(size.width * 0.643f, size.height * 0.766f),
            ),
            alpha = alpha,
        )

        is SlideBackground.Color -> drawRect(color = background.color.toComposeColor(), alpha = alpha)

        is SlideBackground.Gradient -> drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    background.start.toComposeColor(),
                    background.end.toComposeColor(),
                ),
                start = gradientStop(size, background.angle, -1f),
                end = gradientStop(size, background.angle, 1f),
            ),
            alpha = alpha,
        )
    }
}

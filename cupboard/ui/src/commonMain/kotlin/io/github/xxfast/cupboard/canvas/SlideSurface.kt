package io.github.xxfast.cupboard.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.SlideBackground
import kotlinx.coroutines.delay
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
 *
 * [fallbackBackgrounds] is the rest of the cascade under [slideBackground],
 * nearest first (`Slide.backgroundCascade` past its head). Only a picture reads
 * it: whatever a [SlideBackground.Image] leaves uncovered, or all of it while its
 * bytes are missing, shows the next one down, and the app's gradient under that.
 */
@Composable
fun SlideSurface(
    modifier: Modifier = Modifier,
    background: Boolean = true,
    slideBackground: SlideBackground? = null,
    fallbackBackgrounds: List<SlideBackground> = emptyList(),
    crossfade: BackgroundCrossfade? = null,
    zoom: Float? = null,
    slideWidth: Float = Document.SLIDE_WIDTH,
    slideHeight: Float = Document.SLIDE_HEIGHT,
    content: @Composable BoxScope.() -> Unit,
) {
    val layers: List<SlideBackground?> = backgroundLayers(slideBackground, fallbackBackgrounds)
    val fromLayers: List<SlideBackground?>? = crossfade?.let { backgroundLayers(it.from) }
    val images: Map<String, ImageBitmap> =
        rememberBackgroundImages(if (background) layers + fromLayers.orEmpty() else emptyList())

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val fit: Float = (zoom ?: min(
            maxWidth.value / slideWidth,
            maxHeight.value / slideHeight,
        )).coerceAtLeast(0.01f)
        // The scale goes into the density, so a new one lays every element out
        // again, text measured afresh at its new size. Fitting a container that
        // is being resized (a window, the presenter display's split) would do
        // that every frame, so the slide stays laid out at the scale it settled
        // on and a layer stretches it the rest of the way until the size holds
        // still. A zoom is the editor's, which asks for exactly what it says.
        val scale: Float = if (zoom != null) fit else settledScale(fit)
        val stretch: Float = fit / scale
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * scale, density.fontScale),
            LocalCanvasScale provides scale,
            // A picture is read as whatever it sits on: sampling its pixels for
            // the two bits of furniture that ask would cost more than it buys.
            LocalSlideInk provides if (layers.last().isLight()) Color.Black else Color.White,
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(slideWidth.dp, slideHeight.dp)
                    .then(
                        if (stretch == 1f) Modifier
                        else Modifier.graphicsLayer { scaleX = stretch; scaleY = stretch },
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .let {
                        if (background) it.drawBehind {
                            if (fromLayers != null) drawSlideBackground(fromLayers, images)
                            drawSlideBackground(layers, images, crossfade?.progress ?: 1f)
                        }
                        else it
                    },
                content = content,
            )
        }
    }
}

/** How long a fitted slide's size has to hold still before it is laid out at it. */
private const val ScaleSettleMs: Long = 150

/**
 * [target], once it has stopped changing for [ScaleSettleMs]: the scale a fitted
 * slide is laid out at. The first one is taken as it comes, so a slide that
 * never resizes never waits.
 */
@Composable
private fun settledScale(target: Float): Float {
    var settled: Float by remember { mutableFloatStateOf(target) }
    LaunchedEffect(target) {
        if (target == settled) return@LaunchedEffect
        delay(ScaleSettleMs)
        settled = target
    }
    return settled
}

/**
 * What actually paints behind a slide, top first: [top], and when that is a
 * picture, the cascade under it down to the first background that covers the
 * slide, or null (the app's own gradient) when none of them does.
 *
 * Never empty, and always ending in something opaque, so drawing it bottom-up
 * covers the slide whichever of its pictures have landed.
 */
private fun backgroundLayers(
    top: SlideBackground?,
    fallbacks: List<SlideBackground> = emptyList(),
): List<SlideBackground?> {
    if (top == null) return listOf(null)
    val cascade: List<SlideBackground> = listOf(top) + fallbacks
    val covering: Int = cascade.indexOfFirst { it !is SlideBackground.Image }
    return if (covering < 0) cascade + null else cascade.take(covering + 1)
}

/**
 * The decoded pictures among [layers], by asset id. One that is still being read,
 * or that the store has nothing under, is simply not in the map, and its layer
 * draws nothing over the ones under it.
 */
@Composable
private fun rememberBackgroundImages(layers: List<SlideBackground?>): Map<String, ImageBitmap> {
    val ids: List<String> = layers
        .filterIsInstance<SlideBackground.Image>()
        .map { it.assetId }
        .distinct()
    val images: MutableMap<String, ImageBitmap> = mutableMapOf()
    for (id in ids) key(id) {
        val image: ImageBitmap? = rememberAssetImage(id)
        if (image != null) images[id] = image
    }
    return images
}

/**
 * Whether ink on this background wants to be dark: the mean luminance of its
 * stops. A picture is never asked: [backgroundLayers] ends in what it sits on.
 */
private fun SlideBackground?.isLight(): Boolean = when (this) {
    null, is SlideBackground.Image -> false
    is SlideBackground.Color -> color.toComposeColor().luminance() > 0.5f
    is SlideBackground.Gradient ->
        (start.toComposeColor().luminance() + end.toComposeColor().luminance()) / 2 > 0.5f
}

/**
 * [layers] painted bottom-up at [alpha]. More than one fading at once goes through
 * a layer of its own, so the stack fades as one picture rather than each part of
 * it letting the one under it through.
 */
private fun DrawScope.drawSlideBackground(
    layers: List<SlideBackground?>,
    images: Map<String, ImageBitmap>,
    alpha: Float = 1f,
) {
    if (alpha >= 1f || layers.size == 1) {
        for (layer in layers.asReversed()) drawSlideBackground(layer, images, alpha)
        return
    }

    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
    for (layer in layers.asReversed()) drawSlideBackground(layer, images)
    drawContext.canvas.restore()
}

private fun DrawScope.drawSlideBackground(
    background: SlideBackground?,
    images: Map<String, ImageBitmap>,
    alpha: Float = 1f,
) {
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

        is SlideBackground.Image -> {
            val image: ImageBitmap = images[background.assetId] ?: return
            drawImageFill(image, background.mode, alpha)
        }
    }
}

package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ImageFillMode
import io.github.xxfast.cupboard.document.imageFrames
import kotlin.math.roundToInt

/**
 * [image] laid into this scope's whole box by [mode], clipped to the box.
 *
 * Shared by a shape's picture fill and a slide's picture background, which are
 * the same five modes over different boxes. The picture's natural size is its
 * pixels as document units, so it is scaled by the scope's density: on a slide
 * that is one unit per dp at every zoom, which is what keeps a tile the same size
 * on the canvas, in a thumbnail and in an export.
 */
internal fun DrawScope.drawImageFill(image: ImageBitmap, mode: ImageFillMode, alpha: Float = 1f) {
    val frames: List<Frame> = mode.imageFrames(
        imageWidth = image.width * density,
        imageHeight = image.height * density,
        boxWidth = size.width,
        boxHeight = size.height,
    )

    clipRect {
        for (frame in frames) {
            // Both edges rounded rather than the origin and the size, so tiles
            // that meet on a fraction of a pixel still meet with no seam.
            val left: Int = frame.x.roundToInt()
            val top: Int = frame.y.roundToInt()
            val right: Int = (frame.x + frame.width).roundToInt()
            val bottom: Int = (frame.y + frame.height).roundToInt()
            if (right <= left || bottom <= top) continue

            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(left, top),
                dstSize = IntSize(right - left, bottom - top),
                alpha = alpha,
            )
        }
    }
}

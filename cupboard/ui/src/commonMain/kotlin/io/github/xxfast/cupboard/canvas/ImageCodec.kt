package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap

/**
 * [bitmap] as PNG bytes, alpha and all.
 *
 * Expected rather than common because there is no common encoder: everything but
 * android goes through Skia, which Compose already draws with, and android goes
 * through its own framework's. Reading pixels needs neither, which is why
 * [argbPixels] below is written once.
 */
expect fun encodePng(bitmap: ImageBitmap): ByteArray

/**
 * An image over [pixels], packed ARGB and row-major over [width] x [height]:
 * the other half of [argbPixels], and the way a pure pixel function's result gets
 * back onto a canvas.
 */
expect fun argbImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap

/**
 * This image's pixels, packed ARGB and row-major, the layout every pure pixel
 * function in the document module reads.
 */
fun ImageBitmap.argbPixels(): IntArray {
    val map = toPixelMap()
    return IntArray(width * height) { index -> map[index % width, index / width].toArgb() }
}

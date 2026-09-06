package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

actual fun encodePng(bitmap: ImageBitmap): ByteArray =
    Image.makeFromBitmap(bitmap.asSkiaBitmap())
        .encodeToData(EncodedImageFormat.PNG)
        ?.bytes
        ?: ByteArray(0)

actual fun argbImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    // BGRA_8888 is the ARGB int read back a byte at a time on a little-endian
    // machine, which is every target this runs on: blue lowest, alpha highest.
    val bytes = ByteArray(width * height * 4)
    for (index in 0 until width * height) {
        val pixel: Int = pixels[index]
        bytes[index * 4] = (pixel and 0xFF).toByte()
        bytes[index * 4 + 1] = ((pixel shr 8) and 0xFF).toByte()
        bytes[index * 4 + 2] = ((pixel shr 16) and 0xFF).toByte()
        bytes[index * 4 + 3] = ((pixel shr 24) and 0xFF).toByte()
    }

    val bitmap = Bitmap()
    val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    bitmap.allocPixels(info)
    bitmap.installPixels(info, bytes, width * 4)
    return bitmap.asComposeImageBitmap()
}

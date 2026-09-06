package io.github.xxfast.cupboard.export

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import io.github.xxfast.cupboard.canvas.AssetImageCache
import io.github.xxfast.cupboard.canvas.LocalAssetImages
import io.github.xxfast.cupboard.canvas.LocalAssetStore
import io.github.xxfast.cupboard.canvas.SlideView
import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.InMemoryAssetStore
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.layoutOf
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * The other half of every export: a slide, at a step, as pixels.
 *
 * It draws the real thing. `renderComposeScene` composes [SlideView] into an
 * offscreen Skia surface and hands back the frame, so what a PDF or a GIF carries
 * is the canvas the audience would have seen, not a second renderer's idea of it.
 * That is the whole reason the writers in `:cupboard` take a [Rasterizer] rather
 * than a document: the pixels have to come from here, and here cannot be reached
 * from there.
 *
 * On the skiko targets only, which is every one Compose draws with Skia. Android
 * is out (its own framework encodes images), and mingwX64 was never in.
 *
 * One caution: a frame is one composition and one draw. Anything a slide loads
 * asynchronously has not landed by then, so an image whose bytes are not already
 * decoded in [images] renders as its placeholder. A shell that wants pictures in
 * its export warms that cache first, which it can do because it is a parameter
 * rather than a private field.
 */
@OptIn(ExperimentalComposeUiApi::class)
class SlideRasterizer(
    private val document: Document,
    private val assets: AssetStore = InMemoryAssetStore(),
    /** Pixels across. The height follows from the deck's own aspect. */
    val width: Int = 1920,
    private val images: AssetImageCache = AssetImageCache(),
) : Rasterizer {
    /** [width] at the deck's aspect, which is what every frame comes out at. */
    val height: Int =
        maxOf(1, (width * (document.slideHeight / document.slideWidth)).toInt())

    /** Slide [slideIndex] at [step], as a PNG encoded by Skia. */
    fun render(slideIndex: Int, step: Int): ByteArray =
        image(slideIndex, step).encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)

    /** Slide [slideIndex] at [step], as packed ARGB pixels. */
    fun renderPixels(slideIndex: Int, step: Int): RasterFrame {
        val bitmap = Bitmap()
        // BGRA unpremultiplied: the byte order an ARGB int is read back in on
        // every little-endian machine, which is all of them here.
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        bitmap.allocPixels(info)
        image(slideIndex, step).readPixels(bitmap)

        val bytes: ByteArray = bitmap.readPixels() ?: ByteArray(width * height * 4)
        val argb = IntArray(width * height)
        for (index in argb.indices) {
            val blue: Int = bytes[index * 4].toInt() and 0xFF
            val green: Int = bytes[index * 4 + 1].toInt() and 0xFF
            val red: Int = bytes[index * 4 + 2].toInt() and 0xFF
            val alpha: Int = bytes[index * 4 + 3].toInt() and 0xFF
            argb[index] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
        return RasterFrame(width, height, argb)
    }

    override fun frame(slideIndex: Int, step: Int): RasterFrame = renderPixels(slideIndex, step)

    private fun image(slideIndex: Int, step: Int): Image {
        val slide: Slide = document.slides[slideIndex]
        return renderComposeScene(width, height) {
            CompositionLocalProvider(
                LocalAssetStore provides assets,
                LocalAssetImages provides images,
            ) {
                SlideView(
                    slide = slide,
                    layout = document.layoutOf(slide),
                    step = step,
                    background = document.background,
                    slideWidth = document.slideWidth,
                    slideHeight = document.slideHeight,
                )
            }
        }
    }
}

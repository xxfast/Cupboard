package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.stepCount

/**
 * One rendered slide: its size in pixels, and its pixels as packed ARGB
 * (0xAARRGGBB), row by row from the top.
 *
 * The same packing the document model uses for colours, so a writer reading a
 * pixel and a writer reading an element's fill do the same shifts. Equality is
 * the array's identity, as it is for any data class carrying one: nothing here
 * compares frames.
 */
data class RasterFrame(val width: Int, val height: Int, val argb: IntArray)

/**
 * Whatever can draw a slide at a step into pixels.
 *
 * The seam between the writers in this package and the canvas that feeds them:
 * everything below is pure Kotlin that could not link Compose UI even if it
 * wanted to (`:cupboard` carries mingwX64), and the one implementation that
 * matters, `SlideRasterizer` in `:cupboard:ui`, runs Skia. Exports therefore take
 * this rather than a document plus a canvas, and a test hands them a four-pixel
 * fake.
 */
interface Rasterizer {
    /** Slide [slideIndex] of the document, drawn at [step] of its build order. */
    fun frame(slideIndex: Int, step: Int): RasterFrame
}

/**
 * One frame an export walks: which slide of [Document.slides] it is, and which
 * step of that slide.
 *
 * Indices into the deck's own list rather than into the played order, because
 * that is what a [Rasterizer] draws from.
 */
internal data class ExportFrame(val slideIndex: Int, val step: Int)

/**
 * The slides an export covers, as indices into [Document.slides].
 *
 * Skipped slides are left out unless [includeSkipped], which is what skipping one
 * is for. A deck with every slide skipped exports whole rather than empty, the
 * same exception play mode makes: an empty file is never what was wanted.
 */
internal fun Document.exportedSlideIndices(includeSkipped: Boolean = false): List<Int> {
    if (includeSkipped) return slides.indices.toList()
    val kept: List<Int> = slides.indices.filterNot { slides[it].skipped }
    return kept.ifEmpty { slides.indices.toList() }
}

/**
 * Every frame an export writes, in order: one per slide, or one per step of every
 * slide when [everyBuild].
 *
 * The step a slide is drawn at when its builds are not being walked is its last,
 * which is the slide as the audience leaves it rather than as it opens: a handout
 * of half-built slides is a handout of missing content.
 */
internal fun Document.exportFrames(
    everyBuild: Boolean,
    includeSkipped: Boolean = false,
): List<ExportFrame> = exportedSlideIndices(includeSkipped).flatMap { index ->
    val slide: Slide = slides[index]
    if (!everyBuild) listOf(ExportFrame(index, slide.stepCount() - 1))
    else (0 until slide.stepCount()).map { ExportFrame(index, it) }
}

/**
 * The frame's pixels as packed RGB, three bytes each, with alpha composited over
 * [background] (itself packed RGB).
 *
 * Both formats that take these, PDF and JPEG-less PPTX media, are opaque: there
 * is no alpha channel to put the transparency in, so it has to be resolved
 * against something. Slides are dark, so black is the something.
 */
internal fun RasterFrame.rgbBytes(background: Int = 0x000000): ByteArray {
    val out = ByteArray(width * height * 3)
    val backR: Int = (background shr 16) and 0xFF
    val backG: Int = (background shr 8) and 0xFF
    val backB: Int = background and 0xFF

    for (index in 0 until width * height) {
        val pixel: Int = argb[index]
        val alpha: Int = (pixel ushr 24) and 0xFF
        val red: Int = (pixel shr 16) and 0xFF
        val green: Int = (pixel shr 8) and 0xFF
        val blue: Int = pixel and 0xFF
        out[index * 3] = ((red * alpha + backR * (255 - alpha)) / 255).toByte()
        out[index * 3 + 1] = ((green * alpha + backG * (255 - alpha)) / 255).toByte()
        out[index * 3 + 2] = ((blue * alpha + backB * (255 - alpha)) / 255).toByte()
    }
    return out
}

/**
 * The frame at [width] pixels across, keeping its aspect, by averaging the source
 * pixels that fall in each destination one.
 *
 * A box filter rather than a nearest-neighbour pick because what is being
 * downscaled is type: dropping pixels off a 1920-wide slide to make a 960-wide
 * GIF turns anti-aliased letters into gravel. A frame already at or below [width]
 * is returned as it is.
 */
internal fun RasterFrame.scaledTo(width: Int): RasterFrame {
    if (width >= this.width || width < 1) return this
    val height: Int = maxOf(1, (this.height.toLong() * width / this.width).toInt())
    val out = IntArray(width * height)

    for (y in 0 until height) {
        val sourceTop: Int = y * this.height / height
        val sourceBottom: Int = maxOf(sourceTop + 1, (y + 1) * this.height / height)
        for (x in 0 until width) {
            val sourceLeft: Int = x * this.width / width
            val sourceRight: Int = maxOf(sourceLeft + 1, (x + 1) * this.width / width)

            var alpha = 0L
            var red = 0L
            var green = 0L
            var blue = 0L
            var count = 0L
            for (sourceY in sourceTop until sourceBottom) {
                for (sourceX in sourceLeft until sourceRight) {
                    val pixel: Int = argb[sourceY * this.width + sourceX]
                    alpha += (pixel ushr 24) and 0xFF
                    red += (pixel shr 16) and 0xFF
                    green += (pixel shr 8) and 0xFF
                    blue += pixel and 0xFF
                    count++
                }
            }

            out[y * width + x] = (((alpha / count).toInt() and 0xFF) shl 24) or
                (((red / count).toInt() and 0xFF) shl 16) or
                (((green / count).toInt() and 0xFF) shl 8) or
                ((blue / count).toInt() and 0xFF)
        }
    }

    return RasterFrame(width, height, out)
}

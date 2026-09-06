package io.github.xxfast.cupboard.document

import kotlin.math.sqrt

/** The furthest two colours can be apart in RGB: the diagonal of the 255 cube. */
private const val MaxColorDistance: Float = 441.6729f

/**
 * The image with the region the seed sits in rubbed out: instant alpha, as one
 * pure function over pixels.
 *
 * A flood fill from ([seedX], [seedY]) across every 4-connected neighbour whose
 * colour is within [tolerance] of the seed's, clearing the alpha of everything it
 * reaches and leaving the colour behind. [tolerance] is 0..1 over the RGB cube's
 * diagonal, so 0 rubs out exactly the seed's own colour and 1 rubs out the whole
 * image.
 *
 * Connected rather than global on purpose: a subject the same colour as the
 * background survives as long as it is not touching it, which is the difference
 * between instant alpha and a chroma key.
 *
 * [pixels] are packed ARGB, one per pixel, row-major over [width] x [height], and
 * are not touched: the result is a new array. Iterative rather than recursive
 * because a large flat region would otherwise be measured in stack frames, and
 * the stack is bounded by the pixel count since nothing is pushed twice.
 *
 * A seed outside the image, or a size that does not match [pixels], comes back as
 * an unchanged copy: nothing to rub out is not an error.
 */
fun instantAlpha(
    pixels: IntArray,
    width: Int,
    height: Int,
    seedX: Int,
    seedY: Int,
    tolerance: Float,
): IntArray {
    val cleared: IntArray = pixels.copyOf()
    if (width <= 0 || height <= 0 || pixels.size < width * height) return cleared
    if (seedX !in 0 until width || seedY !in 0 until height) return cleared

    val seed: Int = pixels[seedY * width + seedX]
    val seedRed: Int = (seed shr 16) and 0xFF
    val seedGreen: Int = (seed shr 8) and 0xFF
    val seedBlue: Int = seed and 0xFF
    val limit: Float = tolerance.coerceIn(0f, 1f) * MaxColorDistance

    val visited = BooleanArray(width * height)
    val stack = IntArray(width * height)
    var top = 0

    fun push(index: Int) {
        if (visited[index]) return
        visited[index] = true
        stack[top++] = index
    }

    push(seedY * width + seedX)

    while (top > 0) {
        val index: Int = stack[--top]
        val pixel: Int = pixels[index]
        val red: Int = ((pixel shr 16) and 0xFF) - seedRed
        val green: Int = ((pixel shr 8) and 0xFF) - seedGreen
        val blue: Int = (pixel and 0xFF) - seedBlue
        val distance: Float = sqrt((red * red + green * green + blue * blue).toFloat())
        if (distance > limit) continue

        cleared[index] = pixel and 0x00FFFFFF

        val x: Int = index % width
        val y: Int = index / width
        if (x > 0) push(index - 1)
        if (x < width - 1) push(index + 1)
        if (y > 0) push(index - width)
        if (y < height - 1) push(index + width)
    }

    return cleared
}

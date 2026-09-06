package io.github.xxfast.cupboard.document

/**
 * Whether ([u], [v]) falls inside the visible window, both in the same
 * image-normalised units [ImageMask.frame] is written in.
 *
 * [ShapeKind.Ellipse] is the ellipse inscribed in that window; every other kind
 * answers for the window's box. The catalog's other outlines are drawn by
 * clipping rather than by testing points, so treating them as their box here
 * costs a few pixels at the corners of a hit test and buys one implementation.
 */
fun ImageMask.contains(u: Float, v: Float): Boolean {
    if (u < frame.x || u > frame.x + frame.width) return false
    if (v < frame.y || v > frame.y + frame.height) return false
    if (kind != ShapeKind.Ellipse) return true

    val radiusX: Float = frame.width / 2
    val radiusY: Float = frame.height / 2
    if (radiusX <= 0f || radiusY <= 0f) return false

    val dx: Float = (u - frame.centerX) / radiusX
    val dy: Float = (v - frame.centerY) / radiusY
    return dx * dx + dy * dy <= 1f
}

/**
 * The part of the image the element draws, in the natural image's own pixels:
 * the mask's window, or the whole image when there is no mask.
 *
 * A frame rather than four numbers because that is what the renderer hands the
 * canvas as its source rectangle, and what a mask gesture edits.
 */
fun ImageElement.sourceRect(): Frame {
    val width: Float = naturalWidth.toFloat()
    val height: Float = naturalHeight.toFloat()
    val window: ImageMask = mask ?: return Frame(0f, 0f, width, height)

    return Frame(
        x = window.frame.x * width,
        y = window.frame.y * height,
        width = window.frame.width * width,
        height = window.frame.height * height,
    )
}

/** Rec. 709, the weights every modern display's greys are mixed by. */
private const val LuminanceRed: Float = 0.2126f
private const val LuminanceGreen: Float = 0.7152f
private const val LuminanceBlue: Float = 0.0722f

/**
 * The adjustment as a 5x4 colour matrix, row-major: four rows of
 * (red, green, blue, alpha, offset), the shape Compose's `ColorMatrix` and Skia's
 * both take.
 *
 * All three corrections are affine in the channels, so they compose into one
 * matrix rather than into three passes: saturation mixes each channel towards the
 * luminance grey, contrast scales what comes out of that around mid grey, and
 * exposure lifts every channel by a constant. Offsets are on the 0..255 scale the
 * matrix is applied in, which is why both constants carry a `* 255`.
 *
 * At [ImageAdjust]'s defaults this is the identity, to the bit.
 */
fun ImageAdjust.colorMatrix(): FloatArray {
    // Contrast pivots on mid grey, so it contributes the offset that keeps 0.5
    // where it was; exposure is the whole of the rest of it.
    val offset: Float = (0.5f - 0.5f * contrast) * 255f + exposure * 255f

    // Off-diagonal: how much of another channel bleeds in as saturation drops.
    val red: Float = contrast * LuminanceRed * (1f - saturation)
    val green: Float = contrast * LuminanceGreen * (1f - saturation)
    val blue: Float = contrast * LuminanceBlue * (1f - saturation)

    // Diagonal: that same bleed plus the share saturation keeps for the channel.
    val own: Float = contrast * saturation

    return floatArrayOf(
        red + own, green, blue, 0f, offset,
        red, green + own, blue, 0f, offset,
        red, green, blue + own, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )
}

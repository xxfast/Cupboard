package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * How a picture is laid into a box it was not cut for: Keynote's five, the same
 * for a shape's fill and for a slide's background.
 *
 * [ScaleToFill] covers the box, centred, and crops whatever overhangs it.
 * [ScaleToFit] shows all of the picture, centred, and leaves the rest of the box
 * to whatever is under it. [Stretch] pulls the picture to the box's own aspect.
 * [Tile] repeats it at its natural size from the top-left, and [Original] draws it
 * once at its natural size, centred and cropped.
 *
 * A natural size is the picture's pixels counted as document units: one pixel is
 * one unit of a slide, whatever the zoom.
 */
@Serializable
enum class ImageFillMode { ScaleToFill, ScaleToFit, Stretch, Tile, Original }

/**
 * A picture painted into a shape: an id the asset store resolves to bytes, and how
 * those bytes are laid into the shape's box.
 *
 * No natural size is recorded, unlike [ImageElement]: nothing about a fill is
 * arithmetic on the document, so the decoded bitmap's own size is all a renderer
 * needs, and it has that the moment it has anything to draw.
 */
@Serializable
data class ImageFill(
    val assetId: String,
    val mode: ImageFillMode = ImageFillMode.ScaleToFill,
)

/**
 * Where an [imageWidth] x [imageHeight] picture lands in a [boxWidth] x
 * [boxHeight] box under this mode: the whole picture drawn into each frame, in
 * the box's own coordinates, top-left first.
 *
 * One frame for every mode but [ImageFillMode.Tile], which returns as many as it
 * takes to cover the box. A frame can overhang the box (a fill's crop, an original
 * bigger than its box, the last tile in a row), so whoever draws them clips to the
 * box. Units are the caller's, as long as both sizes are in the same ones.
 *
 * A picture or a box with no area has nowhere to go, and comes back empty.
 */
fun ImageFillMode.imageFrames(
    imageWidth: Float,
    imageHeight: Float,
    boxWidth: Float,
    boxHeight: Float,
): List<Frame> {
    if (imageWidth <= 0f || imageHeight <= 0f || boxWidth <= 0f || boxHeight <= 0f) {
        return emptyList()
    }

    fun centred(width: Float, height: Float): Frame =
        Frame((boxWidth - width) / 2, (boxHeight - height) / 2, width, height)

    return when (this) {
        ImageFillMode.ScaleToFill -> {
            val scale: Float = max(boxWidth / imageWidth, boxHeight / imageHeight)
            listOf(centred(imageWidth * scale, imageHeight * scale))
        }

        ImageFillMode.ScaleToFit -> {
            val scale: Float = min(boxWidth / imageWidth, boxHeight / imageHeight)
            listOf(centred(imageWidth * scale, imageHeight * scale))
        }

        ImageFillMode.Stretch -> listOf(Frame(0f, 0f, boxWidth, boxHeight))
        ImageFillMode.Original -> listOf(centred(imageWidth, imageHeight))
        ImageFillMode.Tile -> {
            val columns: Int = ceil(boxWidth / imageWidth).toInt()
            val rows: Int = ceil(boxHeight / imageHeight).toInt()
            List(columns * rows) { index ->
                Frame(
                    x = (index % columns) * imageWidth,
                    y = (index / columns) * imageHeight,
                    width = imageWidth,
                    height = imageHeight,
                )
            }
        }
    }
}

/**
 * What a shape paints its inside with, read off the precedence the renderer
 * follows: an image over a gradient over the solid [ShapeElement.fill].
 *
 * Derived rather than stored, so it can never disagree with the fields under it.
 * A [ShapeKind.Line] has no inside, so its [ShapeElement.imageFill] doesn't count.
 */
enum class ShapeFillKind { Color, Gradient, Image }

val ShapeElement.fillKind: ShapeFillKind
    get() = when {
        imageFill != null && kind != ShapeKind.Line -> ShapeFillKind.Image
        gradient != null -> ShapeFillKind.Gradient
        else -> ShapeFillKind.Color
    }

/**
 * This shape filled with [assetId]'s picture. The mode it was already in is kept,
 * so replacing the picture doesn't re-lay it; a shape with no picture yet starts
 * at [ImageFillMode.ScaleToFill].
 *
 * The solid and the gradient stay stored under it, so dropping the picture again
 * ([withoutImageFill]) goes back to whichever of those was showing.
 */
fun ShapeElement.withImageFill(assetId: String): ShapeElement =
    copy(imageFill = ImageFill(assetId, imageFill?.mode ?: ImageFillMode.ScaleToFill))

/** This shape's picture laid in by [mode] instead. A shape with no picture is left as it is. */
fun ShapeElement.withFillMode(mode: ImageFillMode): ShapeElement {
    val fill: ImageFill = imageFill ?: return this
    return copy(imageFill = fill.copy(mode = mode))
}

/** This shape without its picture: back to its gradient, or its solid when it has none. */
fun ShapeElement.withoutImageFill(): ShapeElement = copy(imageFill = null)

/**
 * This shape switched to a solid fill: the gradient and the picture both go.
 * What the Fill control's Color segment does. Picking a colour while a picture
 * is showing is a plain `copy(fill = ...)` instead, since the solid is what shows
 * around a [ImageFillMode.ScaleToFit] picture.
 */
fun ShapeElement.withSolidFill(color: Long = fill): ShapeElement =
    copy(fill = color, gradient = null, imageFill = null)

/** This shape switched to [gradient]: the picture goes, the solid stays stored under it. */
fun ShapeElement.withGradient(gradient: ShapeGradient): ShapeElement =
    copy(gradient = gradient, imageFill = null)

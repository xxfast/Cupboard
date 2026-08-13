package io.github.xxfast.cupboard.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Serializable
data class Frame(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val centerX: Float get() = x + width / 2
    val centerY: Float get() = y + height / 2

    fun translate(dx: Float, dy: Float): Frame = copy(x = x + dx, y = y + dy)

    /**
     * Whether the two frames share any area. Touching edges don't, so a marquee
     * dragged along an element's edge doesn't sweep it up. Both frames are
     * expected to have positive width and height.
     */
    fun overlaps(other: Frame): Boolean =
        x < other.x + other.width && other.x < x + width &&
            y < other.y + other.height && other.y < y + height
}

/** The smallest frame containing every one of [frames]. Nothing in, nothing out. */
fun boundingFrame(frames: List<Frame>): Frame {
    if (frames.isEmpty()) return Frame(0f, 0f, 0f, 0f)
    val left: Float = frames.minOf { it.x }
    val top: Float = frames.minOf { it.y }
    val right: Float = frames.maxOf { it.x + it.width }
    val bottom: Float = frames.maxOf { it.y + it.height }
    return Frame(left, top, right - left, bottom - top)
}

/**
 * The axis-aligned box the element actually occupies on the slide: its frame,
 * widened by its rotation about the frame center. Grouping and marquee sweeps
 * measure against this, so a rotated element is boxed where it is drawn rather
 * than where it would sit unrotated. Flips leave the footprint alone.
 */
fun Element.drawnBounds(): Frame {
    if (rotation == 0f) return frame
    val radians: Float = rotation * PI.toFloat() / 180f
    val cos: Float = abs(cos(radians))
    val sin: Float = abs(sin(radians))
    val width: Float = frame.width * cos + frame.height * sin
    val height: Float = frame.width * sin + frame.height * cos
    return Frame(frame.centerX - width / 2, frame.centerY - height / 2, width, height)
}

/**
 * The vector ([dx], [dy]) rotated by [degrees], clockwise-positive like the canvas.
 *
 * Here rather than with the editor's geometry because the document needs it too:
 * baking a group's rotation into its children happens without a canvas in sight.
 */
internal fun rotateVector(dx: Float, dy: Float, degrees: Float): Pair<Float, Float> {
    val radians: Float = degrees * PI.toFloat() / 180f
    val cos: Float = cos(radians)
    val sin: Float = sin(radians)
    return (dx * cos - dy * sin) to (dx * sin + dy * cos)
}

@Serializable
sealed interface Element {
    val id: String
    val frame: Frame
    val opacity: Float
    /** Degrees clockwise, applied around the frame's center. */
    val rotation: Float
    val flippedHorizontally: Boolean
    val flippedVertically: Boolean
    /** A locked element still selects and still renders, but nothing edits it until it unlocks. */
    val locked: Boolean

    /**
     * The one mutator every element implements: copies with whichever of the
     * shared properties were passed and keeps the element's own type and fields.
     * Defaulted against `this`, so a caller names only what it is changing.
     */
    fun update(
        frame: Frame = this.frame,
        opacity: Float = this.opacity,
        rotation: Float = this.rotation,
        flippedHorizontally: Boolean = this.flippedHorizontally,
        flippedVertically: Boolean = this.flippedVertically,
        locked: Boolean = this.locked,
    ): Element
}

enum class TextAlign { Start, Center, End }

@Serializable
@SerialName("text")
data class TextElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    val text: String = "",
    val fontSize: Float = 15f,
    val fontWeight: Int = 400,
    val lineHeight: Float = 1.3f,
    val letterSpacing: Float = 0f,
    val color: Long = 0xFFFFFFFF,
    val align: TextAlign = TextAlign.Start,
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
    )
}

enum class ShapeKind { Rectangle, Ellipse }

@Serializable
@SerialName("shape")
data class ShapeElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    val kind: ShapeKind = ShapeKind.Rectangle,
    val cornerRadius: Float = 10f,
    val fill: Long = 0x387F52FF,
    val strokeColor: Long = 0xB3A98FFF,
    val strokeWidth: Float = 1.5f,
    val label: String = "",
    val labelSize: Float = 15f,
    val labelColor: Long = 0xFFD9CFFF,
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
    )
}

/**
 * Image content ships with the file format work; until then an image element
 * renders as the design's drop placeholder.
 */
@Serializable
@SerialName("image")
data class ImageElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    val placeholder: String = "Drop frame capture here",
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
    )
}

/**
 * Elements moved, resized and transformed as one.
 *
 * [children] are stored in absolute slide coordinates, not relative to the
 * group: every renderer, hit test and inspector keeps reading a frame the same
 * way whether the element sits in a group or not. The price is [update], which
 * has to rewrite the children whenever the group's own frame changes.
 *
 * Rotation, flips, opacity and lock stay layered on the group instead, and are
 * baked into the children only when the group comes apart. See
 * `Slide.ungroupElement`.
 */
@Serializable
@SerialName("group")
data class GroupElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    val children: List<Element> = emptyList(),
) : Element {
    /**
     * A new [frame] maps every child through the old-to-new affine, so children
     * follow a move or a resize as it happens rather than at draw time. A nested
     * group recurses through its own [update] and stays consistent with itself.
     *
     * Text and font sizes are not part of that mapping: a resized group scales
     * its children's boxes, not their type. Known simplification.
     */
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
    ): Element {
        // `frame` is the new one, `this.frame` the one the children were laid out in.
        val moved: List<Element> = if (frame == this.frame) children else {
            val scaleX: Float = if (this.frame.width == 0f) 1f else frame.width / this.frame.width
            val scaleY: Float = if (this.frame.height == 0f) 1f else frame.height / this.frame.height
            children.map { child ->
                child.update(
                    frame = Frame(
                        x = frame.x + (child.frame.x - this.frame.x) * scaleX,
                        y = frame.y + (child.frame.y - this.frame.y) * scaleY,
                        width = child.frame.width * scaleX,
                        height = child.frame.height * scaleY,
                    ),
                )
            }
        }

        return copy(
            frame = frame,
            opacity = opacity,
            rotation = rotation,
            flippedHorizontally = flippedHorizontally,
            flippedVertically = flippedVertically,
            locked = locked,
            children = moved,
        )
    }
}

@Serializable
@SerialName("code")
data class CodeElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    val code: String = "",
    val language: String = "kotlin",
    val fontSize: Float = 14f,
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
    )
}

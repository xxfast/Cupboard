package io.github.xxfast.cupboard.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

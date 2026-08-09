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

    fun withFrame(frame: Frame): Element
}

enum class TextAlign { Start, Center, End }

@Serializable
@SerialName("text")
data class TextElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    val text: String = "",
    val fontSize: Float = 15f,
    val fontWeight: Int = 400,
    val lineHeight: Float = 1.3f,
    val letterSpacing: Float = 0f,
    val color: Long = 0xFFFFFFFF,
    val align: TextAlign = TextAlign.Start,
) : Element {
    override fun withFrame(frame: Frame): Element = copy(frame = frame)
}

enum class ShapeKind { Rectangle, Ellipse }

@Serializable
@SerialName("shape")
data class ShapeElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    val kind: ShapeKind = ShapeKind.Rectangle,
    val cornerRadius: Float = 10f,
    val fill: Long = 0x387F52FF,
    val strokeColor: Long = 0xB3A98FFF,
    val strokeWidth: Float = 1.5f,
    val label: String = "",
    val labelSize: Float = 15f,
    val labelColor: Long = 0xFFD9CFFF,
) : Element {
    override fun withFrame(frame: Frame): Element = copy(frame = frame)
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
    val placeholder: String = "Drop frame capture here",
) : Element {
    override fun withFrame(frame: Frame): Element = copy(frame = frame)
}

@Serializable
@SerialName("code")
data class CodeElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    val code: String = "",
    val language: String = "kotlin",
    val fontSize: Float = 14f,
) : Element {
    override fun withFrame(frame: Frame): Element = copy(frame = frame)
}

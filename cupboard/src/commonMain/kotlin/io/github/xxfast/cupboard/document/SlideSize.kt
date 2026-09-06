package io.github.xxfast.cupboard.document

import kotlin.math.min

/**
 * The slide shapes the size picker offers by name, in document units.
 *
 * Both are 1080 tall on purpose: the height is what type is set against, so
 * moving a deck between them narrows or widens the slide rather than resizing
 * everything written on it.
 *
 * A deck is not required to be on one of these. [Document.slideSizePreset]
 * answers null for a custom size, which is what the picker shows as "Custom".
 */
enum class SlideSizePreset(val width: Float, val height: Float, val title: String) {
    Widescreen(1920f, 1080f, "Widescreen (16:9)"),
    Standard(1440f, 1080f, "Standard (4:3)"),
}

/**
 * How small and how large a slide side may be: a thumbnail's worth at the bottom
 * and 8K at the top. Bounds rather than a free number because every renderer
 * lays the slide out at this size in dp, and a slide of zero or of a million
 * units is a canvas that draws nothing either way.
 */
private const val MinSlideSide: Float = 320f
private const val MaxSlideSide: Float = 7680f

/** The preset this deck's slide size is exactly, null for a custom one. */
fun Document.slideSizePreset(): SlideSizePreset? =
    SlideSizePreset.entries.firstOrNull { it.width == slideWidth && it.height == slideHeight }

/**
 * The deck on a [width] by [height] slide, each side clamped to what a slide may
 * be. A size this deck is already on returns this same instance, so a caller can
 * skip the history entry the way [reorderElements] lets it.
 *
 * [scaleContent] carries the deck's content across with the slide: every frame
 * maps by its own axis (x and width by the width ratio, y and height by the
 * height ratio), so a layout that filled the slide still fills it, and type
 * scales by the smaller of the two ratios, so it stays inside the boxes that
 * shrank. Guides move along the axis they run across. Groups go through
 * [Element.update], which is what maps their children, so a group carries its
 * contents the way a resize handle does.
 *
 * Left false, only the slide changes: the deck keeps every frame it had and the
 * new edges fall where they fall, which is what someone widening a deck to make
 * room wants.
 *
 * Layouts scale with the slides, because they are slides in this deck too: a
 * placeholder that missed the resize would put every slide built on it back
 * where the old slide's margins were.
 */
fun Document.resized(width: Float, height: Float, scaleContent: Boolean): Document {
    val newWidth: Float = width.coerceIn(MinSlideSide, MaxSlideSide)
    val newHeight: Float = height.coerceIn(MinSlideSide, MaxSlideSide)
    if (newWidth == slideWidth && newHeight == slideHeight) return this
    if (!scaleContent) return copy(slideWidth = newWidth, slideHeight = newHeight)

    // A deck that somehow carries a zero side has nothing to scale from, so the
    // content stays where it is and only the slide changes.
    val scaleX: Float = if (slideWidth == 0f) 1f else newWidth / slideWidth
    val scaleY: Float = if (slideHeight == 0f) 1f else newHeight / slideHeight
    val scaleType: Float = min(scaleX, scaleY)

    return copy(
        slideWidth = newWidth,
        slideHeight = newHeight,
        slides = slides.map { it.scaled(scaleX, scaleY, scaleType) },
        layouts = layouts.map { it.scaled(scaleX, scaleY, scaleType) },
        guides = guides.map { guide ->
            val scale: Float = if (guide.axis == GuideAxis.Vertical) scaleX else scaleY
            guide.copy(position = guide.position * scale)
        },
    )
}

private fun Slide.scaled(scaleX: Float, scaleY: Float, scaleType: Float): Slide =
    copy(elements = elements.map { it.scaled(scaleX, scaleY, scaleType) })

/**
 * One element mapped onto the new slide: its frame per axis, and whatever it
 * sets type in by [scaleType], the smaller of the two ratios.
 *
 * A group's children are not walked here: [GroupElement.update] maps them off
 * the group's own frame, and it leaves their type alone for the same reason a
 * dragged resize handle does. Known simplification, the group's own.
 */
private fun Element.scaled(scaleX: Float, scaleY: Float, scaleType: Float): Element {
    val scaledFrame = Frame(
        x = frame.x * scaleX,
        y = frame.y * scaleY,
        width = frame.width * scaleX,
        height = frame.height * scaleY,
    )

    return when (this) {
        is TextElement -> copy(frame = scaledFrame, fontSize = fontSize * scaleType)
        is ShapeElement -> copy(
            frame = scaledFrame,
            labelSize = labelSize * scaleType,
            strokeWidth = strokeWidth * scaleType,
            cornerRadius = cornerRadius * scaleType,
        )

        is CodeElement -> copy(frame = scaledFrame, fontSize = fontSize * scaleType)
        is TerminalElement -> copy(frame = scaledFrame, fontSize = fontSize * scaleType)
        is DiagramElement -> copy(frame = scaledFrame, fontSize = fontSize * scaleType)
        is EquationElement -> copy(frame = scaledFrame, fontSize = fontSize * scaleType)
        is ImageElement -> copy(frame = scaledFrame)
        is GalleryElement -> copy(frame = scaledFrame)
        // Nothing about a movie or a sound is set in document units but its box:
        // the pixels are the asset's own, and a trim is measured in milliseconds.
        is VideoElement -> copy(frame = scaledFrame)
        is AudioElement -> copy(frame = scaledFrame)
        is GroupElement -> update(frame = scaledFrame)
    }
}

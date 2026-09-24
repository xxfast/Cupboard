package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * How one slide gives way to the next.
 *
 * [None] cuts, and the four in the middle are the ordinary Keynote set. [MagicMove]
 * is the one that reads the two slides rather than only the change between them:
 * elements that appear on both travel from where they sat to where they now sit,
 * and everything else crossfades. See [magicMovePairs] for what "appear on both"
 * means.
 */
@Serializable
enum class TransitionKind { None, Dissolve, Push, MoveIn, Wipe, MagicMove }

/**
 * The way the motion runs, for the kinds that have one: [Left] is content
 * travelling leftwards, so the arriving slide comes in from the right edge.
 *
 * Mirrored under a right-to-left layout, and mirrored again when the deck is
 * walked backwards, so stepping back always undoes what stepping forward did.
 */
@Serializable
enum class TransitionDirection { Left, Right, Up, Down }

/** Whether the slide waits for the presenter, or leaves on its own. */
@Serializable
enum class TransitionTrigger { OnClick, Automatic }

/**
 * The transition a slide is dressed in.
 *
 * Keynote's convention: this is what plays when the slide is *left* for the next
 * one, and it plays in reverse when the presenter comes back to it. So the
 * animation between two slides is the earlier slide's business, and a deck reads
 * top to bottom.
 *
 * [durationMs] is the whole move. [delayMs] is how long an [TransitionTrigger.Automatic]
 * slide sits on its last step before it goes, and means nothing to a slide that
 * waits for a click.
 */
@Serializable
data class SlideTransition(
    val kind: TransitionKind = TransitionKind.Dissolve,
    val direction: TransitionDirection = TransitionDirection.Left,
    val durationMs: Int = 600,
    val trigger: TransitionTrigger = TransitionTrigger.OnClick,
    val delayMs: Int = 2000,
)

/**
 * What [TransitionKind.MagicMove] matches an element by: its kind, and the
 * content that makes it the object it is.
 *
 * Content rather than id, deliberately. The slide the presenter duplicated and
 * edited has different ids on both sides of the cut, and the two slides someone
 * built separately have different ids too; what the audience recognises across
 * the cut is the words on the box, so that is what is matched. Nothing about the
 * frame, the colour or the type is in the key: moving and restyling the element
 * is the whole point of the transition.
 */
fun Element.matchKey(): String = when (this) {
    is TextElement -> "text:$text"
    is ShapeElement -> "shape:$kind:$label"
    is CodeElement -> "code:$code"
    is TerminalElement -> "terminal:$text"
    is DiagramElement -> "diagram:$source"
    is EquationElement -> "equation:$latex"
    // The bytes are the content; the placeholder is the content of an image that
    // has none yet, so two empty frames still travel across the cut together.
    is ImageElement -> "image:${assetId ?: placeholder}"
    // The picture the carousel opens on is what the audience recognises across
    // the cut: which one it is showing at the moment of the cut is not part of
    // what makes it the same object.
    is GalleryElement -> "gallery:${images.firstOrNull()?.assetId.orEmpty()}"
    // What the movie is, whichever way it is held: the bytes when the deck owns
    // them, the page when it doesn't. Trim, volume and loop are how it plays
    // rather than what it is, so a retrimmed movie still travels across the cut.
    is VideoElement -> "video:${assetId ?: webUrl.orEmpty()}"
    is AudioElement -> "audio:${assetId.orEmpty()}"
    is GroupElement -> "group:${children.joinToString("|") { it.matchKey() }}"
}

/**
 * The elements the two slides have in common, as (leaving, arriving) pairs in the
 * arriving slide's order.
 *
 * Matched by [matchKey], first come first served, and each element spoken for at
 * most once: two boxes reading the same thing pair off in the order they are
 * stored rather than fighting over one partner. Top-level elements only, because
 * a group travels as the one object it draws as.
 */
fun magicMovePairs(from: Slide, to: Slide): List<Pair<Element, Element>> {
    val available: MutableList<Element> = from.elements.toMutableList()

    return to.elements.mapNotNull { arriving ->
        val key: String = arriving.matchKey()
        val index: Int = available.indexOfFirst { it.matchKey() == key }
        if (index == -1) null else available.removeAt(index) to arriving
    }
}

/**
 * This element [fraction] of the way through travelling from [from], for a Magic
 * Move to draw.
 *
 * The geometry rides the element rather than a scale on its layer, because what
 * a slide holds has a size of its own: type set at 48pt in a box that used to be
 * wider is 48pt in a narrower box, not 48pt stretched to fit, so the box is lerped
 * and the content laid out again inside it, the way Keynote reflows. When the two
 * sides are the same kind of element, its look travels too: type size and weight,
 * colours, strokes, corners, shadows, all blended, so a title turning into a
 * subtitle shrinks and recolours as it goes rather than switching on landing.
 * What has no in-between (words, fonts, alignment, an image) is the arriving
 * element's throughout, so at 1 this is `this`.
 */
fun Element.travellingFrom(from: Element, fraction: Float): Element {
    val t: Float = fraction.coerceIn(0f, 1f)
    if (t >= 1f) return this

    val frame = Frame(
        x = blend(from.frame.x, frame.x, t),
        y = blend(from.frame.y, frame.y, t),
        width = blend(from.frame.width, frame.width, t),
        height = blend(from.frame.height, frame.height, t),
    )

    return when (this) {
        is TextElement -> if (from !is TextElement) copy(frame = frame) else copy(
            frame = frame,
            fontSize = blend(from.fontSize, fontSize, t),
            fontWeight = blend(from.fontWeight.toFloat(), fontWeight.toFloat(), t).roundToInt(),
            lineHeight = blend(from.lineHeight, lineHeight, t),
            letterSpacing = blend(from.letterSpacing, letterSpacing, t),
            color = blendColor(from.color, color, t),
        )
        is ShapeElement -> if (from !is ShapeElement) copy(frame = frame) else copy(
            frame = frame,
            cornerRadius = blend(from.cornerRadius, cornerRadius, t),
            fill = blendColor(from.fill, fill, t),
            // A gradient blends with a gradient; with a flat fill on either side
            // there is nothing to blend it against, so it is the target's.
            gradient = if (from.gradient == null || gradient == null) gradient else ShapeGradient(
                start = blendColor(from.gradient.start, gradient.start, t),
                end = blendColor(from.gradient.end, gradient.end, t),
                angle = blend(from.gradient.angle, gradient.angle, t),
            ),
            strokeColor = blendColor(from.strokeColor, strokeColor, t),
            strokeWidth = blend(from.strokeWidth, strokeWidth, t),
            shadow = if (from.shadow == null || shadow == null) shadow else ShapeShadow(
                color = blendColor(from.shadow.color, shadow.color, t),
                blur = blend(from.shadow.blur, shadow.blur, t),
                dx = blend(from.shadow.dx, shadow.dx, t),
                dy = blend(from.shadow.dy, shadow.dy, t),
            ),
            labelSize = blend(from.labelSize, labelSize, t),
            labelColor = blendColor(from.labelColor, labelColor, t),
        )
        is CodeElement -> copy(
            frame = frame,
            fontSize = if (from is CodeElement) blend(from.fontSize, fontSize, t) else fontSize,
        )
        is TerminalElement -> copy(
            frame = frame,
            fontSize = if (from is TerminalElement) blend(from.fontSize, fontSize, t) else fontSize,
        )
        is DiagramElement -> if (from !is DiagramElement) copy(frame = frame) else copy(
            frame = frame,
            fontSize = blend(from.fontSize, fontSize, t),
            nodeFill = blendColor(from.nodeFill, nodeFill, t),
            nodeStroke = blendColor(from.nodeStroke, nodeStroke, t),
            nodeText = blendColor(from.nodeText, nodeText, t),
            edgeColor = blendColor(from.edgeColor, edgeColor, t),
        )
        is EquationElement -> if (from !is EquationElement) copy(frame = frame) else copy(
            frame = frame,
            fontSize = blend(from.fontSize, fontSize, t),
            color = blendColor(from.color, color, t),
        )
        is ImageElement -> if (from !is ImageElement) copy(frame = frame) else copy(
            frame = frame,
            adjust = ImageAdjust(
                exposure = blend(from.adjust.exposure, adjust.exposure, t),
                saturation = blend(from.adjust.saturation, adjust.saturation, t),
                contrast = blend(from.adjust.contrast, adjust.contrast, t),
            ),
        )
        // A group's children sit in absolute coordinates, so the group's own box
        // is all that moves here: its renderer scales them into it.
        is GalleryElement, is VideoElement, is AudioElement, is GroupElement ->
            update(frame, opacity, rotation, flippedHorizontally, flippedVertically, locked)
    }
}

private fun blend(from: Float, to: Float, t: Float): Float = from + (to - from) * t

/** A packed ARGB colour [t] of the way from [from] to [to], channel by channel. */
private fun blendColor(from: Long, to: Long, t: Float): Long {
    var out = 0L
    for (shift in listOf(24, 16, 8, 0)) {
        val a: Float = ((from shr shift) and 0xFF).toFloat()
        val b: Float = ((to shr shift) and 0xFF).toFloat()
        out = out or (blend(a, b, t).roundToInt().toLong().coerceIn(0L, 255L) shl shift)
    }
    return out
}

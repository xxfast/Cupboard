package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable

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
 * and the content laid out again inside it, the way Keynote reflows. The one
 * thing that does grow is the type itself, when the two sides set it differently
 * and are the same kind of element: a title travelling into a subtitle shrinks
 * as it goes. Everything else is the arriving element's, so at 1 this is `this`.
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
        is TextElement -> copy(
            frame = frame,
            fontSize = if (from is TextElement) blend(from.fontSize, fontSize, t) else fontSize,
        )
        is ShapeElement -> copy(
            frame = frame,
            labelSize = if (from is ShapeElement) blend(from.labelSize, labelSize, t) else labelSize,
            cornerRadius = if (from is ShapeElement) blend(from.cornerRadius, cornerRadius, t) else cornerRadius,
        )
        is CodeElement -> copy(
            frame = frame,
            fontSize = if (from is CodeElement) blend(from.fontSize, fontSize, t) else fontSize,
        )
        is TerminalElement -> copy(
            frame = frame,
            fontSize = if (from is TerminalElement) blend(from.fontSize, fontSize, t) else fontSize,
        )
        is DiagramElement -> copy(
            frame = frame,
            fontSize = if (from is DiagramElement) blend(from.fontSize, fontSize, t) else fontSize,
        )
        is EquationElement -> copy(
            frame = frame,
            fontSize = if (from is EquationElement) blend(from.fontSize, fontSize, t) else fontSize,
        )
        // A group's children sit in absolute coordinates, so the group's own box
        // is all that moves here: its renderer scales them into it.
        is ImageElement, is GalleryElement, is VideoElement, is AudioElement, is GroupElement ->
            update(frame, opacity, rotation, flippedHorizontally, flippedVertically, locked)
    }
}

private fun blend(from: Float, to: Float, t: Float): Float = from + (to - from) * t

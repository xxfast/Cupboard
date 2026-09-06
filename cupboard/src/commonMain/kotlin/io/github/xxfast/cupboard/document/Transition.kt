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

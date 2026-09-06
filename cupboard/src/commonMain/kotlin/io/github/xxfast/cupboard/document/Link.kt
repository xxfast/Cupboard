package io.github.xxfast.cupboard.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where clicking an element takes the show.
 *
 * The six Keynote offers, and no more: a link is a jump, not a script. [Slide]
 * names its destination by id rather than by number, so the link survives every
 * reorder, skip and insertion the deck goes through. [Next] and [Previous] are
 * the ordinary walk, so a button can do what the spacebar does in a deck where
 * the spacebar does nothing.
 *
 * [Url] and [ExitShow] leave the deck rather than move inside it, which is why
 * neither resolves to a position: the player hands the first to its host and
 * closes itself for the second.
 */
@Serializable
sealed interface LinkTarget {
    /** Step 0 of the slide with this id. A slide that isn't played goes nowhere. */
    @Serializable
    @SerialName("slide")
    data class Slide(val slideId: String) : LinkTarget

    @Serializable
    @SerialName("next")
    data object Next : LinkTarget

    @Serializable
    @SerialName("previous")
    data object Previous : LinkTarget

    @Serializable
    @SerialName("first")
    data object First : LinkTarget

    @Serializable
    @SerialName("last")
    data object Last : LinkTarget

    /** Opened by the host, which is the only half of this that knows what a browser is. */
    @Serializable
    @SerialName("url")
    data class Url(val url: String) : LinkTarget

    @Serializable
    @SerialName("exit")
    data object ExitShow : LinkTarget
}

/**
 * Where this element points, null for one that points nowhere.
 *
 * [TextElement] answers off either field: [TextElement.linkTarget] when it has
 * one, and its older [TextElement.link] string as a [LinkTarget.Url] when it
 * doesn't. Every deck written before targets existed carries only the string, and
 * a whole-box URL is exactly what it meant.
 *
 * The kinds that hold no link at all, groups included, answer null: a group is
 * moved and drawn as one object, but it is not one thing to click.
 */
fun Element.resolvedLink(): LinkTarget? = when (this) {
    is TextElement -> linkTarget ?: link?.takeIf { it.isNotBlank() }?.let { LinkTarget.Url(it) }
    is ShapeElement -> link
    is ImageElement -> link
    else -> null
}

package io.github.xxfast.cupboard.play

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.stepCount

/**
 * The slides play walks, in presentation order.
 *
 * Skipped slides are left out, which is the whole point of skipping one. A deck
 * with every slide skipped is the exception: there is nothing to play with no
 * slides at all, so the skips are ignored rather than obeyed into an empty
 * window.
 *
 * Lives here rather than beside the CuP compiler because the presenter display
 * counts the show the same way the show does, and it must not link against CuP
 * to do it.
 */
fun Document.playOrder(): List<Slide> = slides.filterNot { it.skipped }.ifEmpty { slides }

/**
 * Where the show is: an index into [playOrder] and the step within that slide,
 * 0 being the slide as it opens. The same two ints the player mirrors out.
 */
data class PlayPosition(val slideIndex: Int, val step: Int)

/**
 * The position one click forward of [at], or null at the very end of the deck.
 *
 * The next step of the same slide while it has one, then step 0 of the next
 * slide: the walk the player performs, worked out without it, so the presenter
 * display can show what is coming.
 */
fun Document.nextPosition(at: PlayPosition): PlayPosition? {
    val order: List<Slide> = playOrder()
    val slide: Slide = order.getOrNull(at.slideIndex) ?: return null

    if (at.step < slide.stepCount() - 1) return PlayPosition(at.slideIndex, at.step + 1)
    if (at.slideIndex >= order.lastIndex) return null

    return PlayPosition(at.slideIndex + 1, 0)
}

/**
 * The position one click back of [at], or null at the very start of the deck.
 * The mirror of [nextPosition]: the previous step of the same slide, else the
 * *last* step of the slide before it, which is where walking back lands.
 */
fun Document.previousPosition(at: PlayPosition): PlayPosition? {
    val order: List<Slide> = playOrder()
    if (order.getOrNull(at.slideIndex) == null) return null

    if (at.step > 0) return PlayPosition(at.slideIndex, at.step - 1)
    if (at.slideIndex <= 0) return null

    val previous: Slide = order[at.slideIndex - 1]

    return PlayPosition(at.slideIndex - 1, previous.stepCount() - 1)
}

package io.github.xxfast.cupboard.play

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.LinkTarget
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

/**
 * Where following [target] from [at] lands the show, null for a link that moves
 * it nowhere.
 *
 * Nowhere covers three things, and the player tells them apart by what it asked
 * for rather than by this: a [LinkTarget.Url] and a [LinkTarget.ExitShow] leave
 * the deck instead of moving inside it, a [LinkTarget.Next] off the end and a
 * [LinkTarget.Previous] off the start have no next and no previous, and a
 * [LinkTarget.Slide] naming a slide this deck skips has nothing to jump to. The
 * last is deliberate: a skipped slide is not in the show, and a link must not be
 * a way back into what the presenter took out.
 *
 * A slide is always entered at step 0, however far along its builds the link was
 * fired from: arriving somewhere mid-build is arriving at a slide half-drawn.
 */
fun Document.linkDestination(target: LinkTarget, at: PlayPosition): PlayPosition? {
    val order: List<Slide> = playOrder()
    if (order.isEmpty()) return null

    return when (target) {
        is LinkTarget.Slide -> order
            .indexOfFirst { it.id == target.slideId }
            .takeIf { it != -1 }
            ?.let { PlayPosition(it, 0) }

        LinkTarget.Next -> nextPosition(at)
        LinkTarget.Previous -> previousPosition(at)
        LinkTarget.First -> PlayPosition(0, 0)
        LinkTarget.Last -> PlayPosition(order.lastIndex, order.last().stepCount() - 1)
        is LinkTarget.Url, LinkTarget.ExitShow -> null
    }
}

/**
 * [nextPosition], and the start of the deck rather than null at the end of a deck
 * that loops. Still null at the end of one that doesn't.
 */
fun Document.wrappedNext(at: PlayPosition): PlayPosition? {
    val next: PlayPosition? = nextPosition(at)
    if (next != null || !playback.loop) return next
    return if (playOrder().isEmpty()) null else PlayPosition(0, 0)
}

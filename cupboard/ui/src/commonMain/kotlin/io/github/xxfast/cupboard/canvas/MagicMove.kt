package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.util.lerp
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.SlideTransition
import io.github.xxfast.cupboard.document.TextElement

/**
 * The slide change currently on screen, for the renderers that have to draw
 * across the cut rather than on one side of it.
 *
 * Null everywhere but play mode: the editor, the thumbnails and the exported
 * project all draw one slide at rest. Both slides in a change see the same value,
 * which is how each of them works out whether it is the one arriving or the one
 * leaving.
 */
val LocalPlayTransition = compositionLocalOf<PlayTransition?> { null }

/**
 * The two slides of a change and the transition between them.
 *
 * [transition] is the one governing this change, which is the leaving slide's
 * going forward and the arriving slide's coming back: a transition belongs to the
 * slide it plays on the way out of, so walking backwards undoes exactly what
 * walking forwards did.
 *
 * [fromBackground] is what the leaving slide paints, already resolved through its
 * layout and the deck (`Slide.effectiveBackground`), because the arriving slide
 * crossfades from it and has no document in hand to resolve it itself.
 */
data class PlayTransition(
    val fromSlide: Slide?,
    val toSlide: Slide,
    val forward: Boolean,
    val transition: SlideTransition,
    val fromBackground: SlideBackground? = null,
)

/**
 * A background part-way through becoming another: [from] under the slide's own,
 * which is drawn at [progress] opacity. Null in [from] is the app's own gradient,
 * as everywhere a background is null.
 */
data class BackgroundCrossfade(val from: SlideBackground?, val progress: Float)

/**
 * A live override of what an element's own frame would have drawn: where it sits
 * relative to that frame, how much of it is drawn, and how far through it is.
 *
 * Deltas on the graphics layer, for what a layer can honestly do: an action build
 * nudges, spins, scales and dims a box that was laid out at rest. A Magic Move
 * does not scale through here, because what a box holds has a size of its own,
 * and type stretched to a wider box is not type set in a wider box: its geometry
 * rides the element instead (`Element.travellingFrom`), laid out again each
 * frame the way Keynote reflows, and only rotation and opacity come this way.
 *
 * [translationX] and [translationY] are in document units, like every other
 * geometry the canvas carries.
 */
data class ElementTransform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
)

/**
 * Where a travelling element is at [progress] of its journey from [from] to [to].
 *
 * Only the two things a layer carries without distorting content: rotation and
 * opacity. Where the element sits and how big it is come from the element
 * itself, blended by `Element.travellingFrom`, so the composable drawn is the
 * target's at its in-between box and type. At progress 1 both are the target's
 * own, so the element lands exactly where the slide says it sits.
 */
fun magicMoveTransform(from: Element, to: Element, progress: Float): ElementTransform {
    val fraction: Float = progress.coerceIn(0f, 1f)

    return ElementTransform(
        rotation = lerp(from.rotation, to.rotation, fraction),
        opacity = lerp(from.opacity, to.opacity, fraction),
    )
}

/**
 * Where a travelling text's words sit in its box at [progress]: the from side's
 * alignment sliding to the to side's, as a bias, since the words themselves are
 * what the audience tracks and a box realigning under them in one frame reads as
 * a jump. Null for anything that is not text on both sides, or that keeps its
 * alignment, so the element draws where it always would.
 */
fun travellingAlignment(from: Element, to: Element, progress: Float): Alignment? {
    if (from !is TextElement || to !is TextElement || from.align == to.align) return null
    val bias: Float = lerp(from.align.bias(), to.align.bias(), progress.coerceIn(0f, 1f))
    return BiasAlignment(bias, -1f)
}

/**
 * The element where it belongs and only dimmed: what an element with nobody to
 * travel from fades in through while the matched ones are moving.
 */
fun Element.fadingTransform(progress: Float): ElementTransform = ElementTransform(
    rotation = rotation,
    opacity = opacity * progress.coerceIn(0f, 1f),
)

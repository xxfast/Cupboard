package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.util.lerp
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideTransition

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
 */
data class PlayTransition(
    val fromSlide: Slide?,
    val toSlide: Slide,
    val forward: Boolean,
    val transition: SlideTransition,
)

/**
 * A live override of what an element's own frame would have drawn: where it sits
 * relative to that frame, how much of it is drawn, and how far through it is.
 *
 * Deltas rather than a frame on purpose. An element interpolated by frame would
 * be measured again every animation frame, so its text would reflow all the way
 * across a Magic Move; a scale and a translation ride the graphics layer the
 * element already has, and its content is laid out once, at rest.
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
 * Measured against [to], the element the arriving slide holds: the composable is
 * the target's, drawn at the target's size, and the transform walks it back to
 * where it started. At progress 1 it is the identity, so the element lands
 * exactly where the slide says it sits.
 */
fun magicMoveTransform(from: Element, to: Element, progress: Float): ElementTransform {
    val fraction: Float = progress.coerceIn(0f, 1f)
    val width: Float = lerp(from.frame.width, to.frame.width, fraction)
    val height: Float = lerp(from.frame.height, to.frame.height, fraction)

    return ElementTransform(
        translationX = lerp(from.frame.centerX, to.frame.centerX, fraction) - to.frame.centerX,
        translationY = lerp(from.frame.centerY, to.frame.centerY, fraction) - to.frame.centerY,
        scaleX = if (to.frame.width == 0f) 1f else width / to.frame.width,
        scaleY = if (to.frame.height == 0f) 1f else height / to.frame.height,
        rotation = lerp(from.rotation, to.rotation, fraction),
        opacity = lerp(from.opacity, to.opacity, fraction),
    )
}

/**
 * The element where it belongs and only dimmed: what an element with nobody to
 * travel from fades in through while the matched ones are moving.
 */
fun Element.fadingTransform(progress: Float): ElementTransform = ElementTransform(
    rotation = rotation,
    opacity = opacity * progress.coerceIn(0f, 1f),
)

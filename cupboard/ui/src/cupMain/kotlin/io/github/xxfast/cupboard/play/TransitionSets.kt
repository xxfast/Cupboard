package io.github.xxfast.cupboard.play

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import io.github.xxfast.cupboard.document.SlideTransition
import io.github.xxfast.cupboard.document.TransitionDirection
import io.github.xxfast.cupboard.document.TransitionKind
import net.kodein.cup.TransitionSet

/**
 * The document's transition as the four animations CuP asks a slide for.
 *
 * Null is the deck's default, which is the horizontal move play has always used,
 * so a deck written before transitions existed looks exactly as it did.
 *
 * Each of the four lambdas is handed `isForward`, which is CuP telling us the
 * presenter is walking backwards. Every direction below is mirrored on it, so
 * stepping back undoes what stepping forward did rather than replaying it.
 */
internal fun SlideTransition?.toTransitionSet(layoutDirection: LayoutDirection): TransitionSet {
    val transition: SlideTransition = this ?: return TransitionSet.moveHorizontal(layoutDirection)
    val duration: Int = transition.durationMs
    val (dx, dy) = transition.direction.unit(layoutDirection)

    return when (transition.kind) {
        // A cut. CuP is happy with a slide that has no animation at all: the
        // AnimatedVisibility around it simply swaps in one frame.
        TransitionKind.None -> TransitionSet(
            enter = { EnterTransition.None },
            exit = { ExitTransition.None },
        )

        // Magic Move crossfades the slide as a whole; the elements that travel
        // across it are the arriving slide's business. See `SlideView`.
        TransitionKind.Dissolve, TransitionKind.MagicMove -> TransitionSet(
            enter = { fadeIn(tween(duration)) },
            exit = { fadeOut(tween(duration)) },
        )

        // Both slides move together, the arriving one shouldering the leaving
        // one off its own edge.
        TransitionKind.Push -> TransitionSet(
            enter = { isForward ->
                slideIn(tween(duration)) { size -> edge(size, dx, dy, if (isForward) -1 else 1) }
            },
            exit = { isForward ->
                slideOut(tween(duration)) { size -> edge(size, dx, dy, if (isForward) 1 else -1) }
            },
        )

        // The arriving slide alone moves, over a leaving slide that stays put
        // and dims out from under it.
        TransitionKind.MoveIn -> TransitionSet(
            enter = { isForward ->
                slideIn(tween(duration)) { size -> edge(size, dx, dy, if (isForward) -1 else 1) }
            },
            exit = { fadeOut(tween(duration)) },
        )

        // Neither slide moves: the arriving one is uncovered from one edge while
        // the leaving one is covered towards the other, both clipped. Symmetric
        // on purpose, because the two slides draw in deck order rather than in
        // travel order, so only one of them is on top whichever way we are going.
        TransitionKind.Wipe -> TransitionSet(
            enter = { isForward ->
                expandIn(
                    animationSpec = tween(duration),
                    expandFrom = alignment(dx, dy, if (isForward) -1 else 1),
                    clip = true,
                ) { full -> flattened(full, dx) }
            },
            exit = { isForward ->
                shrinkOut(
                    animationSpec = tween(duration),
                    shrinkTowards = alignment(dx, dy, if (isForward) 1 else -1),
                    clip = true,
                ) { full -> flattened(full, dx) }
            },
        )
    }
}

/**
 * Which way the content travels, as a unit vector on the slide: [TransitionDirection.Left]
 * is leftwards, so the arriving slide comes in from the right.
 *
 * Mirrored under a right-to-left layout, the way CuP's own `moveHorizontal` is:
 * "forward" points wherever the reader's eye already goes.
 */
private fun TransitionDirection.unit(layoutDirection: LayoutDirection): Pair<Int, Int> {
    val mirror: Int = if (layoutDirection == LayoutDirection.Rtl) -1 else 1
    return when (this) {
        TransitionDirection.Left -> -mirror to 0
        TransitionDirection.Right -> mirror to 0
        TransitionDirection.Up -> 0 to -1
        TransitionDirection.Down -> 0 to 1
    }
}

/** A whole slide off the edge [sign] sends it to, and nothing left on screen. */
private fun edge(size: IntSize, dx: Int, dy: Int, sign: Int): IntOffset =
    IntOffset(dx * sign * size.width, dy * sign * size.height)

/** The edge a wipe grows out of, or shrinks back towards. */
private fun alignment(dx: Int, dy: Int, sign: Int): Alignment {
    if (dx != 0) {
        return if (dx * sign > 0) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft
    }
    return if (dy * sign > 0) Alignment.BottomCenter else Alignment.TopCenter
}

/** The slide with its travelling axis closed up: what a wipe opens out of. */
private fun flattened(full: IntSize, dx: Int): IntSize =
    if (dx != 0) IntSize(0, full.height) else IntSize(full.width, 0)

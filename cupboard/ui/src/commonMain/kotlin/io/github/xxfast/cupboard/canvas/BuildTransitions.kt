package io.github.xxfast.cupboard.canvas

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.BuildEffect
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.TerminalElement

/**
 * Every effect under the name the design gives it: what the inspector's menu
 * offers and what a canvas badge is labelled with, in one place so the two never
 * spell one effect two ways.
 */
val BuildEffectNames: List<Pair<BuildEffect, String>> = listOf(
    BuildEffect.Appear to "Appear",
    BuildEffect.FadeUp to "Fade Up",
    BuildEffect.Pop to "Pop",
    BuildEffect.Dissolve to "Dissolve",
    BuildEffect.MoveIn to "Move In",
    BuildEffect.Scale to "Scale",
    BuildEffect.Wipe to "Wipe",
    BuildEffect.Typewriter to "Typewriter",
)

/** This effect's name, per [BuildEffectNames]. */
fun BuildEffect.title(): String =
    BuildEffectNames.firstOrNull { (effect, _) -> effect == this }?.second ?: name

/** How far a [BuildEffect.FadeUp] travels, as a fraction of the element's own height. */
private const val FadeUpRise: Float = 0.08f

/** Where a [BuildEffect.Pop] starts from: near its own size, so the overshoot reads. */
private const val PopScale: Float = 0.6f

/** A back-out curve, for the overshoot [BuildEffect.Pop] lands on. */
private val PopEasing: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/**
 * How [element] arrives under [build], delayed by [delayMs].
 *
 * Null [build] is an element the slide opens with, which arrives with the slide
 * rather than under anything of its own. [BuildEffect.Typewriter] is the one
 * effect an element plays for itself: a terminal enters bare and types its
 * transcript out, and every other kind takes the dissolve instead.
 */
internal fun buildEnter(element: Element, build: Build?, delayMs: Int): EnterTransition {
    if (build == null) return EnterTransition.None

    val duration: Int = build.durationMs
    return when (build.effect) {
        BuildEffect.Appear -> EnterTransition.None

        BuildEffect.FadeUp -> fadeIn(fade(duration, delayMs)) +
            slideInVertically(offset(duration, delayMs)) { height -> (height * FadeUpRise).toInt() }

        BuildEffect.Pop -> scaleIn(
            animationSpec = tween(duration, delayMs, PopEasing),
            initialScale = PopScale,
        ) + fadeIn(fade(duration, delayMs))

        BuildEffect.Dissolve -> fadeIn(fade(duration, delayMs))

        BuildEffect.MoveIn -> slideInHorizontally(offset(duration, delayMs)) { width -> -width }

        BuildEffect.Scale -> scaleIn(tween(duration, delayMs, FastOutSlowInEasing), initialScale = 0f)

        BuildEffect.Wipe -> expandVertically(size(duration, delayMs), clip = true)

        BuildEffect.Typewriter ->
            if (element is TerminalElement) EnterTransition.None else fadeIn(fade(duration, delayMs))
    }
}

/** [buildEnter]'s mirror: how [element] leaves under an [io.github.xxfast.cupboard.document.BuildKind.Out] build. */
internal fun buildExit(element: Element, build: Build?, delayMs: Int): ExitTransition {
    if (build == null) return ExitTransition.None

    val duration: Int = build.durationMs
    return when (build.effect) {
        BuildEffect.Appear -> ExitTransition.None

        BuildEffect.FadeUp -> fadeOut(fade(duration, delayMs)) +
            slideOutVertically(offset(duration, delayMs)) { height -> (height * FadeUpRise).toInt() }

        BuildEffect.Pop -> scaleOut(
            animationSpec = tween(duration, delayMs, PopEasing),
            targetScale = PopScale,
        ) + fadeOut(fade(duration, delayMs))

        BuildEffect.Dissolve -> fadeOut(fade(duration, delayMs))

        BuildEffect.MoveIn -> slideOutHorizontally(offset(duration, delayMs)) { width -> -width }

        BuildEffect.Scale -> scaleOut(tween(duration, delayMs, FastOutSlowInEasing), targetScale = 0f)

        BuildEffect.Wipe -> shrinkVertically(size(duration, delayMs), clip = true)

        BuildEffect.Typewriter ->
            if (element is TerminalElement) ExitTransition.None else fadeOut(fade(duration, delayMs))
    }
}

// One spec per animated type, so a build's duration and its resolved delay are
// written once rather than at every effect that needs them.
private fun fade(duration: Int, delayMs: Int): FiniteAnimationSpec<Float> =
    tween(duration, delayMs, FastOutSlowInEasing)

private fun offset(duration: Int, delayMs: Int): FiniteAnimationSpec<IntOffset> =
    tween(duration, delayMs, FastOutSlowInEasing)

private fun size(duration: Int, delayMs: Int): FiniteAnimationSpec<IntSize> =
    tween(duration, delayMs, FastOutSlowInEasing)

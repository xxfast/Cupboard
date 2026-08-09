package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable

enum class BuildEffect { FadeUp, Pop, Dissolve }

enum class BuildTrigger {
    /** Advances on its own step (a click in play mode). */
    OnClick,

    /** Plays together with the previous build's step. */
    WithPrevious,
}

/** One entry in a slide's Animate build order. */
@Serializable
data class Build(
    val elementId: String,
    val effect: BuildEffect = BuildEffect.FadeUp,
    val durationMs: Int = 400,
    val trigger: BuildTrigger = BuildTrigger.OnClick,
)

/**
 * Step model (maps 1:1 onto CuP's stepCount):
 * step 0 shows everything without a build; each OnClick build starts a new step,
 * WithPrevious builds join the step of the build before them.
 */
fun Slide.stepCount(): Int = 1 + builds.count { it.trigger == BuildTrigger.OnClick }

/** The step at which each element with a build becomes visible. */
fun Slide.buildSteps(): Map<String, Int> {
    val steps = mutableMapOf<String, Int>()
    var step = 0
    for (build in builds) {
        if (build.trigger == BuildTrigger.OnClick) step++
        steps[build.elementId] = step
    }
    return steps
}

fun Slide.isVisibleAt(elementId: String, step: Int): Boolean {
    val revealStep = buildSteps()[elementId] ?: return true
    return step >= revealStep
}

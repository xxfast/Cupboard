package io.github.xxfast.cupboard.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class BuildEffect {
    FadeUp,
    Pop,
    Dissolve,

    /**
     * Types the element in rather than bringing it in whole. Only
     * [TerminalElement] honours it: its command lines type out character by
     * character over the build's duration, and each block of output lands the
     * moment the command above it has finished. Every other kind treats it as
     * [FadeUp], so an effect set on the wrong element still reveals it.
     */
    Typewriter,
}

enum class BuildTrigger {
    /** Advances on its own step (a click in play mode). */
    OnClick,

    /** Plays together with the previous build's step. */
    WithPrevious,
}

/**
 * One entry in a slide's Animate build order.
 *
 * [elementStep] is the index into the target element's own steps, and is null for
 * every build that only brings an element in. Which steps those are is the
 * element's business: a code block's [CodeElement.steps], a diagram's
 * [DiagramElement.steps]. It rides the existing triggers rather than inventing
 * its own, so a `WithPrevious` step build lands on the same click as the build
 * before it.
 *
 * The tag stays "codeStep": decks were written before the field grew past code,
 * and a rename on disk would be a rename of every one of them.
 */
@Serializable
data class Build(
    val elementId: String,
    val effect: BuildEffect = BuildEffect.FadeUp,
    val durationMs: Int = 400,
    val trigger: BuildTrigger = BuildTrigger.OnClick,
    @SerialName("codeStep") val elementStep: Int? = null,
)

/**
 * Step model (maps 1:1 onto CuP's stepCount):
 * step 0 shows everything without a build; each OnClick build starts a new step,
 * WithPrevious builds join the step of the build before them.
 */
fun Slide.stepCount(): Int = 1 + builds.count { it.trigger == BuildTrigger.OnClick }

/**
 * The step at which each element with a build becomes visible.
 *
 * An element's *first* build is the one that reveals it, and later builds for
 * the same element only change it. That matters now that more than one build per
 * element is ordinary: a code block brought in at step 1 and advanced at steps 2
 * and 3 has to stay on screen throughout, not wait for its last build.
 */
fun Slide.buildSteps(): Map<String, Int> {
    val steps = mutableMapOf<String, Int>()
    var step = 0
    for (build in builds) {
        if (build.trigger == BuildTrigger.OnClick) step++
        if (build.elementId !in steps) steps[build.elementId] = step
    }
    return steps
}

/**
 * The build that reveals [elementId], null when nothing brings it in.
 *
 * The first build for an element is its reveal and the rest only change it, per
 * [buildSteps], so this is the one whose effect a renderer plays on entry.
 */
fun Slide.entryBuild(elementId: String): Build? = builds.firstOrNull { it.elementId == elementId }

fun Slide.isVisibleAt(elementId: String, step: Int): Boolean {
    val revealStep = buildSteps()[elementId] ?: return true
    return step >= revealStep
}

/**
 * Which of [elementId]'s own steps is showing at [step]: the [Build.elementStep]
 * of the last build for that element that carries one and lands at or before
 * [step].
 *
 * Null when no such build has played yet, which is both "the element has no step
 * builds at all" and "its first one is still ahead". A caller with a stepped
 * element reads that as step 0, since a stepped element shows its first state
 * from the moment it is visible; an element with no steps isn't stepped at all.
 */
fun Slide.elementStepAt(elementId: String, step: Int): Int? {
    var buildStep = 0
    var current: Int? = null
    for (build in builds) {
        if (build.trigger == BuildTrigger.OnClick) buildStep++
        // Build steps only ever climb, so nothing past here can land in range.
        if (buildStep > step) break
        if (build.elementId == elementId && build.elementStep != null) current = build.elementStep
    }
    return current
}

/**
 * The state [element] draws in at [step], or null when it has no steps to draw
 * and renders as the whole block. Out-of-range indices clamp rather than throw:
 * a build can outlive the step it pointed at.
 */
fun Slide.codeStepFor(element: CodeElement, step: Int): CodeStep? {
    if (element.steps.isEmpty()) return null
    val index: Int = (elementStepAt(element.id, step) ?: 0).coerceIn(element.steps.indices)
    return element.steps[index]
}

/**
 * The state [element] draws in at [step], or null when it has no steps and
 * renders whole. Clamps like [codeStepFor], and for the same reason: a build can
 * outlive the step it pointed at.
 */
fun Slide.diagramStepFor(element: DiagramElement, step: Int): DiagramStep? {
    if (element.steps.isEmpty()) return null
    val index: Int = (elementStepAt(element.id, step) ?: 0).coerceIn(element.steps.indices)
    return element.steps[index]
}

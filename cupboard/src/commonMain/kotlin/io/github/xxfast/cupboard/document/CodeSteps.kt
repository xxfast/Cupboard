package io.github.xxfast.cupboard.document

/**
 * The steps a code block is walked through, and the edits that shape the list.
 *
 * Two levels, and callers want the second one. The `CodeElement.withStepXxx`
 * edits change the block alone, which leaves the slide's builds pointing at
 * whatever now sits at the index they named. The `Slide.xxxCodeStep` edits are
 * those same edits with the build order carried along, so a build keeps playing
 * the step it was written for. Everything the editor does goes through the
 * slide-level ones; the element-level ones are for building a block up before it
 * is on a slide at all.
 *
 * `CodeVersions.kt` is the same shape one layer down: versions are to steps what
 * steps are to builds.
 */

/**
 * This block with [step] inserted straight behind step [after], `-1` putting it
 * at the front. An index the block has no step at is no edit at all, which is
 * what makes a stale inspector row harmless.
 */
fun CodeElement.withStepAdded(after: Int, step: CodeStep): CodeElement {
    if (after < -1 || after > steps.lastIndex) return this
    return copy(steps = steps.toMutableList().also { it.add(after + 1, step) })
}

/** This block without step [index], or itself when it has no step there. */
fun CodeElement.withStepRemoved(index: Int): CodeElement {
    if (index !in steps.indices) return this
    return copy(steps = steps.toMutableList().also { it.removeAt(index) })
}

/** This block with step [from] moved to sit at [to]. A move that lands where it started is a no-op. */
fun CodeElement.withStepMoved(from: Int, to: Int): CodeElement {
    if (from == to || from !in steps.indices || to !in steps.indices) return this
    return copy(steps = steps.toMutableList().also { it.add(to, it.removeAt(from)) })
}

/** This block with [step] in place of step [index], or itself when it has no step there. */
fun CodeElement.withStepUpdated(index: Int, step: CodeStep): CodeElement {
    if (index !in steps.indices) return this
    return copy(steps = steps.toMutableList().also { it[index] = step })
}

/**
 * This slide with [step] added to the code block [elementId], behind step
 * [after] ([CodeElement.withStepAdded]'s rule, `-1` being the front).
 *
 * Builds at or behind the insertion move up with the steps they play, and a
 * block whose walk is already in the build order gets one more click, for the
 * new step, in its place in that walk: a step added to a queued block is queued.
 * A block with no step builds yet gets none, that is what Add Step Builds is
 * for. An id the slide holds no code block under leaves it exactly as it is.
 */
fun Slide.addingCodeStep(elementId: String, after: Int, step: CodeStep): Slide {
    val element: CodeElement = elementById(elementId) as? CodeElement ?: return this
    val grown: CodeElement = element.withStepAdded(after, step)
    if (grown.steps.size == element.steps.size) return this

    val at: Int = after + 1
    val shifted: Slide = updateElement(grown).remappingStepBuilds(elementId) { index ->
        if (index >= at) index + 1 else index
    }
    return shifted.queueingStep(elementId, at)
}

/**
 * [shifted] with a click for step [at] of [elementId], when the block's walk is
 * in the build order at all. Step 0 never takes a click, so a step put at the
 * front hands the click to the one it pushed to 1.
 *
 * The click lands straight after the last of the block's clicks below it, or
 * ahead of its first when there is none, so the walk stays in step order.
 */
private fun Slide.queueingStep(elementId: String, at: Int): Slide {
    fun Build.walks(): Boolean = this.elementId == elementId && elementStep != null
    if (builds.none { it.walks() }) return this

    val target: Int = maxOf(at, 1)
    val below: Int = builds.indexOfLast { it.walks() && it.elementStep!! < target }
    val position: Int = if (below == -1) builds.indexOfFirst { it.walks() } else below + 1
    return copy(builds = builds.toMutableList().also { it.add(position, stepBuild(elementId, target)) })
}

/**
 * This slide without step [index] of the code block [elementId].
 *
 * Builds behind it come back one, and a build that pointed at the step itself
 * goes: a build for a state that no longer exists costs a click and shows
 * nothing new, which is worse than one build fewer.
 */
fun Slide.removingCodeStep(elementId: String, index: Int): Slide {
    val element: CodeElement = elementById(elementId) as? CodeElement ?: return this
    if (index !in element.steps.indices) return this

    return updateElement(element.withStepRemoved(index)).remappingStepBuilds(elementId) { at ->
        when {
            at == index -> null
            at > index -> at - 1
            else -> at
        }
    }
}

/**
 * This slide with step [from] of the code block [elementId] moved to sit at [to],
 * every build following the step it played.
 *
 * The remap is [versionsAfterMoving]'s: reordering steps under builds and
 * reordering versions under steps are the same list-index problem, so they are
 * the same arithmetic.
 */
fun Slide.movingCodeStep(elementId: String, from: Int, to: Int): Slide {
    val element: CodeElement = elementById(elementId) as? CodeElement ?: return this
    if (from == to || from !in element.steps.indices || to !in element.steps.indices) return this

    val map: List<Int> = versionsAfterMoving(element.steps.size, from, to)
    return updateElement(element.withStepMoved(from, to)).remappingStepBuilds(elementId) { at ->
        map[at.coerceIn(map.indices)]
    }
}

/**
 * This slide with [step] in place of step [index] of the code block [elementId].
 *
 * No build moves: the list is the same length and the same order, so every build
 * still plays the row it named, now saying something else.
 */
fun Slide.updatingCodeStep(elementId: String, index: Int, step: CodeStep): Slide {
    val element: CodeElement = elementById(elementId) as? CodeElement ?: return this
    if (index !in element.steps.indices) return this
    return updateElement(element.withStepUpdated(index, step))
}

/**
 * This slide with every step build of [elementId] put through [map], which hands
 * back the index the build now plays or null for a build with nothing left to
 * play. Builds for other elements, and builds carrying no [Build.elementStep] at
 * all, are left alone.
 */
private fun Slide.remappingStepBuilds(elementId: String, map: (Int) -> Int?): Slide =
    copy(
        builds = builds.mapNotNull { build ->
            val at: Int? = build.elementStep
            if (build.elementId != elementId || at == null) build
            else map(at)?.let { build.copy(elementStep = it) }
        },
    )

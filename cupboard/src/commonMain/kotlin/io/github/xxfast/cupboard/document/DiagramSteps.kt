package io.github.xxfast.cupboard.document

/**
 * The steps a diagram is walked through, and the edits that shape the list.
 *
 * `CodeSteps.kt` told for a diagram, and it reads the same: the
 * `DiagramElement.withStepXxx` edits change the diagram alone, the
 * `Slide.xxxDiagramStep` edits are those same edits with the build order carried
 * along, and everything the editor does goes through the slide-level ones.
 *
 * Only the element half is written out again here. The build half is the
 * `Slide.followingStepXxx` helpers in `CodeSteps.kt`: a build plays a step index
 * and has no idea what kind of element holds the list, so keeping the builds
 * honest is one piece of arithmetic for both.
 */

/**
 * This diagram with [step] inserted straight behind step [after], `-1` putting
 * it at the front. An index the diagram has no step at is no edit at all, which
 * is what makes a stale inspector row harmless.
 */
fun DiagramElement.withStepAdded(after: Int, step: DiagramStep): DiagramElement {
    if (after < -1 || after > steps.lastIndex) return this
    return copy(steps = steps.toMutableList().also { it.add(after + 1, step) })
}

/** This diagram without step [index], or itself when it has no step there. */
fun DiagramElement.withStepRemoved(index: Int): DiagramElement {
    if (index !in steps.indices) return this
    return copy(steps = steps.toMutableList().also { it.removeAt(index) })
}

/** This diagram with step [from] moved to sit at [to]. A move that lands where it started is a no-op. */
fun DiagramElement.withStepMoved(from: Int, to: Int): DiagramElement {
    if (from == to || from !in steps.indices || to !in steps.indices) return this
    return copy(steps = steps.toMutableList().also { it.add(to, it.removeAt(from)) })
}

/** This diagram with [step] in place of step [index], or itself when it has no step there. */
fun DiagramElement.withStepUpdated(index: Int, step: DiagramStep): DiagramElement {
    if (index !in steps.indices) return this
    return copy(steps = steps.toMutableList().also { it[index] = step })
}

/**
 * This slide with [step] added to the diagram [elementId], behind step [after]
 * ([DiagramElement.withStepAdded]'s rule, `-1` being the front).
 *
 * Builds follow exactly as [addingCodeStep]'s do: the ones at or behind the
 * insertion move up, and a diagram whose walk is already queued gets a click for
 * the new step in its place in that walk. An id the slide holds no diagram under
 * leaves it exactly as it is.
 */
fun Slide.addingDiagramStep(elementId: String, after: Int, step: DiagramStep): Slide {
    val element: DiagramElement = elementById(elementId) as? DiagramElement ?: return this
    val grown: DiagramElement = element.withStepAdded(after, step)
    if (grown.steps.size == element.steps.size) return this
    return updateElement(grown).followingStepAdded(elementId, after + 1)
}

/**
 * This slide without step [index] of the diagram [elementId]. Builds behind it
 * come back one and the build that played it goes, for [removingCodeStep]'s
 * reason: a click that shows nothing new is worse than one click fewer.
 */
fun Slide.removingDiagramStep(elementId: String, index: Int): Slide {
    val element: DiagramElement = elementById(elementId) as? DiagramElement ?: return this
    if (index !in element.steps.indices) return this
    return updateElement(element.withStepRemoved(index)).followingStepRemoved(elementId, index)
}

/**
 * This slide with step [from] of the diagram [elementId] moved to sit at [to],
 * every build following the step it played.
 */
fun Slide.movingDiagramStep(elementId: String, from: Int, to: Int): Slide {
    val element: DiagramElement = elementById(elementId) as? DiagramElement ?: return this
    if (from == to || from !in element.steps.indices || to !in element.steps.indices) return this
    return updateElement(element.withStepMoved(from, to))
        .followingStepMoved(elementId, element.steps.size, from, to)
}

/**
 * This slide with [step] in place of step [index] of the diagram [elementId].
 * No build moves: same length, same order, so every build still plays the row
 * it named, now showing something else.
 */
fun Slide.updatingDiagramStep(elementId: String, index: Int, step: DiagramStep): Slide {
    val element: DiagramElement = elementById(elementId) as? DiagramElement ?: return this
    if (index !in element.steps.indices) return this
    return updateElement(element.withStepUpdated(index, step))
}

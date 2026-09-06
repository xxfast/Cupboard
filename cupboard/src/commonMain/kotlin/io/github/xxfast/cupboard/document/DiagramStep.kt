package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable

/**
 * One visual state of a diagram: which of its nodes are on screen, and which of
 * those it is pointing at.
 *
 * An empty [reveal] shows every node, so the plain `DiagramStep()` is the whole
 * chart. An empty [highlight] dims nothing, so a step either spotlights a part of
 * the chart and drops the rest back, or leaves all of it at full strength.
 *
 * Both are node ids, not indices: a step outlives an edit to the source above it,
 * and an id that no longer parses is ignored rather than shifting everything
 * after it onto the wrong node.
 *
 * Steps are held by the element ([DiagramElement.steps]) and advanced through by
 * builds ([Build.elementStep]): the element says what its states are, the slide's
 * build order says when they arrive.
 */
@Serializable
data class DiagramStep(
    val reveal: List<String> = emptyList(),
    val highlight: List<String> = emptyList(),
)

/**
 * The nodes of [graph] that [step] shows. Empty [DiagramStep.reveal] is every one
 * of them, and an id no node carries is dropped.
 */
fun visibleNodes(graph: DiagramGraph, step: DiagramStep): Set<String> {
    if (step.reveal.isEmpty()) return graph.nodes.mapTo(mutableSetOf()) { it.id }
    return graph.nodes.filter { it.id in step.reveal }.mapTo(mutableSetOf()) { it.id }
}

/**
 * The nodes of [graph] that [step] spotlights. Empty when the step highlights
 * nothing, which is the renderer's cue to dim no node at all rather than to dim
 * every one of them.
 *
 * A node can be highlighted without being revealed: the renderer intersects these
 * with [visibleNodes].
 */
fun highlightedNodes(graph: DiagramGraph, step: DiagramStep): Set<String> {
    if (step.highlight.isEmpty()) return emptySet()
    return graph.nodes.filter { it.id in step.highlight }.mapTo(mutableSetOf()) { it.id }
}

/**
 * Whether an edge is in a set of nodes: both of its ends have to be.
 *
 * One rule for both questions a step asks, because they are the same question. An
 * edge is drawn when both its endpoints are drawn, since an arrow into nothing
 * reads as a mistake; it is at full strength when both its endpoints are, since
 * an edge onto a dimmed node is part of what the step dimmed.
 */
fun DiagramEdge.joins(nodes: Set<String>): Boolean = from in nodes && to in nodes

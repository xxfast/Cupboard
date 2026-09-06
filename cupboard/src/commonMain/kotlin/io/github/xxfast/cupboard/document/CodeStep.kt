package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable

/**
 * A run of code lines, 1-based and inclusive, counted in the block's original
 * text rather than in whatever a step happens to be showing.
 *
 * A reversed or degenerate range ([last] before [first]) contains nothing, and a
 * range that runs off either end of the block is clamped to the lines that exist.
 * Both are worth nothing rather than an error: ranges outlive edits to the code
 * they point into, and a deck that loses a line should keep playing.
 */
@Serializable
data class LineRange(val first: Int, val last: Int) {
    operator fun contains(line: Int): Boolean = line in first..last
}

/**
 * One visual state of a code block: what it shows, and what it points at.
 *
 * An empty [reveal] shows every line, so the plain `CodeStep()` is the block as
 * it was written. An empty [highlight] dims nothing, so a step either spotlights
 * a range and drops the rest back, or leaves the whole block at full strength.
 *
 * Steps are held by the element ([CodeElement.steps]) and advanced through by
 * builds ([Build.elementStep]): the element says what its states are, the slide's
 * build order says when they arrive.
 */
@Serializable
data class CodeStep(
    val reveal: List<LineRange> = emptyList(),
    val highlight: List<LineRange> = emptyList(),
)

/**
 * The 1-based original line numbers [step] shows, in order, out of a block of
 * [lineCount] lines. Empty [CodeStep.reveal] means every line.
 *
 * Original numbers rather than positions in the shown text: the gutter draws
 * these, so a block revealing lines 1-3 and 7 numbers them 1, 2, 3, 7.
 */
fun visibleLines(lineCount: Int, step: CodeStep): List<Int> {
    if (lineCount <= 0) return emptyList()
    if (step.reveal.isEmpty()) return (1..lineCount).toList()
    return (1..lineCount).filter { line -> step.reveal.any { line in it } }
}

/**
 * The 1-based original line numbers [step] spotlights. Empty when the step
 * highlights nothing, which is the renderer's cue to dim no line at all rather
 * than to dim every one of them.
 *
 * Lines outside the block are dropped, and a line can sit in a highlight range
 * without being revealed: the renderer intersects these with [visibleLines].
 */
fun highlightedLines(lineCount: Int, step: CodeStep): Set<Int> {
    if (lineCount <= 0 || step.highlight.isEmpty()) return emptySet()
    return (1..lineCount).filterTo(mutableSetOf()) { line -> step.highlight.any { line in it } }
}

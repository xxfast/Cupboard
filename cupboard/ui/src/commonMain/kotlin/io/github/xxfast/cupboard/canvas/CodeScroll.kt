package io.github.xxfast.cupboard.canvas

import io.github.xxfast.cupboard.document.CodeStep

/**
 * One shown line of a block as a renderer measured it: its original [number],
 * and where it sits in the stack of the lines its step shows, in pixels.
 *
 * [height] is the whole line, so one that wrapped is as tall as it came out.
 */
internal data class CodeScrollRow(val number: Int, val top: Int, val height: Int)

/**
 * How far up, in pixels, [step] slides a block whose shown lines are [rows] (in
 * row order) inside a [viewport] that tall: [CodeStep.scrollTo]'s arithmetic, in
 * the one place both render paths read it from.
 *
 * A block that fits never scrolls, and an unbounded [viewport] is one everything
 * fits in. Past that, a step that names a line puts it at the top, or the next
 * line it shows when that one is hidden, or its last when there is none. A step
 * that names nothing follows its highlight: the lines it spotlights are centred,
 * top-aligned when they are taller than the room, and left alone at the top when
 * it spotlights none. Whichever it is stops at the block's last line, so there is
 * never a gap under the code.
 *
 * It is where the block rests in this step and takes nothing from the step
 * before, which is what lets a renderer blend between two of these and land on
 * the same pixel a deck opened on that step draws.
 */
internal fun codeScroll(step: CodeStep, rows: List<CodeScrollRow>, viewport: Int): Int {
    val last: CodeScrollRow = rows.lastOrNull() ?: return 0
    val content: Int = last.top + last.height
    if (content <= viewport) return 0

    val line: Int? = step.scrollTo
    if (line != null) {
        val target: CodeScrollRow = rows.firstOrNull { it.number >= line } ?: last
        return target.top.coerceIn(0, content - viewport)
    }

    val spotlit: List<CodeScrollRow> =
        rows.filter { row -> step.highlight.any { row.number in it } }
    val first: CodeScrollRow = spotlit.firstOrNull() ?: return 0
    val span: Int = spotlit.last().let { it.top + it.height } - first.top
    val top: Int = if (span >= viewport) first.top else first.top - (viewport - span) / 2
    return top.coerceIn(0, content - viewport)
}

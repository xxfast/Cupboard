package io.github.xxfast.cupboard.canvas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.sourceAt
import kotlin.math.roundToInt

/** How long a block takes to walk from one step to the next. */
private const val CodeStepDuration: Int = 350

/**
 * How far, in rows, a line arrives from and leaves towards. Shared with
 * [MorphedCodeLines], whose tokens come and go the same distance.
 */
internal const val CodeStepSlide: Float = 0.4f

/** What one line does between two steps. */
internal enum class LineMotion { Kept, Added, Removed }

/**
 * One line's move between two steps, by its original number.
 *
 * [fromRow] and [toRow] are its places in the two shown blocks, counted from the
 * top of each, and null where the step doesn't show it at all.
 */
internal data class LineTransition(
    val number: Int,
    val fromRow: Int?,
    val toRow: Int?,
    val motion: LineMotion,
)

/**
 * How the block gets from showing [from] to showing [to], a line at a time.
 *
 * Both are lists of original 1-based line numbers, which is the whole matching
 * key: a step says which lines of the block it shows, so a line that survives a
 * step change is the same number in both lists and needs no diffing to find.
 *
 * The union comes back in line-number order rather than in either block's row
 * order, so a row keeps its place in the composition across a step change and
 * animates rather than being torn down and rebuilt.
 */
internal fun lineTransitions(from: List<Int>, to: List<Int>): List<LineTransition> {
    val fromRows: Map<Int, Int> = from.withIndex().associate { (row, number) -> number to row }
    val toRows: Map<Int, Int> = to.withIndex().associate { (row, number) -> number to row }

    return (fromRows.keys + toRows.keys).sorted().map { number ->
        val fromRow: Int? = fromRows[number]
        val toRow: Int? = toRows[number]
        LineTransition(
            number = number,
            fromRow = fromRow,
            toRow = toRow,
            motion = when {
                fromRow != null && toRow != null -> LineMotion.Kept
                toRow != null -> LineMotion.Added
                else -> LineMotion.Removed
            },
        )
    }
}

/** A row on screen: one line of code, and the two ends of the move it is making. */
private data class CodeRow(
    val transition: LineTransition,
    val text: AnnotatedString,
    val fromAlpha: Float,
    val toAlpha: Float,
)

/** Where a row's ink sits at [fraction] of the way through the step change. */
private fun CodeRow.alphaAt(fraction: Float): Float = when (transition.motion) {
    LineMotion.Kept -> lerp(fromAlpha, toAlpha, fraction)
    LineMotion.Added -> fraction * toAlpha
    LineMotion.Removed -> (1f - fraction) * fromAlpha
}

/** Full strength, or dropped back to [CodeDimAlpha]; a line the step doesn't show is neither. */
private fun SteppedLine?.strength(): Float = if (this?.dimmed == true) CodeDimAlpha else 1f

/**
 * [element]'s code in [step], animated from the step it was showing before.
 *
 * A line the two steps share slides from the row it had to the row it gets, one
 * revealed by [step] fades and slides in, one [step] hides fades and slides out,
 * and a line that changes side of the highlight crossfades between the two dims.
 * Matching is by original line number, so nothing is diffed: the block filling in
 * a body line by line is the same line arriving, not a new one that looks like it.
 *
 * All of that is one version of the block. A step that names another version has
 * no lines in common with the one before it, so it hands over to
 * [MorphedCodeLines], which animates the same change a token at a time on the
 * same clock. The line path is what draws at rest either way.
 *
 * A row is laid out rather than set as one string because the two orderings have
 * to be measured against each other, which is also what makes wrap behave: a line
 * that reflows is measured taller and everything under it stacks below that.
 */
@Composable
internal fun AnimatedCodeLines(
    element: CodeElement,
    step: CodeStep,
    modifier: Modifier = Modifier,
) {
    var from: CodeStep by remember { mutableStateOf(step) }
    var to: CodeStep by remember { mutableStateOf(step) }
    val progress: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }

    // The first composition has nothing to come from, so it draws [step] at rest.
    // Both ends are held here rather than read from the parameter, so the frame
    // between a step change and this effect still draws the block as it was.
    LaunchedEffect(step) {
        if (step == to) return@LaunchedEffect
        from = to
        to = step
        progress.snapTo(0f)
        progress.animateTo(1f, tween(CodeStepDuration, easing = FastOutSlowInEasing))
        // Settled, so the lines this step dropped stop being composed at all
        // rather than sitting under the block at zero alpha.
        from = to
    }

    // A step names the version of the block it plays, so the lines a step shows
    // are that version's lines and not the block's first ones.
    val fromSource: String = element.sourceAt(from.version)
    val toSource: String = element.sourceAt(to.version)

    // Two versions are not one text with rows added and taken away, so nothing
    // here can carry a line from one to the other: the token morph does that
    // instead, and hands back at rest, where both ends are one version again.
    if (fromSource != toSource) {
        MorphedCodeLines(element, from, to, progress, modifier)
        return
    }

    val chrome: CodeChrome = element.theme.chrome
    val was: List<SteppedLine> =
        remember(fromSource, element.language, element.theme, from) {
            steppedLines(fromSource, element.language, element.theme, from)
        }
    val now: List<SteppedLine> =
        remember(toSource, element.language, element.theme, to) {
            steppedLines(toSource, element.language, element.theme, to)
        }

    val rows: List<CodeRow> = remember(was, now) {
        val before: Map<Int, SteppedLine> = was.associateBy { it.number }
        val after: Map<Int, SteppedLine> = now.associateBy { it.number }
        lineTransitions(was.map { it.number }, now.map { it.number }).map { transition ->
            val number: Int = transition.number
            val line: SteppedLine = after[number] ?: checkNotNull(before[number])
            CodeRow(
                transition = transition,
                text = line.text,
                fromAlpha = before[number].strength(),
                toAlpha = after[number].strength(),
            )
        }
    }

    Layout(
        modifier = modifier,
        content = {
            for (row in rows) {
                key(row.transition.number) {
                    if (element.showLineNumbers) {
                        Text(
                            text = row.transition.number.toString(),
                            color = chrome.gutter,
                            fontSize = element.fontSize.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = (element.fontSize * CodeLineHeight).sp,
                            softWrap = false,
                            textAlign = TextAlign.End,
                            modifier = Modifier.graphicsLayer {
                                alpha = row.alphaAt(progress.value)
                            },
                        )
                    }

                    Text(
                        text = row.text,
                        color = chrome.text,
                        fontSize = element.fontSize.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = (element.fontSize * CodeLineHeight).sp,
                        softWrap = element.wrap,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.graphicsLayer {
                            alpha = row.alphaAt(progress.value)
                        },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val numbered: Boolean = element.showLineNumbers
        val perRow: Int = if (numbered) 2 else 1

        // One column for every row's number, as wide as the widest of them, so
        // the code starts at the same x on every line however any of them wrap.
        val gutterWidth: Int =
            if (!numbered) 0
            else rows.indices.maxOfOrNull {
                measurables[it * perRow].maxIntrinsicWidth(Constraints.Infinity)
            } ?: 0
        val left: Int = gutterWidth + if (numbered) CodeGutterGap.roundToPx() else 0

        val gutters: List<Placeable>? =
            if (!numbered) null
            else List(rows.size) {
                measurables[it * perRow].measure(Constraints.fixedWidth(gutterWidth))
            }
        val room: Constraints =
            if (!constraints.hasBoundedWidth) Constraints()
            else Constraints(maxWidth = (constraints.maxWidth - left).coerceAtLeast(0))
        val bodies: List<Placeable> = List(rows.size) {
            measurables[it * perRow + perRow - 1].measure(room)
        }
        val heights =
            IntArray(rows.size) { maxOf(bodies[it].height, gutters?.get(it)?.height ?: 0) }

        // Where each row sits in one step's stack. A row that step doesn't show
        // takes no space in it, and a wrapped line pushes the rest down by as
        // much as it measured rather than by one line's worth.
        fun tops(rowOf: (LineTransition) -> Int?): IntArray {
            val tops = IntArray(rows.size)
            var top = 0
            for (index in rows.indices.sortedBy { rowOf(rows[it].transition) ?: Int.MAX_VALUE }) {
                if (rowOf(rows[index].transition) == null) break
                tops[index] = top
                top += heights[index]
            }
            return tops
        }

        val fromTops: IntArray = tops { it.fromRow }
        val toTops: IntArray = tops { it.toRow }
        val stacked: Int = maxOf(
            rows.indices.sumOf { if (rows[it].transition.fromRow != null) heights[it] else 0 },
            rows.indices.sumOf { if (rows[it].transition.toRow != null) heights[it] else 0 },
        )
        val width: Int =
            if (constraints.hasBoundedWidth) constraints.maxWidth
            else left + (bodies.maxOfOrNull { it.width } ?: 0)

        // Only the placement reads the animation, so a frame of it costs a walk
        // over the rows rather than a recomposition and a remeasure of the text.
        layout(width, constraints.constrainHeight(stacked)) {
            val fraction: Float = progress.value
            for (index in rows.indices) {
                val slide: Float = CodeStepSlide * heights[index]
                val y: Float = when (rows[index].transition.motion) {
                    LineMotion.Kept ->
                        lerp(fromTops[index].toFloat(), toTops[index].toFloat(), fraction)

                    LineMotion.Added -> toTops[index] + (1f - fraction) * slide
                    LineMotion.Removed -> fromTops[index] + fraction * slide
                }

                gutters?.get(index)?.place(0, y.roundToInt())
                bodies[index].place(left, y.roundToInt())
            }
        }
    }
}

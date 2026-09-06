package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.CodeTheme
import io.github.xxfast.cupboard.document.highlightedLines
import io.github.xxfast.cupboard.document.visibleLines

/** What a line the step doesn't spotlight is dropped to, ink and gutter alike. */
internal const val CodeDimAlpha: Float = 0.35f

/** A code block as one step shows it: the text to set, and the numbers beside it. */
internal data class SteppedCode(
    val text: AnnotatedString,
    val numbers: AnnotatedString,
)

/**
 * One line of the block a step draws: its own original [number], its ink, and
 * whether the step drops it back.
 *
 * [text] is the line at full strength whatever [dimmed] says. A dim is one alpha
 * over the whole line, so the caller picks how to spend it: [steppedCode] bakes
 * it into spans for the one-[AnnotatedString] path, and [AnimatedCodeLines] puts
 * it on the row's layer, where it can be animated between two steps.
 */
internal data class SteppedLine(
    val number: Int,
    val text: AnnotatedString,
    val dimmed: Boolean,
)

/**
 * The lines [step] shows, in order, each with the number it has in the original
 * block and its own slice of the highlighting.
 *
 * The code is tokenized whole and then sliced per line, rather than tokenized
 * again over the joined visible text. A step that hides the opening of a block
 * comment or of a multi-line string would otherwise re-colour everything under
 * the cut, and the point of revealing a body line by line is that the lines look
 * the same on the way in as they do at the end.
 *
 * A null [step], the editor's case, is every line, undimmed.
 */
internal fun steppedLines(
    code: String,
    language: String,
    theme: CodeTheme,
    step: CodeStep?,
): List<SteppedLine> = steppedLines(code, highlightCode(code, language, theme), step)

/**
 * The block [step] draws: only its revealed lines, each still carrying its own
 * original number, and every line outside its highlight dimmed.
 *
 * A null [step], the editor's case, is the whole block at full strength: same
 * highlighting, same numbers, no spans of ours over the top.
 */
internal fun steppedCode(
    code: String,
    language: String,
    theme: CodeTheme,
    step: CodeStep?,
): SteppedCode {
    val highlighted: AnnotatedString = highlightCode(code, language, theme)
    val lines: List<SteppedLine> = steppedLines(code, highlighted, step)

    // Nothing hidden and nothing dimmed is the block as it was written, which is
    // worth spelling out: it keeps the editor and a plain step on one path, and
    // it hands back the one span a line-by-line slice would have to cut in two.
    if (lines.size == code.count { it == '\n' } + 1 && lines.none { it.dimmed }) {
        val numbers = AnnotatedString(lines.joinToString("\n") { "${it.number}" })
        return SteppedCode(highlighted, numbers)
    }

    val chrome: CodeChrome = theme.chrome
    val dimmedText = SpanStyle(color = chrome.text.copy(alpha = CodeDimAlpha))
    val dimmedGutter = SpanStyle(color = chrome.gutter.copy(alpha = CodeDimAlpha))

    val text: AnnotatedString = buildAnnotatedString {
        for ((index, line) in lines.withIndex()) {
            if (index > 0) append("\n")

            val at: Int = length
            append(line.text.text)

            // The dim goes on first and the tokens over it: later spans win, so
            // a coloured token keeps its own colour, dimmed, and everything the
            // highlighter said nothing about falls back to the dimmed default.
            if (line.dimmed) addStyle(dimmedText, at, length)

            for (span in line.text.spanStyles) {
                addStyle(
                    if (line.dimmed) span.item.dimmed() else span.item,
                    at + span.start,
                    at + span.end,
                )
            }
        }
    }

    val numbers: AnnotatedString = buildAnnotatedString {
        for ((index, line) in lines.withIndex()) {
            if (index > 0) append("\n")
            val at: Int = length
            append(line.number.toString())
            if (line.dimmed) addStyle(dimmedGutter, at, length)
        }
    }

    return SteppedCode(text, numbers)
}

/** [steppedLines] over a block that has already been tokenized, so a caller does it once. */
private fun steppedLines(
    code: String,
    highlighted: AnnotatedString,
    step: CodeStep?,
): List<SteppedLine> {
    val lines: List<String> = code.split("\n")
    val visible: List<Int> =
        if (step == null) (1..lines.size).toList() else visibleLines(lines.size, step)
    val spotlit: Set<Int> = if (step == null) emptySet() else highlightedLines(lines.size, step)

    // Where each line begins in `code`, so the whole-block spans can be sliced.
    val starts = IntArray(lines.size)
    var offset = 0
    for (index in lines.indices) {
        starts[index] = offset
        offset += lines[index].length + 1
    }

    return visible.map { line ->
        val body: String = lines[line - 1]
        val from: Int = starts[line - 1]
        SteppedLine(
            number = line,
            text = buildAnnotatedString {
                append(body)
                for (span in highlighted.spanStyles) {
                    val start: Int = maxOf(span.start, from)
                    val end: Int = minOf(span.end, from + body.length)
                    if (start >= end) continue
                    addStyle(span.item, start - from, end - from)
                }
            },
            dimmed = spotlit.isNotEmpty() && line !in spotlit,
        )
    }
}

/** Keeps a token's colour and takes it back to [CodeDimAlpha]; a bold-only span is left alone. */
private fun SpanStyle.dimmed(): SpanStyle =
    if (color.isSpecified) copy(color = color.copy(alpha = color.alpha * CodeDimAlpha)) else this

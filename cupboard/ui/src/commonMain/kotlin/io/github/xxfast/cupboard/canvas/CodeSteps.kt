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
 * The block [step] draws: only its revealed lines, each still carrying its own
 * original number, and every line outside its highlight dimmed.
 *
 * The code is tokenized whole and then sliced per line, rather than tokenized
 * again over the joined visible text. A step that hides the opening of a block
 * comment or of a multi-line string would otherwise re-colour everything under
 * the cut, and the point of revealing a body line by line is that the lines look
 * the same on the way in as they do at the end.
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
    val lines: List<String> = code.split("\n")
    val visible: List<Int> =
        if (step == null) (1..lines.size).toList() else visibleLines(lines.size, step)
    val spotlit: Set<Int> = if (step == null) emptySet() else highlightedLines(lines.size, step)

    // Nothing hidden and nothing dimmed is the block as it was written, which is
    // worth spelling out: it keeps the editor and a plain step on one path.
    if (visible.size == lines.size && spotlit.isEmpty()) {
        return SteppedCode(highlighted, AnnotatedString(visible.joinToString("\n")))
    }

    // Where each line begins in `code`, so the whole-block spans can be sliced.
    val starts = IntArray(lines.size)
    var offset = 0
    for (index in lines.indices) {
        starts[index] = offset
        offset += lines[index].length + 1
    }

    val chrome: CodeChrome = theme.chrome
    val dimmedText = SpanStyle(color = chrome.text.copy(alpha = CodeDimAlpha))
    val dimmedGutter = SpanStyle(color = chrome.gutter.copy(alpha = CodeDimAlpha))
    fun dims(line: Int): Boolean = spotlit.isNotEmpty() && line !in spotlit

    val text: AnnotatedString = buildAnnotatedString {
        for ((index, line) in visible.withIndex()) {
            if (index > 0) append("\n")

            val body: String = lines[line - 1]
            val from: Int = starts[line - 1]
            val at: Int = length
            append(body)

            // The dim goes on first and the tokens over it: later spans win, so
            // a coloured token keeps its own colour, dimmed, and everything the
            // highlighter said nothing about falls back to the dimmed default.
            val dim: Boolean = dims(line)
            if (dim) addStyle(dimmedText, at, length)

            for (span in highlighted.spanStyles) {
                val start: Int = maxOf(span.start, from)
                val end: Int = minOf(span.end, from + body.length)
                if (start >= end) continue
                addStyle(
                    if (dim) span.item.dimmed() else span.item,
                    at + (start - from),
                    at + (end - from),
                )
            }
        }
    }

    val numbers: AnnotatedString = buildAnnotatedString {
        for ((index, line) in visible.withIndex()) {
            if (index > 0) append("\n")
            val at: Int = length
            append(line.toString())
            if (dims(line)) addStyle(dimmedGutter, at, length)
        }
    }

    return SteppedCode(text, numbers)
}

/** Keeps a token's colour and takes it back to [CodeDimAlpha]; a bold-only span is left alone. */
private fun SpanStyle.dimmed(): SpanStyle =
    if (color.isSpecified) copy(color = color.copy(alpha = color.alpha * CodeDimAlpha)) else this

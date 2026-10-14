package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import io.github.xxfast.cupboard.document.CodeDiagnostic
import io.github.xxfast.cupboard.document.DiagnosticSeverity

/** A diagnostic's message is set this much smaller than the code it speaks about. */
private const val MessageScale: Float = 0.85f

/** One stretch of squiggle: from [left] to [right], under the glyphs sitting on [baseline]. */
internal data class SquiggleSegment(val left: Float, val right: Float, val baseline: Float)

/**
 * A diagnostic as one laid out line draws it: the squiggle under its columns, a
 * segment per visual line it runs across, and its message, already measured,
 * with the top-left it is set at. Null [message] for a diagnostic with nothing
 * to say, which squiggles and stops there.
 *
 * Positions are the layout's own, so the caller moves a mark by moving the
 * layout: a row's place in the stack, a block's scroll, a morph's token.
 */
internal data class DiagnosticMark(
    val diagnostic: CodeDiagnostic,
    val segments: List<SquiggleSegment>,
    val message: TextLayoutResult?,
    val messageAt: Offset,
)

/** The colour [severity] draws in on this chrome. */
internal fun CodeChrome.colorOf(severity: DiagnosticSeverity): Color = when (severity) {
    DiagnosticSeverity.Error -> error
    DiagnosticSeverity.Warning -> warning
}

/**
 * Where [diagnostics], all on one line of code, sit in [layout]: the line is
 * [length] characters from offset [start] of the laid out text, which is 0 for a
 * row set on its own and the row's offset for a block set as one text.
 *
 * Columns are clamped to the line, the way [CodeDiagnostic] promises, and a
 * span with nothing under it (an empty line, a column past the end) is one
 * [em] wide where it lands, so it still points somewhere. The messages go after
 * the line's last visual line, error lens style, one after the other in list
 * order with a [gap] before each.
 */
internal fun diagnosticMarks(
    diagnostics: List<CodeDiagnostic>,
    layout: TextLayoutResult,
    start: Int,
    length: Int,
    measurer: TextMeasurer,
    style: TextStyle,
    chrome: CodeChrome,
    em: Float,
    gap: Float,
): List<DiagnosticMark> {
    if (diagnostics.isEmpty()) return emptyList()

    val last: Int = layout.getLineForOffset(start + (length - 1).coerceAtLeast(0))
    var x: Float = layout.getLineRight(last) + gap

    return diagnostics.map { diagnostic ->
        val from: Int = (diagnostic.startColumn - 1).coerceIn(0, length)
        val to: Int = diagnostic.endColumn.coerceIn(from, length)

        val segments: List<SquiggleSegment> =
            if (from == to) {
                val visual: Int = layout.getLineForOffset(start + from)
                val left: Float = layout.getHorizontalPosition(start + from, usePrimaryDirection = true)
                listOf(SquiggleSegment(left, left + em, layout.getLineBaseline(visual)))
            } else {
                val first: Int = layout.getLineForOffset(start + from)
                val end: Int = layout.getLineForOffset(start + to - 1)
                (first..end).map { visual ->
                    val left: Float =
                        if (visual == first) layout.getBoundingBox(start + from).left
                        else layout.getLineLeft(visual)
                    val right: Float =
                        if (visual == end) layout.getBoundingBox(start + to - 1).right
                        else layout.getLineRight(visual)
                    SquiggleSegment(left, right, layout.getLineBaseline(visual))
                }
            }

        val message: TextLayoutResult? = diagnostic.message?.let { text ->
            measurer.measure(
                text = AnnotatedString(text),
                style = style.copy(
                    color = chrome.colorOf(diagnostic.severity),
                    fontSize = style.fontSize * MessageScale,
                    lineHeight = TextStyle.Default.lineHeight,
                ),
                softWrap = false,
            )
        }

        // Centred on the line it speaks about, padded the way its pill is.
        val top: Float = layout.getLineTop(last)
        val height: Float = layout.getLineBottom(last) - top
        val messageAt = Offset(x + em * 0.4f, top + (height - (message?.size?.height ?: 0)) / 2f)
        if (message != null) x += message.size.width + em * 0.8f + gap

        DiagnosticMark(diagnostic, segments, message, messageAt)
    }
}

/**
 * [mark] at [alpha], its squiggle moved by [shift] and its message set at
 * [messageAt] (the mark's own place, unless the caller is moving it): an IDE's
 * wavy underline in the severity's colour, and the message on a faint pill of
 * the same colour after the line.
 *
 * [em] is the code's font size in pixels, which the squiggle's wave is sized by,
 * so it reads the same at every size a block is set in.
 */
internal fun DrawScope.drawDiagnostic(
    mark: DiagnosticMark,
    chrome: CodeChrome,
    em: Float,
    alpha: Float,
    shift: Offset = Offset.Zero,
    messageAt: Offset = mark.messageAt + shift,
) {
    if (alpha <= 0f) return
    val color: Color = chrome.colorOf(mark.diagnostic.severity)

    val amplitude: Float = em * 0.09f
    val wavelength: Float = em * 0.36f
    val stroke = Stroke(width = (em * 0.07f).coerceAtLeast(1f))
    for (segment in mark.segments) {
        val y: Float = segment.baseline + em * 0.2f + shift.y
        val path = Path()
        var x: Float = segment.left + shift.x
        val end: Float = segment.right + shift.x
        path.moveTo(x, y)
        var up = true
        while (x < end) {
            val next: Float = minOf(x + wavelength / 2f, end)
            path.quadraticTo((x + next) / 2f, if (up) y - amplitude else y + amplitude, next, y)
            up = !up
            x = next
        }
        drawPath(path, color, alpha = alpha, style = stroke)
    }

    val message: TextLayoutResult = mark.message ?: return
    val pad: Float = em * 0.4f
    drawRoundRect(
        color = color,
        topLeft = messageAt - Offset(pad, em * 0.1f),
        size = Size(message.size.width + pad * 2f, message.size.height + em * 0.2f),
        cornerRadius = CornerRadius(em * 0.25f),
        alpha = alpha * 0.14f,
    )
    drawText(message, topLeft = messageAt, alpha = alpha)
}

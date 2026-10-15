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
import androidx.compose.ui.unit.Constraints
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

/**
 * Where a message's pill sits, [width] wide: at [inline], after its line, when it
 * ends inside [maxRight] there, and otherwise at [below], under its squiggle,
 * pulled left until it ends inside [maxRight] but never past the left edge.
 *
 * Never wrapped: a message is one line wherever it goes.
 */
internal fun messagePlacement(
    inline: Offset,
    below: Offset,
    width: Float,
    maxRight: Float,
): Offset {
    if (inline.x + width <= maxRight) return inline
    return Offset(minOf(below.x, maxRight - width).coerceAtLeast(0f), below.y)
}

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
 * order with a [gap] before each. Deck code tends to run the width of its block,
 * so one with no room left there drops under its squiggle instead, the way an
 * IDE's hover does: a line down, over the code below ([messagePlacement]), and a
 * line further for each one before it that dropped too.
 *
 * The room is the width [layout] was set in, which is the block's for both the
 * line path's rows and the morph's whole text: past that the block cuts it off.
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

    val room: Constraints = layout.layoutInput.constraints
    val maxRight: Float =
        if (room.hasBoundedWidth) room.maxWidth.toFloat() else Float.POSITIVE_INFINITY
    val pad: Float = em * 0.4f

    val last: Int = layout.getLineForOffset(start + (length - 1).coerceAtLeast(0))
    var x: Float = layout.getLineRight(last) + gap
    var dropped = 0

    fun lineHeight(visual: Int): Float = layout.getLineBottom(visual) - layout.getLineTop(visual)

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

        // Centred on the visual line it is set against, padded the way its pill is.
        val messageHeight: Int = message?.size?.height ?: 0
        fun centred(visual: Int): Float =
            layout.getLineTop(visual) + (lineHeight(visual) - messageHeight) / 2f

        val inline = Offset(x, centred(last))
        if (message == null) return@map DiagnosticMark(diagnostic, segments, null, inline)

        val under: Int = layout.getLineForOffset(start + if (from == to) from else to - 1)
        val below = Offset(
            x = segments.first().left,
            y = centred(under) + lineHeight(under) * (dropped + 1),
        )
        val width: Float = message.size.width + pad * 2f
        val at: Offset = messagePlacement(inline, below, width, maxRight)
        if (at == inline) x += width + gap else dropped += 1

        DiagnosticMark(diagnostic, segments, message, at + Offset(pad, 0f))
    }
}

/**
 * [mark] at [alpha], its squiggle moved by [shift] and its message set at
 * [messageAt] (the mark's own place, unless the caller is moving it): an IDE's
 * wavy underline in the severity's colour, and the message on a faint pill of
 * the same colour after the line or under it. The pill is laid on the block's own
 * background first, so one dropped over the code below reads as a popup over it
 * rather than as two lines of text on top of each other.
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
    val pillAt: Offset = messageAt - Offset(pad, em * 0.1f)
    val pill = Size(message.size.width + pad * 2f, message.size.height + em * 0.2f)
    val corner = CornerRadius(em * 0.25f)
    drawRoundRect(chrome.background, pillAt, pill, corner, alpha = alpha)
    drawRoundRect(color, pillAt, pill, corner, alpha = alpha * 0.14f)
    drawText(message, topLeft = messageAt, alpha = alpha)
}

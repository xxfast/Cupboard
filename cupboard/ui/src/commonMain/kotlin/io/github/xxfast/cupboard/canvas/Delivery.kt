package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import io.github.xxfast.cupboard.document.BuildDelivery
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.LineRange
import io.github.xxfast.cupboard.document.PieceReveal

/**
 * [body], the slice of a text element's text that starts at [offset] in it, with
 * everything past the [shown]th piece hidden and the newest piece at [alpha].
 *
 * Hidden rather than absent: the pieces still to come are drawn transparent, so
 * the box keeps the shape it will have when the whole text is out and nothing
 * reflows underneath as it arrives. [pieces] are ranges into the whole text
 * ([io.github.xxfast.cupboard.document.pieces]), so a renderer that sets its
 * lines one at a time passes each line's own offset.
 *
 * [color] is the ink the text is set in, which the fading piece is a weaker copy
 * of: a piece coming in at half alpha is that colour at half alpha, never a
 * different colour.
 */
internal fun deliveredText(
    body: String,
    offset: Int,
    pieces: List<IntRange>,
    shown: Int,
    alpha: Float,
    color: Color,
): AnnotatedString = buildAnnotatedString {
    append(body)
    if (body.isEmpty()) return@buildAnnotatedString

    // Pieces arrive in order, so what is still to come is one tail rather than a
    // span per piece: everything past the newest piece's last character.
    val newest: IntRange? = pieces.getOrNull(shown - 1)
    val tail: Int =
        if (newest == null) 0 else (newest.last + 1 - offset).coerceIn(0, body.length)
    if (tail < body.length) addStyle(SpanStyle(color = Color.Transparent), tail, body.length)

    if (newest == null) return@buildAnnotatedString
    val from: Int = (newest.first - offset).coerceIn(0, body.length)
    if (from < tail) addStyle(SpanStyle(color = color.copy(alpha = color.alpha * alpha)), from, tail)
}

/**
 * The step a block delivered a line at a time draws in: its first [reveal]'s
 * worth of lines, and nothing below them.
 *
 * Null when the block isn't being delivered by line, which includes a block whose
 * delivery is by anything else: a code block has no words to hand over one at a
 * time, so it shows whole.
 *
 * Only for a block with no steps of its own. One with steps is walked by them,
 * and its build order says which one it is on.
 */
internal fun deliveredStep(reveal: PieceReveal?): CodeStep? {
    val lines: Int = reveal?.linesShown() ?: return null
    return CodeStep(reveal = listOf(LineRange(1, lines)))
}

/**
 * How many lines of a block are out, null when it isn't being delivered by line.
 * A terminal reads this too: its transcript is lines, the same as a code block's.
 */
internal fun PieceReveal.linesShown(): Int? =
    if (build.delivery == BuildDelivery.ByLine) shown else null

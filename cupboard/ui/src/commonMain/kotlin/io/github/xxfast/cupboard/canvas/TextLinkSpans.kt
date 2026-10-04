package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextLink
import io.github.xxfast.cupboard.document.listBody

/** A range link as one run of drawn text carries it: `[start, end)` of that run. */
internal data class LinkSpan(val start: Int, val end: Int, val target: LinkTarget)

/**
 * One run of a text box as it is set: [body] is what draws, and [start] is where
 * it begins in the element's text. A plain box is one run, the whole text; a list
 * is one per line, each its line with the nesting tabs gone, the marker in front
 * of it drawn on its own.
 */
internal data class TextRun(val start: Int, val body: String) {
    /** [links] cut to this run, in its own offsets. */
    fun spans(links: List<TextLink>): List<LinkSpan> = links.mapNotNull { link ->
        val from: Int = (link.start - start).coerceIn(0, body.length)
        val to: Int = (link.end - start).coerceIn(0, body.length)
        if (from < to) LinkSpan(from, to, link.target) else null
    }

    /** An offset into [body] as the offset into the element's text it came from. */
    fun textOffset(offset: Int): Int = start + offset.coerceIn(0, body.length)
}

/**
 * [text] as the renderer sets it: one [TextRun] for a plain box, one per line for
 * a list. The one place a range link's offsets are mapped to what is drawn, so the
 * slide, the thumbnails, the show and the caret's field all agree on which
 * characters a link covers, markers and indents notwithstanding.
 */
internal fun textRuns(text: String, style: ListStyle): List<TextRun> {
    if (style == ListStyle.None) return listOf(TextRun(0, text))

    var at = 0
    return text.split("\n").map { line ->
        val body: String = line.listBody()
        val run = TextRun(at + (line.length - body.length), body)
        at += line.length + 1
        run
    }
}

/**
 * How a range link sets its words: underlined, and in the link colour only where
 * the box is still in the colour it started in, the same rule [textStyle] follows
 * for a whole-box link. A strikethrough the box carries stays drawn through it.
 */
internal fun TextElement.linkSpanStyle(): SpanStyle {
    val decorations: List<TextDecoration> = buildList {
        add(TextDecoration.Underline)
        if (strikethrough) add(TextDecoration.LineThrough)
    }
    return SpanStyle(
        color = if (color == DefaultTextColor) LinkColor.toComposeColor() else Color.Unspecified,
        textDecoration = TextDecoration.combine(decorations),
    )
}

/** [body] with each of [spans] set in [style]. */
internal fun linkedText(body: String, spans: List<LinkSpan>, style: SpanStyle): AnnotatedString =
    if (spans.isEmpty()) AnnotatedString(body)
    else buildAnnotatedString {
        append(body)
        for (span in spans) addStyle(style, span.start, span.end)
    }

/**
 * The span under [position] in a run laid out as [layout], null when the position
 * is over no linked character: past the end of a line, between lines, or over
 * plain words.
 *
 * The character's own box decides rather than the nearest caret position, which
 * is what a click in the margin beside a link would otherwise land on.
 */
internal fun TextLayoutResult.linkSpanAt(position: Offset, spans: List<LinkSpan>): LinkSpan? {
    if (spans.isEmpty()) return null
    val length: Int = layoutInput.text.length
    if (length == 0) return null

    val caret: Int = getOffsetForPosition(position)
    val character: Int = listOf(caret, caret - 1)
        .filter { it in 0 until length }
        .firstOrNull { getBoundingBox(it).contains(position) }
        ?: return null
    return spans.firstOrNull { character >= it.start && character < it.end }
}

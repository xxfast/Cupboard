package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.TextBaseline
import io.github.xxfast.cupboard.document.TextCapitalization
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextLink
import io.github.xxfast.cupboard.document.TextRun
import io.github.xxfast.cupboard.document.capitalized
import io.github.xxfast.cupboard.document.linkAt
import io.github.xxfast.cupboard.document.listBody
import io.github.xxfast.cupboard.document.resolved
import io.github.xxfast.cupboard.document.resolvedLink

/** A range link as one run of drawn text carries it: `[start, end)` of that run. */
internal data class LinkSpan(val start: Int, val end: Int, val target: LinkTarget)

/**
 * One run of a text box as it is set: [body] is what draws, and [start] is where
 * it begins in the element's text. A plain box is one run, the whole text; a list
 * is one per line, each its line with the nesting tabs gone, the marker in front
 * of it drawn on its own.
 *
 * Drawn rather than styled: a [TextRun] is a stretch in a style of its own, and
 * one of these is cut by the line, whatever styles are on it.
 */
internal data class DrawnRun(val start: Int, val body: String) {
    /** [links] cut to this run, in its own offsets. */
    fun spans(links: List<TextLink>): List<LinkSpan> = links.mapNotNull { link ->
        val from: Int = (link.start - start).coerceIn(0, body.length)
        val to: Int = (link.end - start).coerceIn(0, body.length)
        if (from < to) LinkSpan(from, to, link.target) else null
    }

    /** [body] with each of [styles], measured in the element's text, cut to this run. */
    fun styled(styles: List<AnnotatedString.Range<SpanStyle>>): AnnotatedString =
        if (styles.isEmpty()) AnnotatedString(body)
        else buildAnnotatedString {
            append(body)
            for (style in styles) {
                val from: Int = (style.start - start).coerceIn(0, body.length)
                val to: Int = (style.end - start).coerceIn(0, body.length)
                if (from < to) addStyle(style.item, from, to)
            }
        }

    /** An offset into [body] as the offset into the element's text it came from. */
    fun textOffset(offset: Int): Int = start + offset.coerceIn(0, body.length)
}

/**
 * [text] as the renderer sets it: one [DrawnRun] for a plain box, one per line
 * for a list. The one place a range's offsets are mapped to what is drawn, so the
 * slide, the thumbnails, the show and the caret's field all agree on which
 * characters a link or a style covers, markers and indents notwithstanding.
 */
internal fun drawnRuns(text: String, style: ListStyle): List<DrawnRun> {
    if (style == ListStyle.None) return listOf(DrawnRun(0, text))

    var at = 0
    return text.split("\n").map { line ->
        val body: String = line.listBody()
        val run = DrawnRun(at + (line.length - body.length), body)
        at += line.length + 1
        run
    }
}

/**
 * How a text box cases its letters as they draw: its own [capitalization] and
 * every run that names one of its own. Kept apart from the box so the editor's
 * field can tell when it changes, which a whole element would say on every key.
 *
 * Casing is the one style that changes the characters rather than how they are
 * set, so it goes on the text before anything is cut from it: [drawnRuns] is
 * handed [cased] text, and every offset still lands on its own letter because
 * `capitalized` changes a character for a character.
 */
internal data class TextCasing(
    private val capitalization: TextCapitalization,
    private val runs: List<TextRun>,
) {
    /** Whether [cased] changes nothing, so a draw site can skip it. */
    val isIdentity: Boolean
        get() = (listOf(capitalization) + runs.mapNotNull { it.capitalization }).all { it.keepsLetters }

    /**
     * [text] as it draws: each character cased by the run it lies in, else by
     * the box. Each casing is worked over the whole text and only its run's
     * share taken, so title case sees where a word really starts rather than
     * where a run happens to.
     */
    fun cased(text: String): String {
        if (isIdentity) return text

        val drawn: CharArray = text.capitalized(capitalization).toCharArray()
        for (run in runs) {
            val casing: TextCapitalization = run.capitalization ?: continue
            val from: Int = run.start.coerceIn(0, text.length)
            val to: Int = run.end.coerceIn(from, text.length)
            text.capitalized(casing).toCharArray().copyInto(drawn, from, from, to)
        }
        return drawn.concatToString()
    }

    companion object {
        val None: TextCasing = TextCasing(TextCapitalization.None, emptyList())
    }
}

/** Small capitals are the face's to draw, so they leave the letters as they were typed. */
private val TextCapitalization.keepsLetters: Boolean
    get() = this == TextCapitalization.None || this == TextCapitalization.SmallCaps

/** This box's [TextCasing]. */
internal val TextElement.casing: TextCasing
    get() = TextCasing(capitalization, runs.filter { it.capitalization != null })

/**
 * How much smaller a raised or dropped character draws than its size says:
 * about two thirds, the size word processors set an exponent or an index in.
 * Its line stays as tall as its size, so lifting a character never moves the
 * line under it.
 */
internal const val ShiftedBaselineScale: Float = 2f / 3f

/** The size a character [size] points tall draws at on [baseline]. */
internal fun drawnFontSize(size: Float, baseline: TextBaseline): Float =
    if (baseline == TextBaseline.Normal) size else size * ShiftedBaselineScale

/** How far [baseline] lifts a character, as Compose shifts it. */
internal val TextBaseline.shift: BaselineShift
    get() = when (this) {
        TextBaseline.Normal -> BaselineShift.None
        TextBaseline.Superscript -> BaselineShift.Superscript
        TextBaseline.Subscript -> BaselineShift.Subscript
    }

/**
 * The OpenType features text draws with, in CSS's syntax, which is what Compose
 * reads: `smcp` for [smallCaps], and the ligatures switched off where they
 * aren't [ligatures]. Null when neither asks for anything.
 *
 * A span's features replace its box's outright rather than adding to them, so a
 * span that sets small caps has to say the box's ligatures again too.
 */
internal fun fontFeatures(smallCaps: Boolean, ligatures: Boolean): String? = listOfNotNull(
    "smcp".takeIf { smallCaps },
    "liga 0, clig 0".takeIf { !ligatures },
).joinToString(", ").ifEmpty { null }

/**
 * Everything this box sets on its words over its [textStyle], in the element's
 * text offsets: one span per stretch where a styled run ([TextElement.runs]) or
 * a range link ([TextElement.links]) begins or ends, and none over words that
 * are just the box's.
 *
 * The one builder every draw site goes through (the slide, thumbnails, Magic
 * Move, the show and the caret's field), each cutting it to the runs it draws
 * with [DrawnRun.styled], so one box reads the same in all of them. [deckFonts]
 * is [LocalDeckFonts] at the call site.
 *
 * Last come the characters that ask for their emoji form, set in the emoji face
 * over whatever the box or a run set them in: later spans win where they
 * overlap.
 */
internal fun TextElement.spanStyles(deckFonts: DeckFontFamilies): List<AnnotatedString.Range<SpanStyle>> =
    styledSpans(deckFonts) + emojiSpans()

/** Each stretch [emojiPresentationRanges] finds, in the emoji face, where this machine has one. */
private fun TextElement.emojiSpans(): List<AnnotatedString.Range<SpanStyle>> {
    val emoji: FontFamily = emojiFontFamily() ?: return emptyList()
    return text.emojiPresentationRanges()
        .map { range -> AnnotatedString.Range(SpanStyle(fontFamily = emoji), range.first, range.last + 1) }
}

/** [spanStyles]' runs and range links. */
private fun TextElement.styledSpans(deckFonts: DeckFontFamilies): List<AnnotatedString.Range<SpanStyle>> {
    if (runs.isEmpty() && links.isEmpty()) return emptyList()

    val cuts: List<Int> = (runs.flatMap { listOf(it.start, it.end) } + links.flatMap { listOf(it.start, it.end) })
        .plus(listOf(0, text.length))
        .filter { it in 0..text.length }
        .distinct()
        .sorted()

    return cuts.zipWithNext().mapNotNull { (start, end) ->
        val run: TextRun? = runs.firstOrNull { start >= it.start && start < it.end }
        val linked: Boolean = linkAt(start) != null
        if (run == null && !linked) return@mapNotNull null
        AnnotatedString.Range(spanStyle(run ?: TextRun(), linked, deckFonts), start, end)
    }
}

/**
 * What [run] sets over the box's [textStyle], with a range link's dress on top
 * when [linked]: only the properties the run names, so the box shows through
 * the rest.
 *
 * A link underlines its words and takes the link colour only where they are
 * still in the colour a box starts in, the same rule [textStyle] follows for a
 * whole-box link. The decorations are one property between them, so a run that
 * names either, or a link, writes them whole: what the box and the run say
 * between them, the link's underline added.
 */
private fun TextElement.spanStyle(run: TextRun, linked: Boolean, deckFonts: DeckFontFamilies): SpanStyle {
    val drawn: TextRun = resolved(run)
    val underlined: Boolean = drawn.underline == true || linked || resolvedLink() != null
    val decorations: List<TextDecoration> = buildList {
        if (underlined) add(TextDecoration.Underline)
        if (drawn.strikethrough == true) add(TextDecoration.LineThrough)
    }
    val decorated: Boolean = linked || run.underline != null || run.strikethrough != null

    val ink: Long = drawn.color ?: color
    val recoloured: Boolean = linked || run.color != null
    val drawnInk: Long = if (linked && ink == DefaultTextColor) LinkColor else ink

    // Size and baseline are one property between them, since a raised
    // character draws smaller: a run that names either writes the size whole.
    val shifted: TextBaseline = drawn.baseline ?: baseline
    val sized: Boolean = run.fontSize != null || run.baseline != null

    // A run's features replace the box's (see fontFeatures), so one that names
    // its casing says whether small caps are on either way, and the box's
    // ligatures with it.
    val features: String? = run.capitalization?.let { casing ->
        val smallCaps: Boolean = casing == TextCapitalization.SmallCaps
        fontFeatures(smallCaps, ligatures) ?: "smcp 0"
    }

    return SpanStyle(
        color = if (recoloured) drawnInk.toComposeColor() else Color.Unspecified,
        fontSize =
            if (sized) drawnFontSize(drawn.fontSize ?: fontSize, shifted).sp
            else TextUnit.Unspecified,
        fontWeight = run.fontWeight?.let { FontWeight(it.coerceIn(1, 1000)) },
        fontStyle = run.italic?.let { if (it) FontStyle.Italic else FontStyle.Normal },
        fontFamily =
            if (run.setsFont) fontFamilyOf(drawn.fontName, drawn.fontFamily ?: fontFamily, deckFonts)
            else null,
        letterSpacing = run.letterSpacing?.sp ?: TextUnit.Unspecified,
        fontFeatureSettings = features,
        baselineShift = run.baseline?.shift,
        textDecoration = if (decorated) TextDecoration.combine(decorations) else null,
    )
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

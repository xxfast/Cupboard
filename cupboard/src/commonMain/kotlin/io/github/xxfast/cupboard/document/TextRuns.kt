package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable

/**
 * A stretch `[start, end)` of a text box's text set in a style of its own: the
 * one bold word, the one red one, the one in a different face. What lets one
 * box carry many styles the way Keynote's do.
 *
 * Every field is optional and null falls through to the box, so a run says only
 * what it changes and the box's own style goes on showing through the rest:
 * restyle the box and every run that never mentioned size follows it.
 *
 * The face is one property in two fields, the way the box's is: set either of
 * [fontFamily] and [fontName] and the run owns its face outright, its [fontName]
 * winning and its [fontFamily] (else the box's) what that falls back to. So a
 * run picked into a generic family draws in it even inside a box set by name.
 *
 * A run that has no range of its own, `(0, 0)`, is how a style change is handed
 * around ([styled], [withBoxStyle], `EditorEvent.StyleTextRange`): only its
 * fields are read.
 *
 * Paragraph things (alignment, line spacing, lists) are the box's alone, since
 * they belong to a whole line rather than to the characters on it. So are
 * ligatures, which only a whole box is worth switching off.
 */
@Serializable
data class TextRun(
    override val start: Int = 0,
    override val end: Int = 0,
    val fontFamily: TextFont? = null,
    val fontName: String? = null,
    val fontSize: Float? = null,
    val fontWeight: Int? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strikethrough: Boolean? = null,
    val color: Long? = null,
    val letterSpacing: Float? = null,
    val capitalization: TextCapitalization? = null,
    val baseline: TextBaseline? = null,
) : TextSpan<TextRun> {
    override fun over(start: Int, end: Int): TextRun = copy(start = start, end = end)

    override fun carriesSameAs(other: TextRun): Boolean = other.over(start, end) == this

    /** Whether this sets nothing at all, so the box shows through every character of it. */
    val isPlain: Boolean get() = carriesSameAs(TextRun())

    /** Whether this says which face to draw in; see the class's note on the face. */
    val setsFont: Boolean get() = fontFamily != null || fontName != null
}

/**
 * [runs] as every writer leaves them for a text [length] long: `normalisedLinks`'
 * rules (clamped, sorted, never overlapping, touching neighbours that set the
 * same style merged), and a run that sets nothing dropped, since the box already
 * draws it that way.
 */
fun normalisedRuns(runs: List<TextRun>, length: Int): List<TextRun> =
    normalisedSpans(runs.filterNot { it.isPlain }, length)

/**
 * [runs], measured against [oldText], moved onto [newText]: `shiftedLinks`' rule,
 * bar what the edit put in. That goes to the run that held the first character
 * it replaced, or for a bare insertion the run the caret sat at the end of, so
 * typing on after a bold word goes on in bold, and typing over a bold word
 * types bold.
 */
fun shiftedRuns(oldText: String, newText: String, runs: List<TextRun>): List<TextRun> =
    shiftedSpans(oldText, newText, runs, carriesTyping = true)

/**
 * This box with [style]'s fields laid over `[start, end)` of its text: the runs
 * there split at both ends, each piece inside taking whatever [style] sets and
 * keeping what it doesn't, and the gaps between them becoming runs of [style]
 * alone. Only [style]'s fields are read, never its range.
 *
 * The face goes over as one, so a piece that was set by name and is now picked
 * into a generic family loses the name with it.
 */
fun TextElement.styled(start: Int, end: Int, style: TextRun): TextElement {
    val from: Int = minOf(start, end).coerceIn(0, text.length)
    val to: Int = maxOf(start, end).coerceIn(0, text.length)
    if (from == to || style.isPlain) return this

    val inside: List<TextRun> = runsOver(from, to).map { it.overlaid(style) }
    val styled: List<TextRun> = normalisedRuns(runs.outside(from, to) + inside, text.length)
    return if (styled == runs) this else copy(runs = styled)
}

/**
 * This box wearing [style]'s fields as its own, all the way through: the box
 * takes them, and every run lets go of them so none of it differs any more. What
 * the Text inspector does with no range selected. Only [style]'s fields are read.
 *
 * The face lands the way a font pick lands on a box: a name keeps the box's
 * [TextFont] as its fallback, and a [TextFont] alone clears the name.
 */
fun TextElement.withBoxStyle(style: TextRun): TextElement {
    if (style.isPlain) return this

    val font: Boolean = style.setsFont
    return copy(
        fontFamily = style.fontFamily ?: fontFamily,
        fontName = if (font) style.fontName else fontName,
        fontSize = style.fontSize ?: fontSize,
        fontWeight = style.fontWeight ?: fontWeight,
        italic = style.italic ?: italic,
        underline = style.underline ?: underline,
        strikethrough = style.strikethrough ?: strikethrough,
        color = style.color ?: color,
        letterSpacing = style.letterSpacing ?: letterSpacing,
        capitalization = style.capitalization ?: capitalization,
        baseline = style.baseline ?: baseline,
        runs = normalisedRuns(runs.map { it.without(style) }, text.length),
    )
}

/**
 * `[from, to)` of this box's text cut wherever its style changes, in order and
 * covering every character: each piece is the run it lies in, or a plain run
 * where it lies in none. What a renderer or an exporter walks to set the words.
 */
fun TextElement.runsOver(from: Int, to: Int): List<TextRun> = buildList {
    var at: Int = from
    for (run in runs) {
        val start: Int = maxOf(run.start, from, at)
        val end: Int = minOf(run.end, to)
        if (start >= end) continue

        if (start > at) add(TextRun(at, start))
        add(run.over(start, end))
        at = end
    }
    if (at < to) add(TextRun(at, to))
}

/**
 * [run] with everything it leaves unset filled in from this box: what its
 * characters actually draw in. Every field comes back set bar [TextRun.fontName],
 * which is null for a face picked by [TextFont] alone.
 */
fun TextElement.resolved(run: TextRun): TextRun = run.copy(
    fontFamily = run.fontFamily ?: fontFamily,
    fontName = if (run.setsFont) run.fontName else fontName,
    fontSize = run.fontSize ?: fontSize,
    fontWeight = run.fontWeight ?: fontWeight,
    italic = run.italic ?: italic,
    underline = run.underline ?: underline,
    strikethrough = run.strikethrough ?: strikethrough,
    color = run.color ?: color,
    letterSpacing = run.letterSpacing ?: letterSpacing,
    capitalization = run.capitalization ?: capitalization,
    baseline = run.baseline ?: baseline,
)

/**
 * The largest size anywhere in the box, its own or a run's. What its line
 * spacing is measured against, so a run set bigger than the box gets lines tall
 * enough to hold it rather than lines that run into each other.
 */
val TextElement.largestFontSize: Float
    get() = runs.maxOfOrNull { it.fontSize ?: fontSize }?.coerceAtLeast(fontSize) ?: fontSize

/**
 * What `[from, to)` of a text box is set in, as an inspector shows it: each
 * property's one value across the stretch, or null where its characters
 * disagree, which an inspector reads as Multiple.
 *
 * Nullable fields rather than a wrapper per property, so the shells across the
 * ObjC and .NET boundaries read it field by field like the rest of the state.
 * [fontName] is the one field null also means something set for: a face picked
 * by [TextFont] alone. [fontDisplayName] is the one to ask whether the face is
 * mixed.
 */
data class TextRangeStyle(
    /** What a font picker shows, null for Multiple. */
    val fontDisplayName: String?,
    /** The [TextFont] the face falls back to, null for Multiple. */
    val fontFamily: TextFont?,
    /** The face asked for by name: null for Multiple, and for a [TextFont] alone. */
    val fontName: String?,
    val fontWeight: Int?,
    /** Whether every character reads as bold, null when some do and some don't. */
    val isBold: Boolean?,
    val fontSize: Float?,
    val italic: Boolean?,
    val underline: Boolean?,
    val strikethrough: Boolean?,
    /** Packed ARGB, the document model's colour format. */
    val color: Long?,
    val letterSpacing: Float?,
    val capitalization: TextCapitalization?,
    val baseline: TextBaseline?,
)

/**
 * What `[from, to)` of this box is set in, property by property (see
 * [TextRangeStyle]). An empty stretch, an empty box's whole text included, reads
 * as the box's own style.
 */
fun TextElement.styleOf(from: Int, to: Int): TextRangeStyle {
    val start: Int = minOf(from, to).coerceIn(0, text.length)
    val end: Int = maxOf(from, to).coerceIn(0, text.length)
    val pieces: List<TextRun> =
        if (start == end) listOf(resolved(TextRun()))
        else runsOver(start, end).map { resolved(it) }

    fun <T> one(value: (TextRun) -> T): T? = pieces.map(value).distinct().singleOrNull()

    val face: TextRun? = pieces
        .map { TextRun(fontFamily = it.fontFamily, fontName = it.fontName) }
        .distinct()
        .singleOrNull()

    return TextRangeStyle(
        fontDisplayName = face?.let { it.fontName ?: it.fontFamily?.displayName },
        fontFamily = face?.fontFamily,
        fontName = face?.fontName,
        fontWeight = one { it.fontWeight },
        isBold = one { (it.fontWeight ?: RegularWeight) >= BoldThreshold },
        fontSize = one { it.fontSize },
        italic = one { it.italic },
        underline = one { it.underline },
        strikethrough = one { it.strikethrough },
        color = one { it.color },
        letterSpacing = one { it.letterSpacing },
        capitalization = one { it.capitalization },
        baseline = one { it.baseline },
    )
}

/**
 * The Bold button's change over text set in this: bold all the way through, or
 * back to regular when every character already reads as bold. Mixed turns it
 * on, the way Keynote's button does.
 */
fun TextRangeStyle.boldToggled(): TextRun =
    TextRun(fontWeight = if (isBold == true) RegularWeight else BoldWeight)

/** [boldToggled]'s rule, for italic. */
fun TextRangeStyle.italicToggled(): TextRun = TextRun(italic = italic != true)

/** [boldToggled]'s rule, for the underline. */
fun TextRangeStyle.underlineToggled(): TextRun = TextRun(underline = underline != true)

/** [boldToggled]'s rule, for the strike. */
fun TextRangeStyle.strikethroughToggled(): TextRun = TextRun(strikethrough = strikethrough != true)

/** This run with whatever [style] sets put over its own; the face goes over as one. */
private fun TextRun.overlaid(style: TextRun): TextRun = copy(
    fontFamily = if (style.setsFont) style.fontFamily else fontFamily,
    fontName = if (style.setsFont) style.fontName else fontName,
    fontSize = style.fontSize ?: fontSize,
    fontWeight = style.fontWeight ?: fontWeight,
    italic = style.italic ?: italic,
    underline = style.underline ?: underline,
    strikethrough = style.strikethrough ?: strikethrough,
    color = style.color ?: color,
    letterSpacing = style.letterSpacing ?: letterSpacing,
    capitalization = style.capitalization ?: capitalization,
    baseline = style.baseline ?: baseline,
)

/** This run without whatever [style] sets, so the box shows through there; the face goes as one. */
private fun TextRun.without(style: TextRun): TextRun = copy(
    fontFamily = if (style.setsFont) null else fontFamily,
    fontName = if (style.setsFont) null else fontName,
    fontSize = if (style.fontSize != null) null else fontSize,
    fontWeight = if (style.fontWeight != null) null else fontWeight,
    italic = if (style.italic != null) null else italic,
    underline = if (style.underline != null) null else underline,
    strikethrough = if (style.strikethrough != null) null else strikethrough,
    color = if (style.color != null) null else color,
    letterSpacing = if (style.letterSpacing != null) null else letterSpacing,
    capitalization = if (style.capitalization != null) null else capitalization,
    baseline = if (style.baseline != null) null else baseline,
)

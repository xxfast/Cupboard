package io.github.xxfast.cupboard.document

import kotlin.math.round

/** The weight a box comes back to when bold is switched off. */
const val RegularWeight: Int = 400

/** The weight the bold toggle reaches for. */
const val BoldWeight: Int = 700

/**
 * Anything this heavy reads as bold, so a 600 imported from elsewhere toggles
 * off rather than being bolded a second time.
 */
internal const val BoldThreshold: Int = 600

/** One tab per nesting level, and 24 doc units of indent per tab when drawn. */
private const val Tab: Char = '\t'

/**
 * The bullet a level draws, alternating so a nested list reads as nested even
 * where the indent is hard to see. Levels past the second start over.
 */
private val BulletGlyphs: List<String> = listOf("•", "◦")

/** [ListStyle.Dash]'s marker, an en dash. */
private const val DashGlyph: String = "–"

private val RomanNumerals: List<Pair<Int, String>> = listOf(
    1000 to "m", 900 to "cm", 500 to "d", 400 to "cd",
    100 to "c", 90 to "xc", 50 to "l", 40 to "xl",
    10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i",
)

val TextElement.isBold: Boolean get() = fontWeight >= BoldThreshold

/**
 * The whole box bold, or back to regular when every character already reads as
 * bold: [boldToggled] over its whole text. The runs let go of their weights
 * either way (see [withBoxStyle]), so the box ends up one weight.
 */
fun TextElement.toggleBold(): TextElement = withBoxStyle(wholeStyle.boldToggled())

/**
 * What the box is set in, as a font picker names it: [TextElement.fontName] when
 * the box asks for a face by name, its [TextFont.displayName] otherwise.
 */
val TextElement.fontDisplayName: String get() = fontName ?: fontFamily.displayName

/**
 * Set in the family [name], as a font picker's named pick sets it: the name wins
 * from here on and [TextElement.fontFamily] stays what it falls back to. Null or
 * blank clears the name, so the box is back to its [TextFont] alone. Either
 * way it is the whole box's face: the runs let go of theirs.
 */
fun TextElement.withFont(name: String?): TextElement {
    val picked: String = name?.trim()?.takeIf { it.isNotEmpty() } ?: return withGenericFont(fontFamily)
    return withBoxStyle(TextRun(fontName = picked))
}

/**
 * Set in [font] alone: the pick of one of [TextFont]'s entries, the three generic
 * families above all. The name goes, since a box that kept one would go on
 * drawing in it whatever was picked here, and so do the runs' faces.
 */
fun TextElement.withGenericFont(font: TextFont): TextElement = withBoxStyle(TextRun(fontFamily = font))

/** [toggleBold]'s rule, for italic. */
fun TextElement.toggleItalic(): TextElement = withBoxStyle(wholeStyle.italicToggled())

/** [toggleBold]'s rule, for the underline. */
fun TextElement.toggleUnderline(): TextElement = withBoxStyle(wholeStyle.underlineToggled())

/** [toggleBold]'s rule, for the strike. */
fun TextElement.toggleStrikethrough(): TextElement = withBoxStyle(wholeStyle.strikethroughToggled())

/** What the box's whole text is set in, runs and all. */
private val TextElement.wholeStyle: TextRangeStyle get() = styleOf(0, text.length)

/**
 * [transform] applied to every text box in the selection that can take it, as
 * the elements that actually changed and nothing else.
 *
 * Only unlocked top-level text elements: a lock means what it says, and a group
 * is styled by opening it rather than through its box. Handing the result
 * straight to `EditorViewModel.onUpdateElements` is the point, so an empty list
 * back means there is nothing to commit and no history entry to make.
 */
fun List<Element>.formatText(transform: (TextElement) -> TextElement): List<Element> =
    mapNotNull { element ->
        if (element !is TextElement || element.locked) return@mapNotNull null
        val formatted: TextElement = transform(element)
        return@mapNotNull if (formatted == element) null else formatted
    }

/**
 * [transform] applied to every code block in the selection, the way [formatText]
 * does its text boxes: unlocked and top-level only, and only the ones that
 * actually changed.
 *
 * Separate from [formatText] rather than generic over the element type, because
 * both shells dispatch inspector edits through these two by name and a mixed
 * selection has to leave the other kind alone.
 */
fun List<Element>.formatCode(transform: (CodeElement) -> CodeElement): List<Element> =
    mapNotNull { element ->
        if (element !is CodeElement || element.locked) return@mapNotNull null
        val formatted: CodeElement = transform(element)
        return@mapNotNull if (formatted == element) null else formatted
    }

/** How deep this line sits: one leading tab per level, the way [indentLine] writes it. */
fun String.listIndentLevel(): Int = takeWhile { it == Tab }.length

/** This line without the tabs that carry its nesting: what actually draws. */
fun String.listBody(): String = trimStart(Tab)

/** The line a caret at [offset] sits on. Clamped, so an offset past the end is the last line. */
fun String.lineIndexOf(offset: Int): Int =
    substring(0, offset.coerceIn(0, length)).count { it == '\n' }

/**
 * One level deeper for the line at [lineIndex]. The same string back when there
 * is no such line.
 */
fun String.indentLine(lineIndex: Int): String = editLine(lineIndex) { "$Tab$it" }

/** One level back out. Already at the margin, and nothing happens. */
fun String.outdentLine(lineIndex: Int): String = editLine(lineIndex) { it.removePrefix("$Tab") }

/**
 * Every line of the box one level deeper: the Text inspector's indent button.
 * A line at a time through `withText`, so each tab is one edit its runs and
 * links are carried across, and none of them is smeared over the lines between.
 */
fun TextElement.indented(): TextElement =
    text.split("\n").indices.fold(this) { box, line -> box.withText(box.text.indentLine(line)) }

/** [indented]'s way back out; a line already at the margin stays there. */
fun TextElement.outdented(): TextElement =
    text.split("\n").indices.fold(this) { box, line -> box.withText(box.text.outdentLine(line)) }

/** Whether any line is nested, so [outdented] has something to do. */
val TextElement.canOutdent: Boolean get() = text.split("\n").any { it.listIndentLevel() > 0 }

/** What a Bullets & Lists popup calls each [ListStyle], in its order. */
val ListStyle.title: String
    get() = when (this) {
        ListStyle.None -> "None"
        ListStyle.Bullet -> "Bullet"
        ListStyle.Dash -> "Dash"
        ListStyle.Numbered -> "Numbered"
        ListStyle.NumberedParenthesis -> "Numbered 1)"
        ListStyle.UpperRoman -> "Roman I."
        ListStyle.LowerAlpha -> "Lettered a."
    }

/** Every [ListStyle] in the order a popup lists them: the marks, then the counts. */
val ListStyles: List<ListStyle> = listOf(
    ListStyle.None,
    ListStyle.Bullet,
    ListStyle.Dash,
    ListStyle.Numbered,
    ListStyle.NumberedParenthesis,
    ListStyle.UpperRoman,
    ListStyle.LowerAlpha,
)

/**
 * This text as [capitalization] draws it, a character for a character, so every
 * offset into it (a run, a link, the caret) still points at the same letter.
 * That is also why a letter whose capital is two (`ß`) keeps its one.
 *
 * [TextCapitalization.SmallCaps] comes back as it is: small capitals are the
 * face's to draw, not the text's to become.
 */
fun String.capitalized(capitalization: TextCapitalization): String = when (capitalization) {
    TextCapitalization.None, TextCapitalization.SmallCaps -> this
    TextCapitalization.AllCaps -> map { it.uppercaseChar() }.joinToString("")
    TextCapitalization.Title -> mapIndexed { index, char ->
        val starts: Boolean = index == 0 || !this[index - 1].isLetterOrDigit() && this[index - 1] != '\''
        if (starts) char.titlecaseChar() else char
    }.joinToString("")
}

/** What the Spacing popup calls each [LineSpacingMode], in its order. */
val LineSpacingMode.title: String
    get() = when (this) {
        LineSpacingMode.Lines -> "Lines"
        LineSpacingMode.AtLeast -> "At Least"
        LineSpacingMode.Exactly -> "Exactly"
        LineSpacingMode.Between -> "Between"
    }

/**
 * How tall, in points, a line of this box draws when its largest character is
 * [size] points: [TextElement.lineHeight] read by its [LineSpacingMode]. One
 * line one font size tall is what this app has always called single spacing,
 * so [LineSpacingMode.AtLeast] floors there and [LineSpacingMode.Between] adds
 * to it.
 */
fun TextElement.lineHeightPoints(size: Float = largestFontSize): Float = when (lineSpacingMode) {
    LineSpacingMode.Lines -> size * lineHeight
    LineSpacingMode.Exactly -> lineHeight
    LineSpacingMode.AtLeast -> maxOf(lineHeight, size)
    LineSpacingMode.Between -> size + lineHeight
}

/**
 * Spaced by [mode] from here on, its [TextElement.lineHeight] worked over so the
 * lines draw as tall as they did: switching the popup moves nothing until a
 * value is typed. Lines keep two decimals, points one.
 */
fun TextElement.withLineSpacingMode(mode: LineSpacingMode): TextElement {
    if (mode == lineSpacingMode) return this

    val size: Float = largestFontSize
    val drawn: Float = lineHeightPoints(size)
    val value: Float = when (mode) {
        LineSpacingMode.Lines -> round(drawn / size * 100f) / 100f
        LineSpacingMode.Exactly, LineSpacingMode.AtLeast -> round(drawn * 10f) / 10f
        LineSpacingMode.Between -> round((drawn - size).coerceAtLeast(0f) * 10f) / 10f
    }
    return copy(lineSpacingMode = mode, lineHeight = value)
}

/**
 * The marker each line of [text] draws in front of it, one entry per line and
 * "" for a line that gets none: a blank one, or any line at all under
 * [ListStyle.None].
 *
 * The renderer and the editor's text field both prefix with these, so what sits
 * under the caret is what was drawn before it, to the character.
 *
 * Numbering restarts per level. [ListStyle.Numbered] cycles 1. / a. / i. as it
 * nests, so a sub-item is told apart from its parent by its marker as well as
 * its indent; the other counted styles keep their one marker at every level.
 * A blank line is skipped rather than counted, and leaves the numbering alone.
 */
fun listMarkers(text: String, style: ListStyle): List<String> {
    val lines: List<String> = text.split("\n")
    if (style == ListStyle.None) return lines.map { "" }

    // One running count per level, dropped as soon as the list comes back out
    // of that level: coming back in starts the sub-list over.
    val counts: MutableList<Int> = mutableListOf()
    return lines.map { line ->
        if (line.listBody().isBlank()) return@map ""

        val level: Int = line.listIndentLevel()
        if (style == ListStyle.Bullet) return@map BulletGlyphs[level % BulletGlyphs.size]
        if (style == ListStyle.Dash) return@map DashGlyph

        while (counts.size <= level) counts.add(0)
        while (counts.size > level + 1) counts.removeAt(counts.size - 1)
        counts[level] = counts[level] + 1
        val count: Int = counts[level]
        return@map when (style) {
            ListStyle.NumberedParenthesis -> "$count)"
            ListStyle.UpperRoman -> "${count.toRoman().uppercase()}."
            ListStyle.LowerAlpha -> "${count.toAlphabetic()}."
            else -> when (level % 3) {
                0 -> "$count."
                1 -> "${count.toAlphabetic()}."
                else -> "${count.toRoman()}."
            }
        }
    }
}

private fun String.editLine(lineIndex: Int, edit: (String) -> String): String {
    val lines: List<String> = split("\n")
    if (lineIndex !in lines.indices) return this

    val edited: String = edit(lines[lineIndex])
    if (edited == lines[lineIndex]) return this

    return lines.mapIndexed { index, line -> if (index == lineIndex) edited else line }
        .joinToString("\n")
}

/** 1 is a, 26 is z, 27 is aa: spreadsheet columns, which is what a. b. c. wants past z. */
private fun Int.toAlphabetic(): String {
    var remaining: Int = this
    val letters = StringBuilder()
    while (remaining > 0) {
        letters.append('a' + (remaining - 1) % 26)
        remaining = (remaining - 1) / 26
    }
    return letters.reverse().toString()
}

private fun Int.toRoman(): String {
    var remaining: Int = this
    val numeral = StringBuilder()
    for ((value, symbol) in RomanNumerals) {
        while (remaining >= value) {
            numeral.append(symbol)
            remaining -= value
        }
    }
    return numeral.toString()
}

package io.github.xxfast.cupboard.document

/** The weight a box comes back to when bold is switched off. */
const val RegularWeight: Int = 400

/** The weight the bold toggle reaches for. */
const val BoldWeight: Int = 700

/**
 * Anything this heavy reads as bold, so a 600 imported from elsewhere toggles
 * off rather than being bolded a second time.
 */
private const val BoldThreshold: Int = 600

/** One tab per nesting level, and 24 doc units of indent per tab when drawn. */
private const val Tab: Char = '\t'

/**
 * The bullet a level draws, alternating so a nested list reads as nested even
 * where the indent is hard to see. Levels past the second start over.
 */
private val BulletGlyphs: List<String> = listOf("•", "◦")

private val RomanNumerals: List<Pair<Int, String>> = listOf(
    1000 to "m", 900 to "cm", 500 to "d", 400 to "cd",
    100 to "c", 90 to "xc", 50 to "l", 40 to "xl",
    10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i",
)

val TextElement.isBold: Boolean get() = fontWeight >= BoldThreshold

fun TextElement.toggleBold(): TextElement =
    copy(fontWeight = if (isBold) RegularWeight else BoldWeight)

fun TextElement.toggleItalic(): TextElement = copy(italic = !italic)

fun TextElement.toggleUnderline(): TextElement = copy(underline = !underline)

fun TextElement.toggleStrikethrough(): TextElement = copy(strikethrough = !strikethrough)

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
 * The marker each line of [text] draws in front of it, one entry per line and
 * "" for a line that gets none: a blank one, or any line at all under
 * [ListStyle.None].
 *
 * The renderer and the editor's text field both prefix with these, so what sits
 * under the caret is what was drawn before it, to the character.
 *
 * Numbering restarts per level and cycles 1. / a. / i. as it nests, so a
 * sub-item is told apart from its parent by its marker as well as its indent.
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

        while (counts.size <= level) counts.add(0)
        while (counts.size > level + 1) counts.removeAt(counts.size - 1)
        counts[level] = counts[level] + 1
        return@map when (level % 3) {
            0 -> "${counts[level]}."
            1 -> "${counts[level].toAlphabetic()}."
            else -> "${counts[level].toRoman()}."
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

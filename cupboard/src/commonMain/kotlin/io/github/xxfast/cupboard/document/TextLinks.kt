package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable

/**
 * A run of a text box's words that points somewhere: the half-open range
 * `[start, end)` of [TextElement.text], counted in chars, and where clicking it
 * takes the show.
 *
 * Offsets into the stored text, nesting tabs included, rather than into what is
 * drawn: a list's markers are worked out at draw time and never stored, so the
 * text is the one thing both ends of the range can be held still against.
 */
@Serializable
data class TextLink(val start: Int, val end: Int, val target: LinkTarget)

/**
 * The range link covering the character at [offset], null when none does.
 *
 * A character, not a caret position: `start` is inside the link and `end` is the
 * first character past it. A caret asks the same question of the character it
 * sits in front of.
 */
fun TextElement.linkAt(offset: Int): TextLink? = links.firstOrNull { offset >= it.start && offset < it.end }

/**
 * This box with `[start, end)` pointing at [target], or pointing nowhere when
 * [target] is null.
 *
 * The new range wins wherever it lands: a link it overlaps keeps only the part
 * outside it, so one that straddles it either end is trimmed and one that holds
 * it whole is split in two around it. Null clears the range the same way and
 * puts nothing in its place.
 */
fun TextElement.withLink(start: Int, end: Int, target: LinkTarget?): TextElement {
    val from: Int = minOf(start, end).coerceIn(0, text.length)
    val to: Int = maxOf(start, end).coerceIn(0, text.length)
    if (from == to) return copy(links = normalisedLinks(links, text.length))

    val kept: List<TextLink> = links.flatMap { link ->
        listOf(
            link.copy(end = minOf(link.end, from)),
            link.copy(start = maxOf(link.start, to)),
        )
    }
    val added: List<TextLink> = listOfNotNull(target?.let { TextLink(from, to, it) })
    return copy(links = normalisedLinks(kept + added, text.length))
}

/**
 * This box with its text replaced by [text], its range links carried over by
 * [shiftedLinks]. What every edit that rewrites a box's words goes through, so
 * a link stays on the words it was put on.
 */
fun TextElement.withText(text: String): TextElement =
    if (text == this.text) this else copy(text = text, links = shiftedLinks(this.text, text, links))

/**
 * [links], measured against [oldText], moved onto [newText].
 *
 * One edit is assumed, the one the two texts disagree on once their common start
 * and common end are set aside. A link wholly before it stays where it is and
 * one wholly after it moves by however much longer or shorter the text got. One
 * holding it grows or shrinks with it, one reaching into it is cut back to where
 * it starts, and one whose every character went is gone.
 *
 * Typing hard against a link's end or its start is outside it: the words a link
 * was put on are what it covers, and the next word typed after them is the next
 * word, not more of the link.
 */
fun shiftedLinks(oldText: String, newText: String, links: List<TextLink>): List<TextLink> {
    if (links.isEmpty() || oldText == newText) return normalisedLinks(links, newText.length)

    val edit = TextEdit(oldText, newText)
    val shifted: List<TextLink> = links.mapNotNull { link ->
        edit.shift(link.start, link.end)?.let { (start, end) -> link.copy(start = start, end = end) }
    }
    return normalisedLinks(shifted, newText.length)
}

/**
 * `[start, end)` of [oldText] moved onto [newText] by [shiftedLinks]' rule, as
 * the new start and end, or null when every character of it went. An empty range
 * (a caret) comes back null when the edit swallowed the spot it marked.
 *
 * What the editor carries a selection across an edit with, so a selection and
 * the link it is about to become follow the text the same way.
 */
internal fun shiftedRange(oldText: String, newText: String, start: Int, end: Int): Pair<Int, Int>? =
    if (oldText == newText) start to end
    else TextEdit(oldText, newText).shift(start, end)

/**
 * The one edit that turns [oldText] into [newText], found by setting aside the
 * start and the end the two share: `[prefix, oldEnd)` of the old text became
 * `[prefix, newEnd)` of the new.
 */
private class TextEdit(oldText: String, newText: String) {
    val prefix: Int
    val oldEnd: Int
    val newEnd: Int
    val delta: Int = newText.length - oldText.length

    init {
        val shortest: Int = minOf(oldText.length, newText.length)
        var shared = 0
        while (shared < shortest && oldText[shared] == newText[shared]) shared++
        var suffix = 0
        while (
            suffix < shortest - shared &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) suffix++

        prefix = shared
        oldEnd = oldText.length - suffix
        newEnd = newText.length - suffix
    }

    fun shift(start: Int, end: Int): Pair<Int, Int>? = when {
        end <= prefix -> start to end
        start >= oldEnd -> start + delta to end + delta
        else -> {
            val survivors: Int = maxOf(0, prefix - start) + maxOf(0, end - oldEnd)
            if (survivors == 0) null
            else (if (start <= prefix) start else newEnd) to (if (end >= oldEnd) end + delta else prefix)
        }
    }
}

/**
 * [links] as every writer leaves them for a text [length] long: clamped into the
 * text, empty ones dropped, sorted by start, never overlapping, and neighbours
 * that touch and point at the same place merged into one.
 *
 * An overlap can only come in from outside (a hand-written or damaged file), and
 * the earlier link keeps the characters both claim.
 */
fun normalisedLinks(links: List<TextLink>, length: Int): List<TextLink> {
    if (links.isEmpty()) return links

    val clamped: List<TextLink> = links
        .map { it.copy(start = it.start.coerceIn(0, length), end = it.end.coerceIn(0, length)) }
        .filter { it.start < it.end }
        .sortedBy { it.start }

    val normalised: MutableList<TextLink> = mutableListOf()
    for (link in clamped) {
        val previous: TextLink? = normalised.lastOrNull()
        val start: Int = maxOf(link.start, previous?.end ?: 0)
        if (start >= link.end) continue

        if (previous != null && previous.end == start && previous.target == link.target) {
            normalised[normalised.lastIndex] = previous.copy(end = link.end)
        } else normalised += link.copy(start = start)
    }
    return normalised
}

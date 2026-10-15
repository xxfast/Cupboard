package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable

/**
 * A stretch `[start, end)` of a text box's text, counted in chars, that carries
 * something on it: a [TextLink]'s target or a [TextRun]'s style.
 *
 * What the range arithmetic below is written against, so a link and a run follow
 * an edit, get cut around a new one and settle into order by the one set of
 * rules rather than by two copies of them.
 *
 * Offsets into the stored text, nesting tabs included, rather than into what is
 * drawn: a list's markers are worked out at draw time and never stored, so the
 * text is the one thing both ends of the range can be held still against.
 */
interface TextSpan<S : TextSpan<S>> {
    val start: Int
    val end: Int

    /** This same span, moved to `[start, end)`. */
    fun over(start: Int, end: Int): S

    /** Whether this carries what [other] does, so the two touching read as one. */
    fun carriesSameAs(other: S): Boolean
}

/**
 * A run of a text box's words that points somewhere: the half-open range
 * `[start, end)` of [TextElement.text], and where clicking it takes the show.
 */
@Serializable
data class TextLink(override val start: Int, override val end: Int, val target: LinkTarget) : TextSpan<TextLink> {
    override fun over(start: Int, end: Int): TextLink = copy(start = start, end = end)

    override fun carriesSameAs(other: TextLink): Boolean = other.target == target
}

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

    val added: List<TextLink> = listOfNotNull(target?.let { TextLink(from, to, it) })
    return copy(links = normalisedLinks(links.outside(from, to) + added, text.length))
}

/**
 * This box with its text replaced by [text], its range links and its styled runs
 * carried over by [shiftedLinks] and [shiftedRuns]. What every edit that
 * rewrites a box's words goes through, so a link and a style stay on the words
 * they were put on.
 */
fun TextElement.withText(text: String): TextElement =
    if (text == this.text) this
    else copy(
        text = text,
        links = shiftedLinks(this.text, text, links),
        runs = shiftedRuns(this.text, text, runs),
    )

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
 * word, not more of the link. A styled run is the other way round; see
 * [shiftedRuns].
 */
fun shiftedLinks(oldText: String, newText: String, links: List<TextLink>): List<TextLink> =
    shiftedSpans(oldText, newText, links, carriesTyping = false)

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
    else TextEdit(oldText, newText).shift(start, end, owns = false)

/**
 * [links] as every writer leaves them for a text [length] long: clamped into the
 * text, empty ones dropped, sorted by start, never overlapping, and neighbours
 * that touch and point at the same place merged into one.
 *
 * An overlap can only come in from outside (a hand-written or damaged file), and
 * the earlier link keeps the characters both claim.
 */
fun normalisedLinks(links: List<TextLink>, length: Int): List<TextLink> = normalisedSpans(links, length)

/**
 * [spans], measured against [oldText], moved onto [newText] by [shiftedLinks]'
 * rule. With [carriesTyping], whatever the edit put in goes to the span that
 * held the first character it replaced, or for a bare insertion to the one the
 * caret sat at the end of, so the new words wear what was there.
 */
internal fun <S : TextSpan<S>> shiftedSpans(
    oldText: String,
    newText: String,
    spans: List<S>,
    carriesTyping: Boolean,
): List<S> {
    if (spans.isEmpty() || oldText == newText) return normalisedSpans(spans, newText.length)

    val edit = TextEdit(oldText, newText)
    val owner: Int? = if (carriesTyping) edit.owner else null
    val shifted: List<S> = spans.mapNotNull { span ->
        val owns: Boolean = owner != null && owner >= span.start && owner < span.end
        edit.shift(span.start, span.end, owns)?.let { (start, end) -> span.over(start, end) }
    }
    return normalisedSpans(shifted, newText.length)
}

/**
 * [spans] as every writer leaves them for a text [length] long: [normalisedLinks]'
 * rules, for links and runs alike.
 */
internal fun <S : TextSpan<S>> normalisedSpans(spans: List<S>, length: Int): List<S> {
    if (spans.isEmpty()) return spans

    val clamped: List<S> = spans
        .map { it.over(it.start.coerceIn(0, length), it.end.coerceIn(0, length)) }
        .filter { it.start < it.end }
        .sortedBy { it.start }

    val normalised: MutableList<S> = mutableListOf()
    for (span in clamped) {
        val previous: S? = normalised.lastOrNull()
        val start: Int = maxOf(span.start, previous?.end ?: 0)
        if (start >= span.end) continue

        if (previous != null && previous.end == start && previous.carriesSameAs(span)) {
            normalised[normalised.lastIndex] = previous.over(previous.start, span.end)
        } else normalised += span.over(start, span.end)
    }
    return normalised
}

/**
 * What is left of these spans outside `[from, to)`: one that straddles either
 * end is trimmed to its side, and one that holds the range whole comes back as
 * its two halves. Some may come back empty; [normalisedSpans] drops them.
 */
internal fun <S : TextSpan<S>> List<S>.outside(from: Int, to: Int): List<S> = flatMap { span ->
    listOf(
        span.over(span.start, minOf(span.end, from)),
        span.over(maxOf(span.start, to), span.end),
    )
}

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

    /**
     * The old character whose span takes what this edit put in: the first one
     * it replaced, or the one just before a bare insertion. Null when nothing
     * went in, or when a bare insertion is at the very start with nothing
     * before it.
     */
    val owner: Int?
        get() = when {
            newEnd == prefix -> null
            oldEnd > prefix -> prefix
            prefix > 0 -> prefix - 1
            else -> null
        }

    /**
     * `[start, end)` across this edit. A span that [owns] the edit holds
     * everything the edit put in, whatever it held of what went.
     */
    fun shift(start: Int, end: Int, owns: Boolean): Pair<Int, Int>? = when {
        owns -> start to (if (end >= oldEnd) end + delta else newEnd)
        end <= prefix -> start to end
        start >= oldEnd -> start + delta to end + delta
        else -> {
            val survivors: Int = maxOf(0, prefix - start) + maxOf(0, end - oldEnd)
            if (survivors == 0) null
            else (if (start <= prefix) start else newEnd) to (if (end >= oldEnd) end + delta else prefix)
        }
    }
}

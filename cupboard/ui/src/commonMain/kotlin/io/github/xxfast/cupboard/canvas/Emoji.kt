package io.github.xxfast.cupboard.canvas

/** `U+FE0F`, the variation selector that asks for a character's emoji form. */
private const val EmojiPresentation: Int = 0xFE0F

/** `U+FE0E`, its opposite, which asks for the text form. */
private const val TextPresentation: Int = 0xFE0E

private const val ZeroWidthJoiner: Int = 0x200D

/**
 * Every stretch of this text that asks for its emoji form, `[first, last]` in
 * chars: a character with `U+FE0F` after it, with whatever joins onto it.
 *
 * What a text face with a glyph of its own for the character (JetBrains Mono has
 * one for `⚠`) would otherwise draw in black, since shaping takes the glyph from
 * the first face that has it and never reads the selector. A character that is
 * emoji to begin with (`✅`) needs nothing: no text face has it, so it falls back
 * to the colour face on its own, and isn't in here unless it carries the
 * selector anyway.
 *
 * Whole clusters, so a keycap (`1️⃣`), a skin tone or a ZWJ sequence with the
 * selector anywhere in it (`❤️‍🔥`) goes to the one face together rather than
 * splitting mid-sequence.
 */
internal fun String.emojiPresentationRanges(): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var index = 0
    while (index < length) {
        val start: Int = index
        var asks = false
        index = nextCodePoint(index)
        while (index < length) {
            val point: Int = codePointAt(index)
            when {
                point == EmojiPresentation -> asks = true
                point == ZeroWidthJoiner && index + 1 < length -> index += 1
                !point.attaches -> break
            }
            index = nextCodePoint(index)
        }
        if (asks) ranges += start until index
    }
    return ranges
}

/** What rides on the code point before it rather than starting a cluster of its own. */
private val Int.attaches: Boolean
    get() = this == TextPresentation ||
        this == 0x20E3 || // the keycap that encloses a digit
        this in 0x1F3FB..0x1F3FF || // skin tones
        this in 0xE0020..0xE007F // the tags a subdivision flag spells out

private fun String.codePointAt(index: Int): Int {
    val high: Char = this[index]
    if (!high.isHighSurrogate() || index + 1 >= length) return high.code
    val low: Char = this[index + 1]
    if (!low.isLowSurrogate()) return high.code
    return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
}

private fun String.nextCodePoint(index: Int): Int =
    if (codePointAt(index) >= 0x10000) index + 2 else index + 1

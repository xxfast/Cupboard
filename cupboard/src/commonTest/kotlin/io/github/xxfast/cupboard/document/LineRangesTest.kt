package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The text a line-range field holds, in and out: `1-3, 7, 9-12` either way.
 *
 * Parsing is deliberately forgiving, because this runs on every keystroke of a
 * field someone is halfway through typing into. Formatting is the canonical
 * form, so whatever was typed comes back tidy.
 */
class LineRangesTest {
    @Test
    fun emptyTextIsNoRangesAtAll() {
        assertEquals(emptyList(), parseLineRanges(""))
        assertEquals(emptyList(), parseLineRanges("   "))
        assertEquals("", emptyList<LineRange>().formatLineRanges())
    }

    @Test
    fun singleLinesAndRangesBothParse() {
        assertEquals(listOf(LineRange(7, 7)), parseLineRanges("7"))
        assertEquals(listOf(LineRange(1, 3)), parseLineRanges("1-3"))
        assertEquals(
            listOf(LineRange(1, 3), LineRange(7, 7), LineRange(9, 12)),
            parseLineRanges("1-3, 7, 9-12"),
        )
    }

    @Test
    fun whitespaceAndStrayCommasAreToleratedRatherThanRefused() {
        assertEquals(
            listOf(LineRange(1, 3), LineRange(7, 7)),
            parseLineRanges("  1 - 3 ,, 7 ,  "),
        )
    }

    @Test
    fun aReversedRangeIsReadAsTheOneItObviouslyMeans() {
        assertEquals(listOf(LineRange(1, 3)), parseLineRanges("3-1"))
    }

    @Test
    fun garbageTokensAreDroppedAndTheRestStillParses() {
        assertEquals(listOf(LineRange(4, 4)), parseLineRanges("abc, 4, 1-, -, 1-2-3"))
        assertEquals(emptyList(), parseLineRanges("nonsense"))
    }

    @Test
    fun formattingWritesSingleLinesAsThemselves() {
        val ranges: List<LineRange> = listOf(LineRange(1, 3), LineRange(7, 7))

        assertEquals("1-3, 7", ranges.formatLineRanges())
    }

    @Test
    fun parsingAndFormattingRoundTrip() {
        val text = "1-3, 7, 9-12"

        assertEquals(text, parseLineRanges(text).formatLineRanges())
        // And the other way: formatted text parses back to the very same ranges.
        val ranges: List<LineRange> = listOf(LineRange(2, 2), LineRange(5, 9))
        assertEquals(ranges, parseLineRanges(ranges.formatLineRanges()))
        // Untidy text comes back tidy, and tidy text stays put.
        assertEquals("1-3, 7", parseLineRanges(" 3-1 ,,7").formatLineRanges())
    }
}

package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The text a node-id field holds, in and out: `A, B, C` either way.
 *
 * [LineRangesTest]'s twin. Parsing is forgiving because it runs on every
 * keystroke, formatting is the canonical form, and an id no node carries is
 * kept: half of an id is what a field holds mid-word.
 */
class NodeIdsTest {
    @Test
    fun emptyTextIsNoIdsAtAll() {
        assertEquals(emptyList(), parseNodeIds(""))
        assertEquals(emptyList(), parseNodeIds("   "))
        assertEquals("", emptyList<String>().formatNodeIds())
    }

    @Test
    fun oneIdAndSeveralBothParse() {
        assertEquals(listOf("A"), parseNodeIds("A"))
        assertEquals(listOf("A", "B", "C"), parseNodeIds("A, B, C"))
    }

    @Test
    fun whitespaceAndStrayCommasAreToleratedRatherThanRefused() {
        assertEquals(listOf("A", "B"), parseNodeIds("  A ,, B ,  "))
        assertEquals(listOf("A", "B"), parseNodeIds(",A,B,"))
    }

    @Test
    fun anIdNamedTwiceIsKeptOnceWhereItFirstAppeared() {
        assertEquals(listOf("B", "A", "C"), parseNodeIds("B, A, B, C, A"))
    }

    @Test
    fun orderIsTheOrderTyped() {
        assertEquals(listOf("C", "A", "B"), parseNodeIds("C, A, B"))
    }

    @Test
    fun idsNoNodeCarriesAreKept() {
        // `Ser` is `Server` mid-word: dropping it would eat what is being typed.
        assertEquals(listOf("Client", "Ser"), parseNodeIds("Client, Ser"))
    }

    @Test
    fun parsingAndFormattingRoundTrip() {
        val text = "A, B, C"

        assertEquals(text, parseNodeIds(text).formatNodeIds())
        // And the other way: formatted text parses back to the very same ids.
        val ids: List<String> = listOf("Load", "Parse", "Render")
        assertEquals(ids, parseNodeIds(ids.formatNodeIds()))
        // Untidy text comes back tidy, and tidy text stays put.
        assertEquals("A, B", parseNodeIds(" A ,,B, A").formatNodeIds())
    }
}

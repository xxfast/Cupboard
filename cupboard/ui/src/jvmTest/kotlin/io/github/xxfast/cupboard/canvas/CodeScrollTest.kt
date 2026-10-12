package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.unit.Constraints
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.LineRange
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where a step rests a block that holds more code than its frame. The slide
 * between two of these is Compose's, so what is worth pinning down is the
 * arithmetic: every row here is 10 pixels tall, which makes a line's top ten
 * times the rows above it.
 */
class CodeScrollTest {
    /** Forty lines in room for ten. */
    private val block: List<CodeScrollRow> = rows(1..40)
    private val viewport = 100

    @Test
    fun aBlockThatFitsNeverScrolls() {
        val fits: List<CodeScrollRow> = rows(1..10)
        assertEquals(0, codeScroll(CodeStep(scrollTo = 6), fits, viewport))
        assertEquals(0, codeScroll(CodeStep(highlight = listOf(LineRange(9, 10))), fits, viewport))
    }

    @Test
    fun anEmptyBlockNeverScrolls() {
        assertEquals(0, codeScroll(CodeStep(scrollTo = 3), emptyList(), viewport))
    }

    @Test
    fun aNamedLineGoesToTheTop() {
        assertEquals(200, codeScroll(CodeStep(scrollTo = 21), block, viewport))
        assertEquals(0, codeScroll(CodeStep(scrollTo = 1), block, viewport))
    }

    @Test
    fun aNamedLineNearTheEndStopsAtTheLastLine() {
        // Line 38 at the top would leave seven rows of nothing under the code.
        assertEquals(300, codeScroll(CodeStep(scrollTo = 38), block, viewport))
    }

    @Test
    fun aNamedLinePastTheEndStopsAtTheLastLine() {
        assertEquals(300, codeScroll(CodeStep(scrollTo = 99), block, viewport))
    }

    @Test
    fun aNamedLineTheStepHidesLandsOnTheNextOneItShows() {
        // Lines 11 to 19 are hidden, so line 20 is the eleventh row.
        val revealed: List<CodeScrollRow> = rows((1..10) + (20..49))
        assertEquals(100, codeScroll(CodeStep(scrollTo = 15), revealed, viewport))
    }

    @Test
    fun aNamedLineWinsOverTheHighlight() {
        val step = CodeStep(highlight = listOf(LineRange(5, 6)), scrollTo = 21)
        assertEquals(200, codeScroll(step, block, viewport))
    }

    @Test
    fun aHighlightIsCentred() {
        // Two rows from 190, in room for ten: four rows of margin either side.
        val step = CodeStep(highlight = listOf(LineRange(20, 21)))
        assertEquals(150, codeScroll(step, block, viewport))
    }

    @Test
    fun aHighlightInSeveralRangesIsCentredAsOneSpan() {
        // Line 20's top to line 25's bottom is six rows, so two of margin.
        val step = CodeStep(highlight = listOf(LineRange(20, 20), LineRange(25, 25)))
        assertEquals(170, codeScroll(step, block, viewport))
    }

    @Test
    fun aHighlightTallerThanTheRoomStartsAtItsTop() {
        val step = CodeStep(highlight = listOf(LineRange(10, 30)))
        assertEquals(90, codeScroll(step, block, viewport))
    }

    @Test
    fun aHighlightAtEitherEndStaysInsideTheBlock() {
        val first = CodeStep(highlight = listOf(LineRange(2, 3)))
        val last = CodeStep(highlight = listOf(LineRange(39, 40)))
        assertEquals(0, codeScroll(first, block, viewport))
        assertEquals(300, codeScroll(last, block, viewport))
    }

    @Test
    fun noHighlightStaysAtTheTop() {
        assertEquals(0, codeScroll(CodeStep(), block, viewport))
    }

    @Test
    fun aHighlightTheStepHidesStaysAtTheTop() {
        val revealed: List<CodeScrollRow> = rows((1..10) + (20..49))
        val step = CodeStep(highlight = listOf(LineRange(12, 14)))
        assertEquals(0, codeScroll(step, revealed, viewport))
    }

    @Test
    fun anUnboundedViewportNeverScrolls() {
        assertEquals(0, codeScroll(CodeStep(scrollTo = 21), block, Constraints.Infinity))
    }

    @Test
    fun aWrappedLineCountsForAsTallAsItCameOut() {
        // Line 2 wrapped onto three rows, so line 3 starts 40 down rather than 20.
        val wrapped: List<CodeScrollRow> = listOf(
            CodeScrollRow(number = 1, top = 0, height = 10),
            CodeScrollRow(number = 2, top = 10, height = 30),
            CodeScrollRow(number = 3, top = 40, height = 10),
            CodeScrollRow(number = 4, top = 50, height = 10),
            CodeScrollRow(number = 5, top = 60, height = 10),
        )

        assertEquals(40, codeScroll(CodeStep(scrollTo = 3), wrapped, viewport = 20))
        assertEquals(50, codeScroll(CodeStep(scrollTo = 5), wrapped, viewport = 20))
        // Taller than the room on its own, so it starts at its top.
        val step = CodeStep(highlight = listOf(LineRange(2, 2)))
        assertEquals(10, codeScroll(step, wrapped, viewport = 20))
    }

    /** One 10 pixel row per number, stacked in the order given. */
    private fun rows(numbers: Iterable<Int>): List<CodeScrollRow> =
        numbers.mapIndexed { row, number -> CodeScrollRow(number, top = row * 10, height = 10) }
}

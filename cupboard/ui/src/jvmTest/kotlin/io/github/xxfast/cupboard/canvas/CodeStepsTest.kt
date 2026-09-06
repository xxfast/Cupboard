package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.graphics.isSpecified
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.CodeTheme
import io.github.xxfast.cupboard.document.LineRange
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a step does to the text that gets set, alongside `CodeHighlightingTest`:
 * `CodeStepTest` owns which lines a step means, this owns what the block then
 * draws for them.
 */
class CodeStepsTest {
    private val code = """
        // a comment
        fun main() {
            val x = 1
            println(x)
        }
    """.trimIndent()

    private fun stepped(step: CodeStep?): SteppedCode =
        steppedCode(code, "kotlin", CodeTheme.Atom, step)

    @Test
    fun noStepIsTheWholeBlockHighlightedAsBefore() {
        val whole = stepped(null)
        assertEquals(highlightCode(code, "kotlin", CodeTheme.Atom), whole.text)
        assertEquals("1\n2\n3\n4\n5", whole.numbers.text)
        assertTrue(whole.numbers.spanStyles.isEmpty())
    }

    @Test
    fun aStepThatRevealsEverythingAndHighlightsNothingIsTheSameBlock() {
        assertEquals(stepped(null).text, stepped(CodeStep()).text)
        assertEquals(stepped(null).numbers, stepped(CodeStep()).numbers)
    }

    @Test
    fun hiddenLinesCollapseAndTheRestKeepTheirOwnNumbers() {
        val step = CodeStep(reveal = listOf(LineRange(2, 2), LineRange(5, 5)))
        val shown = stepped(step)
        assertEquals("fun main() {\n}", shown.text.text)
        assertEquals("2\n5", shown.numbers.text)
    }

    @Test
    fun tokensKeepTheColoursTheyHaveInTheWholeBlock() {
        // The line survives the cut with its own spans, offset into the shorter
        // text: the block is tokenized whole and then sliced, not re-tokenized.
        val whole = highlightCode(code, "kotlin", CodeTheme.Atom)
        val wholeStart = code.indexOf("fun main")
        val onItsOwn = stepped(CodeStep(reveal = listOf(LineRange(2, 2))))

        val expected = whole.spanStyles
            .filter { it.start >= wholeStart && it.end <= wholeStart + "fun main() {".length }
            .map { it.item to (it.start - wholeStart to it.end - wholeStart) }
        assertTrue(expected.isNotEmpty())
        assertEquals(
            expected,
            onItsOwn.text.spanStyles.map { it.item to (it.start to it.end) },
        )
    }

    @Test
    fun everyLineOutsideTheHighlightIsDimmed() {
        val shown = stepped(CodeStep(highlight = listOf(LineRange(3, 3))))
        assertEquals(code, shown.text.text)

        val spotlit = code.indexOf("val x = 1")
        // A bold-only span carries no colour of its own and dims by sitting over
        // the line's dimmed default, so only the coloured ones are asserted on.
        val dimmed = shown.text.spanStyles.filter { it.end <= spotlit && it.item.color.isSpecified }
        assertTrue(dimmed.isNotEmpty())
        assertTrue(dimmed.all { it.item.color.alpha nearly CodeDimAlpha })

        // The spotlit line itself is left at full strength.
        val bright = shown.text.spanStyles
            .filter { it.start >= spotlit && it.end <= spotlit + "val x = 1".length }
        assertTrue(bright.isNotEmpty())
        assertTrue(bright.all { !it.item.color.isSpecified || it.item.color.alpha nearly 1f })
    }

    @Test
    fun theGutterDimsWithItsLine() {
        val shown = stepped(CodeStep(highlight = listOf(LineRange(1, 1))))
        assertEquals("1\n2\n3\n4\n5", shown.numbers.text)
        // Four dimmed numbers, and the first one left alone.
        assertEquals(4, shown.numbers.spanStyles.size)
        assertTrue(shown.numbers.spanStyles.all { it.start >= 2 })
        assertTrue(shown.numbers.spanStyles.all { it.item.color.alpha nearly CodeDimAlpha })
    }

    @Test
    fun revealAndHighlightApplyTogether() {
        val step = CodeStep(
            reveal = listOf(LineRange(2, 4)),
            highlight = listOf(LineRange(4, 4)),
        )
        val shown = stepped(step)
        assertEquals("fun main() {\n    val x = 1\n    println(x)", shown.text.text)
        assertEquals("2\n3\n4", shown.numbers.text)

        // Only the last of the three shown lines stays bright.
        val lastLine = shown.text.text.lastIndexOf("    println(x)")
        val dimmed = shown.text.spanStyles.filter { it.end <= lastLine && it.item.color.isSpecified }
        assertTrue(dimmed.isNotEmpty())
        assertTrue(dimmed.all { it.item.color.alpha nearly CodeDimAlpha })
    }

    @Test
    fun revealingNothingDrawsAnEmptyBlock() {
        val shown = stepped(CodeStep(reveal = listOf(LineRange(9, 12))))
        assertEquals("", shown.text.text)
        assertEquals("", shown.numbers.text)
    }

    @Test
    fun anEmptyBlockSteps() {
        val empty = steppedCode("", "kotlin", CodeTheme.Atom, CodeStep())
        assertEquals("", empty.text.text)
        assertEquals("1", empty.numbers.text)
    }

    @Test
    fun linesCarryTheirOwnNumberAndWhetherTheStepDropsThemBack() {
        val step = CodeStep(highlight = listOf(LineRange(3, 3)))
        val lines: List<SteppedLine> = steppedLines(code, "kotlin", CodeTheme.Atom, step)
        assertEquals(listOf(1, 2, 3, 4, 5), lines.map { it.number })
        assertEquals(listOf(true, true, false, true, true), lines.map { it.dimmed })

        // The dim is a flag, not ink: a dropped line's own text is still the one
        // the whole block draws, so a row can be faded between two steps' dims.
        val dropped: SteppedLine = lines.first()
        assertEquals("// a comment", dropped.text.text)
        val coloured = dropped.text.spanStyles.filter { it.item.color.isSpecified }
        assertTrue(coloured.isNotEmpty())
        assertTrue(coloured.all { it.item.color.alpha nearly 1f })
    }

    @Test
    fun hiddenLinesAreLinesThatAreNotThere() {
        val lines: List<SteppedLine> = steppedLines(
            code,
            "kotlin",
            CodeTheme.Atom,
            CodeStep(reveal = listOf(LineRange(2, 2), LineRange(5, 5))),
        )
        assertEquals(listOf(2, 5), lines.map { it.number })
        assertEquals(listOf("fun main() {", "}"), lines.map { it.text.text })
    }

    @Test
    fun theBlockIsTheLinesJoinedBack() {
        for (step in listOf(
            null,
            CodeStep(),
            CodeStep(highlight = listOf(LineRange(3, 3))),
            CodeStep(reveal = listOf(LineRange(2, 4)), highlight = listOf(LineRange(4, 4))),
        )) {
            val lines: List<SteppedLine> = steppedLines(code, "kotlin", CodeTheme.Atom, step)
            assertEquals(lines.joinToString("\n") { it.text.text }, stepped(step).text.text)
            assertEquals(lines.joinToString("\n") { "${it.number}" }, stepped(step).numbers.text)
        }
    }

    private infix fun Float.nearly(other: Float): Boolean = abs(this - other) < 0.001f
}

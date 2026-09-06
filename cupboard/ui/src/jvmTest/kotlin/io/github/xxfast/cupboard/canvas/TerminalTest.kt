package io.github.xxfast.cupboard.canvas

import io.github.xxfast.cupboard.document.Build
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a terminal's text means and how far it has been typed. Both are pure
 * functions over the transcript, so neither needs a canvas to answer for itself.
 */
class TerminalTest {
    /** Output, a command, its output, a second command, its output. */
    private val transcript = "boot\n$ ab\nok\n$ cd\ndone"

    private val lines: List<TerminalLine> = terminalLines(transcript, "$")

    private fun shown(fraction: Float): List<Pair<TerminalLineKind, String>> =
        typewriter(lines, fraction).map { it.line.kind to it.line.text.take(it.shown) }

    @Test
    fun aLineIsACommandOnlyWhenThePromptAndASpaceStartIt() {
        assertEquals(
            listOf(
                TerminalLine(TerminalLineKind.Output, "boot"),
                TerminalLine(TerminalLineKind.Command, "ab"),
                TerminalLine(TerminalLineKind.Output, "ok"),
                TerminalLine(TerminalLineKind.Command, "cd"),
                TerminalLine(TerminalLineKind.Output, "done"),
            ),
            lines,
        )
    }

    @Test
    fun aPromptOnItsOwnIsAnEmptyCommandAndAPromptWithNoSpaceIsOutput() {
        assertEquals(
            listOf(
                TerminalLine(TerminalLineKind.Command, ""),
                TerminalLine(TerminalLineKind.Output, "\$x"),
            ),
            terminalLines("$\n\$x", "$"),
        )
    }

    @Test
    fun noPromptMakesEveryLineOutput() {
        assertEquals(
            listOf(TerminalLineKind.Output, TerminalLineKind.Output),
            terminalLines("$ ab\nok", "").map { it.kind },
        )
    }

    /** Nothing typed yet, but the output above the first command is already there. */
    @Test
    fun atTheStartOnlyTheLeadingOutputAndTheEmptyCommandLineAreOut() {
        assertEquals(
            listOf(
                TerminalLineKind.Output to "boot",
                TerminalLineKind.Command to "",
            ),
            shown(0f),
        )
    }

    /**
     * Three of the four command characters in: the first command is out with the
     * output under it, the second is a character deep, and what follows that
     * second command is not on screen at all.
     */
    @Test
    fun midWayTheCurrentCommandIsPartlyTypedAndNothingBelowItShows() {
        assertEquals(
            listOf(
                TerminalLineKind.Output to "boot",
                TerminalLineKind.Command to "ab",
                TerminalLineKind.Output to "ok",
                TerminalLineKind.Command to "c",
            ),
            shown(0.75f),
        )
    }

    @Test
    fun theWholeTranscriptIsOutAtTheEnd() {
        assertEquals(lines.map { it.kind to it.text }, shown(1f))
        assertEquals(lines.map { it.kind to it.text }, shown(2f))
        assertTrue(typewriter(lines, 1f).none { it.stillTypingItsOwnLine() })
    }

    @Test
    fun outputWithNoCommandAboveItNeverWaits() {
        val plain: List<TerminalLine> = terminalLines("one\ntwo", "$")
        assertEquals(2, typewriter(plain, 0f).size)
    }

    @Test
    fun theBuildsOwnDurationHoldsUntilTheTextWouldOutrunIt() {
        val quick = Build("term", durationMs = 400)
        // Four command characters is a hundred milliseconds' worth, well inside it.
        assertEquals(400, typewriterDurationMs(lines, quick))

        val long: List<TerminalLine> = terminalLines("$ ${"x".repeat(40)}", "$")
        assertEquals(1000, typewriterDurationMs(long, quick))

        // An empty command still costs a beat, so nothing types in no time at all.
        assertEquals(25, typewriterDurationMs(terminalLines("$", "$"), Build("term", durationMs = 0)))
    }

    private fun TypedLine.stillTypingItsOwnLine(): Boolean = shown < line.text.length
}

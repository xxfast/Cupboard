package io.github.xxfast.cupboard.document

import kotlin.math.ceil

/**
 * The pace a [BuildEffect.Typewriter] types at, which is about as fast as a
 * person does. One keyboard, whichever element it is typing into: a terminal's
 * commands and a code block's step change both type at it.
 */
const val TypewriterMsPerChar: Int = 25

/**
 * How long one character takes to backspace out: twice the typing pace, which is
 * what a held backspace does next to someone typing.
 */
const val TypewriterEraseMsPerChar: Float = TypewriterMsPerChar / 2f

/**
 * How long a code block takes to walk from one step to the next, and how long a
 * typed step change spends moving what both steps show.
 */
const val CodeStepDuration: Int = 350

/**
 * What a typed code step change has to get through: the inked characters it
 * backspaces out ([departing]), the ones it types in ([arriving]), and whether
 * anything both steps show changes place or colour, or the block scrolls
 * ([moves]).
 */
data class TypingWork(val departing: Int, val arriving: Int, val moves: Boolean) {
    /** Whether there is no key to press at all: nothing to erase and nothing to type. */
    val typesNothing: Boolean get() = departing == 0 && arriving == 0
}

/** How long each phase of a typed change runs: erase, then move, then type. */
data class TypingPhases(val eraseMs: Float, val moveMs: Float, val typeMs: Float) {
    val totalMs: Float get() = eraseMs + moveMs + typeMs
}

/**
 * The three phases for erasing [departing] and typing [arriving] inked
 * characters, [moves] being whether the move phase has anything to do.
 *
 * Their natural length is each phase at its own pace: [TypewriterEraseMsPerChar]
 * a character out, [CodeStepDuration] to move, [TypewriterMsPerChar] a character
 * in. An empty phase takes no time at all. A build asking for longer
 * ([durationMs]) stretches all three by the same factor; one asking for less gets
 * the natural length anyway, since a long rewrite typed inside a short build
 * reads as a blur. Nothing to do at all is no time at all, whatever the build
 * asks for.
 */
fun typingPhases(departing: Int, arriving: Int, moves: Boolean, durationMs: Int): TypingPhases {
    val erase: Float = departing * TypewriterEraseMsPerChar
    val move: Float = if (moves) CodeStepDuration.toFloat() else 0f
    val type: Float = arriving * TypewriterMsPerChar.toFloat()
    val natural: Float = erase + move + type
    val stretch: Float = if (natural > 0f) maxOf(1f, durationMs / natural) else 1f
    return TypingPhases(eraseMs = erase * stretch, moveMs = move * stretch, typeMs = type * stretch)
}

/**
 * What typing this block from [from] onto [to] has to do, counted the way the
 * renderer plans the drawn change: the core's [diffTokens] pairs the two
 * versions' tokens up, whitespace is ignored, and a token on a line one side
 * doesn't show ([visibleLines]) arrives or leaves rather than moving.
 *
 * A token both sides show moves when its column, its row among the shown lines,
 * or its dimming ([highlightedLines]) differs between them, and the whole block
 * moves when [CodeStep.scrollTo] does. The renderer's colours are the
 * highlighter's on top of that dimming, which this can't see; a matched token
 * the highlighter paints differently in two versions is the one case the two
 * could part ways on.
 */
fun CodeElement.typingWork(from: CodeStep, to: CodeStep): TypingWork {
    val was: TypingSide = typingSide(from)
    val now: TypingSide = typingSide(to)
    var departing = 0
    var arriving = 0
    var moves: Boolean = from.scrollTo != to.scrollTo

    for (edit in diffTokens(was.tokens, now.tokens)) {
        when (edit) {
            is TokenEdit.Insert -> if (now.shows(edit.token)) arriving += edit.token.inked()

            is TokenEdit.Delete -> if (was.shows(edit.token)) departing += edit.token.inked()

            is TokenEdit.Match -> {
                val shownBefore: Boolean = was.shows(edit.previous)
                val shownAfter: Boolean = now.shows(edit.current)
                when {
                    shownBefore && shownAfter -> {
                        if (was.place(edit.previous) != now.place(edit.current)) moves = true
                    }

                    shownAfter -> arriving += edit.current.inked()
                    shownBefore -> departing += edit.previous.inked()
                }
            }
        }
    }

    return TypingWork(departing = departing, arriving = arriving, moves = moves)
}

/**
 * How long typing this block from [from] onto [to] runs, a [durationMs] build
 * asking for its length: [typingWork] through [typingPhases], rounded up to the
 * whole millisecond the renderer's clock runs over. 0 when there is nothing to
 * erase, move or type.
 */
fun CodeElement.typingMs(from: CodeStep, to: CodeStep, durationMs: Int): Int {
    val work: TypingWork = typingWork(from, to)
    val phases: TypingPhases = typingPhases(work.departing, work.arriving, work.moves, durationMs)
    return ceil(phases.totalMs).toInt()
}

/**
 * How long a terminal's [BuildEffect.Typewriter] entry runs: [terminalTypingMs]
 * over every command character in it ([terminalCommandChars]).
 */
fun TerminalElement.typingMs(durationMs: Int): Int =
    terminalTypingMs(terminalCommandChars(text, prompt), durationMs)

/**
 * How long a terminal types [commandChars] characters for a [durationMs] build:
 * the build's own duration, unless that would outrun the text. A long transcript
 * typed inside a short build reads as a blur, so the floor holds the pace at
 * [TypewriterMsPerChar] a character and lets the build take as long as it takes.
 */
fun terminalTypingMs(commandChars: Int, durationMs: Int): Int =
    maxOf(durationMs, TypewriterMsPerChar * commandChars)

/**
 * Every character a terminal's typewriter has to get through, output excluded.
 *
 * A line is a command when it is the [prompt] alone or starts with the prompt
 * and a space, and output otherwise (every line is output when there is no
 * prompt). A command costs its text after the prompt, and an empty one still
 * costs one, so an empty prompt line doesn't flash past in no time.
 */
fun terminalCommandChars(text: String, prompt: String): Int {
    if (prompt.isEmpty()) return 0
    return text.split("\n").sumOf { line ->
        when {
            line == prompt -> 1
            line.startsWith("$prompt ") -> maxOf(1, line.length - prompt.length - 1)
            else -> 0
        }
    }
}

/** One side of a typed change: its tokens, and where on screen each shown line sits. */
private class TypingSide(
    val tokens: List<CodeToken>,
    private val rows: Map<Int, Int>,
    private val spotlit: Set<Int>,
) {
    /** Whether this side shows [token]'s line at all. [CodeToken.line] counts from 0. */
    fun shows(token: CodeToken): Boolean = token.line + 1 in rows

    /** [token]'s row among the shown lines, its column, and whether its line is dimmed. */
    fun place(token: CodeToken): Triple<Int, Int, Boolean> {
        val line: Int = token.line + 1
        val dimmed: Boolean = spotlit.isNotEmpty() && line !in spotlit
        return Triple(rows.getValue(line), token.start, dimmed)
    }
}

private fun CodeElement.typingSide(step: CodeStep): TypingSide {
    val source: String = sourceAt(step.version)
    val lineCount: Int = source.split("\n").size
    val rows: Map<Int, Int> = visibleLines(lineCount, step)
        .withIndex()
        .associate { (row, line) -> line to row }
    return TypingSide(
        tokens = tokenizeCode(source, language),
        rows = rows,
        spotlit = highlightedLines(lineCount, step),
    )
}

/** How many of this token's characters put ink down: the only ones typing costs. */
private fun CodeToken.inked(): Int =
    if (kind == CodeTokenKind.Whitespace) 0 else content.count { !it.isWhitespace() }

package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable

/**
 * A run of code lines, 1-based and inclusive, counted in the block's original
 * text rather than in whatever a step happens to be showing.
 *
 * A reversed or degenerate range ([last] before [first]) contains nothing, and a
 * range that runs off either end of the block is clamped to the lines that exist.
 * Both are worth nothing rather than an error: ranges outlive edits to the code
 * they point into, and a deck that loses a line should keep playing.
 */
@Serializable
data class LineRange(val first: Int, val last: Int) {
    operator fun contains(line: Int): Boolean = line in first..last
}

/**
 * One visual state of a code block: what it shows, and what it points at.
 *
 * An empty [reveal] shows every line, so the plain `CodeStep()` is the block as
 * it was written. An empty [highlight] dims nothing, so a step either spotlights
 * a range and drops the rest back, or leaves the whole block at full strength.
 *
 * Steps are held by the element ([CodeElement.steps]) and advanced through by
 * builds ([Build.elementStep]): the element says what its states are, the slide's
 * build order says when they arrive.
 *
 * [version] is which of the block's sources the step shows ([CodeElement.versions]),
 * and is what makes a morph a morph: two steps on two versions are the same block
 * saying two different things, and the renderer diffs one into the other. It is
 * last in the list so the positional `CodeStep(reveal, highlight)` calls written
 * before versions existed still mean what they meant.
 *
 * [reveal] and [highlight] count lines of that step's own version, not of
 * version 0: a step showing a rewrite points at the rewrite's lines.
 *
 * [scrollTo] is for a block with more code than its frame holds: the line, 1-based
 * and of that same version, the step puts at the top of the block. Null, and so
 * every step written before this field, follows the [highlight] instead, bringing
 * the lines the step points at into view, and stays at the top when it points at
 * nothing. Either way it is where the block rests in this step rather than a
 * distance from the step before, so a deck opened on step five shows what walking
 * there would have. A block that fits its frame never scrolls, and one that
 * doesn't never scrolls past its last line. The renderer does the arithmetic,
 * since only it knows how tall a line came out.
 *
 * [diagnostics] are the errors and warnings the step points out, the way an IDE
 * would: authored, not compiled. They belong to the step, so they arrive and
 * leave with it, and count in the same version as everything else here.
 */
@Serializable
data class CodeStep(
    val reveal: List<LineRange> = emptyList(),
    val highlight: List<LineRange> = emptyList(),
    val version: Int = 0,
    val scrollTo: Int? = null,
    val diagnostics: List<CodeDiagnostic> = emptyList(),
)

/** How loud a [CodeDiagnostic] is: a red squiggle or an amber one. */
@Serializable
enum class DiagnosticSeverity { Error, Warning }

/**
 * One error or warning on a code step: a squiggle under columns [startColumn]
 * to [endColumn] of [line], and [message] drawn after the line's end, or under
 * the squiggle when the line leaves it no room.
 *
 * All three are 1-based and inclusive, the way an IDE reports `3:5` and the way
 * [LineRange] and [CodeStep.scrollTo] count, so the inspector's fields read what
 * the gutter shows. Like a [LineRange], a position past the end of the code is
 * clamped by the renderer rather than refused, and a line that is not there
 * draws nothing: a diagnostic outlives edits to the code it points into.
 */
@Serializable
data class CodeDiagnostic(
    val line: Int,
    val startColumn: Int,
    val endColumn: Int,
    val severity: DiagnosticSeverity = DiagnosticSeverity.Error,
    val message: String? = null,
)

/**
 * This diagnostic made sound: every position at least 1, the span never
 * reversed, and a blank message no message at all. What an edit commits, so a
 * field typed into mid-way never puts a range nothing can draw into the deck.
 */
fun CodeDiagnostic.sanitized(): CodeDiagnostic {
    val start: Int = startColumn.coerceAtLeast(1)
    return copy(
        line = line.coerceAtLeast(1),
        startColumn = start,
        endColumn = endColumn.coerceAtLeast(start),
        message = message?.takeIf { it.isNotBlank() },
    )
}

/**
 * This step with one more diagnostic, on [source] (the text of the version the
 * step plays): under the first line the step highlights, or line 1 when it
 * highlights none, spanning that line's code from its first non-blank column to
 * its last. Somewhere visible to start from, rather than a zero-width mark.
 */
fun CodeStep.withDiagnosticAdded(source: String): CodeStep {
    val lines: List<String> = source.split("\n")
    val line: Int = highlight.firstOrNull()?.first?.coerceIn(1, lines.size) ?: 1
    val text: String = lines[line - 1]
    val start: Int = text.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it } + 1
    val end: Int = maxOf(text.trimEnd().length, start)
    return copy(diagnostics = diagnostics + CodeDiagnostic(line, start, end))
}

/** This step without diagnostic [index], or itself when it has none there. */
fun CodeStep.withDiagnosticRemoved(index: Int): CodeStep {
    if (index !in diagnostics.indices) return this
    return copy(diagnostics = diagnostics.toMutableList().also { it.removeAt(index) })
}

/** This step with [diagnostic] in place of diagnostic [index], or itself when it has none there. */
fun CodeStep.withDiagnosticUpdated(index: Int, diagnostic: CodeDiagnostic): CodeStep {
    if (index !in diagnostics.indices) return this
    return copy(diagnostics = diagnostics.toMutableList().also { it[index] = diagnostic })
}

/**
 * A position field's text as the diagnostic fields write it: a number of at
 * least 1, or null for anything else, which the field reads as "keep what was
 * there" rather than as an error to show someone mid-keystroke.
 */
fun parseDiagnosticPosition(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 1 }

/**
 * The ranges [text] names, as an inspector field writes them: `1-3, 7, 9-12`.
 *
 * Forgiving on purpose, because this parses every keystroke of a field someone
 * is still typing into: whitespace anywhere is ignored, empty and repeated
 * commas are dropped, a token that is not a number or a pair of them is dropped,
 * and a reversed pair (`3-1`) is read as the range it obviously means. Empty
 * text is an empty list, which is a [CodeStep]'s "all lines" and "no highlight".
 *
 * ASCII hyphen only. An en dash is what a word processor would have made of it,
 * and this field is not one.
 */
fun parseLineRanges(text: String): List<LineRange> = text.split(',').mapNotNull { token ->
    val parts: List<String> = token.split('-').map { it.trim() }
    val bounds: List<Int> = parts.mapNotNull { it.toIntOrNull() }
    when {
        bounds.size != parts.size -> null
        bounds.size == 1 -> LineRange(bounds[0], bounds[0])
        bounds.size == 2 -> LineRange(minOf(bounds[0], bounds[1]), maxOf(bounds[0], bounds[1]))
        else -> null
    }
}

/**
 * [parseLineRanges] the other way: what the field shows for these ranges.
 *
 * A one-line range is the line on its own (`7` rather than `7-7`), which is both
 * what someone would write and what parses back to the same thing. Empty is the
 * empty string, so a step that reveals everything shows an empty field.
 */
fun List<LineRange>.formatLineRanges(): String = joinToString(", ") { range ->
    if (range.first == range.last) "${range.first}" else "${range.first}-${range.last}"
}

/**
 * The line [text] names, as the inspector's Scroll To field writes it, or null
 * for the step's own choice ([CodeStep.scrollTo]).
 *
 * As forgiving as [parseLineRanges] and for its reason: an empty field, a zero
 * and anything that is not a line number are all null, which is the field's
 * "Auto", rather than an error to put in front of someone mid-keystroke.
 */
fun parseScrollTo(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 1 }

/**
 * The 1-based original line numbers [step] shows, in order, out of a block of
 * [lineCount] lines. Empty [CodeStep.reveal] means every line.
 *
 * Original numbers rather than positions in the shown text: the gutter draws
 * these, so a block revealing lines 1-3 and 7 numbers them 1, 2, 3, 7.
 */
fun visibleLines(lineCount: Int, step: CodeStep): List<Int> {
    if (lineCount <= 0) return emptyList()
    if (step.reveal.isEmpty()) return (1..lineCount).toList()
    return (1..lineCount).filter { line -> step.reveal.any { line in it } }
}

/**
 * The 1-based original line numbers [step] spotlights. Empty when the step
 * highlights nothing, which is the renderer's cue to dim no line at all rather
 * than to dim every one of them.
 *
 * Lines outside the block are dropped, and a line can sit in a highlight range
 * without being revealed: the renderer intersects these with [visibleLines].
 */
fun highlightedLines(lineCount: Int, step: CodeStep): Set<Int> {
    if (lineCount <= 0 || step.highlight.isEmpty()) return emptySet()
    return (1..lineCount).filterTo(mutableSetOf()) { line -> step.highlight.any { line in it } }
}

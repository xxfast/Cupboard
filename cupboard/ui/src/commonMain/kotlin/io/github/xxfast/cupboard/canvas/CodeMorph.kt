package io.github.xxfast.cupboard.canvas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import io.github.xxfast.cupboard.document.CodeDiagnostic
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.CodeToken
import io.github.xxfast.cupboard.document.CodeTokenKind
import io.github.xxfast.cupboard.document.TokenEdit
import io.github.xxfast.cupboard.document.diffTokens
import io.github.xxfast.cupboard.document.sourceAt
import io.github.xxfast.cupboard.document.tokenizeCode
import kotlin.math.roundToInt

/**
 * Where a token sits on one side of a morph, and the ink it draws in there.
 *
 * Null, everywhere one of these is handed back, is a token the side doesn't show
 * at all: a step that hides its line makes it absent rather than transparent, so
 * a token both versions hold but only one reveals arrives or leaves instead of
 * sliding out of nowhere.
 */
internal data class TokenPlacement(val position: Offset, val color: Color)

/**
 * One run of glyphs in a morph, and the two ends of what it does.
 *
 * Positions are the top-left of the run in the block's own space, taken from the
 * two sides' laid out text, so a run at rest sits exactly where the static render
 * sets it. Colour is the highlighter's at that token, which is why a [Moved]
 * carries two: a line that leaves the step's spotlight dims as it travels.
 */
internal sealed interface GlyphRun {
    val text: String

    /** A token both versions hold: it slides and recolours between the two. */
    data class Moved(
        override val text: String,
        val from: Offset,
        val to: Offset,
        val fromColor: Color,
        val toColor: Color,
    ) : GlyphRun

    /** A token only the new version shows: it fades in where it lands. */
    data class Arrived(
        override val text: String,
        val position: Offset,
        val color: Color,
    ) : GlyphRun

    /** A token only the old version showed: it fades out where it sat. */
    data class Left(
        override val text: String,
        val position: Offset,
        val color: Color,
    ) : GlyphRun
}

/**
 * How [previous] becomes [current] on screen: one run per token that draws ink.
 *
 * The identity comes from the core's [diffTokens] and nothing else, so what moves
 * is decided by the text rather than by where the two layouts happen to put it.
 * The two placement lookups are the only geometry, which is what makes this
 * testable without a font: hand it a grid and the runs come out in grid units.
 *
 * Whitespace draws nothing, so it is left out entirely: an indent that grows is
 * the tokens after it moving, not a run of spaces stretching.
 */
internal fun morphGlyphs(
    previous: List<CodeToken>,
    current: List<CodeToken>,
    previousPlacement: (CodeToken) -> TokenPlacement?,
    currentPlacement: (CodeToken) -> TokenPlacement?,
): List<GlyphRun> {
    val runs = mutableListOf<GlyphRun>()

    fun arrived(token: CodeToken) {
        if (token.kind == CodeTokenKind.Whitespace) return
        val at: TokenPlacement = currentPlacement(token) ?: return
        runs += GlyphRun.Arrived(token.content, at.position, at.color)
    }

    fun left(token: CodeToken) {
        if (token.kind == CodeTokenKind.Whitespace) return
        val at: TokenPlacement = previousPlacement(token) ?: return
        runs += GlyphRun.Left(token.content, at.position, at.color)
    }

    for (edit in diffTokens(previous, current)) {
        when (edit) {
            is TokenEdit.Insert -> arrived(edit.token)

            is TokenEdit.Delete -> left(edit.token)

            is TokenEdit.Match -> {
                if (edit.current.kind == CodeTokenKind.Whitespace) continue

                val was: TokenPlacement? = previousPlacement(edit.previous)
                val now: TokenPlacement? = currentPlacement(edit.current)
                when {
                    was != null && now != null -> runs += GlyphRun.Moved(
                        text = edit.current.content,
                        from = was.position,
                        to = now.position,
                        fromColor = was.color,
                        toColor = now.color,
                    )

                    // One side hides the line the token is on, so from this
                    // block's point of view the token is not there at all.
                    now != null -> arrived(edit.current)
                    was != null -> left(edit.previous)
                }
            }
        }
    }

    return runs
}

/** One side of a morph: its step, the source it draws, its visible lines, and that text set. */
private data class MorphSide(
    val step: CodeStep,
    val source: String,
    val lines: List<SteppedLine>,
    val text: AnnotatedString,
    val numbers: AnnotatedString,
)

/** A run with its glyphs measured once, so a frame of the morph is only drawing. */
private data class MorphRun(val glyph: GlyphRun, val layout: TextLayoutResult)

/**
 * Where a block with more code than room rests on each side of a morph, in pixels
 * up from its top: [codeScroll] over each side's own laid out lines.
 */
private data class MorphScroll(val from: Int, val to: Int) {
    /** The one translation the whole block rides at [fraction] of the way through. */
    fun at(fraction: Float): Float = lerp(from.toFloat(), to.toFloat(), fraction)
}

/**
 * A diagnostic one side of a morph holds, and how it moves through it.
 *
 * Diagnostics belong to steps, so one only fades: out when it is the old step's
 * ([leaving]), in when it is the new one's. It moves as the token it starts on
 * does, so it stays under its code: [travel] is how far that token goes to reach
 * the other side, from the old side's point of view for a leaving one and from
 * the new side's for an arriving one, and null when the token does not survive,
 * which has the mark slide the way an arriving or departing token does.
 * [strength] is its line's, so a diagnostic on a dimmed line is dimmed with it,
 * as the line path draws it at rest.
 */
private data class MorphMark(
    val mark: DiagnosticMark,
    val leaving: Boolean,
    val travel: Offset?,
    val strength: Float,
)

/**
 * Everything a frame needs: the runs, the diagnostics, how tall a row of the new
 * version is, and how far the block has scrolled at either end.
 */
private data class MorphPlan(
    val runs: List<MorphRun>,
    val marks: List<MorphMark>,
    val rowHeight: Float,
    val scroll: MorphScroll,
)

/**
 * [element]'s code morphing from the version [from] shows to the one [to] does,
 * a token at a time.
 *
 * The counterpart of [AnimatedCodeLines], for the one thing lines can't express:
 * two versions of a block are not the same text with rows added and taken away,
 * so what travels is the token. Both sides are laid out once, the diff pairs the
 * tokens up, and every frame is a walk over that plan at one progress, with no
 * composable and no clock of its own per token.
 *
 * A block that scrolls between the two steps does it here the way it does in the
 * line path: one translation of the glyphs and of both gutters, between the two
 * places [codeScroll] rests each side at.
 *
 * At rest the caller draws the line path again, so what is on screen when this
 * stops is the static render of [to] to the pixel rather than this pass's
 * approximation of it.
 */
@Composable
internal fun MorphedCodeLines(
    element: CodeElement,
    from: CodeStep,
    to: CodeStep,
    progress: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
) {
    val chrome: CodeChrome = element.theme.chrome
    val measurer: TextMeasurer = rememberTextMeasurer()
    // Merged into the ambient style the way `Text` merges its parameters, so a run
    // is set exactly as its row is at rest (Material's default carries a letter
    // spacing of its own): type that differs by a hair still reads as the block
    // changing size the moment a morph starts, and again as it settles.
    val style: TextStyle = LocalTextStyle.current.merge(
        color = chrome.text,
        fontSize = element.fontSize.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = (element.fontSize * CodeLineHeight).sp,
    )

    val messageStyle: TextStyle = LocalTextStyle.current.merge(
        fontSize = element.fontSize.sp,
        fontFamily = FontFamily.Monospace,
    )
    val em: Float = with(LocalDensity.current) { element.fontSize.sp.toPx() }

    val was: MorphSide = remember(element, from) { element.morphSide(from) }
    val now: MorphSide = remember(element, to) { element.morphSide(to) }

    // Known only once the code is laid out, which is under the gutter's width
    // and so inside the box below. Handed back up here for the gutters, which
    // read it where they read their alpha: in their layer, never in composition.
    var scroll: MorphScroll by remember { mutableStateOf(MorphScroll(from = 0, to = 0)) }

    Row(modifier.fillMaxSize()) {
        // Crossfaded where the lines are morphed: the numbering changes wholesale
        // between two versions, and a number chasing its line reads as noise
        // beside the code that is actually moving.
        if (element.showLineNumbers) {
            Box(Modifier.padding(end = CodeGutterGap)) {
                GutterNumbers(
                    numbers = was.numbers,
                    element = element,
                    chrome = chrome,
                    alpha = { 1f - progress.value },
                    scroll = { scroll.at(progress.value) },
                )
                GutterNumbers(
                    numbers = now.numbers,
                    element = element,
                    chrome = chrome,
                    alpha = { progress.value },
                    scroll = { scroll.at(progress.value) },
                )
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width: Int = with(LocalDensity.current) { maxWidth.roundToPx() }
            val viewport: Int = constraints.maxHeight
            val plan: MorphPlan = remember(was, now, width, viewport, element.wrap, style, em) {
                morphPlan(element, was, now, style, messageStyle, measurer, width, viewport, em)
            }
            SideEffect { scroll = plan.scroll }

            Canvas(Modifier.fillMaxSize()) {
                val fraction: Float = progress.value
                val slide: Float = plan.rowHeight * CodeStepSlide
                val scrolled: Float = plan.scroll.at(fraction)
                // Code slides under the block's padding before its edge cuts it.
                val margin: Float = CodePadding.toPx()
                val visible: ClosedFloatingPointRange<Float> =
                    scrolled - margin..scrolled + size.height + margin
                translate(top = -scrolled) {
                    for (run in plan.runs) drawGlyphRun(run, fraction, slide, visible)
                    for (mark in plan.marks) drawMorphMark(mark, fraction, slide, chrome, em)
                }
            }
        }
    }
}

/**
 * One side's gutter, at whatever [alpha] the crossfade is giving it this frame
 * and [scroll] pixels up, with the code it numbers.
 *
 * Measured as tall as its numbers run rather than as tall as the block: a text
 * set under a bounded height cuts itself off at it, and the numbers under the
 * fold are the ones a scroll brings up. The layer is inside that for the same
 * reason, a translucent one being a buffer the size of what it wraps.
 */
@Composable
private fun GutterNumbers(
    numbers: AnnotatedString,
    element: CodeElement,
    chrome: CodeChrome,
    alpha: () -> Float,
    scroll: () -> Float,
) {
    Text(
        text = numbers,
        color = chrome.gutter,
        fontSize = element.fontSize.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = (element.fontSize * CodeLineHeight).sp,
        softWrap = false,
        textAlign = TextAlign.End,
        modifier = Modifier
            .wrapContentHeight(align = Alignment.Top, unbounded = true)
            .graphicsLayer {
                this.alpha = alpha()
                translationY = -scroll()
            },
    )
}

/**
 * The block as one step shows it, ready to be morphed against the other.
 *
 * Both the per-line slice and the joined text are taken, because the morph needs
 * both: the lines say which original line each row is, so a token can be found in
 * the text at all, and the text is what gets laid out and read colours from.
 */
private fun CodeElement.morphSide(step: CodeStep): MorphSide {
    val source: String = sourceAt(step.version)
    val stepped: SteppedCode = steppedCode(source, language, theme, step)
    return MorphSide(
        step = step,
        source = source,
        lines = steppedLines(source, language, theme, step),
        text = stepped.text,
        numbers = stepped.numbers,
    )
}

/**
 * The runs [width] pixels of room asks for, measured once per step change.
 *
 * Each side is laid out as one text under the same constraints the line path
 * measures rows with, so a token's rest position is the position the static
 * render gives it. Every run is then measured on its own, so a frame costs one
 * [drawText] per run rather than a fresh layout.
 *
 * The same two layouts say where each side rests in [viewport] pixels of height.
 */
private fun morphPlan(
    element: CodeElement,
    previous: MorphSide,
    current: MorphSide,
    style: TextStyle,
    messageStyle: TextStyle,
    measurer: TextMeasurer,
    width: Int,
    viewport: Int,
    em: Float,
): MorphPlan {
    val chrome: CodeChrome = element.theme.chrome
    val fallback: Color = chrome.text
    fun layout(text: AnnotatedString): TextLayoutResult = measurer.measure(
        text = text,
        style = style,
        softWrap = element.wrap,
        overflow = TextOverflow.Clip,
        constraints = Constraints(maxWidth = width.coerceAtLeast(1)),
    )

    val previousLayout: TextLayoutResult = layout(previous.text)
    val currentLayout: TextLayoutResult = layout(current.text)

    val previousTokens: List<CodeToken> = tokenizeCode(previous.source, element.language)
    val currentTokens: List<CodeToken> = tokenizeCode(current.source, element.language)
    val previousPlacement: (CodeToken) -> TokenPlacement? = previous.placements(previousLayout, fallback)
    val currentPlacement: (CodeToken) -> TokenPlacement? = current.placements(currentLayout, fallback)

    val runs: List<GlyphRun> = morphGlyphs(
        previous = previousTokens,
        current = currentTokens,
        previousPlacement = previousPlacement,
        currentPlacement = currentPlacement,
    )

    // Diffed again only when there is a diagnostic to carry, which most morphs
    // don't have: the pairs say which token a squiggle rides to the other side.
    val marks: List<MorphMark> =
        if (previous.step.diagnostics.isEmpty() && current.step.diagnostics.isEmpty()) emptyList()
        else {
            val matches: List<TokenEdit.Match> =
                diffTokens(previousTokens, currentTokens).filterIsInstance<TokenEdit.Match>()
            val forward: Map<CodeToken, CodeToken> = matches.associate { it.previous to it.current }
            val backward: Map<CodeToken, CodeToken> = matches.associate { it.current to it.previous }

            fun MorphSide.marks(
                layout: TextLayoutResult,
                tokens: List<CodeToken>,
                leaving: Boolean,
                travel: (CodeToken) -> Offset?,
            ): List<MorphMark> {
                var offset = 0
                return lines.flatMap { line ->
                    val start: Int = offset
                    offset += line.text.length + 1
                    diagnosticMarks(
                        diagnostics = step.diagnostics.filter { it.line == line.number },
                        layout = layout,
                        start = start,
                        length = line.text.length,
                        measurer = measurer,
                        style = messageStyle,
                        chrome = chrome,
                        em = em,
                        gap = em * CodeDiagnosticGap,
                    ).map { mark ->
                        val anchor: CodeToken? = tokens.anchorOf(mark.diagnostic)
                        val strength: Float = if (line.dimmed) CodeDimAlpha else 1f
                        MorphMark(mark, leaving, anchor?.let(travel), strength)
                    }
                }
            }

            fun travel(
                from: CodeToken,
                to: CodeToken?,
                fromPlacement: (CodeToken) -> TokenPlacement?,
                toPlacement: (CodeToken) -> TokenPlacement?,
            ): Offset? {
                val was: TokenPlacement = fromPlacement(from) ?: return null
                val now: TokenPlacement = to?.let(toPlacement) ?: return null
                return now.position - was.position
            }

            previous.marks(previousLayout, previousTokens, leaving = true) { token ->
                travel(token, forward[token], previousPlacement, currentPlacement)
            } + current.marks(currentLayout, currentTokens, leaving = false) { token ->
                travel(token, backward[token], currentPlacement, previousPlacement)
            }
        }

    return MorphPlan(
        runs = runs.map { run ->
            MorphRun(run, measurer.measure(AnnotatedString(run.text), style, softWrap = false))
        },
        marks = marks,
        rowHeight = currentLayout.getLineBottom(0) - currentLayout.getLineTop(0),
        scroll = MorphScroll(
            from = codeScroll(previous.step, previous.rows(previousLayout), viewport),
            to = codeScroll(current.step, current.rows(currentLayout), viewport),
        ),
    )
}

/**
 * This side's lines as [codeScroll] wants them, read off the one [layout] they
 * were all set in.
 *
 * A line starts at the top of the visual line its first character is on and runs
 * to where the next one starts, so a line that wrapped is as tall as it came out
 * and the rows under it sit where the line path stacks them.
 */
private fun MorphSide.rows(layout: TextLayoutResult): List<CodeScrollRow> {
    val tops = IntArray(lines.size)
    var offset = 0
    for ((index, line) in lines.withIndex()) {
        tops[index] = layout.getLineTop(layout.getLineForOffset(offset)).roundToInt()
        offset += line.text.length + 1
    }

    val bottom: Int = layout.getLineBottom(layout.lineCount - 1).roundToInt()
    return lines.mapIndexed { index, line ->
        val next: Int = tops.getOrElse(index + 1) { bottom }
        CodeScrollRow(line.number, tops[index], next - tops[index])
    }
}

/**
 * Where each of this side's tokens sits and what colour it is, or null for one
 * on a line this step doesn't show.
 *
 * A token is found by walking from the start of its row, which is why the lines
 * are carried: the tokenizer numbers lines in the whole version, and the text on
 * screen is only the ones the step reveals, joined back up.
 */
private fun MorphSide.placements(
    layout: TextLayoutResult,
    fallback: Color,
): (CodeToken) -> TokenPlacement? {
    val starts = HashMap<Int, Int>(lines.size)
    var offset = 0
    for (line in lines) {
        starts[line.number] = offset
        offset += line.text.length + 1
    }

    return placements@{ token ->
        // [CodeToken.line] counts from 0, [SteppedLine.number] from 1.
        val start: Int = starts[token.line + 1] ?: return@placements null
        val at: Int = (start + token.start).coerceIn(0, (text.length - 1).coerceAtLeast(0))
        val color: Color = text.spanStyles
            .lastOrNull { at >= it.start && at < it.end && it.item.color.isSpecified }
            ?.item?.color
            ?: fallback

        TokenPlacement(
            position = Offset(
                x = layout.getHorizontalPosition(at, usePrimaryDirection = true),
                y = layout.getLineTop(layout.getLineForOffset(at)),
            ),
            color = color,
        )
    }
}

/**
 * The token [diagnostic] starts on: the one under its first column, or the next
 * one on the line when that column is blank. Null when the line has nothing
 * there, which leaves the mark to fade where it is.
 */
private fun List<CodeToken>.anchorOf(diagnostic: CodeDiagnostic): CodeToken? {
    val line: Int = diagnostic.line - 1
    val column: Int = diagnostic.startColumn - 1
    return firstOrNull { token ->
        token.line == line && token.kind != CodeTokenKind.Whitespace && token.end > column
    }
}

/**
 * One diagnostic at [fraction] of the way through the change: fading the way
 * its step does, and moving with its token, or with the slide a token that
 * arrives or leaves makes when its own has no other side to go to.
 */
private fun DrawScope.drawMorphMark(
    mark: MorphMark,
    fraction: Float,
    slide: Float,
    chrome: CodeChrome,
    em: Float,
) {
    val moved: Float = if (mark.leaving) fraction else 1f - fraction
    val shift: Offset = mark.travel?.times(moved) ?: Offset(0f, moved * slide)
    drawDiagnostic(mark.mark, chrome, em, alpha = (1f - moved) * mark.strength, shift = shift)
}

/**
 * One run at [fraction] of the way through the change, [slide] being a row's travel.
 *
 * A run wholly outside the [visible] band of the block is skipped: a long block
 * holds far more tokens than its frame shows, and the clip would only throw their
 * ink away after paying for it.
 */
private fun DrawScope.drawGlyphRun(
    run: MorphRun,
    fraction: Float,
    slide: Float,
    visible: ClosedFloatingPointRange<Float>,
) {
    val glyph: GlyphRun = run.glyph
    val topLeft: Offset = when (glyph) {
        is GlyphRun.Moved -> lerp(glyph.from, glyph.to, fraction)
        // Arrivals and departures travel the same way a whole line does in the
        // line path, so a version change and a reveal read as one language.
        is GlyphRun.Arrived -> glyph.position + Offset(0f, (1f - fraction) * slide)
        is GlyphRun.Left -> glyph.position + Offset(0f, fraction * slide)
    }
    if (topLeft.y > visible.endInclusive || topLeft.y + run.layout.size.height < visible.start) return

    when (glyph) {
        is GlyphRun.Moved -> drawText(
            textLayoutResult = run.layout,
            color = lerp(glyph.fromColor, glyph.toColor, fraction),
            topLeft = topLeft,
        )

        is GlyphRun.Arrived -> drawText(
            textLayoutResult = run.layout,
            color = glyph.color,
            topLeft = topLeft,
            alpha = fraction,
        )

        is GlyphRun.Left -> drawText(
            textLayoutResult = run.layout,
            color = glyph.color,
            topLeft = topLeft,
            alpha = 1f - fraction,
        )
    }
}

package io.github.xxfast.cupboard.canvas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.CodeToken
import io.github.xxfast.cupboard.document.CodeTokenKind
import io.github.xxfast.cupboard.document.TokenEdit
import io.github.xxfast.cupboard.document.diffTokens
import io.github.xxfast.cupboard.document.sourceAt
import io.github.xxfast.cupboard.document.tokenizeCode

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

/** One side of a morph: the source it draws, its visible lines, and that text set. */
private data class MorphSide(
    val source: String,
    val lines: List<SteppedLine>,
    val text: AnnotatedString,
    val numbers: AnnotatedString,
)

/** A run with its glyphs measured once, so a frame of the morph is only drawing. */
private data class MorphRun(val glyph: GlyphRun, val layout: TextLayoutResult)

/** Everything a frame needs: the runs, and how tall a row of the new version is. */
private data class MorphPlan(val runs: List<MorphRun>, val rowHeight: Float)

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

    val was: MorphSide = remember(element, from) { element.morphSide(from) }
    val now: MorphSide = remember(element, to) { element.morphSide(to) }

    Row(modifier.fillMaxSize()) {
        // Crossfaded where the lines are morphed: the numbering changes wholesale
        // between two versions, and a number chasing its line reads as noise
        // beside the code that is actually moving.
        if (element.showLineNumbers) {
            Box(Modifier.padding(end = CodeGutterGap)) {
                GutterNumbers(was.numbers, element, chrome) { 1f - progress.value }
                GutterNumbers(now.numbers, element, chrome) { progress.value }
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width: Int = with(LocalDensity.current) { maxWidth.roundToPx() }
            val plan: MorphPlan = remember(was, now, width, element.wrap, style) {
                morphPlan(element, was, now, style, measurer, width, chrome.text)
            }

            Canvas(Modifier.fillMaxSize()) {
                val fraction: Float = progress.value
                val slide: Float = plan.rowHeight * CodeStepSlide
                for (run in plan.runs) drawGlyphRun(run, fraction, slide)
            }
        }
    }
}

/** One side's gutter, at whatever [alpha] the crossfade is giving it this frame. */
@Composable
private fun GutterNumbers(
    numbers: AnnotatedString,
    element: CodeElement,
    chrome: CodeChrome,
    alpha: () -> Float,
) {
    Text(
        text = numbers,
        color = chrome.gutter,
        fontSize = element.fontSize.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = (element.fontSize * CodeLineHeight).sp,
        softWrap = false,
        textAlign = TextAlign.End,
        modifier = Modifier.graphicsLayer { this.alpha = alpha() },
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
 */
private fun morphPlan(
    element: CodeElement,
    previous: MorphSide,
    current: MorphSide,
    style: TextStyle,
    measurer: TextMeasurer,
    width: Int,
    fallback: Color,
): MorphPlan {
    fun layout(text: AnnotatedString): TextLayoutResult = measurer.measure(
        text = text,
        style = style,
        softWrap = element.wrap,
        overflow = TextOverflow.Clip,
        constraints = Constraints(maxWidth = width.coerceAtLeast(1)),
    )

    val previousLayout: TextLayoutResult = layout(previous.text)
    val currentLayout: TextLayoutResult = layout(current.text)

    val runs: List<GlyphRun> = morphGlyphs(
        previous = tokenizeCode(previous.source, element.language),
        current = tokenizeCode(current.source, element.language),
        previousPlacement = previous.placements(previousLayout, fallback),
        currentPlacement = current.placements(currentLayout, fallback),
    )

    return MorphPlan(
        runs = runs.map { run ->
            MorphRun(run, measurer.measure(AnnotatedString(run.text), style, softWrap = false))
        },
        rowHeight = currentLayout.getLineBottom(0) - currentLayout.getLineTop(0),
    )
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

/** One run at [fraction] of the way through the change, [slide] being a row's travel. */
private fun DrawScope.drawGlyphRun(run: MorphRun, fraction: Float, slide: Float) {
    when (val glyph: GlyphRun = run.glyph) {
        is GlyphRun.Moved -> drawText(
            textLayoutResult = run.layout,
            color = lerp(glyph.fromColor, glyph.toColor, fraction),
            topLeft = lerp(glyph.from, glyph.to, fraction),
        )

        // Arrivals and departures travel the same way a whole line does in the
        // line path, so a version change and a reveal read as one language.
        is GlyphRun.Arrived -> drawText(
            textLayoutResult = run.layout,
            color = glyph.color,
            topLeft = glyph.position + Offset(0f, (1f - fraction) * slide),
            alpha = fraction,
        )

        is GlyphRun.Left -> drawText(
            textLayoutResult = run.layout,
            color = glyph.color,
            topLeft = glyph.position + Offset(0f, fraction * slide),
            alpha = 1f - fraction,
        )
    }
}

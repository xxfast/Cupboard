package io.github.xxfast.cupboard.canvas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.BuildEffect
import io.github.xxfast.cupboard.document.TerminalElement

/** Which half of a session a line is: what was asked, or what came back. */
internal enum class TerminalLineKind { Command, Output }

/**
 * One line of a terminal's text, classified.
 *
 * [text] is the line without its prompt for a [TerminalLineKind.Command] (empty
 * when the line was nothing but the prompt), and the whole line for output. The
 * prompt is dropped here rather than at draw time because the typewriter counts
 * characters that get typed, and nobody types their own prompt.
 */
internal data class TerminalLine(val kind: TerminalLineKind, val text: String)

/**
 * [text] split into lines and each one classified against [prompt].
 *
 * A line is a command when it is [prompt] on its own or starts with [prompt] and
 * a space; the space has to be there, so `$x` in the middle of some output stays
 * output. An empty [prompt] makes every line output, which is the way to draw a
 * block of plain console spill.
 */
internal fun terminalLines(text: String, prompt: String): List<TerminalLine> =
    text.split("\n").map { line ->
        when {
            prompt.isEmpty() -> TerminalLine(TerminalLineKind.Output, line)
            line == prompt -> TerminalLine(TerminalLineKind.Command, "")
            line.startsWith("$prompt ") ->
                TerminalLine(TerminalLineKind.Command, line.substring(prompt.length + 1))

            else -> TerminalLine(TerminalLineKind.Output, line)
        }
    }

/** A line part way through being typed: [shown] is how many of its characters are out. */
internal data class TypedLine(val line: TerminalLine, val shown: Int)

/**
 * How much of a command line the typewriter is charged for. An empty command
 * still takes a beat, so an empty prompt line doesn't flash past in no time.
 */
private fun TerminalLine.typedCost(): Int =
    if (kind == TerminalLineKind.Output) 0 else maxOf(1, text.length)

/** Every character the typewriter has to get through, output excluded. */
private fun List<TerminalLine>.commandCharCount(): Int = sumOf { it.typedCost() }

/**
 * [lines] as far as they have been typed at [fraction] of the way through.
 *
 * The clock runs over the command characters alone: commands type out in order,
 * one character at a time, and a block of output lands whole the moment the
 * command above it has finished, the way a real shell prints it. Output ahead of
 * any command is there from the start, having nothing to wait for.
 *
 * A line the typewriter hasn't reached is absent rather than present at zero, so
 * nothing holds a row open before its turn. The one exception is the command
 * being typed, which is there with its partial count: that is the line the caret
 * sits on.
 */
internal fun typewriter(lines: List<TerminalLine>, fraction: Float): List<TypedLine> {
    val total: Int = lines.commandCharCount()
    var budget: Int = (fraction.coerceIn(0f, 1f) * total).toInt().coerceIn(0, total)

    val typed = mutableListOf<TypedLine>()
    for (line in lines) {
        if (line.kind == TerminalLineKind.Output) {
            // Only reachable once every command above it has typed out in full,
            // since a command still going returns below.
            typed += TypedLine(line, line.text.length)
            continue
        }

        val cost: Int = line.typedCost()
        if (budget < cost) return typed + TypedLine(line, minOf(budget, line.text.length))
        typed += TypedLine(line, line.text.length)
        budget -= cost
    }
    return typed
}

/** Whether there is still typing to come: a line short, or a line half out. */
internal fun List<TypedLine>.stillTyping(lines: List<TerminalLine>): Boolean =
    size < lines.size || lastOrNull()?.let { it.shown < it.line.text.length } == true

/**
 * How long the typewriter runs for: the build's own duration, unless that would
 * outrun the text. A long transcript typed inside a short build reads as a blur,
 * so the floor holds the pace at [TypewriterMsPerChar] a character and lets the
 * build take as long as it takes.
 */
internal fun typewriterDurationMs(lines: List<TerminalLine>, build: Build): Int =
    maxOf(build.durationMs, TypewriterMsPerChar * lines.commandCharCount())

/** The pace a command types at, which is about as fast as a person does. */
private const val TypewriterMsPerChar: Int = 25

/**
 * The chrome, and the half of it that is internal rather than private for the
 * same reason the code block's is: the editor's in-place terminal field builds
 * itself from these, so the block under the caret is the block that was there
 * before it, to the pixel.
 */
internal val TerminalBackground = Color(0xFF16171D)
internal val TerminalBorder = Color(0xFF2C2E36)
private val TerminalPrompt = Color(0xFF5AF78E)
internal val TerminalCommand = Color(0xFFF1F1F1)
private val TerminalOutput = Color(0xFFB4B8C5)

/** The three lights, left to right. */
private val TerminalLights = listOf(Color(0xFFFF5F57), Color(0xFFFEBC2E), Color(0xFF28C840))

internal val TerminalCorner = RoundedCornerShape(10.dp)

/** The title bar's height, and the inset its lights sit at. */
private val TerminalTitleBar = 28.dp
private val TerminalLightsInset = 12.dp

/** The body's inset, and how much looser than prose its lines are set. */
internal val TerminalPadding = 12.dp
internal const val TerminalLineHeight: Float = 1.45f

/** What a caret looks like when the shell owns it. */
private const val TerminalCursor: String = "▍"

/**
 * A terminal block: its chrome, and its transcript coloured a line at a time.
 *
 * [entry] is the build that reveals the element, and the only thing here that
 * reads it is [BuildEffect.Typewriter]: the transcript then types itself out
 * over the build's duration with a block caret on the line in flight. Every
 * other effect, and the editor's null, draws the whole thing at rest.
 *
 * [lineLimit] cuts the transcript to its first so many lines, for a build handing
 * the block over a line at a time. Null is all of it, which is the editor and
 * every build that reveals the terminal whole.
 */
@Composable
internal fun TerminalElementView(
    element: TerminalElement,
    entry: Build? = null,
    lineLimit: Int? = null,
) {
    val lines: List<TerminalLine> = remember(element.text, element.prompt, lineLimit) {
        val all: List<TerminalLine> = terminalLines(element.text, element.prompt)
        if (lineLimit == null) all else all.take(lineLimit)
    }

    // The one effect this element plays itself. Every other one is a reveal the
    // caller runs around the whole box, and the block draws at rest under it.
    val typing: Build? = entry?.takeIf { it.effect == BuildEffect.Typewriter }
    val progress: Animatable<Float, AnimationVector1D> =
        remember(element.id) { Animatable(if (typing == null) 1f else 0f) }

    LaunchedEffect(element.id) {
        if (typing == null) return@LaunchedEffect
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(typewriterDurationMs(lines, typing), easing = LinearEasing),
        )
    }

    val typed: List<TypedLine> =
        if (typing == null) lines.map { TypedLine(it, it.text.length) }
        else typewriter(lines, progress.value)

    Box(
        modifier = Modifier
            .size(element.frame.width.dp, element.frame.height.dp)
            .background(TerminalBackground, TerminalCorner)
            .border(1.dp, TerminalBorder, TerminalCorner)
            .clip(TerminalCorner),
    ) {
        Column {
            if (element.showTitleBar) TerminalTitleBarView(element)

            Box(modifier = Modifier.padding(TerminalPadding)) {
                Text(
                    text = terminalText(
                        typed = typed,
                        prompt = element.prompt,
                        cursor = typing != null && typed.stillTyping(lines),
                    ),
                    fontSize = element.fontSize.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = (element.fontSize * TerminalLineHeight).sp,
                    // Off, so a long command runs to the edge of the block and is
                    // cut there by the clip above rather than reflowing under itself.
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/**
 * The bar and the hairline under it, drawn by the renderer and by the editor's
 * field alike so a terminal opened for typing keeps the head it had.
 */
@Composable
internal fun TerminalTitleBarView(element: TerminalElement) {
    Box(modifier = Modifier.fillMaxWidth().height(TerminalTitleBar)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = TerminalLightsInset),
        ) {
            for (light in TerminalLights) {
                Box(modifier = Modifier.size(10.dp).background(light, CircleShape))
            }
        }

        Text(
            text = element.title,
            color = Color.White.copy(alpha = 0.6f),
            // The bar doesn't grow with the type, so the title stops shrinking
            // where it would stop being readable.
            fontSize = maxOf(12f, element.fontSize * 0.85f).sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.Center),
        )
    }

    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TerminalBorder))
}

/** The transcript as one string: green prompts, bright commands, dim output. */
private fun terminalText(
    typed: List<TypedLine>,
    prompt: String,
    cursor: Boolean,
): AnnotatedString = buildAnnotatedString {
    for ((index, line) in typed.withIndex()) {
        if (index > 0) append("\n")
        when (line.line.kind) {
            TerminalLineKind.Command -> {
                withStyle(SpanStyle(color = TerminalPrompt)) { append("$prompt ") }
                withStyle(SpanStyle(color = TerminalCommand)) {
                    append(line.line.text.take(line.shown))
                }
            }

            TerminalLineKind.Output ->
                withStyle(SpanStyle(color = TerminalOutput)) {
                    append(line.line.text.take(line.shown))
                }
        }
    }

    if (cursor) withStyle(SpanStyle(color = TerminalCommand)) { append(TerminalCursor) }
}

package io.github.xxfast.cupboard.export

/**
 * `Support.kt`: the handful of composables every exported slide is built out of.
 *
 * One raw string rather than something assembled per deck: the placement helper,
 * the build wrapper and the two blocks that draw code and terminals are the same
 * in every export. The board is the one thing that is not, because the slide it
 * lays out is the deck's own shape, so its two constants are written in.
 */
internal fun supportSource(
    packageName: String,
    slideWidth: Float,
    slideHeight: Float,
): String = """
package $packageName

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The slide this deck is laid out on, in document units drawn as dp. */
const val BoardWidth: Float = ${slideWidth.literal()}
const val BoardHeight: Float = ${slideHeight.literal()}

/** One element's box on the board, in document units. */
data class FrameDp(val x: Float, val y: Float, val width: Float, val height: Float)

/** One line of an exported code block: its number, its text, and whether the step dims it. */
data class CodeLine(val number: Int, val text: String, val dimmed: Boolean = false)

/** The block each Cupboard code theme draws on, and the ink it draws in. */
val CodeBackgrounds: Map<String, Long> = mapOf(
    "Atom" to 0xFF14151FL,
    "Darcula" to 0xFF2B2B2BL,
    "Monokai" to 0xFF272822L,
    "Pastel" to 0xFF2E3436L,
    "Matrix" to 0xFF000000L,
    "Notepad" to 0xFFFDFDF6L,
)

val CodeForegrounds: Map<String, Long> = mapOf(
    "Atom" to 0xFFD9CFFFL,
    "Darcula" to 0xFFEDEDEDL,
    "Monokai" to 0xFFF8F8F2L,
    "Pastel" to 0xFFDFDEE0L,
    "Matrix" to 0xFF008500L,
    "Notepad" to 0xFF000080L,
)

/** A packed ARGB colour, the way a Cupboard document stores one. */
fun Long.argb(): Color = Color(this)

/**
 * A brush along a line at [angle] CSS degrees across a [width] by [height] box:
 * 0 points up, and the angle turns clockwise.
 */
fun linearGradient(
    angle: Float,
    width: Float,
    height: Float,
    vararg stops: Pair<Float, Color>,
): Brush {
    val radians: Float = angle * PI.toFloat() / 180f
    val dx: Float = sin(radians)
    val dy: Float = -cos(radians)
    val length: Float = abs(width * dx) + abs(height * dy)
    return Brush.linearGradient(
        colorStops = stops,
        start = Offset(width / 2 - dx * length / 2, height / 2 - dy * length / 2),
        end = Offset(width / 2 + dx * length / 2, height / 2 + dy * length / 2),
    )
}

/** Two stops of packed ARGB along [angle], for a shape that carries a gradient fill. */
fun gradientBrush(start: Long, end: Long, angle: Float, width: Float, height: Float): Brush =
    linearGradient(angle, width, height, 0f to start.argb(), 1f to end.argb())

/**
 * The board a slide draws on: the deck's own [BoardWidth] by [BoardHeight] box
 * scaled uniformly into whatever space the presentation gives it, on the deck's
 * own gradient.
 */
@Composable
fun Board(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scale: Float = min(maxWidth / BoardWidth.dp, maxHeight / BoardHeight.dp)
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .requiredSize(BoardWidth.dp, BoardHeight.dp)
                .drawBehind {
                    drawRect(
                        linearGradient(
                            140f,
                            size.width,
                            size.height,
                            0f to 0xFF2A2452L.argb(),
                            0.55f to 0xFF171930L.argb(),
                            1f to 0xFF101223L.argb(),
                        )
                    )
                },
            content = content,
        )
    }
}

/** One element, where the document puts it. */
@Composable
fun BoxScope.At(
    frame: FrameDp,
    rotation: Float = 0f,
    opacity: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(frame.x.dp, frame.y.dp)
            .size(frame.width.dp, frame.height.dp)
            .graphicsLayer {
                rotationZ = rotation
                alpha = opacity
            },
        content = content,
    )
}

/** The way a build brings its element on, one entry per effect the editor offers. */
enum class BuildEffect { Appear, FadeUp, Pop, Dissolve, MoveIn, Scale, Wipe, Typewriter }

/**
 * A build: what the element does when its step arrives, in the effect and over
 * the timing the deck was written with.
 *
 * Typewriter types its element out in the editor and reveals it whole here, which
 * is why it sits with Appear on the branch that plays nothing at all.
 */
@Composable
fun Appear(
    visible: Boolean,
    effect: BuildEffect = BuildEffect.FadeUp,
    durationMs: Int = 400,
    delayMs: Int = 0,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = when (effect) {
            BuildEffect.FadeUp ->
                fadeIn(tween(durationMs, delayMs)) +
                    slideInVertically(tween(durationMs, delayMs)) { it / 8 }
            BuildEffect.Pop ->
                scaleIn(tween(durationMs, delayMs)) + fadeIn(tween(durationMs, delayMs))
            BuildEffect.Dissolve -> fadeIn(tween(durationMs, delayMs))
            BuildEffect.MoveIn -> slideInHorizontally(tween(durationMs, delayMs)) { -it }
            BuildEffect.Scale -> scaleIn(tween(durationMs, delayMs))
            BuildEffect.Wipe -> expandVertically(tween(durationMs, delayMs))
            BuildEffect.Appear, BuildEffect.Typewriter -> EnterTransition.None
        },
    ) {
        content()
    }
}

/** A code block in the state its step shows: revealed lines, and the dimming around a highlight. */
@Composable
fun CodeBlock(
    lines: List<CodeLine>,
    fontSize: Float,
    theme: String,
    showLineNumbers: Boolean,
) {
    val background: Long = CodeBackgrounds[theme] ?: 0xFF14151FL
    val foreground: Long = CodeForegrounds[theme] ?: 0xFFD9CFFFL
    val gutter: Int = lines.maxOfOrNull { it.number.toString().length } ?: 0
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background.argb(), RoundedCornerShape(16.dp))
            .padding(horizontal = 28.dp, vertical = 22.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                lines.forEachIndexed { index, line ->
                    val ink: Color = foreground.argb().copy(alpha = if (line.dimmed) 0.35f else 1f)
                    withStyle(SpanStyle(color = ink)) {
                        if (showLineNumbers) append(line.number.toString().padStart(gutter) + "  ")
                        append(line.text)
                    }
                    if (index != lines.lastIndex) append("\n")
                }
            },
            fontSize = fontSize.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = (fontSize * 1.45f).sp,
        )
    }
}

/** A terminal transcript. Commands draw bright behind a green prompt, output dim. */
@Composable
fun TerminalBox(
    text: String,
    prompt: String,
    title: String?,
    fontSize: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(0xFF16171DL.argb(), RoundedCornerShape(16.dp))
            .padding(horizontal = 28.dp, vertical = 22.dp),
    ) {
        Column {
            if (title != null) {
                Text(
                    text = title,
                    color = 0xFF8A8F9EL.argb(),
                    fontSize = (fontSize * 0.75f).sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            // Drawn whole: the typewriter build is not exported.
            Text(
                text = transcript(text, prompt),
                fontSize = fontSize.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = (fontSize * 1.45f).sp,
            )
        }
    }
}

private fun transcript(text: String, prompt: String): AnnotatedString = buildAnnotatedString {
    val lines: List<String> = text.split("\n")
    lines.forEachIndexed { index, line ->
        if (line == prompt || line.startsWith(prompt + " ")) {
            withStyle(SpanStyle(color = 0xFF5AF78EL.argb())) { append(prompt) }
            withStyle(SpanStyle(color = 0xFFF1F1F1L.argb())) { append(line.removePrefix(prompt)) }
        } else {
            withStyle(SpanStyle(color = 0xFFB4B8C5L.argb())) { append(line) }
        }
        if (index != lines.lastIndex) append("\n")
    }
}

/** The dashed frame anything the export doesn't draw yet stands in behind. */
@Composable
fun DashedBox(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRoundRect(
                    color = Color(0xFF4A4570),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    ),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                )
            }
            .padding(20.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
""".trimStart()

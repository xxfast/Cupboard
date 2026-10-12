package io.github.xxfast.cupboard.canvas

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.LineRange
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A block with more code than its frame holds, read back a row of pixels at a
 * time: forty lines in room for ten, every one of them blank but line 25, so
 * wherever there is ink is where line 25 is.
 *
 * Ink is whatever differs from the same block with nothing typed in it, which
 * keeps the theme's colours and the block's own chrome out of the question.
 */
class CodeScrollRenderTest {
    private val frame = Frame(20f, 20f, 360f, 10 * ROW + 2 * CodePadding.value)

    private val element = CodeElement(
        frame = frame,
        code = (1..40).joinToString("\n") { if (it == 25) "val answer = 42" else "" },
        // Fourteen at the block's line height is the 21 pixel row the bands
        // below are counted in.
        fontSize = 14f,
    )

    /** Where the code is laid out: the frame, less the block's padding. */
    private val viewportTop: Int = (frame.y + CodePadding.value).toInt()

    @Test
    fun aBlockThatScrollsNowhereDrawsOnlyWhatFits() {
        assertEquals(emptyList(), inkRows(CodeStep()), "line 25 is fifteen rows under the fold")
    }

    @Test
    fun aNamedLineBringsTheCodeUnderTheFoldIntoView() {
        val unscrolled: Bitmap = render(element, CodeStep())
        val scrolled: Bitmap = render(element, CodeStep(scrollTo = 21))
        assertFalse(unscrolled.readPixels().contentEquals(scrolled.readPixels()))

        // Line 21 at the top makes line 25 the fifth row down.
        val ink: List<Int> = inkRows(CodeStep(scrollTo = 21))
        assertTrue(ink.isNotEmpty(), "line 25 is on screen")
        assertTrue(ink.all { it in band(4 * ROW) }, "in the fifth row, and only there: $ink")
    }

    @Test
    fun aHighlightUnderTheFoldIsBroughtToTheMiddle() {
        // One row in room for ten: 94 pixels over it, and 95 under.
        val ink: List<Int> = inkRows(CodeStep(highlight = listOf(LineRange(25, 25))))
        assertTrue(ink.isNotEmpty(), "line 25 is on screen")
        assertTrue(ink.all { it in band((10 * ROW - ROW) / 2) }, "centred: $ink")
    }

    @Test
    fun aLineScrolledOffTheTopIsCutAtTheBlocksEdge() {
        // Line 26 at the top leaves line 25 one row up: part under the padding,
        // where it still draws, and the rest past the edge, where it must not.
        val ink: List<Int> = inkRows(CodeStep(scrollTo = 26))
        assertTrue(ink.isNotEmpty(), "line 25 shows under the padding")
        assertTrue(ink.all { it >= frame.y && it < viewportTop }, "and nowhere else: $ink")
    }

    /**
     * A step change slides the block rather than cutting to it, and comes to rest
     * on the pixels a deck opened on that step draws: where a step rests is its
     * own, and takes nothing from the one before.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun walkingToAStepRestsWhereOpeningOnItDoes() {
        val to = CodeStep(scrollTo = 21)
        val blank: Bitmap = render(element.copy(code = BLANK), to)
        val rest: Bitmap = render(element, to)
        var step: CodeStep by mutableStateOf(CodeStep())

        val scene = ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f))
        scene.setContent { OnSlide { ElementView(element, codeStep = step) } }

        try {
            scene.render(0)
            step = to
            // Frame by frame, the way a display would ask: an animation's clock
            // starts on the first frame it sees, so one long jump lands nowhere.
            val frames: List<Bitmap> = (1..SETTLED_FRAME).map { frame ->
                scene.render(frame * FRAME_NANOS).pixels()
            }

            // Line 25 comes up from under the fold, so somewhere on the way it
            // is lower than the row it ends on.
            val restBand: IntRange = band(4 * ROW)
            assertTrue(
                frames.any { frame -> frame.rowsDifferingFrom(blank).any { it > restBand.last } },
                "the block cut to its step rather than sliding to it",
            )
            assertContentEquals(rest.readPixels(), frames.last().readPixels())
        } finally {
            scene.close()
        }
    }

    /**
     * The token morph scrolls to the same pixel the line path does, at both ends:
     * the line path is what draws once a morph settles, so a scroll that disagreed
     * by a row would show as the block jumping when it starts and when it lands.
     *
     * Numbered, since the morph's gutters are a text each and have to ride along.
     * And set at sixteen rather than the fourteen above: at fourteen and a density
     * of one the baseline lands on a half pixel, which the two paths already round
     * a pixel apart with nothing scrolled at all.
     */
    @Test
    fun aScrolledMorphEndsWhereTheLinePathRests() {
        val versions: CodeElement = element.copy(
            code = (1..40).joinToString("\n") { "val line$it = $it" },
            versions = listOf((1..44).joinToString("\n") { "val line$it = compute($it)" }),
            fontSize = 16f,
            showLineNumbers = true,
        )

        assertMorphEndsAtRest(
            block = versions,
            from = CodeStep(scrollTo = 12),
            to = CodeStep(version = 1, scrollTo = 30),
        )

        // And a step that finds its own way there. A highlight dims the rest, which
        // the morph bakes into each colour and the line path lays over a whole row,
        // and those two round a shade apart: close is as near as this one gets.
        assertMorphEndsAtRest(
            block = versions,
            from = CodeStep(scrollTo = 12),
            to = CodeStep(highlight = listOf(LineRange(30, 31)), version = 1),
            tolerance = 2,
        )
    }

    /**
     * The same with lines that wrap, where a line's height is however many rows it
     * came out as: the morph reads that off one laid out text and the line path
     * off a row a line, and they have to add up to the same place.
     *
     * Unnumbered, since a morph's gutter is one number a row and has never
     * followed a wrap.
     */
    @Test
    fun aScrolledMorphOfWrappedCodeEndsWhereTheLinePathRests() {
        fun source(last: String): String =
            (1..40).joinToString("\n") { "val line$it = first($it) + second($it) + $last($it)" }

        val wrapped: CodeElement = element.copy(
            code = source(last = "third"),
            versions = listOf(source(last = "fourth")),
            fontSize = 16f,
            wrap = true,
        )

        assertMorphEndsAtRest(
            block = wrapped,
            from = CodeStep(scrollTo = 12),
            to = CodeStep(version = 1, scrollTo = 30),
        )
    }

    /**
     * [block] morphing from [from] to [to] is the line path at rest, at either end,
     * to within [tolerance] levels of any one channel of any one pixel. A row out
     * of place is hundreds of levels out, so a small one still catches it.
     */
    private fun assertMorphEndsAtRest(
        block: CodeElement,
        from: CodeStep,
        to: CodeStep,
        tolerance: Int = 0,
    ) {
        fun draw(content: @Composable () -> Unit): Bitmap = render {
            OnSlide {
                Box(
                    modifier = Modifier
                        .offset(frame.x.dp, frame.y.dp)
                        .size(frame.width.dp, frame.height.dp)
                        .codeChrome(block),
                ) {
                    content()
                }
            }
        }

        val restFrom: Bitmap = draw { AnimatedCodeLines(block, from) }
        val restTo: Bitmap = draw { AnimatedCodeLines(block, to) }
        val morphStart: Bitmap = draw {
            MorphedCodeLines(block, from, to, progress = remember { Animatable(0f) })
        }
        val morphEnd: Bitmap = draw {
            MorphedCodeLines(block, from, to, progress = remember { Animatable(1f) })
        }

        val unscrolled: Bitmap = draw { AnimatedCodeLines(block, from.copy(scrollTo = null)) }
        assertFalse(
            restFrom.readPixels().contentEquals(unscrolled.readPixels()),
            "the line path did not scroll at all",
        )
        assertTrue(restFrom.farthestFrom(morphStart) <= tolerance, "morph at 0")
        assertTrue(restTo.farthestFrom(morphEnd) <= tolerance, "morph at 1")
    }

    /** The most any channel of any pixel differs between this and [other], in levels. */
    private fun Bitmap.farthestFrom(other: Bitmap): Int {
        val mine: ByteArray = checkNotNull(readPixels())
        val theirs: ByteArray = checkNotNull(other.readPixels())
        return mine.indices.maxOf { index ->
            abs((mine[index].toInt() and 0xFF) - (theirs[index].toInt() and 0xFF))
        }
    }

    /** The pixel rows [ROW] tall that start [offset] down from the top of the viewport. */
    private fun band(offset: Int): IntRange =
        (viewportTop + offset) until (viewportTop + offset + ROW)

    /** The rows of the whole slide line 25 has put ink on in [step]. */
    private fun inkRows(step: CodeStep): List<Int> =
        render(element, step).rowsDifferingFrom(render(element.copy(code = BLANK), step))

    private fun Bitmap.rowsDifferingFrom(other: Bitmap): List<Int> = (0 until height).filter { y ->
        (0 until width).any { x -> getColor(x, y) != other.getColor(x, y) }
    }

    /** [block] in [step], drawn the way play draws it. */
    private fun render(block: CodeElement, step: CodeStep): Bitmap =
        render { OnSlide { ElementView(block, codeStep = step) } }

    @Composable
    private fun OnSlide(content: @Composable () -> Unit) {
        SlideSurface(zoom = 1f, slideWidth = WIDTH.toFloat(), slideHeight = HEIGHT.toFloat()) {
            content()
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(content: @Composable () -> Unit): Bitmap =
        renderComposeScene(WIDTH, HEIGHT, density = Density(1f)) { content() }.pixels()

    private fun Image.pixels(): Bitmap {
        val pixels = Bitmap()
        pixels.allocN32Pixels(width, height)
        readPixels(pixels)
        return pixels
    }

    private companion object {
        const val WIDTH: Int = 400
        const val HEIGHT: Int = 300

        /** One line of code at 14 points, in pixels at a density of one. */
        const val ROW: Int = 21

        /** Forty lines with nothing on any of them: the block with no ink in it. */
        val BLANK: String = "\n".repeat(39)

        const val FRAME_NANOS: Long = 16_000_000

        /** Half a second in, which is past the end of a 350 millisecond step change. */
        const val SETTLED_FRAME: Int = 32
    }
}

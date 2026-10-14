package io.github.xxfast.cupboard.canvas

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.xxfast.cupboard.document.CodeDiagnostic
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.Frame
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A step's diagnostics, read back off the pixels: where the ink a diagnostic adds
 * lands is wherever a render with it differs from the same render without it.
 */
class CodeDiagnosticsRenderTest {
    private val frame = Frame(20f, 20f, 360f, 160f)

    private val element = CodeElement(
        frame = frame,
        code = "fun main() {\n    println(answr)\n}",
        versions = listOf("fun main() {\n    val x = 1\n    println(answr)\n}"),
        fontSize = 14f,
    )

    /** `answr` on line [line], which is line 2 of version 0 and line 3 of version 1. */
    private fun unresolved(line: Int) =
        CodeDiagnostic(line, startColumn = 13, endColumn = 17, message = "Unresolved reference")

    private val viewportTop: Int = (frame.y + CodePadding.value).toInt()

    @Test
    fun aDiagnosticDrawsOnItsOwnLineInTheThemesErrorColour() {
        val plain = CodeStep()
        val pointed = CodeStep(diagnostics = listOf(unresolved(line = 2)))

        val ink: List<Int> = render(pointed).rowsDifferingFrom(render(plain))
        assertTrue(ink.isNotEmpty(), "the diagnostic drew nothing")
        // Its line's row, and the squiggle hanging a little under its baseline.
        assertTrue(ink.all { it in (viewportTop + ROW)..(viewportTop + 2 * ROW + 3) }, "$ink")

        val error: Int = element.theme.chrome.error.toArgb()
        assertTrue(render(pointed).anyPixelNear(error), "nothing drawn in the error colour")
    }

    @Test
    fun aDiagnosticOnALineTheStepDoesNotShowDrawsNothing() {
        val step = CodeStep(diagnostics = listOf(unresolved(line = 9)))
        assertEquals(emptyList(), render(step).rowsDifferingFrom(render(CodeStep())))
    }

    /**
     * Through a morph the diagnostic stays on `answr`, which moves down a line
     * when version 1 puts a line above it: halfway there, so is the squiggle.
     */
    @Test
    fun throughAMorphADiagnosticRidesItsToken() {
        val from = CodeStep(diagnostics = listOf(unresolved(line = 2)))
        val to = CodeStep(version = 1, diagnostics = listOf(unresolved(line = 3)))

        fun topOfInk(fraction: Float): Int {
            val with: Bitmap = morph(from, to, fraction)
            val without: Bitmap = morph(CodeStep(), CodeStep(version = 1), fraction)
            return with.rowsDifferingFrom(without).min()
        }

        val start: Int = topOfInk(0f)
        val middle: Int = topOfInk(0.5f)
        val end: Int = topOfInk(1f)
        assertTrue(start < middle && middle < end, "$start, $middle, $end")
    }

    private fun Bitmap.rowsDifferingFrom(other: Bitmap): List<Int> = (0 until height).filter { y ->
        (0 until width).any { x -> getColor(x, y) != other.getColor(x, y) }
    }

    private fun Bitmap.anyPixelNear(argb: Int): Boolean {
        val target = Color(argb)
        return (0 until height).any { y ->
            (0 until width).any { x ->
                val pixel = Color(getColor(x, y))
                kotlin.math.abs(pixel.red - target.red) < 0.08f &&
                    kotlin.math.abs(pixel.green - target.green) < 0.08f &&
                    kotlin.math.abs(pixel.blue - target.blue) < 0.08f
            }
        }
    }

    private fun render(step: CodeStep): Bitmap =
        render { OnSlide { ElementView(element, codeStep = step) } }

    private fun morph(from: CodeStep, to: CodeStep, fraction: Float): Bitmap = render {
        OnSlide {
            Box(
                modifier = Modifier
                    .offset(frame.x.dp, frame.y.dp)
                    .size(frame.width.dp, frame.height.dp)
                    .codeChrome(element),
            ) {
                MorphedCodeLines(element, from, to, progress = remember { Animatable(fraction) })
            }
        }
    }

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
        const val HEIGHT: Int = 240

        /** One line of code at 14 points, in pixels at a density of one. */
        const val ROW: Int = 21
    }
}

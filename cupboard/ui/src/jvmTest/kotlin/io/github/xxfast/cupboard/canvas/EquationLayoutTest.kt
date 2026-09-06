package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import io.github.xxfast.cupboard.document.parseMath
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The layout measures real glyphs, so it needs a real text measurer: these run
 * against the one an offscreen composition hands out, which is the same one the
 * canvas draws with.
 *
 * Assertions are relations rather than numbers. What a fraction measures depends
 * on the font the machine resolves for `FontFamily.Serif`, and pinning that would
 * be a test of the machine; that a fraction is taller than its numerator is true
 * of every font there is.
 */
class EquationLayoutTest {
    @Test
    fun aFractionIsTallerThanEitherOfItsHalves() {
        val fraction: MathBox = box("\\frac{a}{b}")
        val numerator: MathBox = box("a")

        assertTrue(
            fraction.height > numerator.height,
            "a fraction (${fraction.height}) should be taller than its numerator " +
                "(${numerator.height})",
        )
        // Two stacked halves and a rule, so comfortably more than one of them.
        assertTrue(fraction.height > numerator.height * 1.4f)
    }

    @Test
    fun aSuperscriptWidensAndRaisesItsBase() {
        val plain: MathBox = box("x")
        val scripted: MathBox = box("x^2")

        assertTrue(scripted.width > plain.width)
        assertTrue(scripted.ascent > plain.ascent)
    }

    @Test
    fun aRowIsWiderThanEitherOfItsBoxes() {
        val row: MathBox = box("ab")
        val first: MathBox = box("a")
        val second: MathBox = box("b")

        assertTrue(row.width > first.width)
        assertTrue(row.width > second.width)
    }

    @Test
    fun aRootReachesOverTheThingUnderIt() {
        val body: MathBox = box("x")
        val root: MathBox = box("\\sqrt{x}")

        assertTrue(root.width > body.width)
        assertTrue(root.ascent > body.ascent)
    }

    @Test
    fun aBigOperatorStacksItsLimitsInDisplayStyle() {
        val bare: MathBox = box("\\sum")
        val limited: MathBox = box("\\sum_{i=1}^{n}")

        assertTrue(limited.height > bare.height)
        // Stacked rather than beside: the limits pull it both ways off the axis.
        assertTrue(limited.ascent > bare.ascent)
        assertTrue(limited.descent > bare.descent)
    }

    @Test
    fun aDelimiterGrowsToWhatItHolds() {
        val small: MathBox = box("\\left( x \\right)")
        val tall: MathBox = box("\\left( \\frac{a}{b} \\right)")

        assertTrue(tall.height > small.height)
    }

    @Test
    fun everyConstructTheSampleDeckUsesMeasuresToSomethingReal() {
        val pieces: List<String> = listOf(
            "t_{frame}",
            "\\sum_{i=1}^{n}",
            "\\frac{w_i}{f}",
            "\\sqrt{\\alpha^2 + \\beta^2}",
            "\\leq",
            "16.6",
            "\\text{ms}",
            "\\sqrt[3]{x}",
            "\\binom{n}{k}",
            "\\hat{x}",
            "\\overline{y}",
            "\\vec{v}",
            "\\left[ \\sin x \\right]",
            "\\operatorname{argmax}",
            SampleLatex,
        )

        for (piece in pieces) {
            val box: MathBox = box(piece)
            assertTrue(box.width.isFinite() && box.width > 0f, "$piece has width ${box.width}")
            assertTrue(box.height.isFinite() && box.height > 0f, "$piece has height ${box.height}")
            assertTrue(box.ascent.isFinite() && box.ascent > 0f, "$piece has ascent ${box.ascent}")
            assertTrue(box.descent.isFinite(), "$piece has descent ${box.descent}")
        }
    }

    /** A spacing command is width and nothing else, which is a box with no height. */
    @Test
    fun aSpaceIsWidthWithoutHeight() {
        val space: MathBox = box("\\quad")
        assertTrue(space.width > 0f)
        assertTrue(space.height == 0f)
    }

    private fun box(latex: String, size: Float = 40f): MathBox =
        layoutMath(parseMath(latex), size, Color.White, tools.measurer, tools.density)

    private companion object {
        const val SampleLatex: String = "t_{frame} = \\sum_{i=1}^{n} \\frac{w_i}{f} + " +
            "\\sqrt{\\alpha^2 + \\beta^2} \\leq 16.6\\,\\text{ms}"

        /**
         * The measurer and the density an offscreen composition hands out. Taken
         * once: both are stateless as far as this test is concerned, and standing
         * a composition up per assertion is the slow part.
         */
        val tools: LayoutTools by lazy { layoutTools() }

        class LayoutTools(val measurer: TextMeasurer, val density: Density)

        @OptIn(ExperimentalComposeUiApi::class)
        fun layoutTools(): LayoutTools {
            var captured: LayoutTools? = null
            renderComposeScene(16, 16) {
                captured = LayoutTools(rememberTextMeasurer(), LocalDensity.current)
            }
            return checkNotNull(captured) { "the composition handed out no text measurer" }
        }
    }
}

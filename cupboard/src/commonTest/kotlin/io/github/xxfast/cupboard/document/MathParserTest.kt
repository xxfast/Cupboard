package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The parser is a pure function from a string to a tree, so all of this is
 * assertions about that tree. What the tree looks like once it is drawn is the
 * canvas' own test.
 */
class MathParserTest {
    @Test
    fun aLetterIsAVariableAndADigitIsNot() {
        assertEquals(MathNode.Symbol("x", italic = true), parseMath("x"))
        assertEquals(MathNode.Symbol("16"), parseMath("16"))

        // Digits and their decimal point are one word, so they kern as one.
        assertEquals(MathNode.Symbol("16.6"), parseMath("16.6"))
    }

    @Test
    fun aFractionHoldsBothOfItsHalves() {
        val fraction = parseMath("\\frac{a}{b}")
        assertEquals(
            MathNode.Fraction(
                numerator = MathNode.Symbol("a", italic = true),
                denominator = MathNode.Symbol("b", italic = true),
            ),
            fraction,
        )

        // The display and text variants are the same stack: which one it is
        // is the style it lands in, not what was typed.
        assertEquals(fraction, parseMath("\\dfrac{a}{b}"))
        assertEquals(fraction, parseMath("\\tfrac{a}{b}"))
    }

    @Test
    fun aBinomialIsARuleLessFractionInsideItsBrackets() {
        val binomial = parseMath("\\binom{n}{k}")
        assertIs<MathNode.Delimited>(binomial)
        assertEquals("(", binomial.left)
        assertEquals(")", binomial.right)

        val stack: MathNode.Fraction = assertIs<MathNode.Fraction>(binomial.body)
        assertEquals(false, stack.rule)
    }

    @Test
    fun bothScriptsLandOnOneNodeWhicheverOrderTheyWereWritten() {
        val expected = MathNode.Scripts(
            base = MathNode.Symbol("x", italic = true),
            superscript = MathNode.Symbol("2"),
            subscript = MathNode.Symbol("i", italic = true),
        )

        assertEquals(expected, parseMath("x^{2}_{i}"))
        assertEquals(expected, parseMath("x_i^2"))
    }

    @Test
    fun aScriptTakesTheWholeGroupAndNotJustItsFirstToken() {
        val scripts: MathNode.Scripts = assertIs(parseMath("e^{i\\pi}"))
        assertEquals(
            MathNode.Row(
                listOf(
                    MathNode.Symbol("i", italic = true),
                    MathNode.Symbol("π", italic = true),
                ),
            ),
            scripts.superscript,
        )
        assertNull(scripts.subscript)
    }

    @Test
    fun aRootCarriesItsIndexWhenItHasOne() {
        assertEquals(
            MathNode.Root(MathNode.Symbol("x", italic = true)),
            parseMath("\\sqrt{x}"),
        )
        assertEquals(
            MathNode.Root(MathNode.Symbol("x", italic = true), MathNode.Symbol("3")),
            parseMath("\\sqrt[3]{x}"),
        )
    }

    @Test
    fun limitsAttachToTheOperatorRatherThanWrappingIt() {
        val sum: MathNode.BigOperator = assertIs(parseMath("\\sum_{i=1}^n"))
        assertEquals("∑", sum.symbol)
        assertEquals(MathNode.Symbol("n", italic = true), sum.upper)
        assertEquals(
            MathNode.Row(
                listOf(
                    MathNode.Symbol("i", italic = true),
                    MathNode.Operator("="),
                    MathNode.Symbol("1"),
                ),
            ),
            sum.lower,
        )
    }

    @Test
    fun greekMapsToItsLetterAndOnlyTheCapitalsStayUpright() {
        assertEquals(MathNode.Symbol("α", italic = true), parseMath("\\alpha"))
        assertEquals(MathNode.Symbol("ω", italic = true), parseMath("\\omega"))
        assertEquals(MathNode.Symbol("Σ", italic = false), parseMath("\\Sigma"))
        assertEquals(MathNode.Symbol("Ω", italic = false), parseMath("\\Omega"))
    }

    @Test
    fun signsThatBindAreOperatorsAndBracketsAreNot() {
        val row: MathNode.Row = assertIs(parseMath("(a+b) \\leq c"))
        val kinds: List<MathNode> = row.children

        assertEquals(MathNode.Symbol("("), kinds.first())
        assertTrue(kinds.contains(MathNode.Operator("+")))
        assertTrue(kinds.contains(MathNode.Operator("≤")))
        assertEquals(MathNode.Symbol(")"), kinds[4])

        // A hyphen is not a minus sign, and on a slide the difference shows.
        assertEquals(MathNode.Operator("−"), parseMath("-"))
    }

    @Test
    fun aFunctionNameIsUprightAndKeepsItsName() {
        assertEquals(MathNode.Function("sin"), parseMath("\\sin"))
        assertEquals(MathNode.Function("argmax"), parseMath("\\operatorname{argmax}"))
    }

    @Test
    fun leftAndRightWrapWhatIsBetweenThem() {
        val delimited: MathNode.Delimited = assertIs(parseMath("\\left( x \\right)"))
        assertEquals("(", delimited.left)
        assertEquals(")", delimited.right)
        assertEquals(MathNode.Symbol("x", italic = true), delimited.body)

        // The delimiter that isn't one, for a half-open interval.
        val open: MathNode.Delimited = assertIs(parseMath("\\left[ x \\right."))
        assertEquals("[", open.left)
        assertEquals(".", open.right)

        // And a `\left` nobody closed closes on nothing at the end of the input.
        val unclosed: MathNode.Delimited = assertIs(parseMath("\\left\\{ x"))
        assertEquals("{", unclosed.left)
        assertEquals(".", unclosed.right)
    }

    @Test
    fun textIsUprightAndKeepsWhatWasTypedInIt() {
        assertEquals(MathNode.Text("ms"), parseMath("\\text{ms}"))
        assertEquals(MathNode.Text("d x"), parseMath("\\text{d x}"))
        assertEquals(MathNode.Text("max", bold = true), parseMath("\\mathbf{max}"))
    }

    @Test
    fun spacingCommandsAreWidthAndNothingElse() {
        assertEquals(MathNode.Space(0.17f), parseMath("\\,"))
        assertEquals(MathNode.Space(2f), parseMath("\\qquad"))
        assertEquals(MathNode.Space(0.33f), parseMath("~"))

        // The negative one, which pulls the next box back.
        assertEquals(MathNode.Space(-0.17f), parseMath("\\!"))
    }

    @Test
    fun anAccentWrapsWhateverItIsOver() {
        assertEquals(
            MathNode.Accent("hat", MathNode.Symbol("x", italic = true)),
            parseMath("\\hat{x}"),
        )
        // Overline is a wide bar, so it folds onto the same mark.
        assertEquals(
            MathNode.Accent("bar", MathNode.Symbol("y", italic = true)),
            parseMath("\\overline{y}"),
        )
    }

    @Test
    fun anUnknownCommandDrawsAsItself() {
        assertEquals(MathNode.Text("\\wat"), parseMath("\\wat"))
    }

    @Test
    fun aBraceNobodyClosedClosesAtTheEnd() {
        assertEquals(
            MathNode.Fraction(
                numerator = MathNode.Symbol("a", italic = true),
                denominator = MathNode.Symbol("b", italic = true),
            ),
            parseMath("\\frac{a}{b"),
        )
    }

    @Test
    fun aBraceNobodyOpenedIsDropped() {
        assertEquals(MathNode.Symbol("x", italic = true), parseMath("x}"))
    }

    @Test
    fun aCommentRunsToTheEndOfItsLine() {
        assertEquals(MathNode.Symbol("x", italic = true), parseMath("x % and the rest"))

        // Escaped, it is a per-cent sign again.
        assertEquals(MathNode.Symbol("%"), parseMath("\\%"))
    }

    @Test
    fun rowBreaksAndAlignmentTabsAreReadAndIgnored() {
        val row: MathNode.Row = assertIs(parseMath("a & \\\\ b"))
        assertEquals(
            listOf(MathNode.Symbol("a", italic = true), MathNode.Symbol("b", italic = true)),
            row.children,
        )
    }

    @Test
    fun theSampleDeckSEquationParsesIntoEveryConstructItUses() {
        val row: MathNode.Row = assertIs(parseMath(SampleLatex))

        assertTrue(row.children.any { it is MathNode.Scripts })
        assertTrue(row.children.any { it is MathNode.BigOperator })
        assertTrue(row.children.any { it is MathNode.Fraction })
        assertTrue(row.children.any { it is MathNode.Root })
        assertTrue(row.children.any { it is MathNode.Text })
        assertTrue(row.children.any { it is MathNode.Space })
        assertTrue(row.children.any { it is MathNode.Operator })
    }

    private companion object {
        const val SampleLatex: String = "t_{frame} = \\sum_{i=1}^{n} \\frac{w_i}{f} + " +
            "\\sqrt{\\alpha^2 + \\beta^2} \\leq 16.6\\,\\text{ms}"
    }
}

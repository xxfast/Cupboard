package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * An equation is document data like any other element, so all of this is pure
 * functions over elements. What its LaTeX means is [MathParserTest]'s, and what
 * it looks like is the canvas' own test.
 */
class EquationElementTest {
    private val frame = Frame(0f, 0f, 200f, 100f)

    @Test
    fun serializationRoundTripsTheWholeEquation() {
        val document = Document(
            slides = listOf(
                Slide(
                    elements = listOf(
                        EquationElement(
                            frame = frame,
                            latex = "e^{i\\pi} + 1 = 0",
                            fontSize = 64f,
                            color = 0xFF112233,
                            opacity = 0.5f,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(document, loadedDocument(document.encodeToString()))
    }

    /** The tag is what a file on disk carries, so it has to stay "equation". */
    @Test
    fun anEquationDecodesFromItsWrittenForm() {
        val json = """
            {
              "id": "doc",
              "name": "Old",
              "slides": [
                {
                  "id": "slide",
                  "elements": [
                    {
                      "type": "equation",
                      "id": "sum",
                      "frame": { "x": 0.0, "y": 0.0, "width": 10.0, "height": 10.0 },
                      "latex": "x^2"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val element = loadedDocument(json).slides.single().elements.single() as EquationElement
        assertEquals("x^2", element.latex)
        assertEquals(40f, element.fontSize)
        assertEquals(0xFFFFFFFF, element.color)
    }

    @Test
    fun styleCarriesTheLookBetweenEquationsAndLeavesTheLatexBehind() {
        val source = EquationElement(
            id = "source",
            frame = frame,
            latex = "\\alpha",
            fontSize = 72f,
            opacity = 0.5f,
            color = 0xFF112233,
        )
        val target = EquationElement(
            id = "target",
            frame = Frame(50f, 50f, 10f, 10f),
            latex = "x^2",
        )

        val styled = target.applyingStyle(source) as EquationElement
        assertEquals(72f, styled.fontSize)
        assertEquals(0.5f, styled.opacity)
        assertEquals(0xFF112233, styled.color)

        // Content and geometry stay the target's: an equation's latex is its own.
        assertEquals("x^2", styled.latex)
        assertEquals(Frame(50f, 50f, 10f, 10f), styled.frame)
    }

    @Test
    fun aCopiedEquationIsTheSameEquationUnderAFreshId() {
        val element = EquationElement(id = "sum", frame = frame, latex = "x^2")
        val copy = element.withNewIds() as EquationElement

        assertNotEquals(element.id, copy.id)
        assertEquals(element.copy(id = copy.id), copy)
    }

    @Test
    fun anEquationTakesACaretBecauseItsLatexIsEditedInPlace() {
        assertTrue(EquationElement(frame = frame).takesCaret)
        assertEquals(false, ImageElement(frame = frame).takesCaret)
    }

    @Test
    fun aFreshEquationComesWithAnIdentityToRewrite() {
        val element = equationElement(frame)
        assertEquals(frame, element.frame)

        // Euler's, and every piece of it parses: a script, a greek letter, two
        // operators and a relation.
        val row: MathNode.Row = assertIs(parseMath(element.latex))
        assertIs<MathNode.Scripts>(row.children.first())
        assertEquals(2, row.children.count { it is MathNode.Operator })
    }
}

package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A diagram is document data like any other element, so all of this is pure
 * functions over elements. What the chart looks like is the canvas' own test,
 * and where its boxes land is [DiagramLayoutTest]'s.
 */
class DiagramElementTest {
    private val frame = Frame(0f, 0f, 200f, 100f)

    @Test
    fun serializationRoundTripsTheWholeDiagram() {
        val document = Document(
            slides = listOf(
                Slide(
                    elements = listOf(
                        DiagramElement(
                            frame = frame,
                            source = "graph LR\nA --> B",
                            fontSize = 22f,
                            nodeFill = 0xFF112233,
                            nodeStroke = 0xFF445566,
                            nodeText = 0xFF778899,
                            edgeColor = 0xFFAABBCC,
                            steps = listOf(
                                DiagramStep(reveal = listOf("A")),
                                DiagramStep(highlight = listOf("B")),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(document, decodeDocument(document.encodeToString()))
    }

    /** The tag is what a file on disk carries, so it has to stay "diagram". */
    @Test
    fun aDiagramDecodesFromItsWrittenForm() {
        val json = """
            {
              "id": "doc",
              "name": "Old",
              "slides": [
                {
                  "id": "slide",
                  "elements": [
                    {
                      "type": "diagram",
                      "id": "chart",
                      "frame": { "x": 0.0, "y": 0.0, "width": 10.0, "height": 10.0 },
                      "source": "graph LR\nA --> B"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val element = decodeDocument(json).slides.single().elements.single() as DiagramElement
        assertEquals("graph LR\nA --> B", element.source)
        assertEquals(16f, element.fontSize)
        assertEquals(emptyList(), element.steps)
    }

    @Test
    fun styleCarriesTheLookBetweenDiagramsAndLeavesTheSourceBehind() {
        val source = DiagramElement(
            id = "source",
            frame = frame,
            source = "graph TD\nX --> Y",
            fontSize = 30f,
            opacity = 0.5f,
            nodeFill = 0xFF112233,
            nodeStroke = 0xFF445566,
            nodeText = 0xFF778899,
            edgeColor = 0xFFAABBCC,
            steps = listOf(DiagramStep(reveal = listOf("X"))),
        )
        val target = DiagramElement(
            id = "target",
            frame = Frame(50f, 50f, 10f, 10f),
            source = "graph LR\nA --> B",
        )

        val styled = target.applyingStyle(source) as DiagramElement
        assertEquals(30f, styled.fontSize)
        assertEquals(0.5f, styled.opacity)
        assertEquals(0xFF112233, styled.nodeFill)
        assertEquals(0xFFAABBCC, styled.edgeColor)

        // Content and geometry stay the target's: a chart's steps are its own.
        assertEquals("graph LR\nA --> B", styled.source)
        assertEquals(emptyList(), styled.steps)
        assertEquals(Frame(50f, 50f, 10f, 10f), styled.frame)
    }

    @Test
    fun aCopiedDiagramIsTheSameDiagramUnderAFreshId() {
        val element = DiagramElement(id = "chart", frame = frame, source = "graph LR\nA --> B")
        val copy = element.withNewIds() as DiagramElement

        assertNotEquals(element.id, copy.id)
        assertEquals(element.copy(id = copy.id), copy)
    }

    @Test
    fun aDiagramTakesACaretBecauseItsSourceIsEditedInPlace() {
        assertTrue(DiagramElement(frame = frame).takesCaret)
        assertEquals(false, ImageElement(frame = frame).takesCaret)
    }

    @Test
    fun aFreshDiagramComesWithAChartToRewrite() {
        val element = diagramElement(frame)
        assertEquals(frame, element.frame)

        val graph: DiagramGraph = parseDiagram(element.source)
        assertEquals(DiagramDirection.LeftRight, graph.direction)
        assertEquals(listOf("A", "B", "C", "D"), graph.nodes.map { it.id })
        // The last one closes the loop back to A, so the starter chart cycles.
        assertEquals(listOf("yes", "no"), graph.edges.mapNotNull { it.label.ifEmpty { null } })
        assertEquals(4, graph.edges.size)
    }
}

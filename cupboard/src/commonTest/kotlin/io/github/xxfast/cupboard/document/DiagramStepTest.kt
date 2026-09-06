package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The step model a diagram is walked through, and the build field it rides on.
 * All pure and all document-side, so the renderer and anything that exports later
 * read one interpretation.
 */
class DiagramStepTest {
    private val frame = Frame(0f, 0f, 200f, 100f)
    private val graph: DiagramGraph = parseDiagram("graph LR\nA --> B --> C")

    private fun steppedDiagram(vararg steps: DiagramStep): DiagramElement = DiagramElement(
        id = "diagram",
        frame = frame,
        source = "graph LR\nA --> B --> C",
        steps = steps.toList(),
    )

    @Test
    fun anEmptyRevealShowsEverythingAndANamedOneShowsOnlyWhatItNames() {
        assertEquals(setOf("A", "B", "C"), visibleNodes(graph, DiagramStep()))
        assertEquals(setOf("A", "B"), visibleNodes(graph, DiagramStep(reveal = listOf("A", "B"))))
    }

    @Test
    fun anEmptyHighlightDimsNothingAtAll() {
        assertTrue(highlightedNodes(graph, DiagramStep()).isEmpty())
        assertEquals(
            setOf("B"),
            highlightedNodes(graph, DiagramStep(highlight = listOf("B"))),
        )
    }

    @Test
    fun anIdNoNodeCarriesIsDroppedRatherThanCountedIn() {
        val step = DiagramStep(reveal = listOf("A", "Z"), highlight = listOf("A", "Z"))

        assertEquals(setOf("A"), visibleNodes(graph, step))
        assertEquals(setOf("A"), highlightedNodes(graph, step))
    }

    @Test
    fun anEdgeIsInASetOnlyWhenBothOfItsEndsAre() {
        val edge: DiagramEdge = graph.edges.first { it.from == "A" }

        assertTrue(edge.joins(setOf("A", "B")))
        assertTrue(!edge.joins(setOf("A")))
        assertTrue(!edge.joins(emptySet()))
    }

    @Test
    fun aStepBuildResolvesToTheStateItPointsAtAndClampsPastTheEnd() {
        val diagram: DiagramElement = steppedDiagram(
            DiagramStep(reveal = listOf("A")),
            DiagramStep(highlight = listOf("C")),
        )
        val slide = Slide(
            elements = listOf(diagram),
            builds = listOf(Build(diagram.id), Build(diagram.id, elementStep = 7)),
        )

        // Visible from its first build, so the state before any step build is 0.
        assertEquals(diagram.steps[0], slide.diagramStepFor(diagram, step = 1))
        // And an index past the end clamps rather than throwing.
        assertEquals(diagram.steps[1], slide.diagramStepFor(diagram, step = 2))
        assertNull(slide.diagramStepFor(diagram.copy(steps = emptyList()), step = 2))
    }

    @Test
    fun oneBuildFieldStepsWhicheverKindTheBuildNames() {
        val diagram: DiagramElement = steppedDiagram(DiagramStep(), DiagramStep())
        val code = CodeElement(id = "code", frame = frame, code = "1\n2", steps = listOf(CodeStep()))
        val slide = Slide(
            elements = listOf(code, diagram),
            builds = listOf(
                Build(code.id),
                Build(diagram.id, elementStep = 1),
            ),
        )

        assertEquals(1, slide.elementStepAt(diagram.id, step = 2))
        assertNull(slide.elementStepAt(code.id, step = 2))
    }

    /** The tag on disk stays "codeStep": every deck already written carries it. */
    @Test
    fun aStepBuildIsStillWrittenAndReadAsCodeStep() {
        val document = Document(
            slides = listOf(Slide(id = "slide", builds = listOf(Build("x", elementStep = 2)))),
        )

        assertTrue(document.encodeToString().contains("\"codeStep\": 2"))

        val json = """
            {
              "id": "doc",
              "name": "Old",
              "slides": [
                { "id": "slide", "builds": [{ "elementId": "x", "codeStep": 2 }] }
              ]
            }
        """.trimIndent()
        assertEquals(2, decodeDocument(json).slides.single().builds.single().elementStep)
    }
}

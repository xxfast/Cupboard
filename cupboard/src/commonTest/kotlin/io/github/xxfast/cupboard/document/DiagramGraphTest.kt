package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The mermaid subset the canvas draws. Every one of these is a source string in
 * and a graph out: the parser has no state and no IO, which is the whole reason
 * it can run on a keystroke.
 */
class DiagramGraphTest {
    private fun nodeIds(graph: DiagramGraph): List<String> = graph.nodes.map { it.id }

    @Test
    fun theHeaderPicksTheDirectionAndAMissingOneIsTopDown() {
        assertEquals(DiagramDirection.LeftRight, parseDiagram("graph LR\nA --> B").direction)
        assertEquals(DiagramDirection.TopDown, parseDiagram("graph TD\nA --> B").direction)
        assertEquals(DiagramDirection.RightLeft, parseDiagram("graph RL\nA --> B").direction)
        assertEquals(DiagramDirection.BottomTop, parseDiagram("graph BT\nA --> B").direction)
        assertEquals(DiagramDirection.LeftRight, parseDiagram("flowchart LR\nA --> B").direction)

        // TB is TD's other spelling, and no header at all is TD as well.
        assertEquals(DiagramDirection.TopDown, parseDiagram("flowchart TB\nA --> B").direction)
        assertEquals(DiagramDirection.TopDown, parseDiagram("A --> B").direction)
    }

    @Test
    fun everyBracketFormIsItsOwnShape() {
        val graph: DiagramGraph = parseDiagram(
            """
                graph LR
                  A[Box]
                  B(Rounded)
                  C([Stadium])
                  D{Diamond}
                  E((Circle))
                  F
            """.trimIndent()
        )

        assertEquals(listOf("A", "B", "C", "D", "E", "F"), nodeIds(graph))
        assertEquals(
            listOf(
                DiagramNodeShape.Box,
                DiagramNodeShape.Rounded,
                DiagramNodeShape.Stadium,
                DiagramNodeShape.Diamond,
                DiagramNodeShape.Circle,
                DiagramNodeShape.Box,
            ),
            graph.nodes.map { it.shape },
        )
        // An unlabelled node draws its own id.
        assertEquals("F", graph.nodes.last().label)
    }

    @Test
    fun aQuotedLabelLosesItsQuotes() {
        val graph: DiagramGraph = parseDiagram("""graph LR
            A["Draw the frame"] --> B{"Ship?"}
        """.trimIndent())

        assertEquals("Draw the frame", graph.nodes[0].label)
        assertEquals("Ship?", graph.nodes[1].label)
        assertEquals(DiagramNodeShape.Diamond, graph.nodes[1].shape)
    }

    @Test
    fun eachConnectorCarriesItsOwnStyleAndHead() {
        val graph: DiagramGraph = parseDiagram(
            """
                graph LR
                  A --> B
                  A --- C
                  A -.-> D
                  A ==> E
                  A -.- F
            """.trimIndent()
        )

        assertEquals(
            listOf(
                DiagramEdgeStyle.Solid,
                DiagramEdgeStyle.Solid,
                DiagramEdgeStyle.Dotted,
                DiagramEdgeStyle.Thick,
                DiagramEdgeStyle.Dotted,
            ),
            graph.edges.map { it.style },
        )
        assertEquals(listOf(true, false, true, true, false), graph.edges.map { it.arrow })
    }

    @Test
    fun bothLabelSyntaxesLandOnTheEdge() {
        val graph: DiagramGraph = parseDiagram(
            """
                graph LR
                  A -- yes --> B
                  A -->|no| C
                  A -. cache .-> D
                  A == hot ==> E
                  A -.->|cold| F
            """.trimIndent()
        )

        assertEquals(
            listOf("yes", "no", "cache", "hot", "cold"),
            graph.edges.map { it.label },
        )
        assertEquals(DiagramEdgeStyle.Dotted, graph.edges.last().style)
    }

    @Test
    fun aChainIsOneEdgePerHop() {
        val graph: DiagramGraph = parseDiagram("graph LR\nA[One] --> B --> C[Three]")

        assertEquals(listOf("A", "B", "C"), nodeIds(graph))
        assertEquals(listOf("A" to "B", "B" to "C"), graph.edges.map { it.from to it.to })
        assertEquals("Three", graph.nodes[2].label)
    }

    @Test
    fun anAmpersandFansOverEveryPairOfEnds() {
        val out: DiagramGraph = parseDiagram("graph LR\nA --> B & C")
        assertEquals(listOf("A" to "B", "A" to "C"), out.edges.map { it.from to it.to })

        val into: DiagramGraph = parseDiagram("graph LR\nA & B --> C")
        assertEquals(listOf("A" to "C", "B" to "C"), into.edges.map { it.from to it.to })

        val both: DiagramGraph = parseDiagram("graph LR\nA & B --> C & D")
        assertEquals(4, both.edges.size)
    }

    @Test
    fun semicolonsSeparateStatementsOnOneLine() {
        val graph: DiagramGraph = parseDiagram("graph LR; A --> B; B --> C; D[Alone]")

        assertEquals(listOf("A", "B", "C", "D"), nodeIds(graph))
        assertEquals(2, graph.edges.size)
        assertEquals(DiagramDirection.LeftRight, graph.direction)
    }

    @Test
    fun commentsAndSubgraphChromeAreSkippedButWhatTheyHoldIsNot() {
        val graph: DiagramGraph = parseDiagram(
            """
                graph LR
                %% the render pass
                  subgraph render
                    A --> B
                  end
                  B --> C
            """.trimIndent()
        )

        assertEquals(listOf("A", "B", "C"), nodeIds(graph))
        assertEquals(2, graph.edges.size)
    }

    @Test
    fun oneUnparseableLineCostsItselfAndNothingElse() {
        val graph: DiagramGraph = parseDiagram(
            """
                graph LR
                  A --> B
                  click A callSomething
                  B -->
                  !!! nonsense
                  B --> C
            """.trimIndent()
        )

        assertEquals(listOf("A", "B", "C"), nodeIds(graph))
        assertEquals(listOf("A" to "B", "B" to "C"), graph.edges.map { it.from to it.to })
    }

    @Test
    fun aLabelGivenLaterWinsOverTheBareMentionThatCameFirst() {
        val graph: DiagramGraph = parseDiagram(
            """
                graph LR
                  A --> B
                  B{Ship?} --> C
            """.trimIndent()
        )

        val labelled: DiagramNode = graph.nodes.single { it.id == "B" }
        assertEquals("Ship?", labelled.label)
        assertEquals(DiagramNodeShape.Diamond, labelled.shape)
        // And the node keeps the place its first mention gave it.
        assertEquals(listOf("A", "B", "C"), nodeIds(graph))
    }

    @Test
    fun anEmptySourceIsAnEmptyGraphRatherThanAFailure() {
        val graph: DiagramGraph = parseDiagram("")

        assertTrue(graph.nodes.isEmpty())
        assertTrue(graph.edges.isEmpty())
        assertEquals(DiagramDirection.TopDown, graph.direction)
    }
}

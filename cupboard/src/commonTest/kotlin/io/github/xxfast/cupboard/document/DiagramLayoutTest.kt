package io.github.xxfast.cupboard.document

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The layered placement. Nothing here checks a pixel: the assertions are the
 * properties a reader would notice, which is what has to hold on every target
 * rather than one set of coordinates that happened to come out today.
 */
class DiagramLayoutTest {
    private val fontSize = 16f

    private fun layoutOf(source: String): DiagramLayout =
        layoutDiagram(parseDiagram(source), fontSize)

    private fun DiagramLayout.frameOf(id: String): Frame =
        nodes.single { it.node.id == id }.frame

    /** Whether [point] sits on [frame]'s outline, give or take rounding. */
    private fun onBoundary(frame: Frame, point: DiagramPoint): Boolean {
        val onSide: Boolean = abs(abs(point.x - frame.centerX) - frame.width / 2f) < 0.5f
        val onCap: Boolean = abs(abs(point.y - frame.centerY) - frame.height / 2f) < 0.5f
        val inX: Boolean = point.x >= frame.x - 0.5f && point.x <= frame.x + frame.width + 0.5f
        val inY: Boolean = point.y >= frame.y - 0.5f && point.y <= frame.y + frame.height + 0.5f
        return (onSide && inY) || (onCap && inX)
    }

    @Test
    fun aChainRunsRightInLeftRightAndDownInTopDown() {
        val across: DiagramLayout = layoutOf("graph LR\nA --> B --> C")
        val a: Frame = across.frameOf("A")
        val b: Frame = across.frameOf("B")
        val c: Frame = across.frameOf("C")

        assertTrue(a.x < b.x && b.x < c.x, "ranks march right: ${a.x}, ${b.x}, ${c.x}")
        assertEquals(a.centerY, b.centerY)
        assertEquals(b.centerY, c.centerY)

        val down: DiagramLayout = layoutOf("graph TD\nA --> B --> C")
        assertTrue(down.frameOf("A").y < down.frameOf("B").y)
        assertTrue(down.frameOf("B").y < down.frameOf("C").y)
        assertEquals(down.frameOf("A").centerX, down.frameOf("B").centerX)
    }

    @Test
    fun theMirroredDirectionsRunTheOtherWay() {
        val back: DiagramLayout = layoutOf("graph RL\nA --> B --> C")
        assertTrue(back.frameOf("A").x > back.frameOf("C").x)

        val up: DiagramLayout = layoutOf("graph BT\nA --> B --> C")
        assertTrue(up.frameOf("A").y > up.frameOf("C").y)
    }

    @Test
    fun aFanPutsBothTargetsInOneRankCentredOnTheirSource() {
        val layout: DiagramLayout = layoutOf("graph TD\nA --> B\nA --> C")
        val a: Frame = layout.frameOf("A")
        val b: Frame = layout.frameOf("B")
        val c: Frame = layout.frameOf("C")

        // One rank: same y, and one rank on from A.
        assertEquals(b.y, c.y)
        assertTrue(b.y > a.y)
        assertEquals(a.centerX, (b.centerX + c.centerX) / 2f, absoluteTolerance = 0.5f)
    }

    @Test
    fun nothingOverlapsInASixNodeGraphThatLoops() {
        val layout: DiagramLayout = layoutOf(
            """
                graph LR
                  A --> B --> C --> D
                  B --> E
                  D --> F
                  F --> B
            """.trimIndent()
        )

        assertEquals(6, layout.nodes.size)
        for (first in layout.nodes.indices) {
            for (second in first + 1 until layout.nodes.size) {
                val one: LaidOutNode = layout.nodes[first]
                val other: LaidOutNode = layout.nodes[second]
                assertTrue(
                    !one.frame.overlaps(other.frame),
                    "${one.node.id} overlaps ${other.node.id}",
                )
            }
        }
    }

    @Test
    fun aCycleStillRanksRatherThanRunningForever() {
        val layout: DiagramLayout = layoutOf("graph LR\nA --> B --> C --> A")

        assertEquals(3, layout.nodes.size)
        assertEquals(3, layout.edges.size)
        // The cycle is broken for ranking only, so the three still spread out.
        assertTrue(layout.frameOf("A").x < layout.frameOf("B").x)
        assertTrue(layout.frameOf("B").x < layout.frameOf("C").x)

        // And the edge that closes it bows out rather than cutting back through.
        val closing: LaidOutEdge = layout.edges.single { it.edge.from == "C" }
        assertEquals(3, closing.points.size)
        assertTrue(closing.points[1].y > layout.frameOf("B").y + layout.frameOf("B").height)
    }

    @Test
    fun everyEdgeStartsAndEndsOnItsNodesOutline() {
        val layout: DiagramLayout = layoutOf(
            """
                graph LR
                  A[Compose] --> B{Ship?}
                  B --> C((Play))
                  B --> D([Wait])
                  C --> A
            """.trimIndent()
        )

        for (edge in layout.edges) {
            val from: Frame = layout.frameOf(edge.edge.from)
            val to: Frame = layout.frameOf(edge.edge.to)
            assertTrue(onBoundary(from, edge.points.first()), "start of ${edge.edge}")
            assertTrue(onBoundary(to, edge.points.last()), "end of ${edge.edge}")
        }
    }

    @Test
    fun aLabelledEdgeGetsAPlaceForItsTextAndAPlainOneDoesNot() {
        val layout: DiagramLayout = layoutOf("graph LR\nA -->|yes| B\nA --> C")

        val labelled: LaidOutEdge = layout.edges.single { it.edge.label == "yes" }
        val plain: LaidOutEdge = layout.edges.single { it.edge.label.isEmpty() }
        assertEquals(null, plain.labelAt)
        assertEquals(
            (labelled.points.first().x + labelled.points.last().x) / 2f,
            labelled.labelAt?.x,
        )
    }

    @Test
    fun theBoundsHoldEveryNodeWithAMarginRoundTheOutside() {
        val layout: DiagramLayout = layoutOf(
            """
                graph TD
                  A[Compose] --> B[Layout] --> C[Draw]
                  A --> D((Present))
            """.trimIndent()
        )

        assertEquals(fontSize, layout.nodes.minOf { it.frame.x })
        assertEquals(fontSize, layout.nodes.minOf { it.frame.y })
        assertEquals(layout.nodes.maxOf { it.frame.x + it.frame.width } + fontSize, layout.width)
        assertEquals(layout.nodes.maxOf { it.frame.y + it.frame.height } + fontSize, layout.height)
    }

    @Test
    fun aDiamondTakesMoreBoxThanABoxWithTheSameLabel() {
        val layout: DiagramLayout = layoutOf("graph LR\nA[Ship] --> B{Ship}")

        assertTrue(layout.frameOf("B").width > layout.frameOf("A").width)
        assertTrue(layout.frameOf("B").height > layout.frameOf("A").height)
    }

    @Test
    fun anEmptyGraphIsAnEmptyBoxRatherThanADivisionByZero() {
        val layout: DiagramLayout = layoutDiagram(parseDiagram(""), fontSize)

        assertTrue(layout.nodes.isEmpty())
        assertTrue(layout.width > 0f && layout.height > 0f)
    }
}

package io.github.xxfast.cupboard.document

import kotlin.math.abs

/** A point in the layout's own space: document units at 1x, origin top-left. */
data class DiagramPoint(val x: Float, val y: Float)

/** One node and the box it was given. */
data class LaidOutNode(val node: DiagramNode, val frame: Frame)

/**
 * One edge as a polyline. [points] is two for a straight run and three for a back
 * edge that has to bow around the ranks it returns through. [labelAt] is where the
 * edge's text sits, and is null when it has none.
 */
data class LaidOutEdge(
    val edge: DiagramEdge,
    val points: List<DiagramPoint>,
    val labelAt: DiagramPoint?,
)

/**
 * A whole diagram placed, in document units at 1x. [width] and [height] are what
 * the renderer scales to fit the element's frame, so nothing here knows what size
 * it is drawn at.
 */
data class DiagramLayout(
    val nodes: List<LaidOutNode>,
    val edges: List<LaidOutEdge>,
    val width: Float,
    val height: Float,
)

/** A node's box before it is placed. */
private class NodeSize(val width: Float, val height: Float)

/** How wide a character runs against the font's size, close enough to centre a label by. */
private const val LabelAspect: Float = 0.62f

/** A node's height, and the padding either side of its label, against the font's size. */
private const val NodeHeightScale: Float = 2.4f
private const val NodeMinWidthScale: Float = 3f

/** A rhombus wastes its corners, so a diamond takes more box than its label needs. */
private const val DiamondWidthScale: Float = 1.4f
private const val DiamondHeightScale: Float = 1.6f

/** Between two ranks along the flow, and between two nodes across it. */
private const val RankGapScale: Float = 3.5f
private const val NodeGapScale: Float = 1.5f

/** How many alternating barycenter sweeps the ordering gets before it is called done. */
private const val OrderingSweeps: Int = 4

private fun nodeSize(node: DiagramNode, fontSize: Float): NodeSize {
    val width: Float = maxOf(
        node.label.length * fontSize * LabelAspect + 2 * fontSize,
        NodeMinWidthScale * fontSize,
    )
    val height: Float = fontSize * NodeHeightScale

    return when (node.shape) {
        // A circle has to hold its label on every diameter, so it takes the square.
        DiagramNodeShape.Circle -> maxOf(width, height).let { NodeSize(it, it) }
        DiagramNodeShape.Diamond -> NodeSize(width * DiamondWidthScale, height * DiamondHeightScale)
        else -> NodeSize(width, height)
    }
}

/**
 * Every node's rank: the longest path to it from a source, over the graph with
 * its cycles broken.
 *
 * Cycles are broken by a depth-first walk, and only for the ranking: an edge back
 * to a node still on the stack is counted the other way round, so the ranks come
 * out of a DAG and the edge is still drawn where it was written. That is what
 * keeps a chart with a loop in it terminating and readable at once.
 */
private fun ranksOf(graph: DiagramGraph): Map<String, Int> {
    val out: Map<String, List<String>> = graph.edges
        .filter { it.from != it.to }
        .groupBy({ it.from }, { it.to })

    // 0 unvisited, 1 on the stack, 2 done.
    val state: MutableMap<String, Int> = mutableMapOf()
    val acyclic: MutableList<Pair<String, String>> = mutableListOf()
    val stack: MutableList<Pair<String, Int>> = mutableListOf()

    for (start in graph.nodes) {
        if (state[start.id] == 2) continue
        stack += start.id to 0
        state[start.id] = 1

        while (stack.isNotEmpty()) {
            val (id: String, index: Int) = stack.removeAt(stack.lastIndex)
            val next: List<String> = out[id].orEmpty()
            if (index == next.size) {
                state[id] = 2
                continue
            }

            stack += id to index + 1
            val child: String = next[index]
            when (state[child] ?: 0) {
                // Still on the stack, so this closes a cycle: rank it backwards.
                1 -> acyclic += child to id
                0 -> {
                    acyclic += id to child
                    state[child] = 1
                    stack += child to 0
                }

                else -> acyclic += id to child
            }
        }
    }

    val successors: Map<String, List<String>> = acyclic.groupBy({ it.first }, { it.second })
    val incoming: MutableMap<String, Int> = mutableMapOf()
    for ((_, to) in acyclic) incoming[to] = (incoming[to] ?: 0) + 1

    val ranks: MutableMap<String, Int> = graph.nodes.associateTo(mutableMapOf()) { it.id to 0 }
    val queue: ArrayDeque<String> =
        ArrayDeque(graph.nodes.map { it.id }.filter { (incoming[it] ?: 0) == 0 })

    while (queue.isNotEmpty()) {
        val id: String = queue.removeFirst()
        for (child in successors[id].orEmpty()) {
            ranks[child] = maxOf(ranks.getValue(child), ranks.getValue(id) + 1)
            val left: Int = (incoming.getValue(child)) - 1
            incoming[child] = left
            if (left == 0) queue.addLast(child)
        }
    }

    return ranks
}

/**
 * Which node sits where in each rank, as a list per rank.
 *
 * Barycenter ordering: a node wants to sit at the average position of what it is
 * joined to in the rank next door, and four alternating sweeps is where the
 * shuffling stops paying for itself. Ties go to first appearance, so a source
 * that says nothing about its order keeps the one the text gave it.
 */
private fun orderRanks(
    graph: DiagramGraph,
    ranks: Map<String, Int>,
    rankCount: Int,
): List<MutableList<String>> {
    val order: List<MutableList<String>> = List(rankCount) { mutableListOf() }
    for (node in graph.nodes) order[ranks.getValue(node.id)] += node.id

    val appearance: Map<String, Int> =
        graph.nodes.withIndex().associate { (index, node) -> node.id to index }
    val incident: Map<String, List<String>> = graph.edges
        .filter { it.from != it.to }
        .flatMap { listOf(it.from to it.to, it.to to it.from) }
        .groupBy({ it.first }, { it.second })

    repeat(OrderingSweeps) { sweep ->
        val forwards: Boolean = sweep % 2 == 0
        val walk: IntProgression = if (forwards) 1 until rankCount else rankCount - 2 downTo 0
        val neighbour: Int = if (forwards) -1 else 1

        for (rank in walk) {
            val places: Map<String, Int> = order[rank + neighbour]
                .withIndex()
                .associate { (index, id) -> id to index }
            val keys: Map<String, Float> = order[rank].withIndex().associate { (index, id) ->
                val anchors: List<Int> = incident[id].orEmpty().mapNotNull { places[it] }
                // Nothing next door to be pulled by, so it holds its place.
                id to if (anchors.isEmpty()) index.toFloat() else anchors.sum().toFloat() / anchors.size
            }

            val sorted: List<String> = order[rank].sortedWith(
                compareBy<String> { keys.getValue(it) }.thenBy { appearance.getValue(it) }
            )
            order[rank].clear()
            order[rank].addAll(sorted)
        }
    }

    return order
}

/** Where the centre-to-[towards] line leaves [frame]. */
private fun boundaryPoint(frame: Frame, towards: DiagramPoint): DiagramPoint {
    val dx: Float = towards.x - frame.centerX
    val dy: Float = towards.y - frame.centerY
    if (dx == 0f && dy == 0f) return DiagramPoint(frame.centerX, frame.centerY)

    // The first of the two edges the ray would cross wins, which is the smaller
    // of the two scale factors that put it on one.
    val toSide: Float = if (dx == 0f) Float.MAX_VALUE else frame.width / 2f / abs(dx)
    val toCap: Float = if (dy == 0f) Float.MAX_VALUE else frame.height / 2f / abs(dy)
    val reach: Float = minOf(toSide, toCap)
    return DiagramPoint(frame.centerX + dx * reach, frame.centerY + dy * reach)
}

/**
 * [graph] placed, in document units at 1x, sized against [fontSize].
 *
 * Layered, the way every flowchart drawing wants to be: nodes get a rank from the
 * longest path into them, ranks are spread along the flow direction, and the
 * nodes inside one are ordered so their edges cross as little as they can. The
 * whole thing is a pure function of the graph and the font size, so the same
 * source draws the same picture on every target and nothing about a position is
 * ever stored.
 *
 * Straight edges run boundary to boundary. An edge back up the ranks bows out
 * across the flow instead, so a loop is read as a loop rather than as a line
 * through the middle of the chart.
 */
fun layoutDiagram(graph: DiagramGraph, fontSize: Float): DiagramLayout {
    val margin: Float = fontSize
    if (graph.nodes.isEmpty()) {
        return DiagramLayout(emptyList(), emptyList(), 2 * margin, 2 * margin)
    }

    val rankGap: Float = fontSize * RankGapScale
    val nodeGap: Float = fontSize * NodeGapScale
    val sizes: Map<String, NodeSize> = graph.nodes.associate { it.id to nodeSize(it, fontSize) }
    val ranks: Map<String, Int> = ranksOf(graph)
    val rankCount: Int = (ranks.values.maxOrNull() ?: 0) + 1
    val order: List<MutableList<String>> = orderRanks(graph, ranks, rankCount)

    // The flow axis is the one the ranks march along; the cross axis is the one
    // a rank is laid out on. Naming them rather than branching on the direction
    // in every line below is what keeps the four directions one piece of code.
    val vertical: Boolean = graph.direction == DiagramDirection.TopDown ||
        graph.direction == DiagramDirection.BottomTop
    val mirrored: Boolean = graph.direction == DiagramDirection.RightLeft ||
        graph.direction == DiagramDirection.BottomTop

    fun NodeSize.flow(): Float = if (vertical) height else width
    fun NodeSize.cross(): Float = if (vertical) width else height

    val depths = FloatArray(rankCount) { rank ->
        order[rank].maxOfOrNull { sizes.getValue(it).flow() } ?: 0f
    }
    val breadths = FloatArray(rankCount) { rank ->
        val nodes: List<String> = order[rank]
        if (nodes.isEmpty()) 0f
        else nodes.sumOf { sizes.getValue(it).cross().toDouble() }.toFloat() +
            (nodes.size - 1) * nodeGap
    }
    val widest: Float = breadths.maxOrNull() ?: 0f

    val starts = FloatArray(rankCount)
    var along = 0f
    for (rank in 0 until rankCount) {
        starts[rank] = along
        along += depths[rank] + rankGap
    }
    val totalFlow: Float = (along - rankGap).coerceAtLeast(0f)

    val frames: MutableMap<String, Frame> = mutableMapOf()
    for (rank in 0 until rankCount) {
        var across: Float = (widest - breadths[rank]) / 2f
        for (id in order[rank]) {
            val size: NodeSize = sizes.getValue(id)
            val centred: Float = starts[rank] + (depths[rank] - size.flow()) / 2f
            val flow: Float = if (mirrored) totalFlow - centred - size.flow() else centred
            frames[id] =
                if (vertical) Frame(across, flow, size.width, size.height)
                else Frame(flow, across, size.width, size.height)
            across += size.cross() + nodeGap
        }
    }

    // Everything is placed from zero so far; slide it so the topmost, leftmost
    // node corner lands on the margin and the bounds hold the lot.
    val dx: Float = margin - frames.values.minOf { it.x }
    val dy: Float = margin - frames.values.minOf { it.y }
    val placed: Map<String, Frame> = frames.mapValues { (_, frame) -> frame.translate(dx, dy) }

    val laidOut: List<LaidOutNode> = graph.nodes.map { LaidOutNode(it, placed.getValue(it.id)) }
    val edges: List<LaidOutEdge> = graph.edges.mapNotNull { edge ->
        val from: Frame = placed[edge.from] ?: return@mapNotNull null
        val to: Frame = placed[edge.to] ?: return@mapNotNull null
        val backwards: Boolean = ranks.getValue(edge.to) <= ranks.getValue(edge.from)

        if (!backwards) {
            val start: DiagramPoint = boundaryPoint(from, DiagramPoint(to.centerX, to.centerY))
            val end: DiagramPoint = boundaryPoint(to, DiagramPoint(from.centerX, from.centerY))
            return@mapNotNull LaidOutEdge(
                edge = edge,
                points = listOf(start, end),
                labelAt = if (edge.label.isEmpty()) null else {
                    DiagramPoint((start.x + end.x) / 2f, (start.y + end.y) / 2f)
                },
            )
        }

        // Out past the far side of both nodes, so the return trip goes around the
        // chart rather than back through it.
        val bow: DiagramPoint =
            if (vertical) DiagramPoint(
                x = maxOf(from.x + from.width, to.x + to.width) + nodeGap,
                y = (from.centerY + to.centerY) / 2f,
            ) else DiagramPoint(
                x = (from.centerX + to.centerX) / 2f,
                y = maxOf(from.y + from.height, to.y + to.height) + nodeGap,
            )

        LaidOutEdge(
            edge = edge,
            points = listOf(boundaryPoint(from, bow), bow, boundaryPoint(to, bow)),
            labelAt = if (edge.label.isEmpty()) null else bow,
        )
    }

    return DiagramLayout(
        nodes = laidOut,
        edges = edges,
        width = laidOut.maxOf { it.frame.x + it.frame.width } + margin,
        height = laidOut.maxOf { it.frame.y + it.frame.height } + margin,
    )
}

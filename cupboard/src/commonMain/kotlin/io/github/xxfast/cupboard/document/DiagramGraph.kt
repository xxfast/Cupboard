package io.github.xxfast.cupboard.document

/** Which way a flowchart runs. `TB` in the source means the same as `TD`. */
enum class DiagramDirection { LeftRight, TopDown, RightLeft, BottomTop }

/** The node outlines the subset spells: `[Box]`, `(Rounded)`, `([Stadium])`, `{Diamond}`, `((Circle))`. */
enum class DiagramNodeShape { Box, Rounded, Stadium, Diamond, Circle }

/** How an edge is drawn: `-->` solid, `-.->` dotted, `==>` thick. */
enum class DiagramEdgeStyle { Solid, Dotted, Thick }

/** One node. [label] is what it draws, and falls back to [id] when nothing named it. */
data class DiagramNode(
    val id: String,
    val label: String,
    val shape: DiagramNodeShape,
)

/** One edge. [arrow] is off for the headless connectors (`---`, `-.-`, `===`). */
data class DiagramEdge(
    val from: String,
    val to: String,
    val label: String = "",
    val style: DiagramEdgeStyle = DiagramEdgeStyle.Solid,
    val arrow: Boolean = true,
)

/** A parsed flowchart: what it holds and which way it flows. Nothing about position. */
data class DiagramGraph(
    val direction: DiagramDirection,
    val nodes: List<DiagramNode>,
    val edges: List<DiagramEdge>,
)

private const val NodeIdPattern: String = "[A-Za-z0-9_]+"

/**
 * An id and at most one bracket form. The alternatives are ordered longest-first
 * so `((circle))` isn't read as a `(rounded)` holding a stray paren, and `([`
 * beats both of the single brackets it is made of.
 */
private val NodeToken = Regex(
    "\\s*($NodeIdPattern)" +
        "(?:\\(\\((.*)\\)\\)|\\(\\[(.*)\\]\\)|\\[(.*)\\]|\\((.*)\\)|\\{(.*)\\})?\\s*"
)

/** The shape each of [NodeToken]'s bracket groups spells, in group order. */
private val TokenShapes: List<DiagramNodeShape> = listOf(
    DiagramNodeShape.Circle,
    DiagramNodeShape.Stadium,
    DiagramNodeShape.Box,
    DiagramNodeShape.Rounded,
    DiagramNodeShape.Diamond,
)

private val Header = Regex(
    "\\s*(?:graph|flowchart)(?:\\s+([A-Za-z]{2}))?\\s*",
    RegexOption.IGNORE_CASE,
)

/**
 * Every connector the subset knows, longest-first.
 *
 * The three labelled forms demand whitespace either side of their text, which is
 * what keeps `A --> B` out of `A -- text --> B`'s hands: the character after the
 * dashes decides between them, so no backtracking gets one to match the other.
 */
private val Connector = Regex(
    "-\\.\\s*(.*?)\\s*\\.->" +
        "|-\\.->" +
        "|-\\.-" +
        "|==\\s+(.*?)\\s+==>" +
        "|==>" +
        "|===" +
        "|--\\s+(.*?)\\s+-->" +
        "|-->" +
        "|---"
)

/** A connector's label written after it rather than inside it: `A -->|text| B`. */
private val PipeLabel = Regex("^\\s*\\|([^|]*)\\|")

/** One node mention: its id, plus the label and shape it was given, if any. */
private class ParsedNode(
    val id: String,
    val label: String?,
    val shape: DiagramNodeShape?,
)

/** A connector between two node groups, as the statement wrote it. */
private class ParsedEdge(
    val label: String,
    val style: DiagramEdgeStyle,
    val arrow: Boolean,
)

/** `"quoted"` labels lose their quotes; everything else is taken as typed. */
private fun String.unquoted(): String =
    if (length >= 2 && startsWith('"') && endsWith('"')) substring(1, length - 1) else this

private fun parseNodeToken(token: String): ParsedNode? {
    val match: MatchResult = NodeToken.matchEntire(token) ?: return null
    val id: String = match.groupValues[1]

    for (group in 2..6) {
        if (match.groups[group] == null) continue
        val label: String = match.groupValues[group].trim().unquoted()
        return ParsedNode(id, label.ifEmpty { id }, TokenShapes[group - 2])
    }
    return ParsedNode(id, label = null, shape = null)
}

/** `A`, or `A & B`: the nodes one end of a connector fans over. Null when it isn't one. */
private fun parseNodeGroup(segment: String): List<ParsedNode>? {
    if (segment.isBlank()) return null
    return segment.split("&").map { parseNodeToken(it) ?: return null }
}

/**
 * A flowchart out of its source, in the mermaid subset the canvas draws.
 *
 * Never throws and never gives up on the whole source: a statement that doesn't
 * parse is dropped and the next one is tried, so a typo mid-deck costs one edge
 * rather than the slide it is on. That is the same bet [CodeStep]'s ranges make,
 * and it is what lets the source be edited live on the canvas: half-typed text
 * is the normal case, not the error case.
 *
 * Recognised: a `graph`/`flowchart` header with a direction (missing means
 * [DiagramDirection.TopDown]), `%%` comments, `subgraph`/`end` lines (ignored,
 * though what they hold still parses), node mentions with any of the five
 * bracket forms, the four connectors with either label syntax, chains
 * (`A --> B --> C`), fans (`A & B --> C`) and `;`-separated statements.
 */
fun parseDiagram(source: String): DiagramGraph {
    var direction: DiagramDirection? = null
    val nodes: MutableMap<String, DiagramNode> = mutableMapOf()
    val edges: MutableList<DiagramEdge> = mutableListOf()

    fun register(parsed: ParsedNode) {
        if (parsed.label == null) {
            // A bare mention only declares; a labelled one later still wins.
            if (parsed.id !in nodes) {
                nodes[parsed.id] = DiagramNode(parsed.id, parsed.id, DiagramNodeShape.Box)
            }
            return
        }
        nodes[parsed.id] =
            DiagramNode(parsed.id, parsed.label, parsed.shape ?: DiagramNodeShape.Box)
    }

    for (line in source.split("\n")) {
        val trimmed: String = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("%%")) continue

        for (statement in trimmed.split(";")) {
            val text: String = statement.trim()
            if (text.isEmpty()) continue
            if (text == "end" || text.startsWith("subgraph")) continue

            val header: MatchResult? = Header.matchEntire(text)
            if (header != null) {
                if (direction == null) direction = directionOf(header.groupValues[1])
                continue
            }

            // Split into node groups and the connectors between them, then commit
            // the lot or none of it: half a statement is worse than no statement.
            val groups: MutableList<List<ParsedNode>> = mutableListOf()
            val links: MutableList<ParsedEdge> = mutableListOf()
            var cursor = 0
            var broken = false

            for (match in Connector.findAll(text)) {
                if (match.range.first < cursor) continue
                val group: List<ParsedNode>? =
                    parseNodeGroup(text.substring(cursor, match.range.first))
                if (group == null) {
                    broken = true
                    break
                }

                groups += group
                cursor = match.range.last + 1

                // An inline label sits in whichever alternative matched; a piped
                // one sits just past the connector and is eaten here so it can't
                // be read as the node group that follows.
                var label: String =
                    match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty()
                val piped: MatchResult? = PipeLabel.find(text.substring(cursor))
                if (piped != null) {
                    label = piped.groupValues[1].trim()
                    cursor += piped.value.length
                }

                links += ParsedEdge(
                    label = label.unquoted(),
                    style = when {
                        match.value.startsWith("-.") -> DiagramEdgeStyle.Dotted
                        match.value.startsWith("==") -> DiagramEdgeStyle.Thick
                        else -> DiagramEdgeStyle.Solid
                    },
                    arrow = match.value.endsWith(">"),
                )
            }
            if (broken) continue

            val tail: List<ParsedNode> = parseNodeGroup(text.substring(cursor)) ?: continue
            groups += tail

            groups.forEach { group -> group.forEach { register(it) } }
            for ((index, link) in links.withIndex()) {
                for (from in groups[index]) {
                    for (to in groups[index + 1]) {
                        edges += DiagramEdge(from.id, to.id, link.label, link.style, link.arrow)
                    }
                }
            }
        }
    }

    return DiagramGraph(
        direction = direction ?: DiagramDirection.TopDown,
        nodes = nodes.values.toList(),
        edges = edges,
    )
}

/** An unrecognised direction falls back rather than failing the header with it. */
private fun directionOf(token: String): DiagramDirection = when (token.uppercase()) {
    "LR" -> DiagramDirection.LeftRight
    "RL" -> DiagramDirection.RightLeft
    "BT" -> DiagramDirection.BottomTop
    else -> DiagramDirection.TopDown
}

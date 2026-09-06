package io.github.xxfast.cupboard.canvas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import io.github.xxfast.cupboard.document.DiagramEdgeStyle
import io.github.xxfast.cupboard.document.DiagramElement
import io.github.xxfast.cupboard.document.DiagramGraph
import io.github.xxfast.cupboard.document.DiagramLayout
import io.github.xxfast.cupboard.document.DiagramNodeShape
import io.github.xxfast.cupboard.document.DiagramPoint
import io.github.xxfast.cupboard.document.DiagramStep
import io.github.xxfast.cupboard.document.LaidOutEdge
import io.github.xxfast.cupboard.document.LaidOutNode
import io.github.xxfast.cupboard.document.highlightedNodes
import io.github.xxfast.cupboard.document.layoutDiagram
import io.github.xxfast.cupboard.document.parseDiagram
import io.github.xxfast.cupboard.document.visibleNodes
import kotlin.math.min

/** How long the chart takes to walk from one step to the next. */
private const val DiagramStepDuration: Int = 300

/** The pill an edge's label sits on: the slide's own dark, so the line under it stops. */
private const val EdgeLabelPill: Long = 0xFF171930

/** An edge label is set under its chart's own size, and padded by this much of it. */
private const val EdgeLabelScale: Float = 0.8f
private const val EdgeLabelPadding: Float = 0.35f

/** A rounded node's corner, and every node's outline, against the font size. */
private const val NodeCornerScale: Float = 0.5f
private const val NodeStroke: Float = 2f

/** A dotted edge's dash and gap, in document units at 1x. */
private const val EdgeDash: Float = 6f
private const val EdgeGap: Float = 5f

/**
 * How strongly each node draws in [step]: gone, dropped back to [CodeDimAlpha],
 * or full strength. Null (the editor's case) is every node at full strength,
 * because what is being edited is the chart rather than the walk through it.
 */
private fun strengths(graph: DiagramGraph, step: DiagramStep?): Map<String, Float> {
    if (step == null) return graph.nodes.associate { it.id to 1f }

    val visible: Set<String> = visibleNodes(graph, step)
    val highlighted: Set<String> = highlightedNodes(graph, step)
    return graph.nodes.associate { node ->
        node.id to when {
            node.id !in visible -> 0f
            highlighted.isEmpty() || node.id in highlighted -> 1f
            else -> CodeDimAlpha
        }
    }
}

/** [color] at [alpha] of whatever transparency it already carried. */
private fun Color.faded(alpha: Float): Color = copy(alpha = this.alpha * alpha)

/**
 * A diagram: its source parsed, laid out, and scaled to fit the element's frame.
 *
 * [step] is the state to draw, or null for the whole chart, which is the editor's
 * case. Everything about the picture is a pure function of the source, so nothing
 * here is stored and the parse only reruns when the text or the size changes.
 *
 * The fit is capped at 1:1: a chart smaller than its box is centred in it rather
 * than blown up, so two diagrams set at the same font size read at the same size
 * however big a box each was dropped into.
 *
 * A step change is one fade over [DiagramStepDuration] with no movement at all: a
 * node holds the place its whole chart gives it, so a reveal reads as the picture
 * filling in rather than as one re-laying itself out under the audience.
 */
@Composable
internal fun DiagramElementView(element: DiagramElement, step: DiagramStep? = null) {
    val graph: DiagramGraph = remember(element.source) { parseDiagram(element.source) }
    val layout: DiagramLayout = remember(graph, element.fontSize) {
        layoutDiagram(graph, element.fontSize)
    }

    // Both ends are held here rather than read from the parameter, so the frame
    // between a step change and the effect below still draws the chart as it was.
    var from: DiagramStep? by remember { mutableStateOf(step) }
    var to: DiagramStep? by remember { mutableStateOf(step) }
    val progress: Animatable<Float, AnimationVector1D> = remember { Animatable(1f) }

    LaunchedEffect(step) {
        if (step == to) return@LaunchedEffect
        from = to
        to = step
        progress.snapTo(0f)
        progress.animateTo(1f, tween(DiagramStepDuration, easing = FastOutSlowInEasing))
    }

    val was: Map<String, Float> = remember(graph, from) { strengths(graph, from) }
    val now: Map<String, Float> = remember(graph, to) { strengths(graph, to) }
    val measurer: TextMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.size(element.frame.width.dp, element.frame.height.dp)) {
        // One document unit is one dp, and the surface has already folded the
        // canvas' zoom into the density, so this is what 1:1 costs in pixels.
        val unit: Float = 1.dp.toPx()
        val zoom: Float = min(
            min(size.width / (layout.width * unit), size.height / (layout.height * unit)),
            1f,
        )
        val scale: Float = zoom * unit
        val left: Float = (size.width - layout.width * scale) / 2f
        val top: Float = (size.height - layout.height * scale) / 2f

        fun at(point: DiagramPoint): Offset =
            Offset(left + point.x * scale, top + point.y * scale)

        fun strengthOf(id: String): Float =
            lerp(was[id] ?: 0f, now[id] ?: 0f, progress.value)

        val labelStyle = TextStyle(
            color = element.nodeText.toComposeColor(),
            fontSize = (element.fontSize * zoom).sp,
            fontFamily = FontFamily.SansSerif,
        )
        val edgeLabelStyle = TextStyle(
            color = element.edgeColor.toComposeColor(),
            fontSize = (element.fontSize * EdgeLabelScale * zoom).sp,
            fontFamily = FontFamily.SansSerif,
        )

        // Edges first, so a head that reaches into a node is covered by it.
        for (laid in layout.edges) {
            val alpha: Float = min(strengthOf(laid.edge.from), strengthOf(laid.edge.to))
            if (alpha <= 0f) continue
            drawDiagramEdge(laid, element.edgeColor.toComposeColor().faded(alpha), scale, ::at)
        }

        for (laid in layout.nodes) {
            val alpha: Float = strengthOf(laid.node.id)
            if (alpha <= 0f) continue

            val outline: Path = laid.outline(
                topLeft = Offset(left + laid.frame.x * scale, top + laid.frame.y * scale),
                boxSize = Size(laid.frame.width * scale, laid.frame.height * scale),
                corner = element.fontSize * NodeCornerScale * scale,
            )
            drawPath(outline, element.nodeFill.toComposeColor().faded(alpha))
            drawPath(
                path = outline,
                color = element.nodeStroke.toComposeColor().faded(alpha),
                style = Stroke(width = NodeStroke * scale),
            )

            val measured: TextLayoutResult = measurer.measure(laid.node.label, labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = left + laid.frame.centerX * scale - measured.size.width / 2f,
                    y = top + laid.frame.centerY * scale - measured.size.height / 2f,
                ),
                alpha = alpha,
            )
        }

        // Labels last: an edge's text has to sit over every line it crosses, not
        // only over its own.
        for (laid in layout.edges) {
            val where: DiagramPoint = laid.labelAt ?: continue
            val alpha: Float = min(strengthOf(laid.edge.from), strengthOf(laid.edge.to))
            if (alpha <= 0f) continue

            val measured: TextLayoutResult = measurer.measure(laid.edge.label, edgeLabelStyle)
            val padding: Float = element.fontSize * EdgeLabelPadding * scale
            val center: Offset = at(where)
            val pill = Size(measured.size.width + 2 * padding, measured.size.height + padding)
            drawRoundRect(
                color = EdgeLabelPill.toComposeColor(),
                topLeft = Offset(center.x - pill.width / 2f, center.y - pill.height / 2f),
                size = pill,
                cornerRadius = CornerRadius(pill.height / 2f),
                alpha = alpha,
            )
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = center.x - measured.size.width / 2f,
                    y = center.y - measured.size.height / 2f,
                ),
                alpha = alpha,
            )
        }
    }
}

/** The path a node is filled and stroked as, in pixels. */
private fun LaidOutNode.outline(topLeft: Offset, boxSize: Size, corner: Float): Path {
    val box = Rect(topLeft, boxSize)
    return Path().apply {
        when (node.shape) {
            DiagramNodeShape.Box -> addRect(box)
            DiagramNodeShape.Rounded -> addRoundRect(RoundRect(box, CornerRadius(corner)))
            // Fully rounded: the cap radius is half the height, whatever that is.
            DiagramNodeShape.Stadium ->
                addRoundRect(RoundRect(box, CornerRadius(boxSize.height / 2f)))

            // A circle's box is already square, so its inscribed oval is a circle.
            DiagramNodeShape.Circle -> addOval(box)
            DiagramNodeShape.Diamond -> {
                moveTo(box.center.x, box.top)
                lineTo(box.right, box.center.y)
                lineTo(box.center.x, box.bottom)
                lineTo(box.left, box.center.y)
                close()
            }
        }
    }
}

/** One edge's polyline and, when it has one, its head. */
private fun DrawScope.drawDiagramEdge(
    laid: LaidOutEdge,
    color: Color,
    scale: Float,
    at: (DiagramPoint) -> Offset,
) {
    val points: List<Offset> = laid.points.map(at)
    if (points.size < 2) return

    val weight: Float = NodeStroke * scale *
        if (laid.edge.style == DiagramEdgeStyle.Thick) 2f else 1f
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (point in points.drop(1)) lineTo(point.x, point.y)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = weight,
            cap = StrokeCap.Round,
            pathEffect = if (laid.edge.style != DiagramEdgeStyle.Dotted) null else {
                PathEffect.dashPathEffect(floatArrayOf(EdgeDash * scale, EdgeGap * scale))
            },
        ),
    )

    if (!laid.edge.arrow) return
    drawArrowHead(color, tip = points.last(), from = points[points.size - 2], weight = weight)
}

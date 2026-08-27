package io.github.xxfast.cupboard.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeGradient
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.ShapeShadow
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextFont
import io.github.xxfast.cupboard.document.listBody
import io.github.xxfast.cupboard.document.listIndentLevel
import io.github.xxfast.cupboard.document.listMarkers
import kotlin.math.hypot

fun Long.toComposeColor(): Color = Color(this)

/**
 * Positions [element] at its frame (1dp == 1 doc unit inside [SlideSurface]) and
 * renders it. Opacity, rotation and both flips ride one graphics layer around the
 * frame's center, so they cost the same as the opacity layer alone used to.
 *
 * [originX] and [originY] are the slide coordinates the offset is measured from:
 * the slide's own corner at the top level, the group's corner for a group's
 * children. Children are stored in absolute slide coordinates, so laying them out
 * inside their group's box would otherwise apply the group's offset twice.
 */
@Composable
fun ElementView(
    element: Element,
    modifier: Modifier = Modifier,
    originX: Float = 0f,
    originY: Float = 0f,
) {
    Box(
        modifier = modifier
            .offset((element.frame.x - originX).dp, (element.frame.y - originY).dp)
            .size(element.frame.width.dp, element.frame.height.dp)
            .graphicsLayer {
                alpha = element.opacity
                rotationZ = element.rotation
                scaleX = if (element.flippedHorizontally) -1f else 1f
                scaleY = if (element.flippedVertically) -1f else 1f
                transformOrigin = TransformOrigin.Center
            }
    ) {
        when (element) {
            is TextElement -> TextElementView(element)
            is ShapeElement -> ShapeElementView(element)
            is ImageElement -> ImageElementView(element)
            is CodeElement -> CodeElementView(element)
            // The group draws nothing of its own: it is the box its transforms
            // hang off, and its children draw inside it. A nested group recurses
            // through here and re-bases its own children the same way.
            is GroupElement -> for (child in element.children) {
                ElementView(child, originX = element.frame.x, originY = element.frame.y)
            }
        }
    }
}

/** The white a text box starts in, and the only colour a link is allowed to take over. */
private const val DefaultTextColor: Long = 0xFFFFFFFF

/** The design's link swatch. */
private const val LinkColor: Long = 0xFFA98FFF

/** How far one nesting level indents a list line, and how wide its marker sits. */
internal const val ListIndent: Float = 24f

/** Breathing room between a marker and its line, taken out of the marker's cell. */
private const val ListMarkerGap: Float = 6f

/**
 * The style the element's text draws in.
 *
 * Internal rather than private: the editor's in-place text field styles itself
 * from this too, so the text under the caret is the same text that was there
 * before it, to the pixel.
 */
internal fun TextElement.textStyle(): TextStyle {
    val decorations: List<TextDecoration> = buildList {
        if (underline || link != null) add(TextDecoration.Underline)
        if (strikethrough) add(TextDecoration.LineThrough)
    }

    // A link recolours the box only where the box never said otherwise: a colour
    // that was chosen outlives the link, and the underline carries it instead.
    val drawn: Long = if (link != null && color == DefaultTextColor) LinkColor else color

    return TextStyle(
        color = drawn.toComposeColor(),
        fontSize = fontSize.sp,
        fontWeight = FontWeight(fontWeight),
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontFamily = when (fontFamily) {
            TextFont.Sans -> FontFamily.SansSerif
            TextFont.Serif -> FontFamily.Serif
            TextFont.Monospace -> FontFamily.Monospace
        },
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        lineHeight = (fontSize * lineHeight).sp,
        letterSpacing = letterSpacing.sp,
        textAlign = when (align) {
            TextAlign.Start -> androidx.compose.ui.text.style.TextAlign.Start
            TextAlign.Center -> androidx.compose.ui.text.style.TextAlign.Center
            TextAlign.End -> androidx.compose.ui.text.style.TextAlign.End
        },
    )
}

/** Where in its frame the text sits. Shared with the editor for the same reason. */
internal fun TextElement.alignment(): Alignment = when (align) {
    TextAlign.Start -> Alignment.TopStart
    TextAlign.Center -> Alignment.TopCenter
    TextAlign.End -> Alignment.TopEnd
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.TextElementView(element: TextElement) {
    val style: TextStyle = element.textStyle()

    // Plain text is one Text, exactly as it always was: only a list pays for the
    // row-per-line layout its markers need.
    if (element.listStyle == ListStyle.None) {
        Text(
            text = element.text,
            style = style,
            modifier = Modifier.align(element.alignment()),
        )
        return
    }

    val lines: List<String> = element.text.split("\n")
    val markers: List<String> = listMarkers(element.text, element.listStyle)
    Column(modifier = Modifier.align(element.alignment()).fillMaxWidth()) {
        for ((index, line) in lines.withIndex()) {
            val indent: Float = line.listIndentLevel() * ListIndent
            Row(modifier = Modifier.fillMaxWidth().padding(start = indent.dp)) {
                Text(
                    text = markers[index],
                    style = style.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End),
                    modifier = Modifier.width(ListIndent.dp).padding(end = ListMarkerGap.dp),
                )
                Text(
                    text = line.listBody(),
                    style = style,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The fill a shape paints: its gradient when it has one, its solid colour otherwise. */
private fun ShapeElement.brush(): Brush {
    val gradient: ShapeGradient = gradient ?: return SolidColor(fill.toComposeColor())

    // A shader brush rather than Brush.linearGradient: the angle's endpoints are
    // a function of the box, and the box isn't known until the fill is drawn.
    return object : ShaderBrush() {
        override fun createShader(size: Size): Shader = LinearGradientShader(
            from = gradientStop(size, gradient.angle, -1f),
            to = gradientStop(size, gradient.angle, 1f),
            colors = listOf(
                gradient.start.toComposeColor(),
                gradient.end.toComposeColor(),
            ),
        )
    }
}

@Composable
private fun ShapeElementView(element: ShapeElement) {
    // A line is a stroke between two corners rather than an outline with a
    // fill, so none of the shape modifiers below have anything to say about it.
    if (element.kind == ShapeKind.Line) {
        LineElementView(element)
        return
    }

    val shape: Shape = element.shape()
    Box(
        modifier = Modifier
            .size(element.frame.width.dp, element.frame.height.dp)
            .let { base ->
                val shadow: ShapeShadow = element.shadow ?: return@let base
                // Compose draws an elevation shadow, which carries its own
                // offset: the document's dx/dy are kept but not honoured here.
                base.shadow(
                    elevation = shadow.blur.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = shadow.color.toComposeColor(),
                    spotColor = shadow.color.toComposeColor(),
                )
            }
            .background(element.brush(), shape)
            .border(element.strokeWidth.dp, element.strokeColor.toComposeColor(), shape),
        contentAlignment = Alignment.Center,
    ) {
        ShapeLabel(element)
    }
}

/** How far an arrowhead reaches back from its tip, against the line's weight. */
private const val ArrowHeadScale: Float = 4f

@Composable
private fun LineElementView(element: ShapeElement) {
    Box(
        modifier = Modifier.size(element.frame.width.dp, element.frame.height.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val color: Color = element.strokeColor.toComposeColor()
            val width: Float = element.strokeWidth.dp.toPx()

            // Corner to corner. The other diagonal is a flip away, and the flip
            // rides the element's graphics layer like every other transform.
            val start = Offset.Zero
            val end = Offset(size.width, size.height)
            drawLine(color, start, end, strokeWidth = width, cap = StrokeCap.Round)

            if (element.startArrow) drawArrowHead(color, tip = start, from = end, weight = width)
            if (element.endArrow) drawArrowHead(color, tip = end, from = start, weight = width)
        }

        ShapeLabel(element)
    }
}

/** A filled triangle pointing at [tip], away from [from]. */
private fun DrawScope.drawArrowHead(color: Color, tip: Offset, from: Offset, weight: Float) {
    val length: Float = hypot(tip.x - from.x, tip.y - from.y)
    if (length == 0f) return

    val reach: Float = ArrowHeadScale * weight
    val dx: Float = (tip.x - from.x) / length
    val dy: Float = (tip.y - from.y) / length
    val baseX: Float = tip.x - dx * reach
    val baseY: Float = tip.y - dy * reach

    // The base's two ends, one half-reach either side of the line's own direction.
    val head: Path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseX - dy * reach / 2, baseY + dx * reach / 2)
        lineTo(baseX + dy * reach / 2, baseY - dx * reach / 2)
        close()
    }
    drawPath(head, color)
}

@Composable
private fun ShapeLabel(element: ShapeElement) {
    if (element.label.isEmpty()) return

    Text(
        text = element.label,
        color = element.labelColor.toComposeColor(),
        fontSize = element.labelSize.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ImageElementView(element: ImageElement) {
    Box(
        modifier = Modifier
            .size(element.frame.width.dp, element.frame.height.dp)
            .drawBehind {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.3f),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = element.placeholder,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun CodeElementView(element: CodeElement) {
    Box(
        modifier = Modifier
            .size(element.frame.width.dp, element.frame.height.dp)
            .background(Color(0xFF14151F), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF33363D), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        val highlighted = remember(element.code, element.language) {
            highlightCode(element.code, element.language)
        }
        Text(
            text = highlighted,
            color = Color(0xFFD9CFFF),
            fontSize = element.fontSize.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = (element.fontSize * 1.5f).sp,
        )
    }
}

package io.github.xxfast.cupboard.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextElement

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

/**
 * The style the element's text draws in.
 *
 * Internal rather than private: the editor's in-place text field styles itself
 * from this too, so the text under the caret is the same text that was there
 * before it, to the pixel.
 */
internal fun TextElement.textStyle(): TextStyle = TextStyle(
    color = color.toComposeColor(),
    fontSize = fontSize.sp,
    fontWeight = FontWeight(fontWeight),
    lineHeight = (fontSize * lineHeight).sp,
    letterSpacing = letterSpacing.sp,
    textAlign = when (align) {
        TextAlign.Start -> androidx.compose.ui.text.style.TextAlign.Start
        TextAlign.Center -> androidx.compose.ui.text.style.TextAlign.Center
        TextAlign.End -> androidx.compose.ui.text.style.TextAlign.End
    },
)

/** Where in its frame the text sits. Shared with the editor for the same reason. */
internal fun TextElement.alignment(): Alignment = when (align) {
    TextAlign.Start -> Alignment.TopStart
    TextAlign.Center -> Alignment.TopCenter
    TextAlign.End -> Alignment.TopEnd
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.TextElementView(element: TextElement) {
    Text(
        text = element.text,
        style = element.textStyle(),
        modifier = Modifier.align(element.alignment()),
    )
}

@Composable
private fun ShapeElementView(element: ShapeElement) {
    val shape: Shape = when (element.kind) {
        ShapeKind.Rectangle -> RoundedCornerShape(element.cornerRadius.dp)
        ShapeKind.Ellipse -> CircleShape
    }
    Box(
        modifier = Modifier
            .size(element.frame.width.dp, element.frame.height.dp)
            .background(element.fill.toComposeColor(), shape)
            .border(element.strokeWidth.dp, element.strokeColor.toComposeColor(), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (element.label.isNotEmpty()) {
            Text(
                text = element.label,
                color = element.labelColor.toComposeColor(),
                fontSize = element.labelSize.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
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

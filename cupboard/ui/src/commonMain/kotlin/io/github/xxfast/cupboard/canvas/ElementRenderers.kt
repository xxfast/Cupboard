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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextElement

fun Long.toComposeColor(): Color = Color(this)

/** Positions [element] at its frame (1dp == 1 doc unit inside [SlideSurface]) and renders it. */
@Composable
fun ElementView(element: Element, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .offset(element.frame.x.dp, element.frame.y.dp)
            .size(element.frame.width.dp, element.frame.height.dp)
            .alpha(element.opacity)
    ) {
        when (element) {
            is TextElement -> TextElementView(element)
            is ShapeElement -> ShapeElementView(element)
            is ImageElement -> ImageElementView(element)
            is CodeElement -> CodeElementView(element)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.TextElementView(element: TextElement) {
    Text(
        text = element.text,
        style = TextStyle(
            color = element.color.toComposeColor(),
            fontSize = element.fontSize.sp,
            fontWeight = FontWeight(element.fontWeight),
            lineHeight = (element.fontSize * element.lineHeight).sp,
            letterSpacing = element.letterSpacing.sp,
            textAlign = when (element.align) {
                TextAlign.Start -> androidx.compose.ui.text.style.TextAlign.Start
                TextAlign.Center -> androidx.compose.ui.text.style.TextAlign.Center
                TextAlign.End -> androidx.compose.ui.text.style.TextAlign.End
            },
        ),
        modifier = Modifier.align(
            when (element.align) {
                TextAlign.Start -> Alignment.TopStart
                TextAlign.Center -> Alignment.TopCenter
                TextAlign.End -> Alignment.TopEnd
            }
        ),
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

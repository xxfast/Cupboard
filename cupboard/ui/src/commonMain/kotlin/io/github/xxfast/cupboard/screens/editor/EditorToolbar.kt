package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTokens

/** Zoom menu steps; 0 is Fit, the rest are percents of the 1920-wide native slide. */
private val ZOOM_STEPS: List<Int> = listOf(25, 50, 75, 100, 125, 150, 200)

/**
 * The stacked layout's 60dp M3 toolbar, per the design's Linux variant: filled
 * Play pill, tonal Add slide, circular insert icon buttons, outlined zoom pill.
 *
 * Add slide and the insert buttons are placeholders until the document gains
 * those operations; [onPlay] null (android/web shells) hides Play entirely.
 */
@Composable
fun EditorToolbar(
    zoomPercent: Int,
    onZoomPercentChange: (Int) -> Unit,
    onPlay: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(tokens.barTop)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onPlay != null) {
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(tokens.accent)
                        .clickable { onPlay() }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlayGlyph(color = tokens.accentText)
                    Text(
                        text = "Play",
                        color = tokens.accentText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(tokens.tonal)
                    .clickable {} // Placeholder until Phase 2 adds slide insertion.
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlusGlyph(color = tokens.tonalText)
                Text(
                    text = "Add slide",
                    color = tokens.tonalText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Box(
                Modifier
                    .padding(horizontal = 6.dp)
                    .width(1.dp)
                    .height(28.dp)
                    .background(tokens.div),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                InsertButton { TextGlyph(color = tokens.icon) }
                InsertButton { ShapeGlyph(color = tokens.icon) }
                InsertButton { ImageGlyph(color = tokens.icon) }
                InsertButton { MediaGlyph(color = tokens.icon) }
            }

            Box(Modifier.weight(1f))

            ZoomPill(zoomPercent = zoomPercent, onZoomPercentChange = onZoomPercentChange)
        }

        HorizontalDivider(thickness = 1.dp, color = tokens.div)
    }
}

/** 40dp circular icon button; a placeholder until element insertion lands. */
@Composable
private fun InsertButton(glyph: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable {},
        contentAlignment = Alignment.Center,
    ) {
        glyph()
    }
}

/**
 * The outlined zoom dropdown. Zoom is view-local (mirroring the macOS shell):
 * an Int percent where 0 means Fit.
 */
@Composable
private fun ZoomPill(zoomPercent: Int, onZoomPercentChange: (Int) -> Unit) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    var menuOpen: Boolean by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, tokens.outline, RoundedCornerShape(20.dp))
                .clickable { menuOpen = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (zoomPercent == 0) "Fit" else "$zoomPercent%",
                color = tokens.text,
                fontSize = 13.sp,
            )
            Text(text = "▾", color = tokens.dim, fontSize = 9.sp)
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ZoomItem(
                label = "Fit in window",
                checked = zoomPercent == 0,
                onClick = {
                    menuOpen = false
                    onZoomPercentChange(0)
                },
            )

            for (step in ZOOM_STEPS) ZoomItem(
                label = "$step%",
                checked = zoomPercent == step,
                onClick = {
                    menuOpen = false
                    onZoomPercentChange(step)
                },
            )
        }
    }
}

@Composable
private fun ZoomItem(label: String, checked: Boolean, onClick: () -> Unit) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    DropdownMenuItem(
        text = { Text(label, fontSize = 13.sp) },
        leadingIcon = {
            Box(Modifier.width(14.dp)) {
                if (checked) Text(text = "✓", color = tokens.accent, fontSize = 12.sp)
            }
        },
        onClick = onClick,
    )
}

// The glyphs below are ported from the design mock's inline SVGs: no icon
// assets exist and material-icons isn't on the classpath, so they are stroked
// by hand like the navigator's DisclosureChevron.

private fun DrawScope.glyphStroke(): Stroke = Stroke(
    width = 1.4.dp.toPx(),
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
)

@Composable
private fun PlayGlyph(color: Color) {
    Canvas(Modifier.size(width = 11.dp, height = 12.dp)) {
        val triangle: Path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(0f, size.height)
            close()
        }
        drawPath(triangle, color)
    }
}

@Composable
private fun PlusGlyph(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val s: Float = size.width / 14f
        drawLine(color, Offset(7f * s, 2f * s), Offset(7f * s, 12f * s), 1.4.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(2f * s, 7f * s), Offset(12f * s, 7f * s), 1.4.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun TextGlyph(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val s: Float = size.width / 13f
        val glyph: Path = Path().apply {
            moveTo(1.5f * s, 3f * s)
            lineTo(1.5f * s, 1f * s)
            lineTo(11.5f * s, 1f * s)
            lineTo(11.5f * s, 3f * s)
            moveTo(6.5f * s, 1f * s)
            lineTo(6.5f * s, 12f * s)
            moveTo(4.5f * s, 12f * s)
            lineTo(8.5f * s, 12f * s)
        }
        drawPath(glyph, color, style = glyphStroke())
    }
}

@Composable
private fun ShapeGlyph(color: Color) {
    Canvas(Modifier.size(15.dp)) {
        val s: Float = size.width / 14f
        val stroke: Stroke = glyphStroke()
        drawRoundRect(
            color = color,
            topLeft = Offset(0.7f * s, 4.7f * s),
            size = Size(8f * s, 8f * s),
            cornerRadius = CornerRadius(1.2f * s),
            style = stroke,
        )
        drawCircle(color, radius = 3.5f * s, center = Offset(9.5f * s, 4.2f * s), style = stroke)
    }
}

@Composable
private fun ImageGlyph(color: Color) {
    Canvas(Modifier.size(15.dp)) {
        val s: Float = size.width / 14f
        val stroke: Stroke = glyphStroke()
        drawRoundRect(
            color = color,
            topLeft = Offset(0.7f * s, 0.7f * s),
            size = Size(12.6f * s, 12.6f * s),
            cornerRadius = CornerRadius(1.5f * s),
            style = stroke,
        )
        drawCircle(color, radius = 1.4f * s, center = Offset(4.5f * s, 4.5f * s), style = stroke)
        val ridge: Path = Path().apply {
            moveTo(1f * s, 10.5f * s)
            lineTo(5f * s, 7f * s)
            lineTo(8f * s, 9.5f * s)
            lineTo(10.5f * s, 7.5f * s)
            lineTo(13f * s, 9.5f * s)
        }
        drawPath(ridge, color, style = stroke)
    }
}

@Composable
private fun MediaGlyph(color: Color) {
    Canvas(Modifier.size(15.dp)) {
        val s: Float = size.width / 14f
        drawRoundRect(
            color = color,
            topLeft = Offset(0.7f * s, 1.7f * s),
            size = Size(12.6f * s, 10.6f * s),
            cornerRadius = CornerRadius(1.5f * s),
            style = glyphStroke(),
        )
        val triangle: Path = Path().apply {
            moveTo(5.5f * s, 4.8f * s)
            lineTo(9.2f * s, 7f * s)
            lineTo(5.5f * s, 9.2f * s)
            close()
        }
        drawPath(triangle, color)
    }
}

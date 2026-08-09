package io.github.xxfast.cupboard.canvas

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide

/** Miniature of a slide via the same renderers, per the navigator design. */
@Composable
fun SlideThumbnail(
    slide: Slide,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
    cornerRadius: Dp = 5.dp,
    selected: Boolean = false,
    accent: Color = Color(0xFF7F52FF),
) {
    SlideView(
        slide = slide,
        modifier = modifier
            .width(width)
            .aspectRatio(Document.SLIDE_WIDTH / Document.SLIDE_HEIGHT)
            .clip(RoundedCornerShape(cornerRadius))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else Color(0xFF33363D),
                shape = RoundedCornerShape(cornerRadius),
            ),
    )
}

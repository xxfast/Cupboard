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
import io.github.xxfast.cupboard.document.SlideBackground

/**
 * Miniature of a slide via the same renderers, per the navigator design.
 * Pure render: selection chrome is the host's business.
 */
@Composable
fun SlideThumbnail(
    slide: Slide,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
    cornerRadius: Dp = 5.dp,
    number: Int? = null,
    /** The layout the slide is built on, whose static objects it draws behind its own. */
    layout: Slide? = null,
    /** The deck's background, behind a slide that has none of its own. */
    background: SlideBackground? = null,
) {
    SlideView(
        slide = slide,
        layout = layout,
        number = number,
        background = background,
        modifier = modifier
            .width(width)
            .aspectRatio(Document.SLIDE_WIDTH / Document.SLIDE_HEIGHT)
            .clip(RoundedCornerShape(cornerRadius))
            .border(1.dp, Color(0xFF33363D), RoundedCornerShape(cornerRadius)),
    )
}

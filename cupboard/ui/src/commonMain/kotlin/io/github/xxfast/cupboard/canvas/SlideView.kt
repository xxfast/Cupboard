package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.isVisibleAt

/**
 * Renders a slide's elements. [step] limits visibility per the build order;
 * null (the editor default) shows everything.
 */
@Composable
fun SlideView(
    slide: Slide,
    modifier: Modifier = Modifier,
    step: Int? = null,
) {
    SlideSurface(modifier) {
        for (element in slide.elements) {
            if (step == null || slide.isVisibleAt(element.id, step)) {
                ElementView(element)
            }
        }
    }
}

package io.github.xxfast.cupboard.play

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.github.xxfast.cupboard.canvas.SlideView
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.layoutOf
import io.github.xxfast.cupboard.document.presentationNumbers
import io.github.xxfast.cupboard.document.stepCount
import net.kodein.cup.Slide as CupSlide

/**
 * Compiles the document into CuP runtime slides. Slide names are our slide ids,
 * so CuP's name-based position restore stays stable across recompiles.
 *
 * Skipped slides are left out, which is the whole point of skipping one, and
 * every slide that stays carries the number it will show. A deck with every
 * slide skipped is the exception: CuP has nothing to play with no slides at
 * all, so the skips are ignored rather than obeyed into an empty window.
 */
internal fun Document.toCupSlides(): List<CupSlide> {
    val numbered: List<Pair<Slide, Int?>> = slides.zip(presentationNumbers())
    val playing: List<Pair<Slide, Int?>> =
        numbered.filterNot { (slide, _) -> slide.skipped }.ifEmpty { numbered }

    return playing.map { (slide, number) ->
        CupSlide(name = slide.id, stepCount = slide.stepCount()) { step ->
            SlideView(
                slide = slide,
                layout = layoutOf(slide),
                modifier = Modifier.fillMaxSize(),
                step = step,
                number = number,
                background = background,
                slideWidth = slideWidth,
                slideHeight = slideHeight,
            )
        }
    }
}

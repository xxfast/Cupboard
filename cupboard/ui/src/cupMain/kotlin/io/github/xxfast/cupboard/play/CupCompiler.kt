package io.github.xxfast.cupboard.play

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.github.xxfast.cupboard.canvas.SlideView
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.stepCount
import net.kodein.cup.Slide as CupSlide

/**
 * Compiles the document into CuP runtime slides. Slide names are our slide ids,
 * so CuP's name-based position restore stays stable across recompiles.
 */
internal fun Document.toCupSlides(): List<CupSlide> = slides.map { slide ->
    CupSlide(name = slide.id, stepCount = slide.stepCount()) { step ->
        SlideView(slide = slide, modifier = Modifier.fillMaxSize(), step = step)
    }
}

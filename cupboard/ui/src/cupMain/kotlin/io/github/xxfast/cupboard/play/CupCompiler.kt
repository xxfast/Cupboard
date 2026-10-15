package io.github.xxfast.cupboard.play

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import io.github.xxfast.cupboard.canvas.SlideView
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideFields
import io.github.xxfast.cupboard.document.layoutOf
import io.github.xxfast.cupboard.document.slideFields
import io.github.xxfast.cupboard.document.stepCount
import net.kodein.cup.SlideSpecs
import net.kodein.cup.TransitionSet
import net.kodein.cup.Slide as CupSlide

/**
 * Compiles the document into CuP runtime slides. Slide names are our slide ids,
 * so CuP's name-based position restore stays stable across recompiles.
 *
 * Every slide carries what its fields will read, and the pair of transition sets
 * CuP animates it with. A transition belongs to the slide it plays on the way
 * out of, so a slide's own is its `endTransitions` and the previous slide's is
 * its `startTransitions`: the two slides of a change then agree on the animation
 * between them, whichever way the presenter is walking. The first slide has no
 * previous one, so it starts on the deck's default.
 *
 * The size is left unspecified, and CuP fills it in from the presentation's own
 * default specs.
 */
internal fun Document.toCupSlides(
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
): List<CupSlide> {
    val fields: Map<String, SlideFields> =
        slides.indices.associate { index -> slides[index].id to slideFields(index) }
    val playing: List<Slide> = playOrder()

    // One set per slide, shared with the slide behind it rather than built twice:
    // the two sides of a change are meant to be the same animation, and a set is
    // compared by identity everywhere CuP touches one.
    val opening: TransitionSet = null.toTransitionSet(layoutDirection)
    val sets: List<TransitionSet> =
        playing.map { it.transition.toTransitionSet(layoutDirection) }

    return playing.mapIndexed { index, slide ->
        CupSlide(
            name = slide.id,
            stepCount = slide.stepCount(),
            specs = SlideSpecs(
                startTransitions = sets.getOrElse(index - 1) { opening },
                endTransitions = sets[index],
            ),
        ) { step ->
            SlideView(
                slide = slide,
                layout = layoutOf(slide),
                modifier = Modifier.fillMaxSize(),
                step = step,
                fields = fields[slide.id],
                background = background,
                slideWidth = slideWidth,
                slideHeight = slideHeight,
            )
        }
    }
}

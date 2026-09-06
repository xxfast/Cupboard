package io.github.xxfast.cupboard.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.document.BuildAt
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.DiagramElement
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.PieceReveal
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.TransitionKind
import io.github.xxfast.cupboard.document.codeStepFor
import io.github.xxfast.cupboard.document.diagramStepFor
import io.github.xxfast.cupboard.document.effectiveBackground
import io.github.xxfast.cupboard.document.entryBuildAt
import io.github.xxfast.cupboard.document.exitBuildAt
import io.github.xxfast.cupboard.document.inheritedElements
import io.github.xxfast.cupboard.document.isVisibleAt
import io.github.xxfast.cupboard.document.magicMovePairs
import io.github.xxfast.cupboard.document.pieceRevealAt

/** How far the slide number sits off the slide's right and bottom edges, in doc units. */
private const val SlideNumberInset: Float = 64f

/** The slide number's size in doc units, and its ink: white, well under half opacity. */
private const val SlideNumberSize: Float = 30f
private const val SlideNumberColor: Long = 0x99FFFFFF

/**
 * Renders a slide's elements. [step] limits visibility per the build order, and
 * puts every stepped code block and diagram in the state its builds have reached;
 * null (the editor default) shows everything, whole.
 *
 * [number] is this slide's place in the presentation, which only a slide that
 * asks for it draws. Passed in rather than worked out here: what counts as a
 * number is the document's business, and a skipped slide has none.
 *
 * [layout] is the layout the slide is built on, which the caller resolves the
 * same way (`Document.layoutOf`). Its static objects draw behind the slide's own
 * and its background stands in where the slide has none, which is the whole of
 * what a slide inherits: the placeholders are already the slide's own elements.
 * Builds and steps never reach them, because nothing on a layout is a step.
 *
 * [background] is the deck's own, `Document.background`, and stands in where
 * neither the slide nor its layout has one: the last step of the fallback, and
 * what a theme paints behind a deck. A [Document] is not passed instead because
 * this renders one slide and has no business reaching past it.
 *
 * A Magic Move is drawn here rather than by the player, because it is the only
 * transition that is about elements rather than about slides: see
 * [LocalPlayTransition] for how a slide learns it is in one.
 */
@Composable
fun SlideView(
    slide: Slide,
    modifier: Modifier = Modifier,
    layout: Slide? = null,
    step: Int? = null,
    number: Int? = null,
    background: SlideBackground? = null,
    /** The deck's slide size, `Document.slideWidth` and `Document.slideHeight`. */
    slideWidth: Float = Document.SLIDE_WIDTH,
    slideHeight: Float = Document.SLIDE_HEIGHT,
) {
    val play: PlayTransition? = LocalPlayTransition.current
    val magic: PlayTransition? =
        play?.takeIf { it.transition.kind == TransitionKind.MagicMove }

    // Which side of the cut this slide is on. Matched elements are drawn once,
    // on the arriving slide: they are the same objects to the audience, so the
    // slide they are leaving lets them go rather than fading a second copy out.
    val arriving: Boolean = magic != null && magic.toSlide.id == slide.id
    val leaving: Boolean = magic != null && magic.fromSlide?.id == slide.id

    val travelling: List<Pair<Element, Element>> = remember(magic) {
        val from: Slide = magic?.fromSlide ?: return@remember emptyList()
        magicMovePairs(from, magic.toSlide)
    }

    val progress = remember(magic) { Animatable(0f) }
    LaunchedEffect(magic) {
        if (magic == null || !arriving) return@LaunchedEffect
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(magic.transition.durationMs, easing = FastOutSlowInEasing),
        )
    }

    SlideSurface(
        modifier = modifier,
        slideBackground = slide.effectiveBackground(layout, background),
        slideWidth = slideWidth,
        slideHeight = slideHeight,
    ) {
        for (element in slide.inheritedElements(layout)) ElementView(element)

        for (element in slide.elements) {
            if (leaving && travelling.any { (from, _) -> from.id == element.id }) continue

            val origin: Element? =
                if (arriving) travelling.firstOrNull { (_, to) -> to.id == element.id }?.first
                else null

            val transform: ElementTransform? = when {
                origin != null -> magicMoveTransform(origin, element, progress.value)
                // Nothing to travel from, so it arrives the ordinary way.
                arriving -> element.fadingTransform(progress.value)
                else -> null
            }

            // The editor draws every element at rest: nothing is hidden, nothing
            // animates itself in, and no build is read at all.
            if (step == null) {
                ElementView(element, transform = transform)
                continue
            }

            val entry: BuildAt? = slide.entryBuildAt(element.id)
            val exit: BuildAt? = slide.exitBuildAt(element.id)
            val reveal: PieceReveal? = slide.pieceRevealAt(element.id, step)

            // The frame rides the wrapper rather than the element, so an effect
            // that clips (a wipe) or slides does it around the element's own box
            // instead of around the slide's corner.
            Box(
                modifier = Modifier
                    .offset(element.frame.x.dp, element.frame.y.dp)
                    .size(element.frame.width.dp, element.frame.height.dp),
            ) {
                AnimatedVisibility(
                    visible = slide.isVisibleAt(element.id, step),
                    enter = buildEnter(element, entry?.build, entry?.delayMs ?: 0),
                    exit = buildExit(element, exit?.build, exit?.delayMs ?: 0),
                ) {
                    ElementView(
                        element = element,
                        originX = element.frame.x,
                        originY = element.frame.y,
                        codeStep = if (element is CodeElement) {
                            slide.codeStepFor(element, step) ?: deliveredStep(reveal)
                        } else null,
                        diagramStep = if (element is DiagramElement) {
                            slide.diagramStepFor(element, step)
                        } else null,
                        entry = entry?.build,
                        pieces = reveal,
                        transform = transform,
                    )
                }
            }
        }
        if (slide.showsSlideNumber && number != null) SlideNumberView(number)
    }
}

/**
 * The slide number in its corner, over everything the slide draws.
 *
 * Internal rather than private: the editor canvas renders its elements itself
 * and still has to put the same number in the same place.
 */
@Composable
internal fun BoxScope.SlideNumberView(number: Int) {
    Text(
        text = number.toString(),
        style = TextStyle(
            color = SlideNumberColor.toComposeColor(),
            fontSize = SlideNumberSize.sp,
            textAlign = TextAlign.End,
        ),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = SlideNumberInset.dp, bottom = SlideNumberInset.dp),
    )
}

package io.github.xxfast.cupboard.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xxfast.cupboard.document.ActionState
import io.github.xxfast.cupboard.document.BuildAt
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.DiagramElement
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.GalleryElement
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.PieceReveal
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.SlideFields
import io.github.xxfast.cupboard.document.TransitionKind
import io.github.xxfast.cupboard.document.actionBuildAt
import io.github.xxfast.cupboard.document.actionStateAt
import io.github.xxfast.cupboard.document.codeStepFor
import io.github.xxfast.cupboard.document.elementStepBuildAt
import io.github.xxfast.cupboard.document.diagramStepFor
import io.github.xxfast.cupboard.document.backgroundCascade
import io.github.xxfast.cupboard.document.effectiveBackground
import io.github.xxfast.cupboard.document.entryBuildAt
import io.github.xxfast.cupboard.document.exitBuildAt
import io.github.xxfast.cupboard.document.galleryImageAt
import io.github.xxfast.cupboard.document.hidesNumberIn
import io.github.xxfast.cupboard.document.withFields
import io.github.xxfast.cupboard.document.travellingFrom
import io.github.xxfast.cupboard.document.inheritedElements
import io.github.xxfast.cupboard.document.isVisibleAt
import io.github.xxfast.cupboard.document.magicMovePairs
import io.github.xxfast.cupboard.document.pieceRevealAt
import io.github.xxfast.cupboard.document.resolvedLink
import io.github.xxfast.cupboard.document.stepCount

/**
 * Renders a slide's elements. [step] limits visibility per the build order, and
 * puts every stepped code block and diagram in the state its builds have reached;
 * null (the editor default) shows everything, whole.
 *
 * [fields] is what this slide's text fields read (`Document.slideFields`): its
 * place in the presentation, the count of places, its title. Every box is drawn
 * with its fields filled in, its layout's too, and a box holding the number draws
 * only while the slide shows its number: a box is the one way a number is drawn.
 * Passed in rather than worked out here: what counts as a number is the
 * document's business, and a skipped slide has none. Null is a slide with no place to read from, a layout or a template, which
 * shows what each field is and every box it holds.
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
 * [LocalPlayTransition] for how a slide learns it is in one. The arriving slide
 * draws all of it, at full opacity: the background crossfading, the leaving
 * slide's unmatched elements fading out, the matched ones travelling and its own
 * unmatched ones fading in, and the two layouts' own objects doing the same
 * underneath. The leaving slide draws nothing, so which of the two
 * the player has on top makes no difference and nothing is dimmed by a layer
 * fading the whole slide.
 */
@Composable
fun SlideView(
    slide: Slide,
    modifier: Modifier = Modifier,
    layout: Slide? = null,
    step: Int? = null,
    fields: SlideFields? = null,
    background: SlideBackground? = null,
    /** The deck's slide size, `Document.slideWidth` and `Document.slideHeight`. */
    slideWidth: Float = Document.SLIDE_WIDTH,
    slideHeight: Float = Document.SLIDE_HEIGHT,
) {
    val play: PlayTransition? = LocalPlayTransition.current
    // Only ever consulted in play mode: the editor provides no handler, and a
    // click on the canvas there is a selection rather than a jump.
    val linkHandler: (LinkTarget) -> Unit = LocalLinkHandler.current
    val magic: PlayTransition? =
        play?.takeIf { it.transition.kind == TransitionKind.MagicMove }

    // Which side of the cut this slide is on. The arriving slide draws the
    // whole move, so the leaving one has nothing to do but hold its place.
    val arrival: PlayTransition? = magic?.takeIf { it.toSlide.id == slide.id }
    val arriving: Boolean = arrival != null
    if (magic != null && magic.fromSlide?.id == slide.id) {
        Box(modifier)
        return
    }

    // What the leaving slide's fields read, for its boxes to keep reading until
    // they are gone, or halfway to the arriving ones.
    val fromFields: SlideFields? = arrival?.fromFields

    // A box holding the number, on a slide that has its number switched off, is
    // not there to draw, nor to travel anywhere. Only a slide drawn knowing where
    // it sits answers to the switch; drawn with no fields, it is a template.
    fun Slide.drawable(elements: List<Element>, fields: SlideFields?): List<Element> =
        if (fields == null) elements else elements.filterNot { hidesNumberIn(it) }

    // Paired as typed, never as filled in: a number box is the same box on both
    // sides of the cut whatever number it reads (see `Element.matchKey`).
    // Only what is on screen either side of the cut: the leaving slide at the
    // step it was left on, the arriving one at the step it arrives on. An
    // element a build hasn't brought in yet is not there to travel from, and
    // pairing it had its partner set off from it, in its colours, for the
    // length of the move.
    val travelling: List<Pair<Element, Element>> = remember(magic, fields) {
        val from: Slide = magic?.fromSlide ?: return@remember emptyList()
        val fromStep: Int = if (magic.forward) from.stepCount() - 1 else 0
        val toStep: Int = step ?: 0
        magicMovePairs(
            from.drawable(from.elements, magic.fromFields).filter { from.isVisibleAt(it.id, fromStep) },
            magic.toSlide.drawable(magic.toSlide.elements, fields).filter { magic.toSlide.isVisibleAt(it.id, toStep) },
        )
    }

    // The two layouts' own objects pair off among themselves, the same way: an
    // island both layouts draw is one island to the audience, wherever each puts
    // it. Two slides on one layout pair every object with itself, which is a
    // journey of no distance.
    val inherited: List<Element> = slide.drawable(slide.inheritedElements(layout), fields)
    val leavingInherited: List<Element> = arrival?.fromSlide
        ?.let { from -> from.drawable(from.inheritedElements(arrival.fromLayout), fromFields) }
        .orEmpty()
    val travellingInherited: List<Pair<Element, Element>> = remember(magic, layout, fields) {
        magicMovePairs(leavingInherited, inherited)
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
        fallbackBackgrounds = slide.backgroundCascade(layout, background).drop(1),
        crossfade = arrival?.let { BackgroundCrossfade(it.fromBackground, progress.value) },
        slideWidth = slideWidth,
        slideHeight = slideHeight,
    ) {
        // What the leaving layout drew that the arriving one doesn't, fading out
        // under everything else, where it always sat.
        for (element in leavingInherited) {
            if (element.hidden) continue
            if (travellingInherited.any { (origin, _) -> origin.id == element.id }) continue

            ElementView(
                element = element.withFields(fromFields),
                transform = element.fadingTransform(1f - progress.value),
            )
        }

        for (element in inherited) {
            if (element.hidden) continue

            val shown: Element = element.withFields(fields)
            if (!arriving) {
                ElementView(shown)
                continue
            }

            val origin: Element? =
                travellingInherited.firstOrNull { (_, to) -> to.id == element.id }?.first

            if (origin == null) {
                ElementView(shown, transform = element.fadingTransform(progress.value))
                continue
            }

            ElementView(
                element = shown.travellingFrom(origin.withFields(fromFields), progress.value),
                transform = magicMoveTransform(origin, element, progress.value),
                alignment = travellingAlignment(origin, element, progress.value),
            )
        }

        // What the leaving slide is letting go of, fading out where it stood. At
        // the step it was on when left: the last one going forward, the first
        // coming back. Drawn whole, with no build or code step read, because a
        // fade is all that is left of it.
        val from: Slide? = arrival?.fromSlide
        if (arrival != null && from != null) {
            val fromStep: Int = if (arrival.forward) from.stepCount() - 1 else 0

            for (element in from.drawable(from.elements, fromFields)) {
                if (element.hidden) continue
                if (travelling.any { (origin, _) -> origin.id == element.id }) continue
                if (!from.isVisibleAt(element.id, fromStep)) continue

                ElementView(
                    element = element.withFields(fromFields),
                    transform = element.fadingTransform(1f - progress.value),
                )
            }
        }

        for (element in slide.drawable(slide.elements, fields)) {
            if (element.hidden) continue

            val origin: Element? =
                if (arriving) travelling.firstOrNull { (_, to) -> to.id == element.id }?.first
                else null

            val travel: ElementTransform? = when {
                origin != null -> magicMoveTransform(origin, element, progress.value)
                // Nothing to travel from, so it arrives the ordinary way.
                arriving -> element.fadingTransform(progress.value)
                else -> null
            }

            // What is drawn this frame: the element with its fields filled in,
            // part-way along its journey (box and type both) or at rest.
            // Everything keyed or looked up by id below stays on [element]; only
            // the drawing moves.
            val shown: Element = element.withFields(fields)
            val drawn: Element = origin
                ?.let { shown.travellingFrom(it.withFields(fromFields), progress.value) }
                ?: shown
            val alignment: Alignment? =
                origin?.let { travellingAlignment(it, element, progress.value) }

            // The editor draws every element at rest: nothing is hidden, nothing
            // animates itself in, and no build is read at all.
            if (step == null) {
                ElementView(drawn, transform = travel, alignment = alignment)
                continue
            }

            // Keyed by id rather than by position, so the animation follows its
            // element when the slide's list is reordered under it.
            val transform: ElementTransform? =
                key(element.id) { actionTransform(slide, element, step, travel) }

            val entry: BuildAt? = slide.entryBuildAt(element.id)
            val exit: BuildAt? = slide.exitBuildAt(element.id)
            val reveal: PieceReveal? = slide.pieceRevealAt(element.id, step)
            val visible: Boolean = slide.isVisibleAt(element.id, step)

            // A link is only clickable while its element is on screen: an element
            // a build hasn't brought in yet is not there to click, however much
            // of its box the wrapper is still holding open.
            val target: LinkTarget? = element.resolvedLink()?.takeIf { visible }

            // A build fades through a layer of its own, around the element's, and
            // that one cuts a shadow off at its edge just the same. So it overhangs
            // the frame by as much, under the tap target rather than over it, and
            // the effects are told how much of what they measure is overhang.
            val bleed: Dp = drawn.shadowBleed().dp
            val overhang: Int = with(LocalDensity.current) { bleed.roundToPx() }

            // The frame rides the wrapper rather than the element, so an effect
            // that clips (a wipe) or slides does it around the element's own box
            // instead of around the slide's corner.
            Box(
                modifier = Modifier
                    .offset(drawn.frame.x.dp, drawn.frame.y.dp)
                    .size(drawn.frame.width.dp, drawn.frame.height.dp)
                    .then(
                        if (target == null) Modifier
                        else Modifier.pointerInput(target, linkHandler) {
                            detectTapGestures { linkHandler(target) }
                        },
                    )
                    .outset(bleed),
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = buildEnter(element, entry?.build, entry?.delayMs ?: 0, overhang),
                    exit = buildExit(element, exit?.build, exit?.delayMs ?: 0, overhang),
                ) {
                    ElementView(
                        element = drawn,
                        modifier = Modifier.padding(bleed),
                        originX = drawn.frame.x,
                        originY = drawn.frame.y,
                        codeStep = if (element is CodeElement) {
                            slide.codeStepFor(element, step) ?: deliveredStep(reveal)
                        } else null,
                        codeStepBuild = if (element is CodeElement) {
                            slide.elementStepBuildAt(element.id, step)
                        } else null,
                        diagramStep = if (element is DiagramElement) {
                            slide.diagramStepFor(element, step)
                        } else null,
                        galleryIndex = if (element is GalleryElement) {
                            slide.galleryImageAt(element, step)
                        } else null,
                        entry = entry?.build,
                        pieces = reveal,
                        transform = transform,
                        alignment = alignment,
                        // A step is what makes this the show: a movie autoplays,
                        // answers a tap and stops itself here, and does none of
                        // the three on the editor's canvas.
                        playing = true,
                    )
                }
            }
        }
    }
}

/**
 * [base] with every action build that has landed by [step] animated on top of it,
 * and [base] itself for an element no action names.
 *
 * One tween per property, spec'd by the action build the state was last read
 * from: a step that changes nothing about this element changes no target, so
 * nothing animates. Walking backwards runs the same tween the other way, since
 * the target is a function of the step rather than of the direction it was
 * reached from.
 *
 * The composition happens document-side, in [actionStateAt]; all this adds is the
 * animation and the arithmetic that folds it into a transform the element already
 * knows how to draw.
 */
@Composable
private fun actionTransform(
    slide: Slide,
    element: Element,
    step: Int,
    base: ElementTransform?,
): ElementTransform? {
    val at: BuildAt = slide.actionBuildAt(element.id, step) ?: return base
    val state: ActionState = slide.actionStateAt(element.id, step)
    val spec: TweenSpec<Float> = tween(at.build.durationMs, at.delayMs, FastOutSlowInEasing)

    val dx: Float by animateFloatAsState(state.dx, spec)
    val dy: Float by animateFloatAsState(state.dy, spec)
    val rotation: Float by animateFloatAsState(state.rotation, spec)
    val scale: Float by animateFloatAsState(state.scale, spec)
    val opacity: Float by animateFloatAsState(state.opacity ?: 1f, spec)

    // A transform overrides rather than multiplies the element's own opacity and
    // rotation, so where there is no travel to build on, the element's own are
    // what the action is measured off.
    return ElementTransform(
        translationX = (base?.translationX ?: 0f) + dx,
        translationY = (base?.translationY ?: 0f) + dy,
        scaleX = (base?.scaleX ?: 1f) * scale,
        scaleY = (base?.scaleY ?: 1f) * scale,
        rotation = (base?.rotation ?: element.rotation) + rotation,
        opacity = (base?.opacity ?: element.opacity) * opacity,
    )
}

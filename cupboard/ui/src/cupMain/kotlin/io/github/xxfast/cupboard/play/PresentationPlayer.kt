package io.github.xxfast.cupboard.play

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.xxfast.cupboard.canvas.LocalPlayTransition
import io.github.xxfast.cupboard.canvas.PlayTransition
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideTransition
import io.github.xxfast.cupboard.document.TransitionTrigger
import io.github.xxfast.cupboard.document.stepCount
import kotlinx.coroutines.delay
import net.kodein.cup.LocalPresentationState
import net.kodein.cup.PluginCupAPI
import net.kodein.cup.Presentation
import net.kodein.cup.PresentationPosition
import net.kodein.cup.PresentationState
import net.kodein.cup.SlideSpecs
import net.kodein.cup.Slides
import net.kodein.cup.TransitionSet
import net.kodein.cup.goToNextSlide
import net.kodein.cup.goToNextStep
import net.kodein.cup.goToPreviousSlide
import net.kodein.cup.goToPreviousStep
import net.kodein.cup.withPresentationState

/**
 * Host-facing playback controls. No-ops until [PresentationPlayer] is composed
 * with this controller.
 */
@Stable
public class PlayerController internal constructor() {
    internal var state: PresentationState? by mutableStateOf(null)

    /**
     * Where the show is: the index into the slides being *played*, so a deck with
     * skips in it counts them out the way the audience sees it.
     *
     * Two plain [Int] states rather than CuP's own position, so a host reads the
     * show without linking against CuP. Both sit at 0 until the player is
     * composed.
     */
    public var slideIndex: Int by mutableStateOf(0)
        internal set

    /** The step within [slideIndex]'s slide, 0 being the slide as it opens. */
    public var step: Int by mutableStateOf(0)
        internal set

    /** How many slides are playing: the deck minus whatever it skips. */
    public var slideCount: Int by mutableStateOf(0)
        internal set

    public fun next() { state?.goToNextStep() }
    public fun previous() { state?.goToPreviousStep() }
    public fun nextSlide() { state?.goToNextSlide() }
    public fun previousSlide() { state?.goToPreviousSlide() }
}

@Composable
public fun rememberPlayerController(): PlayerController = remember { PlayerController() }

/**
 * Plays [document] with CuP, starting at [startIndex] and [startStep] within it.
 * Handles arrows, space, enter, and backspace itself when focused (requested on
 * entry); Escape invokes [onExit]. Hosts can also drive it through [controller],
 * e.g. from a window level key handler when focus has wandered, and read where
 * the show has got to off the same controller.
 *
 * [startStep] is clamped to the steps the starting slide has, so a host that
 * hands over the step the editor was on cannot open the show past the end of it.
 */
@OptIn(PluginCupAPI::class)
@Composable
public fun PresentationPlayer(
    document: Document,
    startIndex: Int = 0,
    startStep: Int = 0,
    modifier: Modifier = Modifier,
    onExit: (() -> Unit)? = null,
    controller: PlayerController = rememberPlayerController(),
) {
    val layoutDirection = LocalLayoutDirection.current
    val slides = remember(document, layoutDirection) { document.toCupSlides(layoutDirection) }
    // The same list CuP is playing, as our own slides: what the automatic
    // trigger and the Magic Move below both read the document off.
    val playing: List<Slide> = remember(document) { document.playedSlides() }
    // CuP's slide is a dp box, and only its aspect matters: the board inside
    // scales itself into whatever it is given. 360dp tall, so a 16:9 deck comes
    // out at exactly SLIDE_SIZE_16_9 and the common case is unchanged.
    val slideSize: DpSize = remember(document.slideWidth, document.slideHeight) {
        DpSize(360.dp * (document.slideWidth / document.slideHeight), 360.dp)
    }
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event -> handleKey(event, controller, onExit) },
    ) {
        withPresentationState(
            initial = { list ->
                val index: Int = startIndex.coerceIn(0, maxOf(0, list.lastIndex))
                index to startStep.coerceIn(0, list.getOrNull(index)?.lastStep ?: 0)
            },
        ) {
            val state = LocalPresentationState.current
            DisposableEffect(state, playing.size) {
                controller.state = state
                controller.slideCount = playing.size
                onDispose { if (controller.state === state) controller.state = null }
            }

            // The show's own position, mirrored out as plain ints: what a host
            // reads to say where the deck is, and what Phase 6's presenter
            // display will follow.
            LaunchedEffect(state, controller) {
                snapshotFlow { state.currentPosition }.collect { at ->
                    controller.slideIndex = at.slideIndex
                    controller.step = at.step
                }
            }
            val transitions = remember { TransitionSet.moveHorizontal(layoutDirection) }
            val position: PresentationPosition = state.currentPosition

            // The change now on screen, for the slides that draw across it
            // rather than on one side of it. Worked out in composition rather
            // than in an effect, so the arriving slide has it on the very first
            // frame it is composed for.
            val play: PlayTransition? = remember(playing, position.slideIndex, state.forward) {
                val to: Slide = playing.getOrNull(position.slideIndex) ?: return@remember null
                val neighbour: Int =
                    if (state.forward) position.slideIndex - 1 else position.slideIndex + 1
                val from: Slide = playing.getOrNull(neighbour) ?: return@remember null
                // The leaving slide owns the animation going forward, the
                // arriving one owns it coming back: either way it is the
                // earlier slide's, played in the direction of travel.
                val governing: SlideTransition =
                    (if (state.forward) from.transition else to.transition) ?: return@remember null
                PlayTransition(from, to, state.forward, governing)
            }

            // A slide that leaves on its own: once it is out of builds, it waits
            // its delay out and goes. Never off the end of the deck, which would
            // be the presentation closing itself.
            LaunchedEffect(playing, position) {
                val slide: Slide = playing.getOrNull(position.slideIndex) ?: return@LaunchedEffect
                val transition: SlideTransition = slide.transition ?: return@LaunchedEffect
                if (transition.trigger != TransitionTrigger.Automatic) return@LaunchedEffect
                if (position.step < slide.stepCount() - 1) return@LaunchedEffect
                if (position.slideIndex >= playing.lastIndex) return@LaunchedEffect

                delay(transition.delayMs.toLong())
                state.goToNextSlide()
            }

            CompositionLocalProvider(LocalPlayTransition provides play) {
                Presentation(
                    slides = Slides(slides),
                    configuration = {
                        defaultSlideSpecs = SlideSpecs(
                            size = slideSize,
                            startTransitions = transitions,
                            endTransitions = transitions,
                        )
                    },
                    backgroundColor = Color(0xFF101223),
                )
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

private fun handleKey(
    event: KeyEvent,
    controller: PlayerController,
    onExit: (() -> Unit)?,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.Escape -> {
            onExit?.invoke()
            onExit != null
        }
        Key.DirectionRight, Key.DirectionDown, Key.Spacebar, Key.Enter -> {
            if (event.isShiftPressed) controller.nextSlide() else controller.next()
            true
        }
        Key.DirectionLeft, Key.DirectionUp, Key.Backspace -> {
            if (event.isShiftPressed) controller.previousSlide() else controller.previous()
            true
        }
        else -> false
    }
}

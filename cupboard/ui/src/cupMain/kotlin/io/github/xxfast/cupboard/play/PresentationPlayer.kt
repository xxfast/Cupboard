package io.github.xxfast.cupboard.play

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.xxfast.cupboard.canvas.LocalLinkHandler
import io.github.xxfast.cupboard.canvas.LocalPlayTransition
import io.github.xxfast.cupboard.canvas.PlayTransition
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.PlaybackSettings
import io.github.xxfast.cupboard.document.PlaybackType
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
import net.kodein.cup.goTo
import net.kodein.cup.goToNextSlide
import net.kodein.cup.goToNextStep
import net.kodein.cup.goToPreviousSlide
import net.kodein.cup.goToPreviousStep
import net.kodein.cup.lastPosition
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

    /**
     * Jumps the show to [slideIndex] and [step], both indices into the slides
     * being *played*. Out-of-range values are clamped to the deck, so a host may
     * hand over whatever it has without checking it first.
     */
    public fun goTo(slideIndex: Int, step: Int = 0) { state?.goTo(slideIndex, step) }

    /** Back to the deck's first slide, as it opens: what a loop and an idle restart do. */
    public fun goToStart() { state?.goTo(0, 0) }

    /** The last step of the last slide: where a deck that loops backwards comes out. */
    public fun goToEnd() {
        val presentation: PresentationState = state ?: return
        if (presentation.slides.isEmpty()) return
        presentation.goTo(presentation.lastPosition)
    }
}

/**
 * One step forward, wrapping round to the start of the deck when [loop] is on and
 * the show is already at its last step. Without [loop] the end of the deck is
 * simply where it stops, which is what [goToNextStep] already does.
 */
private fun PlayerController.advance(loop: Boolean) {
    val presentation: PresentationState = state ?: return
    if (loop && presentation.slides.isNotEmpty() &&
        presentation.currentPosition == presentation.lastPosition
    ) {
        goToStart()
    } else {
        presentation.goToNextStep()
    }
}

/** [advance] backwards: off the start of a looping deck is its very last step. */
private fun PlayerController.retreat(loop: Boolean) {
    val presentation: PresentationState = state ?: return
    if (loop && presentation.currentPosition == PresentationPosition(0, 0)) goToEnd()
    else presentation.goToPreviousStep()
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
 * How much of that actually advances the deck is `Document.playback`'s business:
 * a self-playing deck walks itself along, and a links-only one moves for nothing
 * but its links. See [PlaybackType].
 *
 * [onOpenUrl] is where a [LinkTarget.Url] goes. The player has no idea what a
 * browser is, and neither does anything else in this module: opening one is the
 * shell's job, so a link it is given is a link it decides what to do with.
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
    onOpenUrl: (String) -> Unit = {},
    controller: PlayerController = rememberPlayerController(),
) {
    val layoutDirection = LocalLayoutDirection.current
    val playback: PlaybackSettings = document.playback

    // Bumped by every key and every tap, and by nothing else: what the idle
    // restart measures its wait from. A counter rather than a timestamp so it
    // re-keys the effect that is doing the waiting.
    var activity: Int by remember { mutableStateOf(0) }
    val slides = remember(document, layoutDirection) { document.toCupSlides(layoutDirection) }
    // The same list CuP is playing, as our own slides: what the automatic
    // trigger and the Magic Move below both read the document off.
    val playing: List<Slide> = remember(document) { document.playOrder() }
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
            .onPreviewKeyEvent { event ->
                handleKey(event, controller, playback, onExit) { activity++ }
            }
            // A click anywhere the slide isn't already using. Links sit above
            // this and consume their own taps, so a link is never also a step.
            .pointerInput(playback) {
                detectTapGestures {
                    activity++
                    if (playback.type == PlaybackType.Normal) controller.advance(playback.loop)
                }
            },
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
            // reads to say where the deck is, and what the presenter display
            // follows.
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
            //
            // A presenter-driven deck only: the other two kinds of show have a
            // pace of their own, and a slide's delay would be arguing with it.
            LaunchedEffect(playing, position, playback) {
                if (playback.type != PlaybackType.Normal) return@LaunchedEffect
                val slide: Slide = playing.getOrNull(position.slideIndex) ?: return@LaunchedEffect
                val transition: SlideTransition = slide.transition ?: return@LaunchedEffect
                if (transition.trigger != TransitionTrigger.Automatic) return@LaunchedEffect
                if (position.step < slide.stepCount() - 1) return@LaunchedEffect
                if (position.slideIndex >= playing.lastIndex) return@LaunchedEffect

                delay(transition.delayMs.toLong())
                state.goToNextSlide()
            }

            // The self-playing walk: every step, not every slide, so a deck
            // builds itself at the same pace it turns. The last step is where it
            // stops unless the deck loops, in which case there is no last step.
            LaunchedEffect(position, playback) {
                if (playback.type != PlaybackType.SelfPlaying) return@LaunchedEffect
                val atEnd: Boolean =
                    state.slides.isNotEmpty() && position == state.lastPosition
                if (atEnd && !playback.loop) return@LaunchedEffect

                delay(playback.autoAdvanceMs.toLong())
                controller.advance(playback.loop)
            }

            // Nobody has touched it for long enough: back to the top, ready for
            // the next person to walk up to it. Only a links-only deck does this,
            // and only when it is asked to.
            LaunchedEffect(activity, playback) {
                if (playback.type != PlaybackType.LinksOnly) return@LaunchedEffect
                if (playback.restartAfterIdleMs <= 0) return@LaunchedEffect

                delay(playback.restartAfterIdleMs.toLong())
                controller.goToStart()
            }

            // What a link on a slide does. Everything that moves inside the deck
            // goes through the document, so the player never works out an index
            // of its own; the two that leave the deck go to the host.
            val onLink: (LinkTarget) -> Unit = { target ->
                activity++
                when (target) {
                    is LinkTarget.Url -> onOpenUrl(target.url)
                    LinkTarget.ExitShow -> onExit?.invoke()
                    else -> document
                        .linkDestination(target, PlayPosition(position.slideIndex, position.step))
                        ?.let { controller.goTo(it.slideIndex, it.step) }
                }
            }

            CompositionLocalProvider(
                LocalPlayTransition provides play,
                LocalLinkHandler provides onLink,
            ) {
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

/**
 * The keys the show answers to, and what [playback] lets them do.
 *
 * Escape always exits, whatever kind of show this is: a deck that could not be
 * closed from the keyboard would be a deck that had taken the screen hostage.
 * The rest move the deck in every kind but [PlaybackType.LinksOnly], where only
 * the links on the slides move anything. [onActivity] fires for every key press
 * regardless, since the idle restart is about the room rather than about the deck.
 */
private fun handleKey(
    event: KeyEvent,
    controller: PlayerController,
    playback: PlaybackSettings,
    onExit: (() -> Unit)?,
    onActivity: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    onActivity()

    if (event.key == Key.Escape) {
        onExit?.invoke()
        return onExit != null
    }
    if (playback.type == PlaybackType.LinksOnly) return false

    return when (event.key) {
        Key.DirectionRight, Key.DirectionDown, Key.Spacebar, Key.Enter -> {
            if (event.isShiftPressed) controller.nextSlide() else controller.advance(playback.loop)
            true
        }
        Key.DirectionLeft, Key.DirectionUp, Key.Backspace -> {
            if (event.isShiftPressed) controller.previousSlide()
            else controller.retreat(playback.loop)
            true
        }
        else -> false
    }
}

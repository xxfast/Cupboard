package io.github.xxfast.cupboard.play

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.xxfast.cupboard.document.Document
import net.kodein.cup.LocalPresentationState
import net.kodein.cup.PluginCupAPI
import net.kodein.cup.Presentation
import net.kodein.cup.PresentationState
import net.kodein.cup.SLIDE_SIZE_16_9
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

    public fun next() { state?.goToNextStep() }
    public fun previous() { state?.goToPreviousStep() }
    public fun nextSlide() { state?.goToNextSlide() }
    public fun previousSlide() { state?.goToPreviousSlide() }
}

@Composable
public fun rememberPlayerController(): PlayerController = remember { PlayerController() }

/**
 * Plays [document] with CuP, starting at [startIndex]. Handles arrows, space,
 * enter, and backspace itself when focused (requested on entry); Escape invokes
 * [onExit]. Hosts can also drive it through [controller], e.g. from a window
 * level key handler when focus has wandered.
 */
@OptIn(PluginCupAPI::class)
@Composable
public fun PresentationPlayer(
    document: Document,
    startIndex: Int = 0,
    modifier: Modifier = Modifier,
    onExit: (() -> Unit)? = null,
    controller: PlayerController = rememberPlayerController(),
) {
    val slides = remember(document) { document.toCupSlides() }
    val focusRequester = remember { FocusRequester() }
    val layoutDirection = LocalLayoutDirection.current

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event -> handleKey(event, controller, onExit) },
    ) {
        withPresentationState(
            initial = { list -> startIndex.coerceIn(0, list.lastIndex) to 0 },
        ) {
            val state = LocalPresentationState.current
            DisposableEffect(state) {
                controller.state = state
                onDispose { if (controller.state === state) controller.state = null }
            }
            val transitions = remember { TransitionSet.moveHorizontal(layoutDirection) }
            Presentation(
                slides = Slides(slides),
                configuration = {
                    defaultSlideSpecs = SlideSpecs(
                        size = SLIDE_SIZE_16_9,
                        startTransitions = transitions,
                        endTransitions = transitions,
                    )
                },
                backgroundColor = Color(0xFF101223),
            )
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

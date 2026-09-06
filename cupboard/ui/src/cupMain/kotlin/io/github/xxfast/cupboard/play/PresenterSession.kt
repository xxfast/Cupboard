package io.github.xxfast.cupboard.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.xxfast.cupboard.document.Document
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/**
 * The show's stopwatch. Starts when the presenter display is first composed and
 * runs until it goes away; [resetTimer] puts it back to zero, which is what a
 * presenter wants after a false start.
 *
 * A remembered object rather than a plain state so a host can hold onto it and
 * wire a menu item or a button to the reset.
 */
@Stable
public class PresenterClock internal constructor() {
    /** How long the show has been running, to the second. */
    public var elapsedMs: Long by mutableStateOf(0L)
        internal set

    // Bumped rather than assigned, so a second reset within the same second
    // still restarts the ticker: the count is the key the effect restarts on.
    internal var resets: Int by mutableStateOf(0)
        private set

    public fun resetTimer() { resets++ }
}

@Composable
public fun rememberPresenterClock(): PresenterClock = remember { PresenterClock() }

/**
 * The presenter display, following the same [controller] the show is playing on.
 *
 * All this adds over [PresenterDisplay] is the wiring: the position read off the
 * controller, the stopwatch, and the buttons handed back to the controller. The
 * display itself knows nothing about CuP, which is why it lives in commonMain
 * and this does not.
 *
 * [clock] is the wall clock, as a string, because this module has no date
 * formatter: `kotlinx-datetime` is not a dependency, and `kotlin.time` can
 * measure an interval but cannot name a time of day. Hosts pass their platform's
 * formatter; the default leaves the clock off rather than showing a wrong one.
 *
 * [onNotesChange] is the host's to fold into its document, per keystroke.
 *
 * The arrangement is this view's own state rather than a parameter: it is a
 * lectern preference, changed from the display's own picker mid-show, and no
 * host has anything to say about it.
 */
@Composable
public fun PresenterView(
    document: Document,
    controller: PlayerController,
    onNotesChange: (slideId: String, notes: String) -> Unit,
    modifier: Modifier = Modifier,
    clock: () -> String = { "" },
    presenterClock: PresenterClock = rememberPresenterClock(),
) {
    // Notes over the next slide by default: the next slide is a click away, the
    // notes are the thing you can't get back if the display isn't showing them.
    var layout: PresenterLayout by remember { mutableStateOf(PresenterLayout.CurrentAndNotes) }

    LaunchedEffect(presenterClock, presenterClock.resets) {
        val started = TimeSource.Monotonic.markNow()
        presenterClock.elapsedMs = 0L
        while (true) {
            delay(1000)
            presenterClock.elapsedMs = started.elapsedNow().inWholeMilliseconds
        }
    }

    // Re-read on every tick rather than every recomposition: a wall clock only
    // has to be right to the second, and the ticker is already a second's beat.
    val now: String = remember(presenterClock.elapsedMs, clock) { clock() }

    PresenterDisplay(
        document = document,
        position = PlayPosition(controller.slideIndex, controller.step),
        layout = layout,
        elapsedMs = presenterClock.elapsedMs,
        clock = now,
        onNotesChange = onNotesChange,
        onNext = { controller.next() },
        onPrevious = { controller.previous() },
        onLayoutChange = { layout = it },
        onResetTimer = presenterClock::resetTimer,
        modifier = modifier,
    )
}

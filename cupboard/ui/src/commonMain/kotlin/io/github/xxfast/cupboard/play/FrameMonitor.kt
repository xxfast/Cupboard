package io.github.xxfast.cupboard.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Where a frame report goes, one line at a time, and the display's refresh rate
 * a missed frame is counted against. The shell knows the rate and this module
 * doesn't: guessed off the frames themselves, macOS's jitter had every frame of
 * a smooth 120Hz show counted as missed.
 */
class FrameReporter(val refreshHz: Int, val report: (String) -> Unit)

/**
 * The [FrameReporter] the show measures into. Null, the default, measures
 * nothing: watching frames asks for one every vsync, which a show nobody is
 * profiling has no reason to pay for. Shells hand one in for their dev builds.
 */
val LocalFrameReporter = staticCompositionLocalOf<FrameReporter?> { null }

/** A frame this long is a hitch, said the moment it happens rather than in the summary. */
private const val HitchMs: Float = 50f

/**
 * Times every frame of whatever is on screen and reports to [LocalFrameReporter]:
 * a line per place the show was at ([where], read every frame), saying how many
 * frames it drew there, how many it missed and its worst one, and a line of its
 * own for any frame past [HitchMs] the moment it lands.
 *
 * A miss is a refresh the frame overran, against [FrameReporter.refreshHz].
 */
@Composable
fun FrameMonitor(where: () -> String) {
    val reporter: FrameReporter = LocalFrameReporter.current ?: return
    val place: () -> String by rememberUpdatedState(where)

    LaunchedEffect(reporter) {
        val report: (String) -> Unit = reporter.report
        val frames = FrameStats(refresh = 1_000_000_000L / reporter.refreshHz.coerceAtLeast(1))
        var last: Long = withFrameNanos { it }
        var at: String = place()
        var arrived: Long = last

        while (true) {
            val now: Long = withFrameNanos { it }
            val interval: Long = now - last
            last = now

            val here: String = place()
            if (here != at) {
                report("$at: ${frames.summary()}")
                frames.reset()
                at = here
                arrived = now
            }

            frames.add(interval)
            val ms: Float = interval / 1_000_000f
            if (ms >= HitchMs) {
                report("hitch ${ms.roundToInt()}ms at $here, ${(now - arrived) / 1_000_000}ms in")
            }
        }
    }
}

/** The frames drawn at one place in the show, against a display that refreshes every [refresh] ns. */
internal class FrameStats(private val refresh: Long) {
    private var count: Int = 0
    private var total: Long = 0
    private var worst: Long = 0
    private var missed: Int = 0

    fun add(interval: Long) {
        if (interval <= 0) return
        count++
        total += interval
        worst = max(worst, interval)
        missed += max(0, (interval.toDouble() / refresh).roundToInt() - 1)
    }

    fun reset() {
        count = 0
        total = 0
        worst = 0
        missed = 0
    }

    fun summary(): String =
        "$count frames over ${total / 1_000_000}ms, $missed missed, worst ${worst / 1_000_000}ms"
}

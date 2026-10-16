package io.github.xxfast.cupboard.play

import kotlin.test.Test
import kotlin.test.assertEquals

class FrameStatsTest {
    private val frame: Long = 16_666_667

    @Test
    fun aFrameTwiceTheRefreshIsOneMissed() {
        val stats = FrameStats(refresh = frame)
        repeat(10) { stats.add(frame) }
        stats.add(frame * 2)
        stats.add(frame * 4)
        assertEquals("12 frames over 266ms, 4 missed, worst 66ms", stats.summary())
    }

    /** macOS hands frames over with jitter: a short one and a long one are still on time. */
    @Test
    fun jitterAroundTheRefreshMissesNothing() {
        val stats = FrameStats(refresh = 8_333_333)
        repeat(50) {
            stats.add(4_200_000)
            stats.add(8_300_000)
            stats.add(12_000_000)
        }
        assertEquals(0, stats.summary().substringAfter("ms, ").substringBefore(" missed").toInt())
    }

    @Test
    fun aResetStartsTheCountAgain() {
        val stats = FrameStats(refresh = frame)
        stats.add(frame * 3)
        stats.reset()
        stats.add(frame)
        assertEquals("1 frames over 16ms, 0 missed, worst 16ms", stats.summary())
    }
}

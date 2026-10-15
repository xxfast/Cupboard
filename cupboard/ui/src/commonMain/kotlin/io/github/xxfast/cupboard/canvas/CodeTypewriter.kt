package io.github.xxfast.cupboard.canvas

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.Offset
import io.github.xxfast.cupboard.document.TypingRhythm
import kotlin.math.floor
import kotlin.math.pow

/**
 * How long one character takes to backspace out: twice the typing pace, which is
 * what a held backspace does next to someone typing.
 */
internal const val TypewriterEraseMsPerChar: Float = TypewriterMsPerChar / 2f

/** How many of this run's characters put ink down: the only ones typing costs. */
internal fun String.drawnCount(): Int = count { !it.isWhitespace() }

/**
 * How much of this run to set for [drawn] of its inked characters to show: the
 * length up to and including the [drawn]th one. Whitespace inside a run (a string
 * literal, a comment) rides along with the character ahead of it, so it costs
 * nothing and never holds the caret on a blank.
 */
internal fun String.drawnPrefix(drawn: Int): Int {
    if (drawn <= 0) return 0
    var seen = 0
    for (index in indices) {
        if (this[index].isWhitespace()) continue
        seen += 1
        if (seen == drawn) return index + 1
    }
    return length
}

/**
 * A running total spent over [costs] in order: how much of each one [budget]
 * covers, the first ones whole, then one in part, then none.
 */
internal fun spend(costs: List<Int>, budget: Int): List<Int> {
    var left: Int = budget.coerceAtLeast(0)
    return costs.map { cost -> minOf(cost, left).also { left -= it } }
}

/**
 * How long each key of a typed phase takes, as shares of the phase that average
 * 1 over the [runs]' inked characters in order. The phase's length is the
 * character count times the pace whatever the rhythm; this is only where along
 * it each key lands.
 *
 * [TypingRhythm.Steady] is 1 everywhere. [TypingRhythm.Natural] is what hands
 * do: every gap jittered about the mean on a rough bell, the first key of every
 * run slower (a new token is a new word to find), and now and then a hesitation.
 * A function of the key's place alone, so a frame drawn twice is the same frame
 * and a test can ask for one.
 */
internal fun keyPaces(runs: List<Int>, rhythm: TypingRhythm): List<Float> {
    val total: Int = runs.sum()
    if (rhythm == TypingRhythm.Steady || total == 0) return List(total) { 1f }

    val raw = ArrayList<Float>(total)
    var index = 0
    for (run in runs) {
        for (key in 0 until run) {
            val jitter: Float = 2f.pow((noise(index, 1) + noise(index, 2) - 1f) * 1.2f)
            val start: Float = if (key == 0) 1.8f else 1f
            val hesitation: Float = if (noise(index, 3) < 0.05f) 3f else 1f
            raw += jitter * start * hesitation
            index += 1
        }
    }
    val mean: Float = raw.sum() / total
    return raw.map { it / mean }
}

/** A fixed 0..1 for a key's [index] under a [salt], an integer hash's low bits. */
private fun noise(index: Int, salt: Int): Float {
    var x: Int = index * 0x9E3779B1.toInt() + salt * 0x6A09E667
    x = x xor (x ushr 16)
    x *= 0x7FEB352D
    x = x xor (x ushr 15)
    x *= 0x846CA68B.toInt()
    x = x xor (x ushr 16)
    return (x ushr 8) / 16777216f
}

/**
 * How many keys of [paces] have landed [local] of the way through their phase:
 * the leading ones whose shares, added up, the time so far covers. All paces 1
 * is a plain count, which is what a steady phase reads.
 */
internal fun landed(local: Float, paces: List<Float>): Int {
    val budget: Float = local.coerceIn(0f, 1f) * paces.sum() + 1e-4f
    var spent = 0f
    var count = 0
    for (pace in paces) {
        spent += pace
        if (spent > budget) break
        count += 1
    }
    return count
}

/**
 * Which runs of a morph a typed step change erases and types, in reading order.
 *
 * Indices into the runs, so the plan that measured them can find each one again.
 * Reading order is by position, top to bottom then left to right, rather than the
 * order [morphGlyphs] happens to hand them back in: a token whose line one side
 * hides comes out of the diff where its match sits, which need not be where it
 * reads. Sorting on position is the same answer on a grid and in a laid out block,
 * which is what lets the font-free timeline and the drawn frame agree on which
 * run is which.
 */
internal data class TypingOrder(val departing: List<Int>, val arriving: List<Int>)

internal fun typingOrder(runs: List<GlyphRun>): TypingOrder {
    val reading: Comparator<Pair<Int, Offset>> = compareBy({ it.second.y }, { it.second.x })

    val departing: List<Int> = runs.withIndex()
        .mapNotNull { (index, run) -> (run as? GlyphRun.Left)?.let { index to it.position } }
        .sortedWith(reading)
        .map { it.first }

    val arriving: List<Int> = runs.withIndex()
        .mapNotNull { (index, run) -> (run as? GlyphRun.Arrived)?.let { index to it.position } }
        .sortedWith(reading)
        .map { it.first }

    return TypingOrder(departing, arriving)
}

/**
 * Where the caret sits in one frame of a typed step change: after [drawn] inked
 * characters of the [run]th departing run while erasing, or of the [run]th
 * arriving one while typing. Runs count in [TypingOrder]'s reading order.
 */
internal data class TypewriterCaret(val departing: Boolean, val run: Int, val drawn: Int)

/**
 * A typed step change on one clock, in three phases: erase, move, type.
 *
 * [departing] and [arriving] are the inked character counts of the runs the old
 * step loses and the new one gains, both in reading order. Erasing walks the
 * departing ones from the back, a held backspace from the end of the last of
 * them, at a key repeat's even pace; typing walks the arriving ones from the
 * front, each key taking its share of [paces]. In between, everything both
 * steps show slides and recolours, the block scrolls and the gutter crossfades,
 * over [moveMs].
 *
 * Everything here reads a fraction of the whole clock, so the one [progress]
 * the block already runs is enough: each phase is a slice of it.
 */
internal data class TypewriterTimeline(
    val departing: List<Int>,
    val arriving: List<Int>,
    val eraseMs: Float,
    val moveMs: Float,
    val typeMs: Float,
    /** One per arriving inked character, in order: see [keyPaces]. */
    val paces: List<Float>,
) {
    val totalMs: Float get() = eraseMs + moveMs + typeMs

    private fun elapsed(fraction: Float): Float = fraction.coerceIn(0f, 1f) * totalMs

    /** How far through a phase starting [start] ms in and lasting [length] the clock is. */
    private fun local(fraction: Float, start: Float, length: Float): Float =
        if (length <= 0f) (if (elapsed(fraction) >= start) 1f else 0f)
        else ((elapsed(fraction) - start) / length).coerceIn(0f, 1f)

    /** How many inked characters a linear phase has got through, out of [total]. */
    private fun count(local: Float, total: Int): Int =
        floor(local * total).toInt().coerceIn(0, total)

    /** Each departing run's inked characters still showing at [fraction]. */
    fun departingAt(fraction: Float): List<Int> {
        val erased: Int = count(local(fraction, 0f, eraseMs), departing.sum())
        val gone: List<Int> = spend(departing.asReversed(), erased).asReversed()
        return departing.mapIndexed { index, cost -> cost - gone[index] }
    }

    /** How far the move phase is at [fraction], eased: 0 where it starts, 1 where it ends. */
    fun moveAt(fraction: Float): Float =
        FastOutSlowInEasing.transform(local(fraction, eraseMs, moveMs))

    /** Each arriving run's inked characters typed by [fraction]. */
    fun arrivingAt(fraction: Float): List<Int> {
        val typed: Int = landed(local(fraction, eraseMs + moveMs, typeMs), paces)
        return spend(arriving, typed)
    }

    /**
     * Where the caret is at [fraction], null outside the erase and type phases.
     *
     * Erasing, it sits at the erase point: after the last character still showing
     * of the last departing run that has any. Typing, it sits after the last
     * character typed, or before the first one when none has been yet.
     */
    fun caretAt(fraction: Float): TypewriterCaret? {
        if (fraction >= 1f) return null
        val now: Float = elapsed(fraction)

        if (now < eraseMs) {
            val showing: List<Int> = departingAt(fraction)
            val run: Int = showing.indexOfLast { it > 0 }
            return if (run < 0) null else TypewriterCaret(departing = true, run = run, drawn = showing[run])
        }

        if (typeMs > 0f && now >= eraseMs + moveMs) {
            val typed: List<Int> = arrivingAt(fraction)
            val run: Int = typed.indexOfLast { it > 0 }
            return if (run < 0) TypewriterCaret(departing = false, run = 0, drawn = 0)
            else TypewriterCaret(departing = false, run = run, drawn = typed[run])
        }

        return null
    }
}

/**
 * The timeline for erasing [departing] and typing [arriving] inked characters,
 * [moves] being whether anything both steps show changes place or colour, and
 * [rhythm] how the typing's keys fall.
 *
 * Its natural length is the three phases at their own paces: [TypewriterEraseMsPerChar]
 * a character out, [CodeStepDuration] to move, [TypewriterMsPerChar] a character
 * in. An empty phase takes no time at all. A build asking for longer ([durationMs])
 * stretches all three by the same factor; one asking for less gets the natural
 * length anyway, the floor [typewriterDurationMs] holds a terminal to: a long
 * rewrite typed inside a short build reads as a blur.
 */
internal fun typewriterTimeline(
    departing: List<Int>,
    arriving: List<Int>,
    moves: Boolean,
    durationMs: Int,
    rhythm: TypingRhythm,
): TypewriterTimeline {
    val erase: Float = departing.sum() * TypewriterEraseMsPerChar
    val move: Float = if (moves) CodeStepDuration.toFloat() else 0f
    val type: Float = arriving.sum() * TypewriterMsPerChar.toFloat()
    val natural: Float = erase + move + type
    val stretch: Float = if (natural > 0f) maxOf(1f, durationMs / natural) else 1f
    return TypewriterTimeline(
        departing = departing,
        arriving = arriving,
        eraseMs = erase * stretch,
        moveMs = move * stretch,
        typeMs = type * stretch,
        paces = keyPaces(arriving, rhythm),
    )
}

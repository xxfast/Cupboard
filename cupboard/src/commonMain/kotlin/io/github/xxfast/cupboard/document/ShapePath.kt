package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * An outline of a shape's own: what a [ShapeKind.Path] draws by.
 *
 * [data] is SVG path data, normalised to absolute `M`, `L`, `C` and `Z`, so a
 * renderer reads four commands rather than the twenty SVG has (see [commands]).
 * It is written in a coordinate space of its own, and [bounds] is the box of that
 * space the element's frame shows: the renderer maps the one onto the other, so a
 * resize stretches the outline and nobody rewrites a number.
 *
 * That is also what keeps breaking a compound shape apart cheap. Each piece keeps
 * its slice of [data] exactly as it was written and gets a tighter [bounds] (see
 * `Slide.breakApartElement`).
 *
 * [evenOdd] is SVG's `fill-rule`. False is nonzero, where a contour cuts a hole
 * only when it winds against the one around it.
 */
@Serializable
data class ShapePath(
    val data: String,
    val bounds: Frame,
    val evenOdd: Boolean = false,
)

/** The four commands [ShapePath.data] is written in, every coordinate absolute. */
sealed interface PathCommand {
    data class MoveTo(val x: Float, val y: Float) : PathCommand
    data class LineTo(val x: Float, val y: Float) : PathCommand
    data class CubicTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x: Float,
        val y: Float,
    ) : PathCommand
    data object Close : PathCommand
}

/** Parsed from [ShapePath.data]; tolerant, a malformed tail is dropped rather than thrown. */
val ShapePath.commands: List<PathCommand>
    get() {
        val scanner = PathScanner(data)
        val commands: MutableList<PathCommand> = mutableListOf()
        var letter: Char = scanner.letter() ?: return commands

        while (true) {
            commands += when (letter) {
                'M' -> {
                    val (x, y) = scanner.numbers(2) ?: break
                    PathCommand.MoveTo(x, y)
                }

                'L' -> {
                    val (x, y) = scanner.numbers(2) ?: break
                    PathCommand.LineTo(x, y)
                }

                'C' -> {
                    val at: List<Float> = scanner.numbers(6) ?: break
                    PathCommand.CubicTo(at[0], at[1], at[2], at[3], at[4], at[5])
                }

                'Z' -> PathCommand.Close

                // Anything else is not something this file wrote: stop at it.
                else -> break
            }

            // More numbers with no letter in front repeat the command, as in SVG.
            if (letter != 'Z' && scanner.hasNumber()) continue
            letter = scanner.letter() ?: break
        }

        return commands
    }

/** A Path shape with two or more subpaths: what Break Apart takes. */
val Element.isCompoundShape: Boolean
    get() {
        if (this !is ShapeElement || kind != ShapeKind.Path) return false
        val path: ShapePath = path ?: return false
        return path.commands.subpaths().size >= 2
    }

/** These commands as [ShapePath.data]: what [ShapePath.commands] reads back. */
internal fun List<PathCommand>.toPathData(): String = joinToString(" ") { command ->
    when (command) {
        is PathCommand.MoveTo -> "M${command.x.written()} ${command.y.written()}"
        is PathCommand.LineTo -> "L${command.x.written()} ${command.y.written()}"
        is PathCommand.CubicTo ->
            "C${command.x1.written()} ${command.y1.written()} " +
                "${command.x2.written()} ${command.y2.written()} " +
                "${command.x.written()} ${command.y.written()}"
        PathCommand.Close -> "Z"
    }
}

/**
 * At most four decimals, trailing zeros stripped. A float printed in full is
 * nine digits of noise per coordinate in a file people diff, and a ten-thousandth
 * of a unit is well under a pixel at any size an outline is drawn at.
 */
private fun Float.written(): String {
    if (!isFinite()) return "0"
    val scaled: Long = (toDouble() * 10_000).roundToLong()
    val whole: Long = abs(scaled) / 10_000
    val fraction: String = (abs(scaled) % 10_000).toString().padStart(4, '0').trimEnd('0')
    val sign: String = if (scaled < 0) "-" else ""
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}

/**
 * The smallest box the outline fits in, all zeros for one that draws nothing.
 *
 * A curve is measured where it actually turns rather than by its control points:
 * those sit well outside the curve they pull on, and a frame fitted to them would
 * leave the handles floating off the shape.
 */
internal fun List<PathCommand>.tightBounds(): Frame {
    var left: Float = Float.POSITIVE_INFINITY
    var top: Float = Float.POSITIVE_INFINITY
    var right: Float = Float.NEGATIVE_INFINITY
    var bottom: Float = Float.NEGATIVE_INFINITY

    fun include(x: Float, y: Float) {
        left = min(left, x)
        top = min(top, y)
        right = max(right, x)
        bottom = max(bottom, y)
    }

    // Where the pen is, and where the subpath it is drawing began.
    var x = 0f
    var y = 0f
    var startX = 0f
    var startY = 0f

    for (command in this) {
        when (command) {
            // A move draws nothing by itself, so it only counts once something
            // is drawn from it.
            is PathCommand.MoveTo -> {
                x = command.x
                y = command.y
                startX = x
                startY = y
            }

            is PathCommand.LineTo -> {
                include(x, y)
                include(command.x, command.y)
                x = command.x
                y = command.y
            }

            is PathCommand.CubicTo -> {
                include(x, y)
                include(command.x, command.y)
                val turns: List<Float> =
                    cubicTurns(x, command.x1, command.x2, command.x) +
                        cubicTurns(y, command.y1, command.y2, command.y)
                for (t in turns) {
                    include(
                        cubicAt(x, command.x1, command.x2, command.x, t),
                        cubicAt(y, command.y1, command.y2, command.y, t),
                    )
                }
                x = command.x
                y = command.y
            }

            PathCommand.Close -> {
                x = startX
                y = startY
            }
        }
    }

    if (left > right) return Frame(0f, 0f, 0f, 0f)
    return Frame(left, top, right - left, bottom - top)
}

/** Where along a cubic one coordinate stops and turns back, as `t` strictly inside 0..1. */
private fun cubicTurns(p0: Float, p1: Float, p2: Float, p3: Float): List<Float> {
    // The derivative, over three: a quadratic in t.
    val a: Double = -p0.toDouble() + 3.0 * p1 - 3.0 * p2 + p3
    val b: Double = 2.0 * (p0.toDouble() - 2.0 * p1 + p2)
    val c: Double = p1.toDouble() - p0

    val roots: List<Double> = when {
        abs(a) < 1e-9 -> if (abs(b) < 1e-9) emptyList() else listOf(-c / b)

        else -> {
            val discriminant: Double = b * b - 4.0 * a * c
            if (discriminant < 0.0) emptyList()
            else listOf((-b + sqrt(discriminant)) / (2.0 * a), (-b - sqrt(discriminant)) / (2.0 * a))
        }
    }

    return roots.filter { it > 0.0 && it < 1.0 }.map { it.toFloat() }
}

private fun cubicAt(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
    val u: Float = 1f - t
    return u * u * u * p0 + 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t * p3
}

/**
 * The contours of an outline, each a command list starting at its own `M`: what
 * Break Apart hands back one shape apiece for.
 *
 * A move that nothing is drawn from is no contour and is dropped. A contour
 * picked back up after its `Z` without a move starts where the closed one did,
 * which is SVG's rule, and gets that move written out so it stands alone.
 */
internal fun List<PathCommand>.subpaths(): List<List<PathCommand>> {
    val subpaths: MutableList<List<PathCommand>> = mutableListOf()
    var current: MutableList<PathCommand> = mutableListOf()
    var start = PathCommand.MoveTo(0f, 0f)

    fun flush() {
        if (current.isEmpty()) return
        subpaths += current
        current = mutableListOf()
    }

    for (command in this) {
        when (command) {
            is PathCommand.MoveTo -> {
                flush()
                start = command
            }

            PathCommand.Close -> {
                if (current.isEmpty()) continue
                current += command
                flush()
            }

            else -> {
                if (current.isEmpty()) current += start
                current += command
            }
        }
    }

    flush()
    return subpaths
}

/**
 * This box with any side shorter than its minimum grown to it, around its centre.
 *
 * A straight line's tight bounds have no width or no height, and a frame like
 * that has no handles to grab and nothing to stretch an outline across. Growing
 * the path's bounds and the element's frame by the same rule keeps the outline
 * drawn exactly where it was.
 */
internal fun Frame.atLeast(minWidth: Float, minHeight: Float): Frame {
    val grownWidth: Float = max(width, minWidth)
    val grownHeight: Float = max(height, minHeight)
    return Frame(
        x = x - (grownWidth - width) / 2,
        y = y - (grownHeight - height) / 2,
        width = grownWidth,
        height = grownHeight,
    )
}

/**
 * Reads path data a token at a time, the way SVG packs it: whitespace and commas
 * are optional wherever the next token can only start a new one, so `1.5.5` is
 * two numbers and so is `-1-2`.
 *
 * Shared by [ShapePath.commands], which reads the four commands this file writes,
 * and the SVG reader, which reads all twenty.
 */
internal class PathScanner(private val text: String) {
    private var index: Int = 0

    /** Whatever has not been read yet: the unit after a length's number. */
    val rest: String get() = text.substring(index)

    private fun skipSeparators() {
        while (index < text.length && (text[index].isWhitespace() || text[index] == ',')) index++
    }

    /** The command letter next up, consumed. Null, and nothing consumed, for anything else. */
    fun letter(): Char? {
        skipSeparators()
        val next: Char = text.getOrNull(index) ?: return null
        if (next !in 'a'..'z' && next !in 'A'..'Z') return null
        index++
        return next
    }

    /** Whether a number is next, which after a command's arguments means "the same again". */
    fun hasNumber(): Boolean {
        skipSeparators()
        val next: Char = text.getOrNull(index) ?: return false
        return next in '0'..'9' || next == '.' || next == '-' || next == '+'
    }

    /** The number next up, consumed. Null, and nothing consumed, when there is none. */
    fun number(): Float? {
        skipSeparators()
        var at: Int = index
        if (at < text.length && (text[at] == '-' || text[at] == '+')) at++

        var digits = 0
        while (at < text.length && text[at] in '0'..'9') {
            at++
            digits++
        }
        if (at < text.length && text[at] == '.') {
            at++
            while (at < text.length && text[at] in '0'..'9') {
                at++
                digits++
            }
        }
        if (digits == 0) return null

        // An exponent only when digits follow it: the `e` of `1em` is a unit.
        if (at < text.length && (text[at] == 'e' || text[at] == 'E')) {
            var exponent: Int = at + 1
            if (exponent < text.length && (text[exponent] == '-' || text[exponent] == '+')) exponent++
            if (exponent < text.length && text[exponent] in '0'..'9') {
                while (exponent < text.length && text[exponent] in '0'..'9') exponent++
                at = exponent
            }
        }

        val number: Float = text.substring(index, at).toFloatOrNull()?.takeIf { it.isFinite() }
            ?: return null
        index = at
        return number
    }

    /** [count] numbers in a row, or null when the data runs out before the last of them. */
    fun numbers(count: Int): List<Float>? {
        val numbers: MutableList<Float> = mutableListOf()
        repeat(count) { numbers += number() ?: return null }
        return numbers
    }

    /**
     * One of an arc's two flags: a single `0` or `1`, which SVG lets run straight
     * into the number after it (`a1 1 0 01.5.5`), so it cannot be read as a number.
     */
    fun flag(): Boolean? {
        skipSeparators()
        val flag: Boolean = when (text.getOrNull(index)) {
            '0' -> false
            '1' -> true
            else -> return null
        }
        index++
        return flag
    }
}

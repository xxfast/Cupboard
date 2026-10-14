package io.github.xxfast.cupboard.document

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * What a colour well's popover offers: Keynote's Colour Fills, taken off the
 * deck's theme rather than hardcoded, so a pick off the grid keeps a slide on it.
 *
 * Packed ARGB like the rest of the model, every swatch opaque. [grid] is
 * [PALETTE_TINTS] rows of [PALETTE_HUES], lightest row first, each column one
 * hue. [greys] is the row under it, white to black.
 */
data class ColourPalette(
    val grid: List<List<Long>>,
    val greys: List<Long>,
)

const val PALETTE_HUES: Int = 6
const val PALETTE_TINTS: Int = 4

/**
 * The palette this deck's look implies: one column per distinct hue among the
 * accent, the shape fill and stroke and the three inks, in that order, topped up
 * to six from a fixed spread of hues when the theme has fewer.
 *
 * A column is four tints of its hue, light to dark, and the theme's own colour
 * stands in for the tint nearest its lightness, so the accent is in the grid
 * exactly rather than as a near miss. Greys and near-black or near-white inks
 * take no column: the bottom row already has them.
 */
fun ElementDefaults.palette(): ColourPalette {
    val sources: List<Long> = listOf(
        accent,
        shapeFill,
        shapeStroke,
        textColor,
        bodyColor,
        shapeLabelColor,
    )

    val bases: MutableList<Hsl> = mutableListOf()
    for (source in sources) {
        if (bases.size == PALETTE_HUES) break

        val hsl: Hsl = source.toHsl()
        if (hsl.saturation < CHROMATIC || hsl.lightness !in INKED) continue
        if (bases.any { hueDistance(it.hue, hsl.hue) < DISTINCT_HUE }) continue

        bases += hsl
    }

    // Spread first, then whatever is left: a theme that has taken the room around
    // every fallback still gets six columns.
    for (hue in FALLBACK_HUES) {
        if (bases.size == PALETTE_HUES) break
        if (bases.any { hueDistance(it.hue, hue) < DISTINCT_HUE }) continue

        bases += Hsl(hue, FALLBACK_SATURATION, FALLBACK_LIGHTNESS)
    }

    for (hue in FALLBACK_HUES) {
        if (bases.size == PALETTE_HUES) break
        if (bases.any { it.hue == hue }) continue

        bases += Hsl(hue, FALLBACK_SATURATION, FALLBACK_LIGHTNESS)
    }

    val columns: List<List<Long>> = bases.map { base ->
        val exact: Int = TINT_LIGHTNESSES.indices.minBy { abs(TINT_LIGHTNESSES[it] - base.lightness) }
        TINT_LIGHTNESSES.mapIndexed { index, lightness ->
            if (index == exact) base.toArgb()
            else base.copy(lightness = lightness, argb = null).toArgb()
        }
    }

    return ColourPalette(
        grid = List(PALETTE_TINTS) { row -> columns.map { column -> column[row] } },
        greys = GREYS,
    )
}

private val GREYS: List<Long> = listOf(0xFFFFFFFF, 0xFFBFBFBF, 0xFF808080, 0xFF404040, 0xFF000000)

/** Light to dark, the four rows of the grid. */
private val TINT_LIGHTNESSES: List<Float> = listOf(0.85f, 0.65f, 0.45f, 0.28f)

/** Keynote's own order: blue, green, yellow, orange, red, purple, then two spares. */
private val FALLBACK_HUES: List<Float> = listOf(210f, 140f, 50f, 25f, 0f, 285f, 180f, 330f)
private const val FALLBACK_SATURATION: Float = 0.7f
private const val FALLBACK_LIGHTNESS: Float = 0.5f

/** Below this a colour reads as grey, and its hue is noise. */
private const val CHROMATIC: Float = 0.15f
private val INKED: ClosedFloatingPointRange<Float> = 0.08f..0.95f

/** How far apart two hues are, in degrees, before they earn columns of their own. */
private const val DISTINCT_HUE: Float = 30f

/**
 * A colour as hue (degrees), saturation and lightness, the space tints are taken
 * in: holding hue and saturation and walking lightness is what keeps a column
 * one colour. [argb] is the colour it came from, opaque, so a base converts back
 * to exactly what the theme said.
 */
private data class Hsl(
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
    val argb: Long? = null,
)

private fun hueDistance(a: Float, b: Float): Float {
    val distance: Float = abs(a - b) % 360f
    return if (distance > 180f) 360f - distance else distance
}

private fun Long.toHsl(): Hsl {
    val red: Float = ((this shr 16) and 0xFF) / 255f
    val green: Float = ((this shr 8) and 0xFF) / 255f
    val blue: Float = (this and 0xFF) / 255f
    val max: Float = maxOf(red, green, blue)
    val min: Float = minOf(red, green, blue)
    val lightness: Float = (max + min) / 2f
    val delta: Float = max - min
    val opaque: Long = (this and 0xFFFFFF) or 0xFF000000

    if (delta == 0f) return Hsl(hue = 0f, saturation = 0f, lightness = lightness, argb = opaque)

    val saturation: Float = delta / (1f - abs(2f * lightness - 1f))
    val sector: Float = when (max) {
        red -> ((green - blue) / delta).mod(6f)
        green -> (blue - red) / delta + 2f
        else -> (red - green) / delta + 4f
    }

    return Hsl(hue = sector * 60f, saturation = saturation, lightness = lightness, argb = opaque)
}

private fun Hsl.toArgb(): Long {
    if (argb != null) return argb

    val chroma: Float = (1f - abs(2f * lightness - 1f)) * saturation
    val sector: Float = hue / 60f
    val second: Float = chroma * (1f - abs(sector.mod(2f) - 1f))
    val (red, green, blue) = when (sector.toInt()) {
        0 -> Triple(chroma, second, 0f)
        1 -> Triple(second, chroma, 0f)
        2 -> Triple(0f, chroma, second)
        3 -> Triple(0f, second, chroma)
        4 -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
    }
    val offset: Float = lightness - chroma / 2f

    fun channel(value: Float): Long = ((value + offset) * 255f).roundToLong().coerceIn(0, 255)

    return 0xFF000000 or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
}

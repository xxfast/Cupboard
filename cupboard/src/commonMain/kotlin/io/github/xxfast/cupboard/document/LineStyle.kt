package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable

/**
 * A saved line look, Keynote's Line Styles: stroke colour, width and style, and
 * the heads at either end, under a name. [ObjectStyle]'s twin for a [LineElement].
 *
 * The ends ride along because Keynote's "+" saves them with the rest (a saved
 * swatch shows the double arrow). The theme's six carry none, so applying one of
 * them clears both ends back to [LineEnd.None], which is what Keynote's do.
 */
@Serializable
data class LineStyle(
    val id: String = newId(),
    val name: String,
    val strokeColor: Long,
    val strokeWidth: Float,
    val strokeStyle: StrokeStyle = StrokeStyle.Solid,
    val startEnd: LineEnd = LineEnd.None,
    val endEnd: LineEnd = LineEnd.None,
)

/** This line wearing [style]: its stroke and its ends, never where it runs or how it bends. */
fun LineElement.applyingLineStyle(style: LineStyle): LineElement = copy(
    strokeColor = style.strokeColor,
    strokeWidth = style.strokeWidth,
    strokeStyle = style.strokeStyle,
    startEnd = style.startEnd,
    endEnd = style.endEnd,
)

/**
 * This line's look lifted out as a style called [name], ends included. A fresh
 * id every time, for [asObjectStyle]'s reason.
 */
fun LineElement.asLineStyle(name: String): LineStyle = LineStyle(
    name = name,
    strokeColor = strokeColor,
    strokeWidth = strokeWidth,
    strokeStyle = strokeStyle,
    startEnd = startEnd,
    endEnd = endEnd,
)

/** The weight of the theme's plain lines. */
private const val ThinLine: Float = 2f

/** The weight of its dashed ones, heavy enough for the dashes to read. */
private const val HeavyLine: Float = 6f

/** The weight of its dotted one. */
private const val DottedLine: Float = 3f

/**
 * The six line styles every deck starts with, dressed in [defaults]: Keynote's
 * White theme set, in the deck's colours. Its two chalk brushes are dashes here,
 * since Cupboard draws no brushes, and its light grey is the stroke at half
 * strength. Fixed ids, for [defaultObjectStyles]' reason.
 */
fun defaultLineStyles(defaults: ElementDefaults): List<LineStyle> = listOf(
    LineStyle(
        id = "line-solid",
        name = "Solid",
        strokeColor = defaults.shapeStroke,
        strokeWidth = ThinLine,
    ),
    LineStyle(
        id = "line-accent",
        name = "Accent",
        strokeColor = defaults.accent,
        strokeWidth = ThinLine,
    ),
    LineStyle(
        id = "line-long-dash",
        name = "Long Dash",
        strokeColor = defaults.accent,
        strokeWidth = HeavyLine,
        strokeStyle = StrokeStyle.LongDash,
    ),
    LineStyle(
        id = "line-short-dash",
        name = "Short Dash",
        strokeColor = defaults.shapeStroke,
        strokeWidth = HeavyLine,
        strokeStyle = StrokeStyle.ShortDash,
    ),
    LineStyle(
        id = "line-muted",
        name = "Muted",
        strokeColor = defaults.shapeStroke.halved(),
        strokeWidth = ThinLine,
    ),
    LineStyle(
        id = "line-dots",
        name = "Dots",
        strokeColor = defaults.shapeStroke,
        strokeWidth = DottedLine,
        strokeStyle = StrokeStyle.RoundDots,
    ),
)

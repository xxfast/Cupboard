package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable

/**
 * A saved shape appearance: fill, border and shadow under a name, the deck's own
 * little library of looks.
 *
 * Shapes only, deliberately. A style here is exactly the fields a shape draws
 * itself with, so there is nothing to guess about what applying one to a text box
 * or a code block would mean; `Element.applyingStyle` is the other, per-element
 * answer to that question and it stays where it is.
 *
 * Not the kind, not the label, not the frame: a style is how a shape looks, never
 * what it is, what it says or where it sits. [ShapeElement.applyingObjectStyle] is
 * the whole of what applying one does.
 *
 * [cornerRadius] rides along even though only [ShapeKind.Rectangle] honours it,
 * for the same reason [ShapeElement] carries it: a style that rounded a rectangle
 * and then forgot when the shape became an ellipse would be a style that changes
 * under you.
 */
@Serializable
data class ObjectStyle(
    val id: String = newId(),
    val name: String,
    val fill: Long,
    /** Painted instead of [fill] when set, per [ShapeElement.gradient]. */
    val gradient: ShapeGradient? = null,
    val strokeColor: Long,
    val strokeWidth: Float,
    val shadow: ShapeShadow? = null,
    val cornerRadius: Float,
)

/** This shape wearing [style]: its appearance, and nothing else about it. */
fun ShapeElement.applyingObjectStyle(style: ObjectStyle): ShapeElement = copy(
    fill = style.fill,
    gradient = style.gradient,
    strokeColor = style.strokeColor,
    strokeWidth = style.strokeWidth,
    shadow = style.shadow,
    cornerRadius = style.cornerRadius,
)

/**
 * This shape's look lifted out as a style called [name]: Save Style.
 *
 * A fresh id every time, so saving the same shape twice is two entries rather
 * than one that quietly replaced the other. The six [defaultObjectStyles] keep
 * fixed ids instead, which is what lets a theme change regenerate them in place.
 */
fun ShapeElement.asObjectStyle(name: String): ObjectStyle = ObjectStyle(
    name = name,
    fill = fill,
    gradient = gradient,
    strokeColor = strokeColor,
    strokeWidth = strokeWidth,
    shadow = shadow,
    cornerRadius = cornerRadius,
)

/** The stroke a shape starts on, and what "Filled" is drawn with. See `ShapeCatalog`. */
private const val StyleStrokeWidth: Float = 1.5f

/** An outline carries the whole shape, so it is drawn heavier than one under a fill. */
private const val OutlinedStrokeWidth: Float = 2f

/** The radius every default style rounds to: the app's rounded rectangle. */
private const val StyleCornerRadius: Float = 10f

/** Fully transparent: no fill at all, or no stroke at all. */
private const val Transparent: Long = 0x00000000

/** The warning amber, at fill and at stroke strength. */
private const val WarningFill: Long = 0x24F5C518
private const val WarningStroke: Long = 0x99F5C518

/** This colour at half the alpha it carries, the rest of it untouched. */
private fun Long.halved(): Long {
    val alpha: Long = (this ushr 24) and 0xFF
    return ((alpha / 2) shl 24) or (this and 0xFFFFFF)
}

/**
 * The six styles every deck starts with, dressed in [defaults].
 *
 * Derived from the theme rather than hardcoded, so a deck on Terminal gets six
 * green styles and one on Nord gets six blue ones: a style library that ignored
 * the theme would be six ways to break the deck's look.
 *
 * The ids are fixed, unlike a saved style's: these are six slots rather than six
 * entries, so a theme change regenerates them where they sit instead of piling up
 * another six alongside.
 */
fun defaultObjectStyles(defaults: ElementDefaults): List<ObjectStyle> = listOf(
    ObjectStyle(
        id = "filled",
        name = "Filled",
        fill = defaults.shapeFill,
        strokeColor = defaults.shapeStroke,
        strokeWidth = StyleStrokeWidth,
        cornerRadius = StyleCornerRadius,
    ),
    ObjectStyle(
        id = "outlined",
        name = "Outlined",
        fill = Transparent,
        strokeColor = defaults.accent,
        strokeWidth = OutlinedStrokeWidth,
        cornerRadius = StyleCornerRadius,
    ),
    ObjectStyle(
        id = "subtle",
        name = "Subtle",
        fill = defaults.shapeFill.halved(),
        strokeColor = Transparent,
        strokeWidth = StyleStrokeWidth,
        cornerRadius = StyleCornerRadius,
    ),
    ObjectStyle(
        id = "accent",
        name = "Accent",
        fill = defaults.accent,
        strokeColor = defaults.accent,
        strokeWidth = StyleStrokeWidth,
        cornerRadius = StyleCornerRadius,
    ),
    ObjectStyle(
        id = "warning",
        name = "Warning",
        fill = WarningFill,
        strokeColor = WarningStroke,
        strokeWidth = StyleStrokeWidth,
        cornerRadius = StyleCornerRadius,
    ),
    ObjectStyle(
        id = "shadowed",
        name = "Shadowed",
        fill = defaults.shapeFill,
        strokeColor = defaults.shapeStroke,
        strokeWidth = StyleStrokeWidth,
        shadow = ShapeShadow(),
        cornerRadius = StyleCornerRadius,
    ),
)

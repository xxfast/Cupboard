package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable

/**
 * What a fresh element is dressed in, and so the whole of what a theme decides
 * about content it hasn't laid out itself.
 *
 * Colors are packed ARGB like everywhere else. The values here are the ones the
 * element types were hardcoded to before themes existed, so a document that
 * carries no defaults at all loads looking exactly as it did.
 *
 * Deliberately short: a dozen facts, shared across every element kind, rather
 * than a palette per kind. A diagram's nodes are shapes and its edges are body
 * text, an equation is text, a code block is its syntax palette. Anything a kind
 * needs beyond that is the element's own business, not the theme's.
 */
@Serializable
data class ElementDefaults(
    /** Headings, text boxes and equations: the ink content is written in. */
    val textColor: Long = 0xFFFFFFFF,
    /** Body copy, and the dimmer ink beside a heading: bullets, subtitles, edges. */
    val bodyColor: Long = 0xFFB8B3D6,
    val textFont: TextFont = TextFont.Sans,
    /**
     * How a fresh text box is set: the four facts Use As Default carries across
     * from one, on top of [textColor] and [textFont].
     *
     * [textSize] is the size the text box factory was hardcoded to before these
     * existed, not [TextElement]'s own default: a fresh box on a slide is a
     * heading-sized thing, and what these preserve is what inserting one did.
     */
    val textSize: Float = 32f,
    /** Bold is a point on this scale rather than a flag; see [TextElement.fontWeight]. */
    val textWeight: Int = 400,
    val textAlign: TextAlign = TextAlign.Start,
    val textLineHeight: Float = 1.3f,
    val shapeFill: Long = 0x387F52FF,
    val shapeStroke: Long = 0xB3A98FFF,
    /** The ink on a shape's label, and on a diagram's nodes. */
    val shapeLabelColor: Long = 0xFFD9CFFF,
    val codeTheme: CodeTheme = CodeTheme.Atom,
    /**
     * The theme's signature colour. Nothing on a slide is drawn in it yet: it is
     * what a shell tints its own chrome with when it wants to show which theme
     * the deck is on, which is why it travels with the deck rather than with the
     * app.
     */
    val accent: Long = 0xFF7F52FF,
)

/**
 * These defaults with [text]'s look written into them: Use As Default, for a text
 * box. Everything the next fresh box takes off the deck comes from this one.
 *
 * Appearance only, the same line [ObjectStyle] draws: not the text, not the link,
 * not the frame. The per-run switches ([TextElement.italic],
 * [TextElement.underline], [TextElement.listStyle]) stay out too, deliberately.
 * They read as decisions about one box rather than as a deck-wide default, and a
 * deck where every new text box arrived underlined would be a deck to fight.
 */
fun ElementDefaults.fromText(text: TextElement): ElementDefaults = copy(
    textColor = text.color,
    textFont = text.fontFamily,
    textSize = text.fontSize,
    textWeight = text.fontWeight,
    textAlign = text.align,
    textLineHeight = text.lineHeight,
)

/**
 * These defaults with [shape]'s colours written into them: Use As Default, for a
 * shape. [fromText]'s twin, and the three facts a fresh shape is dressed in.
 *
 * The border, the shadow and the corner radius are no part of it: those are what
 * an [ObjectStyle] is for, and a shape's whole look saved twice under two features
 * is a look that goes out of sync with itself.
 */
fun ElementDefaults.fromShape(shape: ShapeElement): ElementDefaults = copy(
    shapeFill = shape.fill,
    shapeStroke = shape.strokeColor,
    shapeLabelColor = shape.labelColor,
)

/**
 * A look a deck can be put on: what sits behind every slide, what fresh elements
 * are dressed in, and the layouts they are laid out by.
 *
 * A theme is a bundle rather than a palette because a look is not only colour: a
 * layout decides where a title goes and how big it is, and swapping a theme that
 * left the layouts alone would only ever recolour. [Document.applyingTheme] is
 * what putting a deck on one means, [Document.asTheme] the way back out.
 *
 * [layouts] are ordinary [Slide]s, the same as [Document.layouts]: applying a
 * theme copies them in under fresh ids, so a deck owns its layouts and can edit
 * them without touching the theme they came from.
 */
@Serializable
data class Theme(
    val name: String,
    /** Behind every slide that asks for none of its own; null is the app's dark gradient. */
    val background: SlideBackground?,
    val defaults: ElementDefaults,
    val layouts: List<Slide>,
    /**
     * The shape looks a deck on this theme gets. Empty is the built-ins' answer
     * and the useful default: [Document.applyingTheme] then regenerates the six
     * [defaultObjectStyles] from [defaults], so a theme that says nothing about
     * styles still hands the deck six in its own colours.
     *
     * A theme saved out of a deck carries whatever that deck had, which is what
     * makes a hand-built style library travel with a saved look.
     */
    val objectStyles: List<ObjectStyle> = emptyList(),
)

/**
 * The five themes every deck can reach, dark-first: a slide stays dark in both
 * app themes, so there is no light one here.
 *
 * "Cupboard" is the app's own look, and so is exactly what a document that has
 * never been themed already shows: [ElementDefaults]' own values, no background
 * of its own, and [defaultLayouts]. The other four are recolourings of the same
 * four layouts, which is why each is a [defaultLayouts] over its own defaults
 * rather than a hand-built list.
 */
object BuiltInThemes {
    val Cupboard: Theme = theme(
        name = "Cupboard",
        background = null,
        defaults = ElementDefaults(),
    )

    val Graphite: Theme = theme(
        name = "Graphite",
        background = SlideBackground.Color(0xFF17181C),
        defaults = ElementDefaults(
            textColor = 0xFFF2F2F2,
            bodyColor = 0xFFB0B4BC,
            shapeFill = 0x33FFFFFF,
            shapeStroke = 0x80FFFFFF,
            shapeLabelColor = 0xFFF2F2F2,
            codeTheme = CodeTheme.Darcula,
            accent = 0xFFB9B9C6,
        ),
    )

    val Nord: Theme = theme(
        name = "Nord",
        background = SlideBackground.Gradient(start = 0xFF2E3440, end = 0xFF3B4252),
        defaults = ElementDefaults(
            textColor = 0xFFECEFF4,
            bodyColor = 0xFFD8DEE9,
            shapeFill = 0x4D88C0D0,
            shapeStroke = 0xB388C0D0,
            shapeLabelColor = 0xFFECEFF4,
            codeTheme = CodeTheme.Pastel,
            accent = 0xFF88C0D0,
        ),
    )

    val Solarized: Theme = theme(
        name = "Solarized",
        background = SlideBackground.Color(0xFF002B36),
        defaults = ElementDefaults(
            textColor = 0xFF93A1A1,
            bodyColor = 0xFF839496,
            shapeFill = 0x33268BD2,
            shapeStroke = 0xB3268BD2,
            shapeLabelColor = 0xFFEEE8D5,
            codeTheme = CodeTheme.Monokai,
            accent = 0xFFB58900,
        ),
    )

    val Terminal: Theme = theme(
        name = "Terminal",
        background = SlideBackground.Color(0xFF000000),
        defaults = ElementDefaults(
            textColor = 0xFF33FF66,
            bodyColor = 0xFF22CC55,
            textFont = TextFont.Monospace,
            shapeFill = 0x2233FF66,
            shapeStroke = 0xB333FF66,
            shapeLabelColor = 0xFF33FF66,
            codeTheme = CodeTheme.Matrix,
            accent = 0xFF33FF66,
        ),
    )

    val all: List<Theme> = listOf(Cupboard, Graphite, Nord, Solarized, Terminal)
}

/** A built-in: its own defaults, and the four default layouts dressed in them. */
private fun theme(name: String, background: SlideBackground?, defaults: ElementDefaults): Theme =
    Theme(name = name, background = background, defaults = defaults, layouts = defaultLayouts(defaults))

/**
 * This deck put on [theme]: its background, its defaults, and copies of its
 * layouts under fresh ids, so editing them afterwards is editing the deck's own.
 *
 * Slides are remapped by layout *title* rather than by id, because the incoming
 * layouts are a different set of objects with the same names: a slide on "Code"
 * lands on the new theme's "Code". One the new theme has no name for keeps every
 * element it had and comes off layouts altogether, the rule
 * [Document.removeLayout] already sets: a theme change must never cost content.
 *
 * The matched slides are then reapplied, so placeholder instances take the new
 * frames and the new ink while keeping their own text, their own ids and their
 * own builds. That is the whole of what changing a theme does to a slide.
 *
 * The style library comes from the theme, or is regenerated from its defaults when
 * it brought none: a style is made of the deck's colours, so six styles in the old
 * theme's palette would be six ways to undo the change one shape at a time.
 */
fun Document.applyingTheme(theme: Theme): Document {
    val fresh: List<Slide> = theme.layouts.map { it.duplicated() }
    val byTitle: Map<String, Slide> = fresh.associateBy { it.title }
    val titles: Map<String, String> = layouts.associate { it.id to it.title }

    return copy(
        themeName = theme.name,
        background = theme.background,
        defaults = theme.defaults,
        objectStyles = theme.objectStyles.ifEmpty { defaultObjectStyles(theme.defaults) },
        layouts = fresh,
        slides = slides.map { slide ->
            slide.applyingLayout(slide.layoutId?.let { titles[it] }?.let { byTitle[it] })
        },
    )
}

/**
 * This deck's look lifted out as a theme called [name]: Save Theme.
 *
 * The layouts go across as they are, ids included; [applyingTheme] is what makes
 * fresh ones, so the copy happens on the way in rather than on the way out.
 */
fun Document.asTheme(name: String): Theme = Theme(
    name = name,
    background = background,
    defaults = defaults,
    layouts = layouts,
    objectStyles = objectStyles,
)

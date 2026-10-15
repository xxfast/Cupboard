package io.github.xxfast.cupboard.document

/**
 * The named character styles Keynote's Text inspector offers, each a single
 * switch over the selected words. Not stored anywhere: picking one is the same
 * change as pressing its button, and what the popup shows is read back off the
 * text ([TextRangeStyle.characterStyle]).
 */
enum class CharacterStyle { None, Emphasis, Strong, Underline, Strikethrough }

/**
 * Which [CharacterStyle] text set in this reads as: [CharacterStyle.None] with
 * none of the four switched on, the one that is when only one is, and null when
 * more than one is or some of the text disagrees, which a popup reads as
 * Multiple.
 */
val TextRangeStyle.characterStyle: CharacterStyle?
    get() {
        val switches: List<Pair<CharacterStyle, Boolean?>> = listOf(
            CharacterStyle.Strong to isBold,
            CharacterStyle.Emphasis to italic,
            CharacterStyle.Underline to underline,
            CharacterStyle.Strikethrough to strikethrough,
        )
        if (switches.any { (_, on) -> on == null }) return null

        val on: List<CharacterStyle> = switches.filter { (_, on) -> on == true }.map { (style, _) -> style }
        return when (on.size) {
            0 -> CharacterStyle.None
            1 -> on.single()
            else -> null
        }
    }

/**
 * The change [style] makes to text set in this, for `EditorViewModel.onStyleText`:
 * its one switch on, or for [CharacterStyle.None] all four off. A weight only
 * goes back to regular where the text reads as bold, so a light box stays light.
 */
fun TextRangeStyle.withCharacterStyle(style: CharacterStyle): TextRun = when (style) {
    CharacterStyle.None -> TextRun(
        fontWeight = if (isBold == false) null else RegularWeight,
        italic = false,
        underline = false,
        strikethrough = false,
    )

    CharacterStyle.Emphasis -> TextRun(italic = true)
    CharacterStyle.Strong -> TextRun(fontWeight = BoldWeight)
    CharacterStyle.Underline -> TextRun(underline = true)
    CharacterStyle.Strikethrough -> TextRun(strikethrough = true)
}

/**
 * A named look a whole text box can be put in: Keynote's paragraph styles,
 * which here are the looks of the layout's own text placeholders, so a box set
 * in Title looks like the slide's title does.
 *
 * [look] is the placeholder itself; only its text style is read (see
 * [withParagraphStyle]), never its words or its frame.
 */
data class ParagraphStyle(val role: PlaceholderRole, val look: TextElement) {
    /** What the popup calls it, which is the role's own name. */
    val name: String get() = role.name
}

/**
 * The paragraph styles a text box on [slide] can be put in, Title then Body: its
 * layout's first text placeholder of each role, or the slide's own instances
 * for a slide with no layout (a layout being edited is its own). Body is always
 * there, as the deck's fresh text box where nothing on the layout is one.
 */
fun Document.paragraphStyles(slide: Slide): List<ParagraphStyle> {
    val layout: Slide = layoutOf(slide) ?: slide
    val placeholders: List<TextElement> = layout.elements.filterIsInstance<TextElement>()
    fun placeholder(role: PlaceholderRole): TextElement? = placeholders.firstOrNull { it.role == role }

    val title: TextElement? = placeholder(PlaceholderRole.Title)
    val body: TextElement = placeholder(PlaceholderRole.Body) ?: textBoxElement(Frame(0f, 0f, 0f, 0f), defaults)
    return listOfNotNull(
        title?.let { ParagraphStyle(PlaceholderRole.Title, it) },
        ParagraphStyle(PlaceholderRole.Body, body),
    )
}

/**
 * This box set in [style], whole: the face, size, weight, the four switches,
 * colour, character spacing, casing and baseline, alignment, spacing and list
 * all taken off its look, and every run let go, so no word of it differs any
 * more. The words, the frame, the links and the role stay this box's own.
 */
fun TextElement.withParagraphStyle(style: ParagraphStyle): TextElement {
    val look: TextElement = style.look
    return copy(
        fontFamily = look.fontFamily,
        fontName = look.fontName,
        fontSize = look.fontSize,
        fontWeight = look.fontWeight,
        italic = look.italic,
        underline = look.underline,
        strikethrough = look.strikethrough,
        color = look.color,
        letterSpacing = look.letterSpacing,
        capitalization = look.capitalization,
        baseline = look.baseline,
        ligatures = look.ligatures,
        align = look.align,
        lineSpacingMode = look.lineSpacingMode,
        lineHeight = look.lineHeight,
        paragraphSpacingBefore = look.paragraphSpacingBefore,
        paragraphSpacingAfter = look.paragraphSpacingAfter,
        listStyle = look.listStyle,
        runs = emptyList(),
    )
}

/**
 * Which of [styles] this box is in: its own role, when that is one of them,
 * else the first it would not change at all if put in, else Body. Body is what
 * Keynote calls a free text box too, so a box with no role and a look of its
 * own reads as Body rather than as nothing.
 */
fun TextElement.paragraphStyleIn(styles: List<ParagraphStyle>): PlaceholderRole =
    role?.takeIf { own -> styles.any { it.role == own } }
        ?: styles.firstOrNull { withParagraphStyle(it) == this }?.role
        ?: PlaceholderRole.Body

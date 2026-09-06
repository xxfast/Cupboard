package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Saved shape looks: what applying one carries and what it leaves behind, what
 * the six a deck starts with are, and where they come from when a theme changes.
 */
class ObjectStyleTest {
    private val frame = Frame(10f, 20f, 100f, 50f)

    private fun shape(): ShapeElement = ShapeElement(
        id = "shape",
        frame = frame,
        kind = ShapeKind.Star,
        cornerRadius = 4f,
        fill = 0xFF112233,
        gradient = ShapeGradient(start = 0xFF000000, end = 0xFFFFFFFF),
        strokeColor = 0xFF445566,
        strokeWidth = 3f,
        shadow = ShapeShadow(blur = 20f),
        label = "Kept",
    )

    @Test
    fun applyingAStyleDressesTheShapeAndLeavesWhatItIsAndWhereItSits() {
        val style: ObjectStyle = defaultObjectStyles(ElementDefaults())
            .first { it.name == "Shadowed" }
        val styled: ShapeElement = shape().applyingObjectStyle(style)

        assertEquals(style.fill, styled.fill)
        assertEquals(style.strokeColor, styled.strokeColor)
        assertEquals(style.strokeWidth, styled.strokeWidth)
        assertEquals(style.shadow, styled.shadow)
        assertEquals(style.cornerRadius, styled.cornerRadius)
        // A style with no gradient paints a solid, rather than leaving the old
        // gradient over the new fill.
        assertNull(styled.gradient)

        // What it is, what it says and where it sits are none of a style's business.
        assertEquals("shape", styled.id)
        assertEquals(ShapeKind.Star, styled.kind)
        assertEquals("Kept", styled.label)
        assertEquals(frame, styled.frame)
    }

    @Test
    fun aShapeSavedAsAStyleAndDressedInItAgainIsTheShapeItWas() {
        val shape: ShapeElement = shape()
        val saved: ObjectStyle = shape.asObjectStyle("Mine")

        assertEquals("Mine", saved.name)
        assertEquals(shape, shape.applyingObjectStyle(saved))

        // And the same look on another shape, which keeps its own kind and box.
        val plain = ShapeElement(id = "other", frame = Frame(0f, 0f, 10f, 10f))
        assertEquals(
            shape.copy(id = "other", frame = plain.frame, kind = plain.kind, label = ""),
            plain.applyingObjectStyle(saved),
        )
    }

    @Test
    fun theDefaultsAreSixStylesDressedInTheThemesColours() {
        val defaults = ElementDefaults()
        val styles: List<ObjectStyle> = defaultObjectStyles(defaults)

        assertEquals(6, styles.size)
        assertEquals(6, styles.map { it.name }.toSet().size)
        assertEquals(6, styles.map { it.id }.toSet().size)

        val filled: ObjectStyle = styles.first { it.name == "Filled" }
        assertEquals(defaults.shapeFill, filled.fill)
        assertEquals(defaults.shapeStroke, filled.strokeColor)
        assertEquals(1.5f, filled.strokeWidth)
        assertEquals(10f, filled.cornerRadius)
        assertNull(filled.shadow)

        // Shadowed is Filled with a shadow under it, and nothing else.
        val shadowed: ObjectStyle = styles.first { it.name == "Shadowed" }
        assertEquals(
            filled.copy(id = shadowed.id, name = "Shadowed", shadow = ShapeShadow()),
            shadowed,
        )

        assertEquals(0x00000000, styles.first { it.name == "Outlined" }.fill)
        assertEquals(defaults.accent, styles.first { it.name == "Outlined" }.strokeColor)
        assertEquals(defaults.accent, styles.first { it.name == "Accent" }.fill)
        assertEquals(0x00000000, styles.first { it.name == "Subtle" }.strokeColor)
        // Half the fill's alpha, the colour itself untouched.
        assertEquals(0x1C7F52FF, styles.first { it.name == "Subtle" }.fill)
    }

    /** A style is made of the deck's colours, so another theme's six are its own. */
    @Test
    fun anotherThemesSixAreTheSameSlotsInItsOwnColours() {
        val terminal: List<ObjectStyle> = defaultObjectStyles(BuiltInThemes.Terminal.defaults)
        val cupboard: List<ObjectStyle> = defaultObjectStyles(ElementDefaults())

        assertEquals(cupboard.map { it.id }, terminal.map { it.id })
        assertEquals(cupboard.map { it.name }, terminal.map { it.name })
        assertEquals(BuiltInThemes.Terminal.defaults.accent, terminal.first { it.name == "Accent" }.fill)
        assertEquals(BuiltInThemes.Terminal.defaults.shapeFill, terminal.first { it.name == "Filled" }.fill)
    }

    @Test
    fun aThemeChangeRegeneratesTheStylesFromTheThemesDefaults() {
        val themed: Document = Document(id = "doc").applyingTheme(BuiltInThemes.Nord)

        assertEquals(defaultObjectStyles(BuiltInThemes.Nord.defaults), themed.objectStyles)

        // A theme carrying a library of its own installs that instead, and a deck
        // saved as a theme carries whatever it had.
        val mine: Theme = themed.copy(objectStyles = themed.objectStyles.take(2)).asTheme("Mine")
        assertEquals(2, mine.objectStyles.size)
        assertEquals(mine.objectStyles, Document().applyingTheme(mine).objectStyles)
    }

    /** Documents written before styles existed carry none, and open with the six. */
    @Test
    fun aDocumentWithoutStylesStillLoads() {
        val decoded: Document = decodeDocument(
            """{"id":"old","name":"Old","slides":[{"id":"s","title":"S"}]}""",
        )

        assertEquals(defaultObjectStyles(ElementDefaults()), decoded.objectStyles)
    }

    /** What Use As Default writes: the look, never the content or the box. */
    @Test
    fun theDefaultsTakeAnElementsLookAndNothingElse() {
        val text = TextElement(
            frame = frame,
            text = "Kept",
            fontSize = 44f,
            fontWeight = 700,
            lineHeight = 1.8f,
            color = 0xFF00FF00,
            align = TextAlign.Center,
            fontFamily = TextFont.Monospace,
        )
        val fromText: ElementDefaults = ElementDefaults().fromText(text)

        assertEquals(44f, fromText.textSize)
        assertEquals(700, fromText.textWeight)
        assertEquals(1.8f, fromText.textLineHeight)
        assertEquals(0xFF00FF00, fromText.textColor)
        assertEquals(TextAlign.Center, fromText.textAlign)
        assertEquals(TextFont.Monospace, fromText.textFont)
        // Shapes are none of a text box's business.
        assertEquals(ElementDefaults().shapeFill, fromText.shapeFill)

        val fromShape: ElementDefaults = ElementDefaults().fromShape(shape().copy(labelColor = 0xFF708090))
        assertEquals(0xFF112233, fromShape.shapeFill)
        assertEquals(0xFF445566, fromShape.shapeStroke)
        assertEquals(0xFF708090, fromShape.shapeLabelColor)
        assertEquals(ElementDefaults().textColor, fromShape.textColor)
    }

    /** The other half of Use As Default: a fresh box is dressed in what was written. */
    @Test
    fun aFreshTextBoxIsSetTheWayTheDeckSaysItIs() {
        val defaults = ElementDefaults(
            textSize = 44f,
            textWeight = 700,
            textAlign = TextAlign.End,
            textLineHeight = 1.8f,
        )
        val box: TextElement = textBoxElement(frame, defaults)

        assertEquals(44f, box.fontSize)
        assertEquals(700, box.fontWeight)
        assertEquals(TextAlign.End, box.align)
        assertEquals(1.8f, box.lineHeight)

        // No defaults asked for is what inserting a text box always did.
        assertEquals(32f, textBoxElement(frame).fontSize)
        assertEquals(TextAlign.Start, textBoxElement(frame).align)
    }
}

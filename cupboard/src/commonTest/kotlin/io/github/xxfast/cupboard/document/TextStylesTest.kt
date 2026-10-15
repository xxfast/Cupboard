package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The Text inspector's model: the list styles past bullets and numbers, casing
 * as it draws, the four line spacing modes, indenting a whole box, and the
 * character and paragraph styles it offers.
 */
class TextStylesTest {
    private fun box(text: String = "Hello world", vararg runs: TextRun): TextElement =
        TextElement(id = "t", frame = Frame(0f, 0f, 100f, 40f), text = text, runs = runs.toList())

    @Test
    fun theNewListStylesKeepOneMarkerAtEveryLevel() {
        val text = "One\n\tTwo\n\tThree\nFour"
        assertEquals(listOf("–", "–", "–", "–"), listMarkers(text, ListStyle.Dash))
        assertEquals(listOf("1)", "1)", "2)", "2)"), listMarkers(text, ListStyle.NumberedParenthesis))
        assertEquals(listOf("I.", "I.", "II.", "II."), listMarkers(text, ListStyle.UpperRoman))
        assertEquals(listOf("a.", "a.", "b.", "b."), listMarkers(text, ListStyle.LowerAlpha))
        assertEquals(listOf("1.", "a.", "b.", "2."), listMarkers(text, ListStyle.Numbered))
    }

    @Test
    fun everyListStyleHasATitleAndAPlaceInThePopup() {
        assertEquals(ListStyle.entries.toSet(), ListStyles.toSet())
        assertEquals(ListStyles.size, ListStyles.map { it.title }.distinct().size)
    }

    @Test
    fun casingKeepsEveryCharacterWhereItWas() {
        assertEquals("HELLO, WORLD", "Hello, world".capitalized(TextCapitalization.AllCaps))
        assertEquals("Don't Stop Me-Now", "don't stop me-now".capitalized(TextCapitalization.Title))
        assertEquals("small caps", "small caps".capitalized(TextCapitalization.SmallCaps))
        assertEquals("Straße".length, "Straße".capitalized(TextCapitalization.AllCaps).length)
    }

    @Test
    fun eachSpacingModeReadsTheLineHeightItsOwnWay() {
        val lines: TextElement = box().copy(fontSize = 20f, lineHeight = 1.5f)
        assertEquals(30f, lines.lineHeightPoints())
        assertEquals(24f, lines.copy(lineSpacingMode = LineSpacingMode.Exactly, lineHeight = 24f).lineHeightPoints())
        assertEquals(20f, lines.copy(lineSpacingMode = LineSpacingMode.AtLeast, lineHeight = 12f).lineHeightPoints())
        assertEquals(28f, lines.copy(lineSpacingMode = LineSpacingMode.AtLeast, lineHeight = 28f).lineHeightPoints())
        assertEquals(26f, lines.copy(lineSpacingMode = LineSpacingMode.Between, lineHeight = 6f).lineHeightPoints())
    }

    @Test
    fun switchingTheSpacingModeMovesNoLine() {
        val lines: TextElement = box().copy(fontSize = 20f, lineHeight = 1.5f)
        val exactly: TextElement = lines.withLineSpacingMode(LineSpacingMode.Exactly)
        assertEquals(LineSpacingMode.Exactly, exactly.lineSpacingMode)
        assertEquals(30f, exactly.lineHeight)

        val between: TextElement = exactly.withLineSpacingMode(LineSpacingMode.Between)
        assertEquals(10f, between.lineHeight)
        assertEquals(30f, between.lineHeightPoints())

        assertEquals(1.5f, between.withLineSpacingMode(LineSpacingMode.Lines).lineHeight)
        assertSame(lines, lines.withLineSpacingMode(LineSpacingMode.Lines))
    }

    @Test
    fun indentingTheWholeBoxKeepsEveryRunOnItsWords() {
        val element: TextElement = box("One\nTwo", TextRun(0, 3, italic = true), TextRun(4, 7, fontWeight = BoldWeight))
        assertFalse(element.canOutdent)

        val indented: TextElement = element.indented()
        assertEquals("\tOne\n\tTwo", indented.text)
        assertTrue(indented.canOutdent)
        assertEquals(listOf("One", "Two"), indented.runs.map { indented.text.substring(it.start, it.end).trim() })
        assertEquals(listOf(true, null), indented.runs.map { it.italic })

        val back: TextElement = indented.outdented()
        assertEquals("One\nTwo", back.text)
        assertEquals(element.runs, back.runs)
        assertSame(back, back.outdented())
    }

    @Test
    fun aCharacterStyleIsReadBackOffTheSwitches() {
        val element: TextElement = box("Hello world", TextRun(0, 5, italic = true))
        assertEquals(CharacterStyle.Emphasis, element.styleOf(0, 5).characterStyle)
        assertEquals(CharacterStyle.None, element.styleOf(6, 11).characterStyle)
        assertNull(element.styleOf(0, 11).characterStyle)

        val both: TextElement = element.styled(0, 5, TextRun(underline = true))
        assertNull(both.styleOf(0, 5).characterStyle)
    }

    @Test
    fun noneSwitchesEverythingOffButLeavesALightBoxLight() {
        val light: TextRangeStyle = box().copy(fontWeight = 300, italic = true).styleOf(0, 11)
        assertEquals(TextRun(italic = false, underline = false, strikethrough = false), light.withCharacterStyle(CharacterStyle.None))

        val bold: TextRangeStyle = box().copy(fontWeight = BoldWeight).styleOf(0, 11)
        assertEquals(RegularWeight, bold.withCharacterStyle(CharacterStyle.None).fontWeight)
        assertEquals(TextRun(fontWeight = BoldWeight), light.withCharacterStyle(CharacterStyle.Strong))
    }

    private val title = TextElement(
        id = "title",
        frame = Frame(0f, 0f, 800f, 80f),
        fontSize = 60f,
        fontWeight = BoldWeight,
        fontFamily = TextFont.Serif,
        align = TextAlign.Center,
        role = PlaceholderRole.Title,
    )
    private val body = TextElement(
        id = "body",
        frame = Frame(0f, 100f, 800f, 300f),
        fontSize = 28f,
        listStyle = ListStyle.Bullet,
        role = PlaceholderRole.Body,
    )

    private fun deck(vararg placeholders: TextElement): Pair<Document, Slide> {
        val layout = Slide(id = "layout", elements = placeholders.toList())
        val slide = Slide(id = "slide", layoutId = "layout")
        return Document(id = "doc", slides = listOf(slide), layouts = listOf(layout)) to slide
    }

    @Test
    fun paragraphStylesAreTheLayoutsPlaceholders() {
        val (document, slide) = deck(body, title)
        val styles: List<ParagraphStyle> = document.paragraphStyles(slide)
        assertEquals(listOf("Title", "Body"), styles.map { it.name })
        assertEquals(60f, styles.first().look.fontSize)
    }

    @Test
    fun aLayoutWithNoBodyStillOffersOneOffTheDecksDefaults() {
        val (document, slide) = deck(title)
        val styles: List<ParagraphStyle> = document.paragraphStyles(slide)
        assertEquals(listOf(PlaceholderRole.Title, PlaceholderRole.Body), styles.map { it.role })
        assertEquals(document.defaults.textSize, styles.last().look.fontSize)
    }

    @Test
    fun aParagraphStyleSetsTheWholeBoxAndKeepsItsWords() {
        val (document, slide) = deck(title, body)
        val styles: List<ParagraphStyle> = document.paragraphStyles(slide)
        val element: TextElement = box("Hello world", TextRun(0, 5, italic = true)).copy(link = "https://home")

        val titled: TextElement = element.withParagraphStyle(styles.first())
        assertEquals("Hello world", titled.text)
        assertEquals(element.frame, titled.frame)
        assertEquals("https://home", titled.link)
        assertEquals(emptyList(), titled.runs)
        assertEquals(60f, titled.fontSize)
        assertEquals(TextAlign.Center, titled.align)
        assertNull(titled.role)
    }

    @Test
    fun aBoxReadsAsItsRoleElseTheStyleItMatchesElseBody() {
        val (document, slide) = deck(title, body)
        val styles: List<ParagraphStyle> = document.paragraphStyles(slide)
        val free: TextElement = box()

        assertEquals(PlaceholderRole.Body, free.paragraphStyleIn(styles))
        assertEquals(PlaceholderRole.Title, free.withParagraphStyle(styles.first()).paragraphStyleIn(styles))
        assertEquals(PlaceholderRole.Title, free.copy(role = PlaceholderRole.Title).paragraphStyleIn(styles))
    }
}

package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Styled runs inside a text box: how they settle into order, how styling a
 * range splits and merges them, how they ride an edit alongside the range links,
 * and what an inspector reads off a stretch of them.
 */
class TextRunsTest {
    private val red: Long = 0xFFFF0000
    private val home: LinkTarget = LinkTarget.Url("https://home")

    private fun box(text: String, vararg runs: TextRun, links: List<TextLink> = emptyList()): TextElement =
        TextElement(id = "t", frame = Frame(0f, 0f, 100f, 40f), text = text, runs = runs.toList(), links = links)

    private fun TextElement.words(run: TextRun): String = text.substring(run.start, run.end)

    @Test
    fun normalisingSortsClampsAndDropsWhatDrawsNothing() {
        val normalised: List<TextRun> = normalisedRuns(
            listOf(
                TextRun(6, 20, italic = true),
                TextRun(0, 3, fontWeight = BoldWeight),
                TextRun(3, 3, color = red),
                TextRun(3, 5),
            ),
            length = 11,
        )
        assertEquals(listOf(TextRun(0, 3, fontWeight = BoldWeight), TextRun(6, 11, italic = true)), normalised)
    }

    @Test
    fun normalisingMergesTouchingRunsInOneStyleAndLetsTheEarlierWinAnOverlap() {
        val normalised: List<TextRun> = normalisedRuns(
            listOf(
                TextRun(0, 5, color = red),
                TextRun(5, 8, color = red),
                TextRun(6, 10, italic = true),
            ),
            length = 11,
        )
        assertEquals(listOf(TextRun(0, 8, color = red), TextRun(8, 10, italic = true)), normalised)
    }

    @Test
    fun stylingARangeSplitsTheRunsItCrosses() {
        val styled: TextElement = box("Hello world", TextRun(0, 5, italic = true))
            .styled(2, 8, TextRun(fontWeight = BoldWeight))
        assertEquals(
            listOf(
                TextRun(0, 2, italic = true),
                TextRun(2, 5, fontWeight = BoldWeight, italic = true),
                TextRun(5, 8, fontWeight = BoldWeight),
            ),
            styled.runs,
        )
    }

    @Test
    fun stylingTheGapBetweenTwoLikeRunsMergesThemIntoOne() {
        val styled: TextElement = box("Hello world", TextRun(0, 4, color = red), TextRun(7, 11, color = red))
            .styled(4, 7, TextRun(color = red))
        assertEquals(listOf(TextRun(0, 11, color = red)), styled.runs)
    }

    @Test
    fun stylingWithWhatIsAlreadyThereChangesNothing() {
        val element: TextElement = box("Hello world", TextRun(0, 5, color = red))
        assertSame(element, element.styled(1, 3, TextRun(color = red)))
        assertSame(element, element.styled(3, 3, TextRun(fontSize = 40f)))
        assertSame(element, element.styled(0, 5, TextRun()))
    }

    @Test
    fun aFacePickedIntoAGenericFamilyDropsTheNameItWasSetIn() {
        val named: TextElement = box("Hello world").styled(0, 5, TextRun(fontName = "Inter", color = red))
        val generic: TextElement = named.styled(0, 5, TextRun(fontFamily = TextFont.Serif))
        assertEquals(listOf(TextRun(0, 5, fontFamily = TextFont.Serif, color = red)), generic.runs)

        // Owning its face, the run draws in the family even inside a box set by name.
        val inNamedBox: TextElement = box("Hello world").withFont("Canela")
            .styled(0, 5, TextRun(fontFamily = TextFont.Serif))
        val drawn: TextRun = inNamedBox.resolved(inNamedBox.runs.single())
        assertEquals(TextFont.Serif, drawn.fontFamily)
        assertNull(drawn.fontName)
        assertEquals("Canela", inNamedBox.resolved(TextRun()).fontName)
    }

    @Test
    fun typingOnAtTheEndOfARunWearsItsStyle() {
        val element: TextElement = box("Hello world", TextRun(0, 5, fontWeight = BoldWeight))
        val typed: TextElement = element.withText("Hello! world")
        assertEquals("Hello!", typed.words(typed.runs.single()))
    }

    @Test
    fun typingInFrontOfARunAtTheStartIsTheBoxs() {
        val element: TextElement = box("Hello world", TextRun(0, 5, fontWeight = BoldWeight))
        val typed: TextElement = element.withText("Oh Hello world")
        assertEquals("Hello", typed.words(typed.runs.single()))
    }

    @Test
    fun typingOverASelectionWearsTheStyleOfItsFirstCharacter() {
        val element: TextElement = box("Hello world", TextRun(6, 11, color = red))
        val typed: TextElement = element.withText("Hello there")
        assertEquals("there", typed.words(typed.runs.single()))
    }

    @Test
    fun deletingAcrossRunsAndLinksKeepsBothOnTheWordsThatAreLeft() {
        val element: TextElement = box(
            "Hello brave world",
            TextRun(0, 11, fontWeight = BoldWeight),
            links = listOf(TextLink(6, 17, home)),
        )
        val edited: TextElement = element.withText("Hello brrld")
        assertEquals("Hello br", edited.words(edited.runs.single()))
        val link: TextLink = edited.links.single()
        assertEquals("brrld", edited.text.substring(link.start, link.end))
    }

    @Test
    fun deletingEveryCharacterOfARunTakesTheRunWithIt() {
        val element: TextElement = box("Hello brave world", TextRun(6, 12, italic = true))
        assertEquals(emptyList(), element.withText("Hello world").runs)
    }

    @Test
    fun runsOverCoversEveryCharacterWithPlainRunsBetween() {
        val element: TextElement = box("Hello world", TextRun(2, 4, italic = true), TextRun(6, 8, color = red))
        assertEquals(
            listOf(
                TextRun(1, 2),
                TextRun(2, 4, italic = true),
                TextRun(4, 6),
                TextRun(6, 8, color = red),
                TextRun(8, 9),
            ),
            element.runsOver(1, 9),
        )
    }

    @Test
    fun aBoxStyleIsTheWholeBoxsAndTheRunsLetGoOfIt() {
        val element: TextElement = box("Hello world", TextRun(0, 5, fontWeight = BoldWeight, color = red))

        val regular: TextElement = element.withBoxStyle(TextRun(fontWeight = RegularWeight))
        assertEquals(RegularWeight, regular.fontWeight)
        assertEquals(listOf(TextRun(0, 5, color = red)), regular.runs)

        val blue: TextElement = regular.withBoxStyle(TextRun(color = 0xFF0000FF))
        assertEquals(0xFF0000FF, blue.color)
        assertEquals(emptyList(), blue.runs)
    }

    @Test
    fun toggleBoldOnABoxWithOneBoldWordBoldsAllOfIt() {
        val element: TextElement = box("Hello world", TextRun(0, 5, fontWeight = BoldWeight))
        val bolded: TextElement = element.toggleBold()
        assertEquals(BoldWeight, bolded.fontWeight)
        assertEquals(emptyList(), bolded.runs)
        assertEquals(RegularWeight, bolded.toggleBold().fontWeight)
    }

    @Test
    fun aStretchInOneStyleReadsAsThatStyleAndAMixedOneAsMultiple() {
        val element: TextElement = box(
            "Hello world",
            TextRun(0, 5, fontSize = 30f, fontWeight = BoldWeight),
        ).copy(fontSize = 15f, color = red)

        val hello: TextRangeStyle = element.styleOf(0, 5)
        assertEquals(30f, hello.fontSize)
        assertEquals(true, hello.isBold)
        assertEquals(red, hello.color)

        val whole: TextRangeStyle = element.styleOf(0, element.text.length)
        assertNull(whole.fontSize)
        assertNull(whole.fontWeight)
        assertNull(whole.isBold)
        assertEquals(red, whole.color)
        assertEquals(TextFont.Sans.displayName, whole.fontDisplayName)
    }

    @Test
    fun twoFacesReadAsMultipleFaces() {
        val element: TextElement = box("Hello world", TextRun(0, 5, fontName = "Inter"))
        val whole: TextRangeStyle = element.styleOf(0, element.text.length)
        assertNull(whole.fontDisplayName)
        assertNull(whole.fontFamily)
        assertEquals("Inter", element.styleOf(0, 5).fontDisplayName)
    }

    @Test
    fun anEmptyBoxReadsAsItsOwnStyle() {
        val style: TextRangeStyle = box("").copy(fontSize = 22f).styleOf(0, 0)
        assertEquals(22f, style.fontSize)
        assertEquals(false, style.isBold)
    }

    @Test
    fun linesAreSpacedForTheLargestSizeInTheBox() {
        assertEquals(15f, box("Hello").largestFontSize)
        assertEquals(40f, box("Hello", TextRun(0, 2, fontSize = 40f)).largestFontSize)
        assertEquals(15f, box("Hello", TextRun(0, 2, fontSize = 9f)).largestFontSize)
    }

    @Test
    fun runsRoundTripThroughTheDocumentFormat() {
        val element: TextElement = box(
            "Hello world",
            TextRun(0, 5, fontFamily = TextFont.Serif, fontName = "Canela", fontSize = 30f, fontWeight = 700),
            TextRun(6, 11, italic = true, underline = true, strikethrough = false, color = red, letterSpacing = 2f),
        )
        val json: String = DocumentJson.encodeToString(Element.serializer(), element)
        assertEquals(element, DocumentJson.decodeFromString(Element.serializer(), json))
    }

    @Test
    fun casingAndBaselineGoOnARangeAndMergeLikeAnyOtherStyle() {
        val styled: TextElement = box("H2O and E=mc2")
            .styled(1, 2, TextRun(baseline = TextBaseline.Subscript))
            .styled(12, 13, TextRun(baseline = TextBaseline.Superscript))
            .styled(4, 7, TextRun(capitalization = TextCapitalization.AllCaps))
            .styled(4, 7, TextRun(capitalization = TextCapitalization.AllCaps))
        assertEquals(
            listOf(
                TextRun(1, 2, baseline = TextBaseline.Subscript),
                TextRun(4, 7, capitalization = TextCapitalization.AllCaps),
                TextRun(12, 13, baseline = TextBaseline.Superscript),
            ),
            styled.runs,
        )

        val merged: TextElement = styled.styled(7, 9, TextRun(capitalization = TextCapitalization.AllCaps))
        assertEquals(TextRun(4, 9, capitalization = TextCapitalization.AllCaps), merged.runs[1])
    }

    @Test
    fun casingAndBaselineReadAsMultipleWhereTheTextDisagrees() {
        val element: TextElement = box(
            "x2 y",
            TextRun(1, 2, baseline = TextBaseline.Superscript, capitalization = TextCapitalization.SmallCaps),
        )
        val whole: TextRangeStyle = element.styleOf(0, element.text.length)
        assertNull(whole.baseline)
        assertNull(whole.capitalization)

        val two: TextRangeStyle = element.styleOf(1, 2)
        assertEquals(TextBaseline.Superscript, two.baseline)
        assertEquals(TextCapitalization.SmallCaps, two.capitalization)
        assertEquals(TextBaseline.Normal, element.styleOf(3, 4).baseline)
    }

    @Test
    fun casingOnTheWholeBoxLetsTheRunsGoOfTheirs() {
        val element: TextElement = box(
            "Hello world",
            TextRun(0, 5, capitalization = TextCapitalization.Title, italic = true),
            TextRun(6, 11, baseline = TextBaseline.Subscript),
        )
        val whole: TextElement = element.withBoxStyle(TextRun(capitalization = TextCapitalization.AllCaps))
        assertEquals(TextCapitalization.AllCaps, whole.capitalization)
        assertEquals(
            listOf(TextRun(0, 5, italic = true), TextRun(6, 11, baseline = TextBaseline.Subscript)),
            whole.runs,
        )
        assertEquals(TextBaseline.Subscript, whole.resolved(whole.runs[1]).baseline)
        assertEquals(TextCapitalization.AllCaps, whole.resolved(whole.runs[1]).capitalization)
    }

    @Test
    fun aRunThatSetsTheBoxsOwnCasingIsStillARun() {
        // Null is what falls through to the box; a value, even the box's, is a
        // decision the run keeps when the box changes later.
        val normalised: List<TextRun> = normalisedRuns(
            listOf(TextRun(0, 2, capitalization = TextCapitalization.None), TextRun(2, 4)),
            length = 4,
        )
        assertEquals(listOf(TextRun(0, 2, capitalization = TextCapitalization.None)), normalised)
    }

    @Test
    fun theTextModelsAdditionsRoundTripThroughTheDocumentFormat() {
        val element: TextElement = box(
            "One\nTwo",
            TextRun(0, 3, capitalization = TextCapitalization.SmallCaps, baseline = TextBaseline.Superscript),
        ).copy(
            align = TextAlign.Justify,
            verticalAlign = VerticalAlign.Bottom,
            capitalization = TextCapitalization.Title,
            baseline = TextBaseline.Subscript,
            ligatures = false,
            lineSpacingMode = LineSpacingMode.Exactly,
            lineHeight = 40f,
            paragraphSpacingBefore = 12f,
            paragraphSpacingAfter = 6f,
            listStyle = ListStyle.UpperRoman,
        )
        val json: String = DocumentJson.encodeToString(Element.serializer(), element)
        assertEquals(element, DocumentJson.decodeFromString(Element.serializer(), json))
    }

    @Test
    fun aBoxWrittenBeforeTheTextInspectorReadsAsItAlwaysDrew() {
        val json = """{"type":"text","id":"t","frame":{"x":0,"y":0,"width":10,"height":10},""" +
            """"text":"Hi","align":"Center","lineHeight":1.5,"listStyle":"Numbered","runs":[{"start":0,"end":1,"italic":true}]}"""
        val element = DocumentJson.decodeFromString(Element.serializer(), json) as TextElement
        assertEquals(TextAlign.Center, element.align)
        assertEquals(VerticalAlign.Top, element.verticalAlign)
        assertEquals(TextCapitalization.None, element.capitalization)
        assertEquals(TextBaseline.Normal, element.baseline)
        assertTrue(element.ligatures)
        assertEquals(LineSpacingMode.Lines, element.lineSpacingMode)
        assertEquals(1.5f, element.lineHeight)
        assertEquals(0f, element.paragraphSpacingBefore)
        assertEquals(0f, element.paragraphSpacingAfter)
        assertEquals(ListStyle.Numbered, element.listStyle)
        assertEquals(listOf(TextRun(0, 1, italic = true)), element.runs)
    }

    @Test
    fun listStylesKeepTheirNamesOnDisk() {
        val names: List<String> = listOf("Bullet", "Numbered", "Dash", "NumberedParenthesis", "UpperRoman", "LowerAlpha")
        for (name in names) {
            val json = """{"type":"text","id":"t","frame":{"x":0,"y":0,"width":10,"height":10},"listStyle":"$name"}"""
            val element = DocumentJson.decodeFromString(Element.serializer(), json) as TextElement
            assertEquals(name, element.listStyle.name)
            assertTrue(DocumentJson.encodeToString(Element.serializer(), element).contains(""""listStyle": "$name""""))
        }
    }

    @Test
    fun aBoxWrittenBeforeRunsReadsWithNone() {
        val json = """{"type":"text","id":"t","frame":{"x":0,"y":0,"width":10,"height":10},"text":"Hi","fontWeight":700}"""
        val element = DocumentJson.decodeFromString(Element.serializer(), json) as TextElement
        assertEquals(emptyList(), element.runs)
        assertTrue(element.isBold)
    }
}

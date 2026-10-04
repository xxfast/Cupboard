package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A face by name beside the [TextFont] it falls back to, and the faces a deck
 * carries: on disk, through every flow a text box's look already travels, and
 * the deck's list itself.
 */
class DeckFontsTest {
    private val box = Frame(0f, 0f, 400f, 100f)
    private val inter = DeckFont(assetId = "inter.ttf", family = "Inter")

    private fun deck(vararg elements: Element, fonts: List<DeckFont> = emptyList()): Document =
        Document(
            id = "doc",
            slides = listOf(Slide(id = "one", elements = elements.toList())),
            fonts = fonts,
        )

    private fun Document.reloaded(): Document =
        assertIs<DocumentLoad.Loaded>(decodeDocument(encodeToString())).document

    @Test
    fun aFontNameAndTheDecksFontsRoundTrip() {
        val text = TextElement(id = "t", frame = box, fontFamily = TextFont.Serif, fontName = "Inter")
        val original: Document = deck(text, fonts = listOf(inter, inter.copy(assetId = "b.otf", weight = 700, italic = true)))

        assertEquals(original, original.reloaded())
    }

    @Test
    fun aDeckWrittenBeforeEitherLoadsWithNeither() {
        val written: String = deck(TextElement(id = "t", frame = box)).encodeToString()
            .replace(Regex(""",\s*"fontName":\s*null"""), "")
            .replace(Regex(""",\s*"fonts":\s*\[\s*]"""), "")
        assertTrue("fontName" !in written && "\"fonts\"" !in written, "the keys really are gone")

        val loaded: Document = assertIs<DocumentLoad.Loaded>(decodeDocument(written)).document
        val text: TextElement = loaded.slides.single().elements.single() as TextElement
        assertNull(text.fontName)
        assertEquals(emptyList(), loaded.fonts)
    }

    @Test
    fun aNamedPickSetsTheNameAndKeepsTheFallback() {
        val text = TextElement(frame = box, fontFamily = TextFont.Serif)

        val named: TextElement = text.withFont("Inter")
        assertEquals("Inter", named.fontName)
        assertEquals(TextFont.Serif, named.fontFamily, "still what it falls back to")
        assertEquals("Inter", named.fontDisplayName)

        assertNull(named.withFont(" ").fontName, "blank is no name")
        assertNull(named.withFont(null).fontName)
    }

    @Test
    fun aGenericPickClearsTheName() {
        val named = TextElement(frame = box, fontFamily = TextFont.Serif, fontName = "Inter")

        val generic: TextElement = named.withGenericFont(TextFont.Monospace)
        assertNull(generic.fontName)
        assertEquals(TextFont.Monospace, generic.fontFamily)
        assertEquals("Monospace", generic.fontDisplayName)
        assertEquals("Helvetica Neue", generic.withGenericFont(TextFont.HelveticaNeue).fontDisplayName)
    }

    @Test
    fun theBatchFormatEditCarriesAPick() {
        val a = TextElement(id = "a", frame = box)
        val b = TextElement(id = "b", frame = box, fontName = "Inter")
        val locked = TextElement(id = "c", frame = box, locked = true)

        val edits: List<Element> = listOf(a, b, locked).formatText { it.withFont("Inter") }

        assertEquals(listOf("a"), edits.map { it.id }, "b already was, and c is locked")
        assertEquals("Inter", (edits.single() as TextElement).fontName)
    }

    @Test
    fun aPastedStyleCarriesTheName() {
        val source = TextElement(frame = box, fontFamily = TextFont.Serif, fontName = "Inter")
        val target = TextElement(frame = box, text = "mine")

        val styled = target.applyingStyle(source) as TextElement
        assertEquals("Inter", styled.fontName)
        assertEquals(TextFont.Serif, styled.fontFamily)
        assertEquals("mine", styled.text)

        val cleared = styled.applyingStyle(TextElement(frame = box)) as TextElement
        assertNull(cleared.fontName, "a source with no name takes the name away")
    }

    @Test
    fun useAsDefaultCarriesTheNameIntoTheNextBoxAndTheLayouts() {
        val source = TextElement(frame = box, fontFamily = TextFont.Serif, fontName = "Inter")
        val defaults: ElementDefaults = ElementDefaults().fromText(source)

        assertEquals("Inter", defaults.textFontName)
        assertEquals("Inter", textBoxElement(box, defaults).fontName)
        assertEquals(TextFont.Serif, textBoxElement(box, defaults).fontFamily)

        val placeholders: List<TextElement> = defaultLayouts(defaults)
            .flatMap { it.elements }
            .filterIsInstance<TextElement>()
        assertTrue(placeholders.isNotEmpty())
        assertTrue(placeholders.all { it.fontName == "Inter" })
        assertEquals("Inter", (placeholderElement(PlaceholderRole.Body, defaults) as TextElement).fontName)
    }

    @Test
    fun builtInThemesStayOnTheEnum() {
        assertTrue(BuiltInThemes.all.all { it.defaults.textFontName == null })
        val named: List<TextElement> = BuiltInThemes.all
            .flatMap { it.layouts + it.presetLayouts.values.flatten() }
            .flatMap { it.elements }
            .filterIsInstance<TextElement>()
            .filter { it.fontName != null }
        assertEquals(emptyList(), named)
    }

    @Test
    fun applyingAndReapplyingALayoutCarriesItsPlaceholdersName() {
        val layout = Slide(
            id = "layout",
            elements = listOf(
                TextElement(id = "title", frame = box, fontName = "Inter", role = PlaceholderRole.Title),
            ),
        )
        val instance = TextElement(id = "mine", frame = Frame(9f, 9f, 9f, 9f), text = "Hello", role = PlaceholderRole.Title)
        val slide = Slide(id = "slide", elements = listOf(instance))

        val applied: TextElement = slide.applyingLayout(layout).elements.single() as TextElement
        assertEquals("Inter", applied.fontName)
        assertEquals("Hello", applied.text)

        val fresh: TextElement = Slide(id = "new").instantiating(layout).elements.single() as TextElement
        assertEquals("Inter", fresh.fontName)
    }

    @Test
    fun resizingAndRethemingKeepTheNameAndTheFonts() {
        val text = TextElement(id = "t", frame = box, fontName = "Inter")
        val original: Document = deck(text, fonts = listOf(inter))

        val resized: Document = original.resized(1024f, 768f, scaleContent = true)
        assertEquals("Inter", (resized.slides.single().elements.single() as TextElement).fontName)
        assertEquals(listOf(inter), resized.fonts)

        val rethemed: Document = original.applyingTheme(BuiltInThemes.BasicWhite)
        assertEquals(listOf(inter), rethemed.fonts)
        assertEquals("Inter", (rethemed.slides.single().elements.single() as TextElement).fontName)
    }

    @Test
    fun addingAFontAppendsAndTheSameCutReplaces() {
        val bold = DeckFont("bold.ttf", "Inter", weight = 700)
        val added: Document = deck().addFont(inter).addFont(bold)
        assertEquals(listOf(inter, bold), added.fonts)

        val newer = DeckFont("newer.ttf", "inter", weight = 700)
        assertEquals(listOf(inter, newer), added.addFont(newer).fonts, "same cut, case aside")
        assertSame(added, added.addFont(bold), "nothing to change")
    }

    @Test
    fun removingAFontLeavesTheTextNamingIt() {
        val text = TextElement(id = "t", frame = box, fontName = "Inter")
        val original: Document = deck(text, fonts = listOf(inter))

        val removed: Document = original.removeFont("inter.ttf")
        assertEquals(emptyList(), removed.fonts)
        assertEquals("Inter", (removed.slides.single().elements.single() as TextElement).fontName)
        assertSame(removed, removed.removeFont("inter.ttf"))
    }

    @Test
    fun theDecksFamiliesAreDistinctAndSorted() {
        val fonts = listOf(
            DeckFont("1", "Zilla"),
            DeckFont("2", "inter", weight = 700),
            DeckFont("3", "Inter"),
            DeckFont("4", "Bodoni"),
        )

        assertEquals(listOf("Bodoni", "inter", "Zilla"), deck(fonts = fonts).fontFamilies)
    }
}

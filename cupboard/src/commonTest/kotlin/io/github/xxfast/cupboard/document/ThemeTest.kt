package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The theme bundle: what the built-ins are, what putting a deck on one does to
 * its slides, and what a fresh element takes off one.
 */
class ThemeTest {
    private val layoutTitles: List<String> = listOf("Title", "Title & Body", "Code", "Blank")

    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                layoutId = "code",
                elements = listOf(
                    TextElement(
                        id = "one-title",
                        frame = Frame(0f, 0f, 10f, 10f),
                        text = "What I wrote",
                        color = 0xFF123456,
                        role = PlaceholderRole.Title,
                    ),
                ),
            ),
            Slide(
                id = "two",
                layoutId = "bespoke",
                elements = listOf(
                    TextElement(id = "two-note", frame = Frame(1f, 2f, 3f, 4f), text = "Kept"),
                ),
            ),
        ),
        layouts = listOf(
            Slide(id = "code", title = "Code"),
            Slide(id = "bespoke", title = "Bespoke"),
        ),
    )

    @Test
    fun theBuiltInsAreDistinctThemes() {
        assertEquals(16, BuiltInThemes.all.size)
        assertEquals(16, BuiltInThemes.all.map { it.name }.toSet().size)
        assertEquals(BuiltInThemes.basic + BuiltInThemes.cupboard, BuiltInThemes.all)
        assertEquals(listOf("Basic", "Cupboard"), BuiltInThemes.categories.map { it.name })
    }

    @Test
    fun cupboardsOwnAreOverTheSameFourLayouts() {
        assertEquals(11, BuiltInThemes.cupboard.size)
        for (theme in BuiltInThemes.cupboard) {
            assertEquals(layoutTitles, theme.layouts.map { it.title }, theme.name)
        }
    }

    /** Keynote's layouts, by the names Keynote shows them under, less the two Live Video ones. */
    @Test
    fun theBasicsAreOverKeynotesOwnLayouts() {
        val basic: List<String> = listOf(
            "Title", "Title and Photo", "Title and Photo Alt", "Title and Bullets", "Bullets",
            "Title, Bullets and Photo", "Section", "Title Only", "Agenda", "Statement", "Big Fact",
            "Quote", "Photo - 3 Up", "Photo", "Blank",
        )
        val plain: List<String> = listOf(
            "Title", "Photo - Horizontal", "Title - Centre", "Photo - Vertical", "Title - Top",
            "Title and Bullets", "Title, Bullets and Photo", "Bullets", "Photo - 3 Up", "Quote",
            "Photo", "Blank",
        )

        assertEquals(
            listOf("Basic White", "Basic Black", "Classic White", "White", "Black"),
            BuiltInThemes.basic.map { it.name },
        )
        for (theme in BuiltInThemes.basic) {
            val expected: List<String> = if (theme.name in listOf("White", "Black")) plain else basic
            assertEquals(expected, theme.layouts.map { it.title }, theme.name)
            val ids: List<String> = theme.layouts.flatMap { layout -> layout.elements.map { it.id } + layout.id }
            assertEquals(ids.size, ids.toSet().size, theme.name)
        }
    }

    /** Switching between Basic themes carries every line of a title card to its own slot. */
    @Test
    fun aBasicThemeSwitchKeepsTitleSubtitleAndAuthorInPlace() {
        val deck: Document = Document().applyingTheme(BuiltInThemes.BasicWhite)
        val card: Slide = Slide(id = "card").instantiating(deck.layouts.first())
        val texts: List<String> = listOf("Cupboard", "Slides for developers", "xxfast, 2026")
        val written: Slide = card.copy(
            elements = card.elements.zip(texts) { element, text -> (element as TextElement).copy(text = text) },
        )

        val switched: Document = deck.copy(slides = listOf(written)).applyingTheme(BuiltInThemes.ClassicWhite)

        val slide: Slide = switched.slides.single()
        val classic: Slide = switched.layouts.first()
        assertEquals(texts, slide.elements.map { (it as TextElement).text })
        assertEquals(classic.elements.map { it.frame }, slide.elements.map { it.frame })
        assertEquals(TextFont.Canela, (slide.elements.first() as TextElement).fontFamily)
        assertEquals(TextAlign.Center, (slide.elements.last() as TextElement).align)
    }

    /** Basic White sets its title card flush left with an author line; White centres it without one. */
    @Test
    fun theBasicTitleCardsDifferOnlyWhereKeynotesDo() {
        val basic: Slide = BuiltInThemes.BasicWhite.layouts.first()
        val white: Slide = BuiltInThemes.White.layouts.first()
        val classic: Slide = BuiltInThemes.ClassicWhite.layouts.first()

        fun titleOf(layout: Slide): TextElement =
            layout.placeholders().getValue(PlaceholderSlot(PlaceholderRole.Title, 0)) as TextElement

        assertEquals(TextAlign.Start, titleOf(basic).align)
        assertEquals(TextAlign.Center, titleOf(white).align)
        assertEquals(3, basic.elements.size)
        assertEquals(2, white.elements.size)
        assertEquals(TextFont.Canela, titleOf(classic).fontFamily)
        assertEquals(0xFFFFFFFF, titleOf(BuiltInThemes.Black.layouts.first()).color)
    }

    /** "Cupboard" is the app's own look, so it is what an unthemed deck already shows. */
    @Test
    fun theCupboardThemeIsWhatADeckStartsOn() {
        assertEquals(BuiltInThemes.Cupboard.name, Document().themeName)
        assertEquals(BuiltInThemes.Cupboard.defaults, Document().defaults)
        assertEquals(BuiltInThemes.Cupboard.background, Document().background)
        assertEquals(BuiltInThemes.Cupboard.layouts, defaultLayouts())
    }

    @Test
    fun aThemeBringsItsBackgroundDefaultsAndLayoutsWithIt() {
        val themed: Document = document().applyingTheme(BuiltInThemes.Terminal)

        assertEquals("Terminal", themed.themeName)
        assertEquals(BuiltInThemes.Terminal.background, themed.background)
        assertEquals(BuiltInThemes.Terminal.defaults, themed.defaults)
        assertEquals(layoutTitles, themed.layouts.map { it.title })

        // Copies, not the theme's own: the deck owns its layouts and editing one
        // must never edit the theme it came from.
        val themeIds: Set<String> = BuiltInThemes.Terminal.layouts.mapTo(mutableSetOf()) { it.id }
        assertTrue(themed.layouts.none { it.id in themeIds })
    }

    @Test
    fun slidesFollowTheirLayoutByNameAndKeepWhatTheySay() {
        val themed: Document = document().applyingTheme(BuiltInThemes.Terminal)
        val code: Slide = themed.layouts.first { it.title == "Code" }
        val slide: Slide = themed.slides.first { it.id == "one" }

        assertEquals(code.id, slide.layoutId)

        val title: TextElement = slide.elements.filterIsInstance<TextElement>()
            .first { it.role == PlaceholderRole.Title }
        // Same element, still saying what it said, wearing the theme's ink and
        // the layout's frame.
        assertEquals("one-title", title.id)
        assertEquals("What I wrote", title.text)
        assertEquals(BuiltInThemes.Terminal.defaults.textColor, title.color)
        assertEquals(BuiltInThemes.Terminal.defaults.textFont, title.fontFamily)
        assertEquals(code.placeholders().getValue(PlaceholderSlot(PlaceholderRole.Title, 0)).frame, title.frame)

        // The layout's other placeholder arrives as an instance of its own.
        assertTrue(slide.elements.any { it.placeholderRole == PlaceholderRole.Code })
    }

    @Test
    fun aSlideOnALayoutTheThemeHasNoNameForKeepsEverythingAndComesOffLayouts() {
        val themed: Document = document().applyingTheme(BuiltInThemes.Nord)
        val slide: Slide = themed.slides.first { it.id == "two" }

        assertNull(slide.layoutId)
        assertEquals(document().slides.first { it.id == "two" }.elements, slide.elements)
    }

    @Test
    fun aDeckSavedAsAThemeAndPutBackOnItIsTheDeckItWas() {
        val themed: Document = document().applyingTheme(BuiltInThemes.Nord)
        val again: Document = themed.applyingTheme(themed.asTheme("Mine"))

        assertEquals("Mine", again.themeName)
        assertEquals(themed.background, again.background)
        assertEquals(themed.defaults, again.defaults)
        assertEquals(themed.layouts.map { it.title }, again.layouts.map { it.title })
        assertEquals(themed.slides.map { it.elements }, again.slides.map { it.elements })

        // Same layouts by name, under fresh ids: the copy happens on the way in.
        assertEquals(
            themed.slides.map { slide -> themed.layouts.firstOrNull { it.id == slide.layoutId }?.title },
            again.slides.map { slide -> again.layouts.firstOrNull { it.id == slide.layoutId }?.title },
        )
        assertNotEquals(themed.layouts.map { it.id }, again.layouts.map { it.id })
    }

    /** Documents written before themes existed carry none of the three new fields. */
    @Test
    fun aDocumentWithoutAThemeStillLoads() {
        val decoded: Document = loadedDocument(
            """{"id":"old","name":"Old","slides":[{"id":"s","title":"S"}]}""",
        )

        assertEquals("Cupboard", decoded.themeName)
        assertNull(decoded.background)
        assertEquals(ElementDefaults(), decoded.defaults)
        assertEquals(defaultLayouts(), decoded.layouts)
    }

    @Test
    fun aFreshElementIsDressedInTheDecksDefaults() {
        val frame = Frame(0f, 0f, 100f, 100f)
        val defaults: ElementDefaults = BuiltInThemes.Solarized.defaults

        assertEquals(defaults.textColor, textBoxElement(frame, defaults).color)
        assertEquals(defaults.textFont, textBoxElement(frame, defaults).fontFamily)

        val shape: ShapeElement = shapeElement(ShapeKind.Rectangle, frame, defaults)
        assertEquals(defaults.shapeFill, shape.fill)
        assertEquals(defaults.shapeStroke, shape.strokeColor)
        assertEquals(defaults.shapeLabelColor, shape.labelColor)

        assertEquals(defaults.codeTheme, codeBoxElement(frame, defaults).theme)

        val diagram: DiagramElement = diagramElement(frame, defaults)
        assertEquals(defaults.shapeFill, diagram.nodeFill)
        assertEquals(defaults.shapeStroke, diagram.nodeStroke)
        assertEquals(defaults.shapeLabelColor, diagram.nodeText)
        assertEquals(defaults.bodyColor, diagram.edgeColor)

        assertEquals(defaults.textColor, equationElement(frame, defaults).color)
    }

    /** No defaults asked for is the app's own look, unchanged from before themes. */
    @Test
    fun anElementInsertedWithNoDefaultsLooksTheWayItAlwaysDid() {
        val frame = Frame(0f, 0f, 100f, 100f)

        assertEquals(0xFFFFFFFF, textBoxElement(frame).color)
        assertEquals(0x387F52FF, shapeElement(ShapeKind.Rectangle, frame).fill)
        assertEquals(CodeTheme.Atom, codeBoxElement(frame).theme)
        assertEquals(0xFFFFFFFF, equationElement(frame).color)
        // An edge is body copy now, so a fresh diagram takes ElementDefaults'
        // body colour rather than the one DiagramElement itself defaults to.
        assertEquals(ElementDefaults().bodyColor, diagramElement(frame).edgeColor)
    }
}

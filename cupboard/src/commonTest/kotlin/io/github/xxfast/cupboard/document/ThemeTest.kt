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
    fun theBuiltInsAreDistinctThemesOverTheSameFourLayouts() {
        assertEquals(11, BuiltInThemes.all.size)
        assertEquals(11, BuiltInThemes.all.map { it.name }.toSet().size)
        for (theme in BuiltInThemes.all) {
            assertEquals(layoutTitles, theme.layouts.map { it.title }, theme.name)
        }
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
        assertEquals(code.placeholders().getValue(PlaceholderRole.Title).frame, title.frame)

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

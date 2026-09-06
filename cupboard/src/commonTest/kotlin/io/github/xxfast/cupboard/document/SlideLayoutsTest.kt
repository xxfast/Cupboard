package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The layout half of the document model: what a deck starts with, what a slide
 * takes when it moves onto a layout, and what it only ever inherits.
 */
class SlideLayoutsTest {
    private fun layout(): Slide = Slide(
        id = "layout",
        title = "Title & Body",
        elements = listOf(
            TextElement(
                id = "placeholder-title",
                frame = Frame(146f, 130f, 1627f, 142f),
                text = "Title",
                fontSize = 94f,
                role = PlaceholderRole.Title,
            ),
            TextElement(
                id = "placeholder-body",
                frame = Frame(146f, 300f, 1627f, 650f),
                text = "Body text",
                fontSize = 39f,
                role = PlaceholderRole.Body,
            ),
            ShapeElement(id = "rule", frame = Frame(146f, 280f, 1627f, 2f)),
        ),
    )

    @Test
    fun aDeckStartsWithTheFourDefaultLayouts() {
        val layouts: List<Slide> = defaultLayouts()

        assertEquals(listOf("Title", "Title & Body", "Code", "Blank"), layouts.map { it.title })
        assertEquals(
            listOf(PlaceholderRole.Title, PlaceholderRole.Body),
            layouts[0].elements.map { it.placeholderRole },
        )
        assertEquals(
            listOf(PlaceholderRole.Title, PlaceholderRole.Body),
            layouts[1].elements.map { it.placeholderRole },
        )
        assertEquals(
            listOf(PlaceholderRole.Title, PlaceholderRole.Code),
            layouts[2].elements.map { it.placeholderRole },
        )
        assertTrue(layouts[3].elements.isEmpty())
        assertEquals(defaultLayouts(), Document().layouts)
    }

    /**
     * Fixed ids, so two decks built from the defaults compare equal. Every test
     * in the editor suite that asserts an undo landed back on the document it
     * started from rests on this.
     */
    @Test
    fun theDefaultLayoutsAreTheSameEveryTime() {
        assertEquals(defaultLayouts(), defaultLayouts())
        assertEquals(Document(id = "doc"), Document(id = "doc"))
    }

    @Test
    fun applyingALayoutRedressesTheSlidesOwnElementsAndKeepsWhatTheySay() {
        val slide = Slide(
            id = "slide",
            elements = listOf(
                TextElement(
                    id = "mine",
                    frame = Frame(0f, 0f, 100f, 100f),
                    text = "What I wrote",
                    fontSize = 12f,
                    role = PlaceholderRole.Title,
                ),
            ),
        )

        val applied: Slide = slide.applyingLayout(layout())
        val title = applied.elements.first { it.placeholderRole == PlaceholderRole.Title } as TextElement

        assertEquals("layout", applied.layoutId)
        assertEquals("mine", title.id)
        assertEquals("What I wrote", title.text)
        assertEquals(Frame(146f, 130f, 1627f, 142f), title.frame)
        assertEquals(94f, title.fontSize)
    }

    @Test
    fun applyingALayoutAppendsThePlaceholdersTheSlideHasNothingFor() {
        val applied: Slide = Slide(id = "slide").applyingLayout(layout())
        val body = applied.elements.single { it.placeholderRole == PlaceholderRole.Body }

        assertEquals(2, applied.elements.size)
        assertEquals("Body text", (body as TextElement).text)
        // A copy, not the layout's own element: editing the slide must not edit
        // every other slide on the layout.
        assertNotEquals("placeholder-body", body.id)
    }

    @Test
    fun anElementWhoseRoleTheNewLayoutHasNoPlaceholderForStaysWhereItIs() {
        val code = CodeElement(
            id = "code",
            frame = Frame(10f, 20f, 30f, 40f),
            code = "fun main() {}",
            role = PlaceholderRole.Code,
        )
        val applied: Slide = Slide(id = "slide", elements = listOf(code)).applyingLayout(layout())

        assertEquals(code, applied.elements.first())
    }

    @Test
    fun applyingNoLayoutOnlyUnhooksTheSlide() {
        val slide: Slide = Slide(id = "slide").applyingLayout(layout())
        val cleared: Slide = slide.applyingLayout(null)

        assertNull(cleared.layoutId)
        assertEquals(slide.elements, cleared.elements)
    }

    @Test
    fun instantiatingCopiesEveryPlaceholderInUnderFreshIds() {
        val fresh: Slide = Slide(id = "slide").instantiating(layout())

        assertEquals("layout", fresh.layoutId)
        assertEquals(
            listOf(PlaceholderRole.Title, PlaceholderRole.Body),
            fresh.elements.map { it.placeholderRole },
        )
        assertTrue(fresh.elements.none { it.id.startsWith("placeholder-") })
        assertNull(Slide(id = "slide").instantiating(null).layoutId)
    }

    @Test
    fun onlyTheLayoutsStaticObjectsAreInherited() {
        val slide: Slide = Slide(id = "slide").applyingLayout(layout())

        assertEquals(listOf("rule"), slide.inheritedElements(layout()).map { it.id })
        assertTrue(slide.inheritedElements(null).isEmpty())
    }

    @Test
    fun theLayoutsBackgroundStandsInWhereTheSlideHasNone() {
        val dressed: Slide = layout().copy(background = SlideBackground.Color(0xFF101010))
        val own = SlideBackground.Color(0xFF202020)

        assertEquals(dressed.background, Slide().effectiveBackground(dressed))
        assertEquals(own, Slide(background = own).effectiveBackground(dressed))
        assertNull(Slide().effectiveBackground(null))
    }

    @Test
    fun updatingASlideReachesIntoTheLayoutsToo() {
        val document = Document(id = "doc", slides = listOf(Slide(id = "slide")), layouts = listOf(layout()))
        val renamed: Document = document.updateSlide(layout().copy(title = "Renamed"))

        assertEquals("Renamed", renamed.layouts.single().title)
        assertEquals(document.slides, renamed.slides)
        assertTrue(document.isLayout("layout"))
        assertEquals("layout", document.slideById("layout")?.id)
        assertEquals("slide", document.slideById("slide")?.id)
        assertNull(document.slideById("nowhere"))
    }

    @Test
    fun removingALayoutKeepsTheLastOneAndUnhooksTheSlidesThatWereOnIt() {
        val document = Document(
            id = "doc",
            slides = listOf(Slide(id = "slide", layoutId = "layout")),
            layouts = listOf(layout(), Slide(id = "other", title = "Other")),
        )

        val removed: Document = document.removeLayout("layout")
        assertEquals(listOf("other"), removed.layouts.map { it.id })
        assertNull(removed.slides.single().layoutId)

        // The last one stays, so there is always something to build a slide on.
        assertTrue(removed.removeLayout("other") === removed)
    }

    @Test
    fun serializationRoundTripsLayoutsAndRoles() {
        val document = Document(
            id = "doc",
            slides = listOf(Slide(id = "slide").applyingLayout(layout())),
            layouts = listOf(layout()),
        )

        val decoded: Document = loadedDocument(document.encodeToString())
        assertEquals(document, decoded)
        assertEquals("layout", decoded.slides.single().layoutId)
        assertEquals(PlaceholderRole.Title, decoded.layouts.single().elements.first().placeholderRole)
    }

    /**
     * Layouts arrived after the first documents were written, so a file without
     * them has to keep opening: on the defaults, on no layout, and with every
     * element a static one. Hand-written JSON on purpose, the same argument
     * `DocumentTest` makes for the transform fields.
     */
    @Test
    fun aDocumentWrittenBeforeLayoutsStillDecodes() {
        val json = """
            {
              "id": "doc",
              "name": "Old",
              "slides": [
                {
                  "id": "slide",
                  "elements": [
                    {
                      "type": "text",
                      "id": "text",
                      "frame": { "x": 0.0, "y": 0.0, "width": 10.0, "height": 10.0 },
                      "text": "Hello"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val decoded: Document = loadedDocument(json)
        assertEquals(defaultLayouts(), decoded.layouts)
        assertNull(decoded.slides.single().layoutId)
        assertNull(decoded.slides.single().elements.single().placeholderRole)
    }
}

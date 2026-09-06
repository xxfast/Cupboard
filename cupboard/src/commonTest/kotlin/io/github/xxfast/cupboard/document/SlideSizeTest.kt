package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The deck's slide size, and what moving it does to the deck. Widescreen to
 * Standard throughout, because it is the resize that isn't uniform: 0.75 across
 * and 1.0 down, so a frame that mapped by one ratio on both axes would show up
 * as a wrong y.
 */
class SlideSizeTest {
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                elements = listOf(
                    TextElement(
                        id = "text",
                        frame = Frame(100f, 200f, 400f, 300f),
                        fontSize = 40f,
                    ),
                    GroupElement(
                        id = "group",
                        frame = Frame(400f, 100f, 200f, 100f),
                        children = listOf(
                            ShapeElement(id = "child", frame = Frame(400f, 100f, 100f, 100f)),
                        ),
                    ),
                ),
            ),
        ),
        layouts = listOf(
            Slide(
                id = "layout",
                elements = listOf(
                    TextElement(
                        id = "title",
                        frame = Frame(200f, 100f, 1600f, 140f),
                        fontSize = 60f,
                    ),
                ),
            ),
        ),
        guides = listOf(
            Guide(id = "vertical", axis = GuideAxis.Vertical, position = 960f),
            Guide(id = "horizontal", axis = GuideAxis.Horizontal, position = 540f),
        ),
    )

    private fun Document.element(id: String): Element =
        slides.single().elements.first { it.id == id }

    @Test
    fun aDeckOnAPresetSizeResolvesToIt() {
        assertEquals(SlideSizePreset.Widescreen, Document().slideSizePreset())
        assertEquals(
            SlideSizePreset.Standard,
            Document(slideWidth = 1440f, slideHeight = 1080f).slideSizePreset(),
        )
    }

    @Test
    fun aDeckOnItsOwnSizeIsOnNoPreset() {
        assertNull(Document(slideWidth = 1000f, slideHeight = 800f).slideSizePreset())
    }

    @Test
    fun theSizeItIsAlreadyOnIsTheSameDeck() {
        val document = document()
        assertSame(document, document.resized(1920f, 1080f, scaleContent = true))
        assertSame(document, document.resized(1920f, 1080f, scaleContent = false))
    }

    @Test
    fun everyFrameMapsByItsOwnAxis() {
        val resized = document().resized(1440f, 1080f, scaleContent = true)

        assertEquals(1440f, resized.slideWidth)
        assertEquals(1080f, resized.slideHeight)
        assertEquals(Frame(75f, 200f, 300f, 300f), resized.element("text").frame)
    }

    @Test
    fun typeScalesByTheSmallerRatio() {
        val resized = document().resized(1440f, 1080f, scaleContent = true)
        val text = resized.element("text") as TextElement

        assertEquals(30f, text.fontSize)
    }

    @Test
    fun aGroupCarriesItsChildren() {
        val resized = document().resized(1440f, 1080f, scaleContent = true)
        val group = resized.element("group") as GroupElement

        assertEquals(Frame(300f, 100f, 150f, 100f), group.frame)
        assertEquals(Frame(300f, 100f, 75f, 100f), group.children.single().frame)
    }

    @Test
    fun guidesMoveAlongTheAxisTheyRunAcross() {
        val resized = document().resized(1440f, 1080f, scaleContent = true)

        assertEquals(720f, resized.guides.first { it.id == "vertical" }.position)
        assertEquals(540f, resized.guides.first { it.id == "horizontal" }.position)
    }

    @Test
    fun layoutsScaleWithTheSlides() {
        val resized = document().resized(1440f, 1080f, scaleContent = true)
        val title = resized.layouts.single().elements.single() as TextElement

        assertEquals(Frame(150f, 100f, 1200f, 140f), title.frame)
        assertEquals(45f, title.fontSize)
    }

    @Test
    fun withoutScalingOnlyTheSlideChanges() {
        val document = document()
        val resized = document.resized(1440f, 1080f, scaleContent = false)

        assertEquals(1440f, resized.slideWidth)
        assertEquals(document.slides, resized.slides)
        assertEquals(document.layouts, resized.layouts)
        assertEquals(document.guides, resized.guides)
    }

    @Test
    fun eachSideIsClampedToWhatASlideMayBe() {
        val narrow = document().resized(10f, 10f, scaleContent = false)
        assertEquals(320f, narrow.slideWidth)
        assertEquals(320f, narrow.slideHeight)

        val huge = document().resized(100_000f, 100_000f, scaleContent = false)
        assertEquals(7680f, huge.slideWidth)
        assertEquals(7680f, huge.slideHeight)
    }
}

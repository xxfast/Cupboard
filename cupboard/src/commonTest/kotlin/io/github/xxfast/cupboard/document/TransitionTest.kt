package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransitionTest {
    private fun text(text: String, x: Float = 0f): TextElement =
        TextElement(frame = Frame(x, 0f, 100f, 40f), text = text)

    @Test
    fun aMatchKeyIsTheKindAndWhatTheElementSays() {
        // Same words, different look: the key is content, so they still pair.
        assertEquals(
            text("Hello").matchKey(),
            text("Hello", x = 900f).copy(fontSize = 92f, color = 0xFF00FF00).matchKey(),
        )
        assertNotEquals(text("Hello").matchKey(), text("Goodbye").matchKey())

        // Kinds never pair with each other, however alike they read.
        val shape = ShapeElement(frame = Frame(0f, 0f, 10f, 10f), label = "Hello")
        assertNotEquals(text("Hello").matchKey(), shape.matchKey())
    }

    @Test
    fun aGroupIsKeyedByWhatItHolds() {
        val group = GroupElement(
            frame = Frame(0f, 0f, 200f, 40f),
            children = listOf(text("One"), text("Two", x = 100f)),
        )
        val moved = group.copy(
            id = newId(),
            frame = Frame(500f, 300f, 200f, 40f),
            children = listOf(text("One", x = 500f), text("Two", x = 600f)),
        )
        val reordered = group.copy(children = group.children.reversed())

        assertEquals(group.matchKey(), moved.matchKey())
        assertNotEquals(group.matchKey(), reordered.matchKey())
    }

    @Test
    fun pairsMatchByContentAndSpeakForEachElementOnce() {
        val from = Slide(
            elements = listOf(text("Title"), text("Body", x = 200f), text("Gone", x = 400f)),
        )
        val to = Slide(
            elements = listOf(text("Body", x = 10f), text("Title", x = 20f), text("New", x = 30f)),
        )

        val pairs: List<Pair<Element, Element>> = magicMovePairs(from, to)

        // In the arriving slide's order, and only the two that appear on both.
        assertEquals(listOf("Body", "Title"), pairs.map { (_, arriving) -> arriving.text() })
        assertEquals(listOf("Body", "Title"), pairs.map { (leaving, _) -> leaving.text() })
        // Paired with the elements they actually came off, not with lookalikes.
        assertEquals(
            from.elements.filter { it.text() != "Gone" }.mapTo(mutableSetOf()) { it.id },
            pairs.mapTo(mutableSetOf()) { (leaving, _) -> leaving.id },
        )
    }

    @Test
    fun twoElementsSayingTheSameThingPairOffInOrder() {
        val from = Slide(elements = listOf(text("Same"), text("Same", x = 200f)))
        val to = Slide(elements = listOf(text("Same", x = 10f)))

        val pairs: List<Pair<Element, Element>> = magicMovePairs(from, to)

        // One arriving element, so only the first of the two leaving ones travels.
        assertEquals(1, pairs.size)
        assertEquals(from.elements.first().id, pairs.single().first.id)
    }

    @Test
    fun onlyTopLevelElementsTravel() {
        val group = GroupElement(
            frame = Frame(0f, 0f, 100f, 40f),
            children = listOf(text("Inside")),
        )
        val from = Slide(elements = listOf(group))
        val to = Slide(elements = listOf(text("Inside", x = 500f)))

        assertTrue(magicMovePairs(from, to).isEmpty())
    }

    @Test
    fun aSlideWrittenBeforeTransitionsExistedOpensOnTheDeckDefault() {
        val json = """
            {
              "slides": [
                { "id": "one", "title": "One", "elements": [] }
              ]
            }
        """.trimIndent()

        val document: Document = loadedDocument(json)
        assertNull(document.slides.single().transition)
    }

    @Test
    fun settingATransitionIsANoOpWhenNothingChanges() {
        val document = Document(slides = listOf(Slide(id = "one")))
        val transition = SlideTransition(kind = TransitionKind.Wipe)

        val dressed: Document = document.setSlideTransition("one", transition)
        assertEquals(transition, dressed.slides.single().transition)

        assertTrue(dressed === dressed.setSlideTransition("one", transition))
        assertTrue(document === document.setSlideTransition("nobody", transition))
        assertTrue(document === document.setSlideTransition("one", null))
    }
}

/** The words on an element, for the tests that only ever put text on a slide. */
private fun Element.text(): String = (this as TextElement).text

class TravellingElementTest {
    private val from = TextElement(frame = Frame(0f, 0f, 200f, 100f), text = "Hi", fontSize = 96f)
    private val to = TextElement(frame = Frame(100f, 50f, 400f, 300f), text = "Hi", fontSize = 48f)

    @Test
    fun atZeroItSitsWhereItLeftFromAtTheSizeItLeftAt() {
        val at: TextElement = to.travellingFrom(from, 0f) as TextElement
        assertEquals(from.frame, at.frame)
        assertEquals(from.fontSize, at.fontSize)
        assertEquals(to.text, at.text)
    }

    @Test
    fun atOneItIsTheArrivingElement() {
        assertEquals(to, to.travellingFrom(from, 1f))
        assertEquals(to, to.travellingFrom(from, 1.5f))
    }

    @Test
    fun halfwayItIsHalfwayInBoxAndInType() {
        val at: TextElement = to.travellingFrom(from, 0.5f) as TextElement
        assertEquals(Frame(50f, 25f, 300f, 200f), at.frame)
        assertEquals(72f, at.fontSize)
    }

    @Test
    fun aDifferentKindOnlyLendsItsBox() {
        val shape = ShapeElement(frame = from.frame, labelSize = 30f)
        val at: TextElement = to.travellingFrom(shape, 0f) as TextElement
        assertEquals(from.frame, at.frame)
        assertEquals(to.fontSize, at.fontSize)
    }

    @Test
    fun aShapeBlendsItsLabelAndCorners() {
        val a = ShapeElement(frame = from.frame, labelSize = 10f, cornerRadius = 0f)
        val b = ShapeElement(frame = to.frame, labelSize = 30f, cornerRadius = 40f)
        val at: ShapeElement = b.travellingFrom(a, 0.5f) as ShapeElement
        assertEquals(20f, at.labelSize)
        assertEquals(20f, at.cornerRadius)
    }

    @Test
    fun coloursTravelChannelByChannel() {
        val a = to.copy(color = 0xFF000000, fontWeight = 400)
        val b = to.copy(color = 0xFFFFFFFF, fontWeight = 700)
        val at: TextElement = b.travellingFrom(a, 0.5f) as TextElement
        assertEquals(0xFF808080, at.color)
        assertEquals(550, at.fontWeight)
    }

    @Test
    fun aShapeBlendsItsPaint() {
        val a = ShapeElement(
            frame = from.frame,
            fill = 0x00000000,
            strokeColor = 0xFF0000FF,
            strokeWidth = 0f,
            labelColor = 0xFF000000,
            gradient = ShapeGradient(start = 0xFF000000, end = 0xFF000000, angle = 0f),
            shadow = ShapeShadow(color = 0x00000000, blur = 0f, dx = 0f, dy = 0f),
        )
        val b = ShapeElement(
            frame = to.frame,
            fill = 0xFFFFFFFF,
            strokeColor = 0xFFFF0000,
            strokeWidth = 4f,
            labelColor = 0xFFFFFFFF,
            gradient = ShapeGradient(start = 0xFFFFFFFF, end = 0xFFFFFFFF, angle = 180f),
            shadow = ShapeShadow(color = 0xFF000000, blur = 10f, dx = 2f, dy = 8f),
        )
        val at: ShapeElement = b.travellingFrom(a, 0.5f) as ShapeElement
        assertEquals(0x80808080, at.fill)
        assertEquals(0xFF800080, at.strokeColor)
        assertEquals(2f, at.strokeWidth)
        assertEquals(0xFF808080, at.labelColor)
        assertEquals(ShapeGradient(start = 0xFF808080, end = 0xFF808080, angle = 90f), at.gradient)
        assertEquals(ShapeShadow(color = 0x80000000, blur = 5f, dx = 1f, dy = 4f), at.shadow)
    }

    /** A flat fill has no gradient to blend against, so the arriving one is drawn as is. */
    @Test
    fun aGradientArrivingFromAFlatFillIsTheArrivingOne() {
        val a = ShapeElement(frame = from.frame, gradient = null)
        val gradient = ShapeGradient(start = 0xFFFFFFFF, end = 0xFF000000)
        val b = ShapeElement(frame = to.frame, gradient = gradient)
        assertEquals(gradient, (b.travellingFrom(a, 0.5f) as ShapeElement).gradient)
    }
}

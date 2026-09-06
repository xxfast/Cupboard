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

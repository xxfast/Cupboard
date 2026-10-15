package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TextFieldsTest {
    private val frame = Frame(1700f, 40f, 180f, 40f)
    private val numberBox = TextElement(id = "number", frame = frame, text = "{number} / {total}")

    private val layout = Slide(id = "layout", title = "Title & Body", elements = listOf(numberBox))

    private val deck = Document(
        slides = listOf(
            Slide(id = "a", title = "Welcome", layoutId = layout.id, showsSlideNumber = false),
            Slide(id = "b", title = "Agenda", layoutId = layout.id),
            Slide(id = "c", title = "Backup", layoutId = layout.id, skipped = true),
            Slide(id = "d", title = "Kotlin", layoutId = layout.id),
        ),
        layouts = listOf(layout),
    )

    @Test
    fun everyFieldIsFilledIn() {
        val fields = SlideFields(number = 5, total = 89, title = "Coroutines")
        assertEquals("5 / 89", "{number} / {total}".resolveFields(fields))
        assertEquals("Coroutines · 5", "{title} · {number}".resolveFields(fields))
        assertEquals("55", "{number}{number}".resolveFields(fields))
    }

    @Test
    fun bracesRoundAnythingElseStayAsTyped() {
        val fields = SlideFields(number = 5, total = 89, title = "Coroutines")
        val typed = "{name} and {Number} and {}"
        assertEquals(typed, typed.resolveFields(fields))
        assertEquals("fun main() { }", "fun main() { }".resolveFields(fields))
    }

    @Test
    fun aTitleHoldingAFieldIsNotFilledInAgain() {
        val fields = SlideFields(number = 5, total = 89, title = "Why {number}?")
        assertEquals("Why {number}? 5", "{title} {number}".resolveFields(fields))
    }

    @Test
    fun aSkippedSlideHasNoNumberToShow() {
        val fields = SlideFields(number = null, total = 3, title = "Backup")
        assertEquals(" / 3", "{number} / {total}".resolveFields(fields))
    }

    @Test
    fun aLayoutShowsWhatEachFieldIs() {
        assertEquals("# / # Title", "{number} / {total} {title}".resolveFields(null))
    }

    @Test
    fun skippedSlidesCloseUpTheNumbersAndTheTotal() {
        val fields: List<SlideFields> = deck.slides.indices.map { deck.slideFields(it) }
        assertEquals(listOf(1, 2, null, 3), fields.map { it.number })
        assertEquals(listOf(3, 3, 3, 3), fields.map { it.total })
        assertEquals(deck.presentationNumbers(), fields.map { it.number })
        assertEquals(listOf("Welcome", "Agenda", "Backup", "Kotlin"), fields.map { it.title })
    }

    @Test
    fun fieldsByIdAreNoneForALayout() {
        assertEquals(deck.slideFields(3), deck.slideFields("d"))
        assertEquals(null, deck.slideFields(layout.id))
        assertEquals(null, deck.slideFields("nowhere"))
    }

    @Test
    fun oneBoxOnALayoutReadsRightOnEverySlideBuiltOnIt() {
        val read: List<String> = deck.slides.indices.map { index ->
            val slide: Slide = deck.slides[index]
            val box: Element = slide.inheritedElements(deck.layoutOf(slide)).single()
            (box.withFields(deck.slideFields(index)) as TextElement).text
        }
        assertEquals(listOf("1 / 3", "2 / 3", " / 3", "3 / 3"), read)
    }

    @Test
    fun aBoxWithNoFieldIsLeftAlone() {
        val plain = TextElement(frame = frame, text = "Hello")
        assertSame(plain, plain.withFields(deck.slideFields(0)))
        assertFalse(plain.hasFields)
    }

    @Test
    fun aGroupFillsInItsChildren() {
        val group = GroupElement(frame = frame, children = listOf(numberBox))
        val fields = SlideFields(number = 7, total = 9, title = "")
        val filled = group.withFields(fields) as GroupElement
        assertEquals("7 / 9", (filled.children.single() as TextElement).text)
    }

    @Test
    fun aRangeLinkStaysOnTheWordsItWasPutOn() {
        val linked = TextElement(
            frame = frame,
            text = "{number} of {total}",
            links = listOf(TextLink(start = 9, end = 11, target = LinkTarget.First)),
        )
        val fields = SlideFields(number = 12, total = 89, title = "")
        val filled = linked.withFields(fields) as TextElement
        assertEquals("12 of 89", filled.text)
        val link: TextLink = filled.links.single()
        assertEquals("of", filled.text.substring(link.start, link.end))
    }

    @Test
    fun onlyTheNumberAndTotalAreTheSlideNumber() {
        assertTrue(TextElement(frame = frame, text = "{number}").hasSlideNumber)
        assertTrue(TextElement(frame = frame, text = "of {total}").hasSlideNumber)
        assertFalse(TextElement(frame = frame, text = "{title}").hasSlideNumber)
        assertTrue(TextElement(frame = frame, text = "{title}").hasFields)
    }

    @Test
    fun theSwitchHidesTheNumberAndNothingElse() {
        val title = TextElement(frame = frame, text = "{title}")
        val off: Slide = deck.slides[0]
        val on: Slide = deck.slides[1]

        assertTrue(off.hidesNumberIn(numberBox))
        assertFalse(off.hidesNumberIn(title))
        assertFalse(on.hidesNumberIn(numberBox))
        assertTrue(off.hidesNumberIn(GroupElement(frame = frame, children = listOf(numberBox))))
    }

    @Test
    fun aSlideReadsFieldsOnlyWhenItOrItsLayoutHoldsOne() {
        val quiet = Slide()
        assertFalse(quiet.readsFields(layout = null))
        assertTrue(quiet.readsFields(layout))
        val titled = TextElement(frame = frame, text = "{title}")
        assertTrue(quiet.copy(elements = listOf(titled)).readsFields(layout = null))
    }

    @Test
    fun newSlidesShowTheirNumber() {
        assertTrue(Slide().showsSlideNumber)
    }

    @Test
    fun twoSlideNumbersPairWhateverTheyAreTypedAs() {
        val from = Slide(elements = listOf(numberBox))
        val other = TextElement(id = "other", frame = frame, text = "Slide {number}")
        val to = Slide(elements = listOf(other))

        val pairs: List<Pair<Element, Element>> = magicMovePairs(from, to)
        assertEquals(listOf("number" to "other"), pairs.map { (a, b) -> a.id to b.id })
    }

    @Test
    fun aSlideNumberSwapsItsWordsHalfwayRatherThanCrossfading() {
        val leaving = numberBox.withFields(SlideFields(number = 5, total = 89, title = ""))
        val arriving = numberBox.withFields(SlideFields(number = 6, total = 89, title = ""))

        assertEquals("5 / 89", (arriving.travellingFrom(leaving, 0.25f) as TextElement).text)
        assertEquals("6 / 89", (arriving.travellingFrom(leaving, 0.5f) as TextElement).text)
        assertEquals(arriving, arriving.travellingFrom(leaving, 1f))
    }
}

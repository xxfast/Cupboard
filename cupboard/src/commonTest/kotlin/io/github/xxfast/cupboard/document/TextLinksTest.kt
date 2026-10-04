package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Range links inside a text box: setting and clearing one over others, and how
 * they ride an edit to the text they are anchored to.
 */
class TextLinksTest {
    private val home: LinkTarget = LinkTarget.Url("https://home")
    private val away: LinkTarget = LinkTarget.Slide("away")

    private fun box(text: String, vararg links: TextLink): TextElement =
        TextElement(id = "t", frame = Frame(0f, 0f, 100f, 40f), text = text, links = links.toList())

    @Test
    fun aLinkGoesOnTheRangeItNames() {
        val linked: TextElement = box("Hello world").withLink(6, 11, home)
        assertEquals(listOf(TextLink(6, 11, home)), linked.links)
        assertEquals(TextLink(6, 11, home), linked.linkAt(6))
        assertEquals(TextLink(6, 11, home), linked.linkAt(10))
        assertNull(linked.linkAt(11))
        assertNull(linked.linkAt(5))
    }

    @Test
    fun aRangeIsClampedIntoTheTextAndABackwardsOneIsTurnedRound() {
        assertEquals(listOf(TextLink(3, 5, home)), box("Hello").withLink(9, 3, home).links)
        assertEquals(emptyList(), box("Hello").withLink(2, 2, home).links)
    }

    @Test
    fun anOverlappedLinkIsTrimmed() {
        val linked: TextElement = box("Hello world", TextLink(0, 5, home)).withLink(3, 8, away)
        assertEquals(listOf(TextLink(0, 3, home), TextLink(3, 8, away)), linked.links)
    }

    @Test
    fun aLinkInsideAnotherSplitsIt() {
        val linked: TextElement = box("Hello world", TextLink(0, 11, home)).withLink(4, 6, away)
        assertEquals(
            listOf(TextLink(0, 4, home), TextLink(4, 6, away), TextLink(6, 11, home)),
            linked.links,
        )
    }

    @Test
    fun aNullTargetRemovesTheRangeAndSplitsAroundIt() {
        val cleared: TextElement = box("Hello world", TextLink(0, 11, home)).withLink(4, 6, null)
        assertEquals(listOf(TextLink(0, 4, home), TextLink(6, 11, home)), cleared.links)
    }

    @Test
    fun clearingEverythingLeavesNoLinks() {
        val cleared: TextElement =
            box("Hello world", TextLink(0, 3, home), TextLink(5, 9, away)).withLink(0, 11, null)
        assertEquals(emptyList(), cleared.links)
    }

    @Test
    fun touchingLinksToTheSamePlaceMerge() {
        val linked: TextElement = box("Hello world", TextLink(0, 4, home)).withLink(4, 8, home)
        assertEquals(listOf(TextLink(0, 8, home)), linked.links)
    }

    @Test
    fun normalisingSortsClampsDropsEmptiesAndResolvesOverlaps() {
        val normalised: List<TextLink> = normalisedLinks(
            listOf(TextLink(6, 20, away), TextLink(2, 2, home), TextLink(0, 8, home)),
            length = 11,
        )
        assertEquals(listOf(TextLink(0, 8, home), TextLink(8, 11, away)), normalised)
    }

    @Test
    fun anInsertBeforeALinkMovesIt() {
        val links: List<TextLink> = listOf(TextLink(6, 11, home))
        assertEquals(listOf(TextLink(9, 14, home)), shiftedLinks("Hello world", "Oh Hello world", links))
    }

    @Test
    fun anInsertInsideALinkGrowsIt() {
        val links: List<TextLink> = listOf(TextLink(6, 11, home))
        assertEquals(listOf(TextLink(6, 13, home)), shiftedLinks("Hello world", "Hello wo--rld", links))
    }

    @Test
    fun anInsertAfterALinkLeavesItAlone() {
        val links: List<TextLink> = listOf(TextLink(0, 5, home))
        assertEquals(links, shiftedLinks("Hello world", "Hello big world", links))
    }

    @Test
    fun typingRightAfterALinkDoesNotExtendIt() {
        val links: List<TextLink> = listOf(TextLink(0, 5, home))
        assertEquals(links, shiftedLinks("Hello world", "Hellos world", links))
    }

    @Test
    fun typingRightAtALinksStartDoesNotExtendIt() {
        val links: List<TextLink> = listOf(TextLink(6, 11, home))
        assertEquals(listOf(TextLink(7, 12, home)), shiftedLinks("Hello world", "Hello Xworld", links))
    }

    @Test
    fun aDeleteAcrossALinksStartClipsIt() {
        // "lo wo" goes: the link keeps "rld".
        val links: List<TextLink> = listOf(TextLink(6, 11, home))
        assertEquals(listOf(TextLink(3, 6, home)), shiftedLinks("Hello world", "Helrld", links))
    }

    @Test
    fun aDeleteAcrossALinksEndClipsIt() {
        // "lo w" goes: the link keeps "Hel".
        val links: List<TextLink> = listOf(TextLink(0, 5, home))
        assertEquals(listOf(TextLink(0, 3, home)), shiftedLinks("Hello world", "Helorld", links))
    }

    @Test
    fun deletingAllOfALinksTextDropsIt() {
        val links: List<TextLink> = listOf(TextLink(0, 6, home), TextLink(6, 11, away))
        assertEquals(listOf(TextLink(0, 6, home)), shiftedLinks("Hello world", "Hello ", links))
    }

    @Test
    fun replacingAllTheTextDropsEveryLink() {
        val links: List<TextLink> = listOf(TextLink(0, 5, home), TextLink(6, 11, away))
        assertEquals(emptyList(), shiftedLinks("Hello world", "Goodbye", links))
    }

    @Test
    fun withTextCarriesTheLinksAlong() {
        val edited: TextElement = box("Hello world", TextLink(6, 11, home)).withText("Hi world")
        assertEquals("Hi world", edited.text)
        assertEquals(listOf(TextLink(3, 8, home)), edited.links)
    }

    @Test
    fun aRangeLinkRoundTripsThroughTheDocumentFormat() {
        val element: TextElement = box("Hello world", TextLink(0, 5, home), TextLink(6, 11, away))
        val json: String = DocumentJson.encodeToString(Element.serializer(), element)
        assertEquals(element, DocumentJson.decodeFromString(Element.serializer(), json))
    }

    @Test
    fun aBoxWrittenBeforeRangeLinksReadsWithNone() {
        val json = """{"type":"text","id":"t","frame":{"x":0,"y":0,"width":10,"height":10},"text":"Hi","link":"https://old"}"""
        val element = DocumentJson.decodeFromString(Element.serializer(), json) as TextElement
        assertEquals(emptyList(), element.links)
        assertEquals(LinkTarget.Url("https://old"), element.resolvedLink())
    }
}

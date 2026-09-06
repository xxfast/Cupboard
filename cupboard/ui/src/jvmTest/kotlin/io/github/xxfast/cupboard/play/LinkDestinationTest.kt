package io.github.xxfast.cupboard.play

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.PlaybackSettings
import io.github.xxfast.cupboard.document.Slide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Where a link lands the show. Three plain slides, one step each, so a position
 * is a slide and nothing else is in the way.
 */
class LinkDestinationTest {
    private val document = Document(
        id = "doc",
        slides = listOf(
            Slide(id = "one", title = "One"),
            Slide(id = "two", title = "Two"),
            Slide(id = "three", title = "Three"),
        ),
    )

    private val middle = PlayPosition(1, 0)

    @Test
    fun aSlideTargetLandsOnThatSlideAsItOpens() {
        assertEquals(
            PlayPosition(2, 0),
            document.linkDestination(LinkTarget.Slide("three"), middle),
        )
        // Its own slide is a destination like any other, and rewinds it.
        assertEquals(
            PlayPosition(1, 0),
            document.linkDestination(LinkTarget.Slide("two"), middle),
        )
    }

    @Test
    fun nextAndPreviousAreTheOrdinaryWalk() {
        assertEquals(PlayPosition(2, 0), document.linkDestination(LinkTarget.Next, middle))
        assertEquals(PlayPosition(0, 0), document.linkDestination(LinkTarget.Previous, middle))
    }

    @Test
    fun nextAndPreviousRunOutAtTheEndsOfTheDeck() {
        assertNull(document.linkDestination(LinkTarget.Next, PlayPosition(2, 0)))
        assertNull(document.linkDestination(LinkTarget.Previous, PlayPosition(0, 0)))
    }

    @Test
    fun firstAndLastAreTheEndsOfTheDeck() {
        assertEquals(PlayPosition(0, 0), document.linkDestination(LinkTarget.First, middle))
        assertEquals(PlayPosition(2, 0), document.linkDestination(LinkTarget.Last, middle))
    }

    @Test
    fun theTwoThatLeaveTheDeckResolveToNoPosition() {
        assertNull(document.linkDestination(LinkTarget.Url("https://example.com"), middle))
        assertNull(document.linkDestination(LinkTarget.ExitShow, middle))
    }

    @Test
    fun aSlideTheDeckSkipsIsNoDestination() {
        val deck: Document = document.copy(
            slides = document.slides.map { if (it.id == "three") it.copy(skipped = true) else it },
        )

        // Not in the show, so a link is not a way back into it.
        assertNull(deck.linkDestination(LinkTarget.Slide("three"), middle))
        // And the deck it does play is two slides long.
        assertEquals(PlayPosition(1, 0), deck.linkDestination(LinkTarget.Last, PlayPosition(0, 0)))
    }

    @Test
    fun aSlideTheDeckHasNeverHeardOfIsNoDestination() {
        assertNull(document.linkDestination(LinkTarget.Slide("nobody"), middle))
    }

    @Test
    fun aLoopingDeckWrapsPastItsLastStep() {
        val end = PlayPosition(2, 0)
        val looping: Document = document.copy(playback = PlaybackSettings(loop = true))

        assertNull(document.wrappedNext(end))
        assertEquals(PlayPosition(0, 0), looping.wrappedNext(end))
        // Everywhere but the end, looping changes nothing.
        assertEquals(document.nextPosition(middle), looping.wrappedNext(middle))
    }
}

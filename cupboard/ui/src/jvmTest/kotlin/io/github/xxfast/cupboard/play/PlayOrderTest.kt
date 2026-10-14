package io.github.xxfast.cupboard.play

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.document.setSlideSkipped
import io.github.xxfast.cupboard.document.stepCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The walk the presenter display counts the show by, checked against the deck it
 * is counting: the same rule the CuP compiler plays, so what the display says is
 * coming is what arrives.
 */
class PlayOrderTest {

    @Test
    fun skippedSlidesAreLeftOut() {
        val document: Document = sampleDocument()
        val skipped: Slide = document.slides[1]
        val order: List<Slide> = document.setSlideSkipped(skipped.id, true).playOrder()

        assertEquals(document.slides.size - 1, order.size)
        assertFalse(order.any { it.id == skipped.id })
    }

    @Test
    fun aDeckWithEverySlideSkippedPlaysWhole() {
        val document: Document = sampleDocument().let { deck ->
            deck.slides.fold(deck) { acc, slide -> acc.setSlideSkipped(slide.id, true) }
        }

        assertEquals(document.slides, document.playOrder())
    }

    @Test
    fun playIndexIsTheSlideIndexOnADeckWithNoSkips() {
        val document: Document = sampleDocument()

        assertEquals(2, document.playIndexOf(2))
    }

    @Test
    fun playIndexCountsOutTheSkippedSlidesBefore() {
        val document: Document = sampleDocument().let { it.setSlideSkipped(it.slides[0].id, true) }

        assertEquals(1, document.playIndexOf(2))
        assertEquals(document.slides[2].id, document.playOrder()[document.playIndexOf(2)].id)
    }

    @Test
    fun playIndexOfASkippedSlideOpensOnTheNextOnePlayed() {
        val document: Document = sampleDocument().let { it.setSlideSkipped(it.slides[1].id, true) }

        assertEquals(document.slides[2].id, document.playOrder()[document.playIndexOf(1)].id)
    }

    @Test
    fun playIndexOfASkippedLastSlideOpensOnTheLastOnePlayed() {
        val document: Document = sampleDocument().let { it.setSlideSkipped(it.slides.last().id, true) }

        assertEquals(document.playOrder().lastIndex, document.playIndexOf(document.slides.lastIndex))
    }

    @Test
    fun nextWalksEveryStepOfEverySlideInOrder() {
        val document: Document = sampleDocument()

        assertEquals(document.everyPosition(), document.walkForward())
    }

    @Test
    fun nextIsNullAtTheEndOfTheDeck() {
        val document: Document = sampleDocument()
        val order: List<Slide> = document.playOrder()
        val last = PlayPosition(order.lastIndex, order.last().stepCount() - 1)

        assertNull(document.nextPosition(last))
    }

    @Test
    fun previousWalksTheSameSlidesAndStepsBackwards() {
        val document: Document = sampleDocument()

        assertEquals(document.everyPosition().reversed(), document.walkBackward())
    }

    @Test
    fun previousIsNullAtTheStartOfTheDeck() {
        assertNull(sampleDocument().previousPosition(PlayPosition(0, 0)))
    }

    /**
     * The two ends of a slide change, spelled out: the last step of one slide
     * goes to step 0 of the next, and coming back lands on that last step again
     * rather than on the slide's opening.
     */
    @Test
    fun aSlideChangeOpensTheNextSlideAndComesBackToTheLastStepOfThePrevious() {
        val document: Document = sampleDocument()
        val order: List<Slide> = document.playOrder()
        val edge = PlayPosition(0, order[0].stepCount() - 1)

        assertEquals(PlayPosition(1, 0), document.nextPosition(edge))
        assertEquals(edge, document.previousPosition(PlayPosition(1, 0)))
    }

    @Test
    fun skippedSlidesAreWalkedPast() {
        val document: Document = sampleDocument()
        val skipped: Slide = document.slides[1]
        val deck: Document = document.setSlideSkipped(skipped.id, true)
        val opening = PlayPosition(0, deck.playOrder()[0].stepCount() - 1)

        // Index 1 of the play order is now the deck's third slide.
        assertEquals(document.slides[2].id, deck.playOrder()[1].id)
        assertEquals(PlayPosition(1, 0), deck.nextPosition(opening))
    }
}

/** Every position the deck has, in order, worked out straight off the step counts. */
private fun Document.everyPosition(): List<PlayPosition> =
    playOrder().flatMapIndexed { index: Int, slide: Slide ->
        (0 until slide.stepCount()).map { step -> PlayPosition(index, step) }
    }

private fun Document.walkForward(): List<PlayPosition> = buildList {
    var at: PlayPosition? = PlayPosition(0, 0)
    while (at != null) {
        add(at)
        at = nextPosition(at)
    }
}

private fun Document.walkBackward(): List<PlayPosition> = buildList {
    val order: List<Slide> = playOrder()
    var at: PlayPosition? = PlayPosition(order.lastIndex, order.last().stepCount() - 1)
    while (at != null) {
        add(at)
        at = previousPosition(at)
    }
}

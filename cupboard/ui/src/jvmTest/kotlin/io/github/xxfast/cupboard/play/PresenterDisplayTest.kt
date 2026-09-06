package io.github.xxfast.cupboard.play

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.document.stepCount
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The presenter display drawn for real, in each of its arrangements: it composes
 * two slides of the deck at once through the same renderers the show uses, so a
 * layout that throws here is one a presenter would have found mid-talk.
 */
class PresenterDisplayTest {

    @Test
    fun everyLayoutRendersAtAMidPosition() {
        val document: Document = sampleDocument()
        val order: List<Slide> = document.playOrder()
        val at = PlayPosition(order.size / 2, 0)

        for (layout in PresenterLayout.entries) {
            val image: Image = document.render(at, layout)
            assertTrue(
                image.width == 1600 && image.height == 900,
                "$layout rendered ${image.width}x${image.height}",
            )
        }
    }

    /** The end of the deck has no next slide, and the pane says so rather than throwing. */
    @Test
    fun theLastStepOfTheLastSlideRendersWithNoNextSlide() {
        val document: Document = sampleDocument()
        val order: List<Slide> = document.playOrder()
        val end = PlayPosition(order.lastIndex, order.last().stepCount() - 1)

        for (layout in PresenterLayout.entries) document.render(end, layout)
    }

    @Test
    fun theTimerReadsAsHoursMinutesSeconds() {
        assertEquals("0:00:00", elapsed(0))
        assertEquals("0:00:09", elapsed(9_400))
        assertEquals("0:12:05", elapsed(725_000))
        assertEquals("2:03:20", elapsed(7_400_000))
        assertEquals("0:00:00", elapsed(-1))
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Document.render(at: PlayPosition, layout: PresenterLayout): Image =
    renderComposeScene(1600, 900) {
        PresenterDisplay(
            document = this@render,
            position = at,
            layout = layout,
            elapsedMs = 754_000,
            clock = "10:42",
            onNotesChange = { _, _ -> },
            onNext = {},
            onPrevious = {},
        )
    }

package io.github.xxfast.cupboard.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.renderComposeScene
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.sampleDocument
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The controls a presenter drives the show with once it is running: the number
 * they type, the strip they pick a slide off, and the sheet that reminds them
 * which key does what.
 *
 * The arithmetic is tested on its own because it is what decides where the deck
 * lands; the overlays are rendered for real because a panel that throws over a
 * live show is a talk that ends there.
 */
class InShowControlsTest {

    @Test
    fun aTypedNumberIsTheSlideTheAudienceWouldCount() {
        assertEquals(0, jumpTarget("1", 9))
        assertEquals(8, jumpTarget("9", 9))
        assertEquals(11, jumpTarget("12", 20))
    }

    /** A number that is not a slide of this show moves nothing. */
    @Test
    fun aNumberOutsideTheDeckIsNoJumpAtAll() {
        assertNull(jumpTarget("0", 9))
        assertNull(jumpTarget("10", 9))
        assertNull(jumpTarget("", 9))
        assertNull(jumpTarget("1", 0))
        assertNull(jumpTarget("-1", 9))
        assertNull(jumpTarget("x", 9))
    }

    @Test
    fun theHighlightStopsAtBothEndsOfTheStrip() {
        assertEquals(3, movedHighlight(2, 1, 5))
        assertEquals(1, movedHighlight(2, -1, 5))
        assertEquals(4, movedHighlight(4, 1, 5))
        assertEquals(0, movedHighlight(0, -1, 5))
        assertEquals(4, movedHighlight(0, 5, 5))
    }

    /** An empty deck has nowhere to highlight, and asking must not go negative. */
    @Test
    fun anEmptyDeckHighlightsNothing() {
        assertEquals(0, movedHighlight(0, 1, 0))
        assertEquals(0, movedHighlight(3, -1, 0))
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun theSwitcherDrawsEverySlideOfTheShow() {
        val document: Document = sampleDocument()
        val order: List<Slide> = document.playOrder()

        val image: Image = renderComposeScene(1600, 900) {
            Box(Modifier.fillMaxSize()) {
                SlideSwitcher(
                    document = document,
                    slides = order,
                    current = 0,
                    highlight = order.size / 2,
                    onPick = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        assertTrue(image.width == 1600 && image.height == 900)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun theShortcutSheetAndTheJumpBadgeDraw() {
        renderComposeScene(1600, 900) {
            Box(Modifier.fillMaxSize()) {
                ShortcutSheet(Modifier.align(Alignment.Center))
                JumpBadge(
                    buffer = "12",
                    slideCount = 20,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}

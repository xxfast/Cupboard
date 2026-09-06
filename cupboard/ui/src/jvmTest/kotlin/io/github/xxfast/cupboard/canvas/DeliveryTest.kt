package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.BuildDelivery
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.CodeTheme
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.LineRange
import io.github.xxfast.cupboard.document.PieceReveal
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.pieces
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a build delivered in pieces draws: the text styled down to what is out,
 * and the synthetic step a code block is cut to. `BuildTimelineTest` owns which
 * pieces are out at a step, this owns what the canvas then sets.
 */
class DeliveryTest {
    private val element = TextElement(
        id = "body",
        frame = Frame(0f, 0f, 200f, 100f),
        text = "One two three",
    )

    private val ink = Color.White

    private fun delivered(shown: Int, alpha: Float = 1f): AnnotatedString = deliveredText(
        body = element.text,
        offset = 0,
        pieces = element.pieces(BuildDelivery.ByWord),
        shown = shown,
        alpha = alpha,
        color = ink,
    )

    @Test
    fun thePiecesStillToComeAreDrawnRatherThanDropped() {
        val text = delivered(shown = 1)
        // The whole string is set either way: the box is laid out for all of it
        // from the first piece on, so nothing reflows as the rest arrives.
        assertEquals(element.text, text.text)

        val hidden = text.spanStyles.single { it.item.color == Color.Transparent }
        assertEquals(3, hidden.start)
        assertEquals(element.text.length, hidden.end)
    }

    @Test
    fun theNewestPieceCarriesTheFade() {
        val text = delivered(shown = 2, alpha = 0.25f)
        val fading = text.spanStyles.single { it.item.color != Color.Transparent }
        assertEquals(4, fading.start)
        assertEquals(7, fading.end)
        // A colour keeps its alpha in 8 bits, so the fade lands near rather than on.
        assertTrue(abs(0.25f - fading.item.color.alpha) < 0.01f)
        // And the piece before it is left alone: no span of ours over it at all.
        assertTrue(text.spanStyles.none { it.start == 0 })
    }

    @Test
    fun theLastPieceLeavesTheTextAsItWasWritten() {
        val text = delivered(shown = 3)
        assertTrue(text.spanStyles.none { it.item.color == Color.Transparent })
        assertEquals(element.text, text.text)
    }

    @Test
    fun aSliceIsStyledAgainstItsOwnOffset() {
        // The second line of a two-line box, set on a row of its own.
        val lines = TextElement(id = "b", frame = element.frame, text = "One\nTwo")
        val pieces = lines.pieces(BuildDelivery.ByLine)

        val first = deliveredText("One", 0, pieces, shown = 1, alpha = 1f, color = ink)
        assertTrue(first.spanStyles.none { it.item.color == Color.Transparent })

        // Its line hasn't landed yet, so all of it is out of sight.
        val second = deliveredText("Two", 4, pieces, shown = 1, alpha = 1f, color = ink)
        val hidden = second.spanStyles.single()
        assertEquals(Color.Transparent, hidden.item.color)
        assertEquals(0, hidden.start)
        assertEquals(3, hidden.end)
    }

    @Test
    fun aBlockDeliveredByLineIsCutToTheLinesThatAreOut() {
        val reveal = PieceReveal(Build("code", delivery = BuildDelivery.ByLine), shown = 2, total = 4)
        assertEquals(CodeStep(reveal = listOf(LineRange(1, 2))), deliveredStep(reveal))
        assertEquals(2, reveal.linesShown())

        // Nothing to cut for a block handed over whole, or one delivered by
        // something a block has no pieces for.
        assertNull(deliveredStep(null))
        assertNull(deliveredStep(PieceReveal(Build("code", delivery = BuildDelivery.ByWord), 1, 1)))
    }

    @Test
    fun theSyntheticStepDrawsTheSameLinesAStepWould() {
        val code = "a\nb\nc\nd"
        val reveal = PieceReveal(Build("code", delivery = BuildDelivery.ByLine), shown = 2, total = 4)
        val stepped: SteppedCode = steppedCode(code, "kotlin", CodeTheme.Atom, deliveredStep(reveal))

        assertEquals("a\nb", stepped.text.text)
        assertEquals("1\n2", stepped.numbers.text)
    }
}

package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where a diagnostic's message goes, worked out without a font: pixels in, a top-left out. */
class CodeDiagnosticsTest {
    private val inline = Offset(150f, 20f)
    private val below = Offset(100f, 40f)

    @Test
    fun aMessageWithRoomAfterItsLineStaysThere() {
        assertEquals(inline, messagePlacement(inline, below, width = 100f, maxRight = 300f))
        // Ending exactly on the edge is still room.
        assertEquals(inline, messagePlacement(inline, below, width = 150f, maxRight = 300f))
    }

    @Test
    fun aMessageWithNoRoomDropsUnderItsSquiggle() {
        assertEquals(below, messagePlacement(inline, below, width = 160f, maxRight = 300f))
    }

    @Test
    fun aDroppedMessageThatWouldRunOffTheBlockIsPulledLeft() {
        // From the squiggle's 100 it would end at 340, so it ends on the edge instead.
        assertEquals(Offset(60f, 40f), messagePlacement(inline, below, width = 240f, maxRight = 300f))
    }

    @Test
    fun aMessageWiderThanTheBlockStartsAtItsLeftEdge() {
        assertEquals(Offset(0f, 40f), messagePlacement(inline, below, width = 400f, maxRight = 300f))
    }

    @Test
    fun aBlockWithNoEdgeNeverDropsAnything() {
        val endless: Offset =
            messagePlacement(inline, below, width = 10_000f, maxRight = Float.POSITIVE_INFINITY)
        assertEquals(inline, endless)
    }
}

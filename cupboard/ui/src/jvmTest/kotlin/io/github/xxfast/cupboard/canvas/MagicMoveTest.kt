package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.BiasAlignment
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MagicMoveTest {
    private val from = TextElement(
        frame = Frame(100f, 100f, 200f, 100f),
        text = "Travels",
        rotation = 0f,
        opacity = 0.5f,
    )
    private val to = TextElement(
        frame = Frame(500f, 300f, 400f, 200f),
        text = "Travels",
        rotation = 90f,
        opacity = 1f,
    )

    @Test
    fun theEndOfTheJourneyIsTheElementAtRest() {
        val landed: ElementTransform = magicMoveTransform(from, to, 1f)
        assertEquals(ElementTransform(rotation = 90f, opacity = 1f), landed)
    }

    /**
     * Where the element is and how big it is never come through the layer: the
     * box and the type are blended on the element itself (`travellingFrom`), so
     * content reflows into its new size rather than being stretched to it.
     */
    @Test
    fun theStartOfTheJourneyIsTheElementAsItLeft() {
        val start: ElementTransform = magicMoveTransform(from, to, 0f)
        assertEquals(ElementTransform(rotation = 0f, opacity = 0.5f), start)
    }

    @Test
    fun halfwayIsHalfwayInRotationAndOpacityOnly() {
        val half: ElementTransform = magicMoveTransform(from, to, 0.5f)
        assertEquals(ElementTransform(rotation = 45f, opacity = 0.75f), half)
    }

    /** Start to End is a slide of the words across the box, not a snap on frame one. */
    @Test
    fun aRealignedTextSlidesItsWordsAcrossTheBox() {
        val start: TextElement = to.copy(align = TextAlign.Start)
        val end: TextElement = to.copy(align = TextAlign.End)
        assertEquals(BiasAlignment(0f, -1f), travellingAlignment(start, end, 0.5f))
        assertEquals(BiasAlignment(-1f, -1f), travellingAlignment(start, end, 0f))
        assertNull(travellingAlignment(start, start, 0.5f))
    }

    @Test
    fun anUnmatchedElementOnlyFades() {
        assertEquals(ElementTransform(rotation = 90f, opacity = 0.25f), to.fadingTransform(0.25f))
    }
}

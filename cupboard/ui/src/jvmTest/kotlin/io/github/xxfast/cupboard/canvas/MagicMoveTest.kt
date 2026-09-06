package io.github.xxfast.cupboard.canvas

import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.TextElement
import kotlin.test.Test
import kotlin.test.assertEquals

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

    /** Measured against the target, so the start is the whole of the distance back. */
    @Test
    fun theStartOfTheJourneyIsWhereTheElementCameFrom() {
        val start: ElementTransform = magicMoveTransform(from, to, 0f)
        assertEquals(-500f, start.translationX)
        assertEquals(-250f, start.translationY)
        assertEquals(0.5f, start.scaleX)
        assertEquals(0.5f, start.scaleY)
        assertEquals(0f, start.rotation)
        assertEquals(0.5f, start.opacity)
    }

    @Test
    fun halfwayIsHalfwayInEveryDimension() {
        val half: ElementTransform = magicMoveTransform(from, to, 0.5f)
        assertEquals(-250f, half.translationX)
        assertEquals(-125f, half.translationY)
        assertEquals(0.75f, half.scaleX)
        assertEquals(0.75f, half.scaleY)
        assertEquals(45f, half.rotation)
        assertEquals(0.75f, half.opacity)
    }

    @Test
    fun anUnmatchedElementOnlyFades() {
        assertEquals(ElementTransform(rotation = 90f, opacity = 0.25f), to.fadingTransform(0.25f))
    }
}

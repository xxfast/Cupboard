package io.github.xxfast.cupboard.canvas

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which line goes where between two steps. The motion itself is Compose's, so
 * what is worth pinning down is the pairing: a line is matched by the number it
 * has in the original block, never by the row it happens to be sitting on.
 */
class CodeStepTransitionTest {
    @Test
    fun theSameBlockTwiceMovesNothing() {
        val transitions: List<LineTransition> = lineTransitions(listOf(1, 2, 3), listOf(1, 2, 3))
        assertEquals(3, transitions.size)
        assertEquals(List(3) { LineMotion.Kept }, transitions.map { it.motion })
        transitions.forEach { assertEquals(it.fromRow, it.toRow) }
    }

    @Test
    fun aRevealAddsTheLinesItOpensAndLeavesTheRestWhereTheyAre() {
        val transitions: List<LineTransition> = lineTransitions(listOf(1, 2), listOf(1, 2, 3, 4))
        assertEquals(
            listOf(LineMotion.Kept, LineMotion.Kept, LineMotion.Added, LineMotion.Added),
            transitions.map { it.motion },
        )
        assertEquals(listOf(0, 1, null, null), transitions.map { it.fromRow })
        assertEquals(listOf(0, 1, 2, 3), transitions.map { it.toRow })
    }

    @Test
    fun aStepThatHidesLinesRemovesThem() {
        val transitions: List<LineTransition> = lineTransitions(listOf(1, 2, 3), listOf(1, 3))
        assertEquals(
            listOf(LineMotion.Kept, LineMotion.Removed, LineMotion.Kept),
            transitions.map { it.motion },
        )
        assertEquals(listOf(0, 1, 2), transitions.map { it.fromRow })
        assertEquals(listOf(0, null, 1), transitions.map { it.toRow })
    }

    @Test
    fun aKeptLineCarriesBothOfItsRowsSoItCanSlideBetweenThem() {
        // Line 5 was under line 2 alone and ends up under three lines.
        val transitions: List<LineTransition> = lineTransitions(listOf(2, 5), listOf(1, 2, 3, 5))
        val moved: LineTransition = transitions.single { it.number == 5 }
        assertEquals(LineMotion.Kept, moved.motion)
        assertEquals(1, moved.fromRow)
        assertEquals(3, moved.toRow)
    }

    @Test
    fun twoStepsSharingNoLinesSwapWholesale() {
        val transitions: List<LineTransition> = lineTransitions(listOf(1, 2, 3), listOf(5, 6, 7))
        assertEquals(listOf(1, 2, 3, 5, 6, 7), transitions.map { it.number })
        assertEquals(
            List(3) { LineMotion.Removed } + List(3) { LineMotion.Added },
            transitions.map { it.motion },
        )
    }

    @Test
    fun theUnionComesBackInLineOrderWhicheverStepEachLineIsIn() {
        val transitions: List<LineTransition> = lineTransitions(listOf(4, 5, 9), listOf(1, 5, 12))
        assertEquals(listOf(1, 4, 5, 9, 12), transitions.map { it.number })
        assertEquals(
            listOf(
                LineMotion.Added,
                LineMotion.Removed,
                LineMotion.Kept,
                LineMotion.Removed,
                LineMotion.Added,
            ),
            transitions.map { it.motion },
        )
    }

    @Test
    fun anEmptyStepIsAllRemovedAndAllAddedComingBack() {
        assertEquals(
            List(2) { LineMotion.Removed },
            lineTransitions(listOf(1, 2), emptyList()).map { it.motion },
        )
        assertEquals(
            List(2) { LineMotion.Added },
            lineTransitions(emptyList(), listOf(1, 2)).map { it.motion },
        )
        assertEquals(emptyList<LineTransition>(), lineTransitions(emptyList(), emptyList()))
    }
}

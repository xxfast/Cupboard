package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import io.github.xxfast.cupboard.document.CodeToken
import io.github.xxfast.cupboard.document.tokenizeCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a token morph draws, given where the two versions put their tokens.
 *
 * The placement is a grid here rather than a font: a column is a column wide and
 * a line is a line tall, so a position in these assertions reads as the place in
 * the code it is. What is under test is the pairing and the three motions, which
 * is all of the morph that is ours; the tweening between the two positions is
 * Compose's.
 */
class CodeMorphTest {
    /** A monospace grid, one unit a column and ten units a line. */
    private fun grid(hidden: Set<Int> = emptySet()): (CodeToken) -> TokenPlacement? = { token ->
        if (token.line in hidden) null
        else TokenPlacement(Offset(token.start.toFloat(), token.line * 10f), Color.White)
    }

    private fun glyphs(
        previous: String,
        current: String,
        previousHidden: Set<Int> = emptySet(),
        currentHidden: Set<Int> = emptySet(),
    ): List<GlyphRun> = morphGlyphs(
        previous = tokenizeCode(previous, "kotlin"),
        current = tokenizeCode(current, "kotlin"),
        previousPlacement = grid(previousHidden),
        currentPlacement = grid(currentHidden),
    )

    @Test
    fun oneChangedTokenLeavesAndArrivesAndTheRestStandStill() {
        val runs: List<GlyphRun> = glyphs("val x = 10", "val x = 20")

        assertEquals(listOf("10"), runs.filterIsInstance<GlyphRun.Left>().map { it.text })
        assertEquals(listOf("20"), runs.filterIsInstance<GlyphRun.Arrived>().map { it.text })

        val moved: List<GlyphRun.Moved> = runs.filterIsInstance<GlyphRun.Moved>()
        assertEquals(listOf("val", "x", "="), moved.map { it.text })
        moved.forEach { assertEquals(it.from, it.to) }
    }

    @Test
    fun aTokenThatChangesLineMoves() {
        val runs: List<GlyphRun> = glyphs("val x = 10", "// note\nval x = 10")
        val moved: GlyphRun.Moved = runs.filterIsInstance<GlyphRun.Moved>().first { it.text == "x" }

        assertEquals(0f, moved.from.y)
        assertEquals(10f, moved.to.y)
        assertEquals(moved.from.x, moved.to.x)
    }

    @Test
    fun theSameVersionTwiceOnlyStandsStill() {
        val code = "fun load(id: String): User {\n    return api.fetch(id)\n}"
        val runs: List<GlyphRun> = glyphs(code, code)

        assertTrue(runs.isNotEmpty())
        assertTrue(runs.all { it is GlyphRun.Moved })
        runs.filterIsInstance<GlyphRun.Moved>().forEach { assertEquals(it.from, it.to) }
    }

    @Test
    fun aTokenOnALineTheStepHidesArrivesInsteadOfMoving() {
        // The second line is there in both versions, and the old step hides it:
        // from the block's point of view those tokens are not leaving anywhere,
        // they are turning up.
        val code = "val x = 10\nval y = 20"
        val runs: List<GlyphRun> = glyphs(code, code, previousHidden = setOf(1))

        val arrived: List<String> = runs.filterIsInstance<GlyphRun.Arrived>().map { it.text }
        assertEquals(listOf("val", "y", "=", "20"), arrived)
        assertTrue(runs.filterIsInstance<GlyphRun.Left>().isEmpty())
        // The first line is shown on both sides, so it is still standing still.
        assertEquals(
            listOf("val", "x", "=", "10"),
            runs.filterIsInstance<GlyphRun.Moved>().map { it.text },
        )
    }

    @Test
    fun aTokenTheNewStepHidesLeaves() {
        val code = "val x = 10\nval y = 20"
        val runs: List<GlyphRun> = glyphs(code, code, currentHidden = setOf(1))

        assertEquals(
            listOf("val", "y", "=", "20"),
            runs.filterIsInstance<GlyphRun.Left>().map { it.text },
        )
        assertTrue(runs.filterIsInstance<GlyphRun.Arrived>().isEmpty())
    }

    @Test
    fun whitespaceDrawsNothingSoItIsNotARun() {
        val runs: List<GlyphRun> = glyphs("val x = 10", "val    x = 10")
        assertTrue(runs.none { it.text.isBlank() })
    }
}

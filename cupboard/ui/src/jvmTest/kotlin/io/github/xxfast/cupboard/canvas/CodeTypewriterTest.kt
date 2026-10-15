package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.BuildAt
import io.github.xxfast.cupboard.document.BuildEffect
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeToken
import io.github.xxfast.cupboard.document.TypingRhythm
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.showcaseDocument
import io.github.xxfast.cupboard.document.tokenizeCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A typed step change, worked out without a font: char counts in, phases and
 * per-run reveal counts out. The drawing reads every frame off these, so what is
 * asserted here is the whole of the keyboard that is ours.
 */
class CodeTypewriterTest {
    /** A monospace grid, one unit a column and ten units a line, as in [CodeMorphTest]. */
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
    fun onlyInkedCharactersCost() {
        assertEquals(3, "val".drawnCount())
        assertEquals(13, "\"Hello, world\"".drawnCount())
        assertEquals(0, "    ".drawnCount())
    }

    @Test
    fun aPrefixRunsToTheLastInkedCharacterAndCarriesTheBlanksBeforeIt() {
        val text = "// a b"
        assertEquals(0, text.drawnPrefix(0))
        assertEquals(1, text.drawnPrefix(1))
        // The third inked character is `a`, so the space before it comes along.
        assertEquals(4, text.drawnPrefix(3))
        assertEquals(text.length, text.drawnPrefix(4))
        assertEquals(text.length, text.drawnPrefix(99))
    }

    @Test
    fun aBudgetIsSpentInOrder() {
        assertEquals(listOf(3, 1, 0), spend(listOf(3, 2, 4), 4))
        assertEquals(listOf(0, 0), spend(listOf(3, 2), 0))
        assertEquals(listOf(3, 2), spend(listOf(3, 2), 99))
    }

    @Test
    fun aSteadyRhythmIsAPlainCount() {
        val paces: List<Float> = keyPaces(listOf(3, 2), TypingRhythm.Steady)
        assertEquals(List(5) { 1f }, paces)
        assertEquals(0, landed(0f, paces))
        assertEquals(3, landed(3f / 5, paces))
        assertEquals(5, landed(1f, paces))
    }

    @Test
    fun aNaturalRhythmVariesTheKeysButNotTheLength() {
        val runs: List<Int> = listOf(6, 6, 6, 6, 6)
        val paces: List<Float> = keyPaces(runs, TypingRhythm.Natural)
        assertEquals(30, paces.size)
        // The keys share one phase the same length as a steady run's.
        assertEquals(30f, paces.sum(), 0.001f)
        assertTrue(paces.all { it > 0f })
        // No two gaps in a row alike, which is the whole point.
        assertTrue(paces.zipWithNext().any { (a, b) -> a != b })
        // Nothing lands before the start, everything by the end, and the same
        // key lands at the same moment each time it is asked.
        assertEquals(0, landed(0f, paces))
        assertEquals(30, landed(1f, paces))
        assertEquals(keyPaces(runs, TypingRhythm.Natural), paces)
    }

    @Test
    fun aNaturalRhythmFillsTheSameRunsByTheEnd() {
        val natural: TypewriterTimeline = typewriterTimeline(
            departing = emptyList(), arriving = listOf(2, 3), moves = false,
            durationMs = 0, rhythm = TypingRhythm.Natural,
        )
        assertEquals(5f * TypewriterMsPerChar, natural.totalMs)
        assertEquals(listOf(0, 0), natural.arrivingAt(0f))
        assertEquals(listOf(2, 3), natural.arrivingAt(1f))
        assertNull(natural.caretAt(1f))
    }

    @Test
    fun theNaturalLengthIsThreePhasesAtTheirOwnPaces() {
        val timeline: TypewriterTimeline =
            typewriterTimeline(
                departing = listOf(4), arriving = listOf(2, 2), moves = true,
                durationMs = 0, rhythm = TypingRhythm.Steady,
            )

        assertEquals(4 * TypewriterEraseMsPerChar, timeline.eraseMs)
        assertEquals(CodeStepDuration.toFloat(), timeline.moveMs)
        assertEquals(4f * TypewriterMsPerChar, timeline.typeMs)
        // Erasing is a held backspace: twice the pace of typing.
        assertEquals(TypewriterMsPerChar / 2f, TypewriterEraseMsPerChar)
    }

    @Test
    fun anEmptyPhaseTakesNoTime() {
        val typedOnly: TypewriterTimeline =
            typewriterTimeline(
                departing = emptyList(), arriving = listOf(5), moves = false,
                durationMs = 0, rhythm = TypingRhythm.Steady,
            )
        assertEquals(0f, typedOnly.eraseMs)
        assertEquals(0f, typedOnly.moveMs)
        assertEquals(5f * TypewriterMsPerChar, typedOnly.totalMs)

        val movedOnly: TypewriterTimeline =
            typewriterTimeline(
                departing = emptyList(), arriving = emptyList(), moves = true,
                durationMs = 0, rhythm = TypingRhythm.Steady,
            )
        assertEquals(CodeStepDuration.toFloat(), movedOnly.totalMs)
        assertNull(movedOnly.caretAt(0f))
        assertNull(movedOnly.caretAt(0.5f))
    }

    @Test
    fun aLongerBuildStretchesEveryPhaseAndAShorterOneIsIgnored() {
        val natural: TypewriterTimeline =
            typewriterTimeline(listOf(4), listOf(4), moves = true, durationMs = 0, rhythm = TypingRhythm.Steady)
        val stretched: TypewriterTimeline =
            typewriterTimeline(
                listOf(4), listOf(4), moves = true,
                durationMs = (natural.totalMs * 2).toInt(), rhythm = TypingRhythm.Steady,
            )
        val short: TypewriterTimeline =
            typewriterTimeline(listOf(4), listOf(4), moves = true, durationMs = 10, rhythm = TypingRhythm.Steady)

        assertEquals(natural, short)
        assertEquals(natural.eraseMs * 2, stretched.eraseMs, 0.01f)
        assertEquals(natural.moveMs * 2, stretched.moveMs, 0.01f)
        assertEquals(natural.typeMs * 2, stretched.typeMs, 0.01f)
    }

    @Test
    fun erasingBackspacesFromTheEndOfTheLastRun() {
        // Two departing runs of 3 and 2, nothing else: the whole clock is erase.
        val timeline: TypewriterTimeline =
            typewriterTimeline(listOf(3, 2), emptyList(), moves = false, durationMs = 0, rhythm = TypingRhythm.Steady)

        assertEquals(listOf(3, 2), timeline.departingAt(0f))
        assertEquals(TypewriterCaret(departing = true, run = 1, drawn = 2), timeline.caretAt(0f))

        // Two of five out: the last run is gone and the caret sits after the first.
        assertEquals(listOf(3, 0), timeline.departingAt(2f / 5))
        assertEquals(TypewriterCaret(departing = true, run = 0, drawn = 3), timeline.caretAt(2f / 5))

        assertEquals(listOf(1, 0), timeline.departingAt(4f / 5))
        assertEquals(listOf(0, 0), timeline.departingAt(1f))
        assertNull(timeline.caretAt(1f))
    }

    @Test
    fun typingFillsRunsFromTheFrontWithTheCaretAfterTheLastCharacter() {
        val timeline: TypewriterTimeline =
            typewriterTimeline(emptyList(), listOf(2, 3), moves = false, durationMs = 0, rhythm = TypingRhythm.Steady)

        assertEquals(listOf(0, 0), timeline.arrivingAt(0f))
        // Before the first key lands the caret waits where the first run starts.
        assertEquals(TypewriterCaret(departing = false, run = 0, drawn = 0), timeline.caretAt(0f))

        assertEquals(listOf(2, 1), timeline.arrivingAt(3f / 5))
        assertEquals(TypewriterCaret(departing = false, run = 1, drawn = 1), timeline.caretAt(3f / 5))

        // At rest every run is whole and the caret is gone.
        assertEquals(listOf(2, 3), timeline.arrivingAt(1f))
        assertNull(timeline.caretAt(1f))
    }

    @Test
    fun thePhasesFollowEachOtherOnOneClock() {
        // 4 out at 12.5ms, 350ms of move, 4 in at 25ms: 50 + 350 + 100 = 500ms.
        val timeline: TypewriterTimeline =
            typewriterTimeline(listOf(4), listOf(4), moves = true, durationMs = 0, rhythm = TypingRhythm.Steady)
        assertEquals(500f, timeline.totalMs)

        val eraseEnd = 50f / 500
        val moveEnd = 400f / 500

        // Erasing: nothing has moved and nothing is typed yet.
        assertEquals(0f, timeline.moveAt(eraseEnd / 2))
        assertEquals(listOf(0), timeline.arrivingAt(eraseEnd / 2))
        assertTrue(timeline.caretAt(eraseEnd / 2)!!.departing)

        // Moving: the old runs are gone, the new ones not started, no caret.
        val midMove: Float = (eraseEnd + moveEnd) / 2
        assertEquals(listOf(0), timeline.departingAt(midMove))
        assertEquals(listOf(0), timeline.arrivingAt(midMove))
        assertNull(timeline.caretAt(midMove))
        val eased: Float = timeline.moveAt(midMove)
        assertTrue(eased > 0f && eased < 1f)

        // Typing: the move has landed and the caret is the typist's.
        assertEquals(1f, timeline.moveAt(moveEnd))
        assertEquals(listOf(2), timeline.arrivingAt(moveEnd + 50f / 500))
        assertFalse(timeline.caretAt(moveEnd + 50f / 500)!!.departing)
    }

    @Test
    fun runsAreTypedAndErasedInReadingOrderNotDiffOrder() {
        // The second line is hidden on the old side, so its matched tokens come
        // out of the diff as arrivals wherever their match sits, among the
        // inserts on the first line.
        val runs: List<GlyphRun> = glyphs(
            previous = "val x = 10\nval y = 20",
            current = "val x = 11\nval y = 20",
            previousHidden = setOf(1),
        )
        val order: TypingOrder = typingOrder(runs)

        assertEquals(listOf("10"), order.departing.map { runs[it].text })
        assertEquals(listOf("11", "val", "y", "=", "20"), order.arriving.map { runs[it].text })

        val positions: List<Offset> = order.arriving.map { (runs[it] as GlyphRun.Arrived).position }
        assertEquals(positions.sortedWith(compareBy({ it.y }, { it.x })), positions)
    }

    @Test
    fun aRevealWithinOneVersionOnlyTypes() {
        // The same version, its second line hidden before and shown after: the
        // tokens on it are arrivals and nothing else changes at all.
        val code = "val x = 10\nval y = 20"
        val runs: List<GlyphRun> = glyphs(code, code, previousHidden = setOf(1))
        val order: TypingOrder = typingOrder(runs)

        assertTrue(order.departing.isEmpty())
        assertEquals(listOf("val", "y", "=", "20"), order.arriving.map { runs[it].text })
        assertTrue(runs.filterIsInstance<GlyphRun.Moved>().all { it.from == it.to })
    }

    @Test
    fun onlyAForwardTypewriterStepBuildTypes() {
        fun landing(firstStep: Int, effect: BuildEffect): BuildAt =
            BuildAt(firstStep, Build("code", effect = effect, elementStep = 1), firstStep, firstStep, 0)

        val typed: BuildAt = landing(2, BuildEffect.Typewriter)
        assertTrue(typesOnto(typed, previous = null))
        assertTrue(typesOnto(typed, previous = landing(1, BuildEffect.Dissolve)))
        // Backwards onto it, or onto anything else, is the plain transition.
        assertFalse(typesOnto(typed, previous = landing(3, BuildEffect.Typewriter)))
        assertFalse(typesOnto(landing(2, BuildEffect.Dissolve), previous = null))
        assertFalse(typesOnto(null, previous = typed))
    }

    @Test
    fun theShowcaseWalkErasesMovesAndTypesWhereEachStepAsks() {
        val slide: Slide = showcaseDocument().allSlides().first { it.title == "Code Typewriter" }
        val element: CodeElement = slide.elements.filterIsInstance<CodeElement>().single()
        val (zero, one, two, three) = element.steps

        // The rewrite: something goes, something comes, the signature shifts.
        val rewrite: TypewriterTimeline =
            element.typewriterTimeline(zero, one, durationMs = 400, rhythm = TypingRhythm.Steady)
        assertTrue(rewrite.eraseMs > 0f)
        assertTrue(rewrite.moveMs > 0f)
        assertTrue(rewrite.typeMs > 0f)
        assertFalse(rewrite.typesNothing)

        // A reveal within one version: nothing to erase, the closing brace
        // dropping a row to make room, and the new line typed in.
        val reveal: TypewriterTimeline =
            element.typewriterTimeline(one, two, durationMs = 400, rhythm = TypingRhythm.Steady)
        assertEquals(0f, reveal.eraseMs)
        assertEquals(CodeStepDuration.toFloat(), reveal.moveMs)
        assertEquals(
            "returnif(loud)greeting.uppercase()elsegreeting".length * TypewriterMsPerChar.toFloat(),
            reveal.typeMs,
        )
        assertFalse(reveal.typesNothing)

        // A spotlight moving: the dim is a recolour, so it is all move.
        val spotlight: TypewriterTimeline =
            element.typewriterTimeline(two, three, durationMs = 0, rhythm = TypingRhythm.Steady)
        assertEquals(0f, spotlight.eraseMs)
        assertEquals(CodeStepDuration.toFloat(), spotlight.moveMs)
        assertEquals(0f, spotlight.typeMs)
        // No key to press, so the block plays it on the line path instead.
        assertTrue(spotlight.typesNothing)
    }
}

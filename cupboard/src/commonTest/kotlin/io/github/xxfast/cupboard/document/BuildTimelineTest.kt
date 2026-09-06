package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The step model the whole build order is written on: how many pieces a build
 * hands over, where each build lands, and what is on the slide when. All pure and
 * document-side, so the player, the canvas and the export read one answer.
 */
class BuildTimelineTest {
    private val frame = Frame(0f, 0f, 200f, 100f)

    private val body = TextElement(
        id = "body",
        frame = frame,
        text = "One two\n\nthree",
    )

    private val block = CodeElement(id = "code", frame = frame, code = "a\nb\nc")

    private fun slide(vararg builds: Build): Slide =
        Slide(id = "slide", elements = listOf(body, block), builds = builds.toList())

    @Test
    fun everyDeliveryCountsItsOwnPieces() {
        val whole = slide(Build("body"))
        assertEquals(1, whole.builds.single().pieceCount(whole))

        fun pieces(delivery: BuildDelivery): Int {
            val slide = slide(Build("body", delivery = delivery))
            return slide.builds.single().pieceCount(slide)
        }

        // "One two\n\nthree": three lines, two of them with words on.
        assertEquals(3, pieces(BuildDelivery.ByLine))
        assertEquals(2, pieces(BuildDelivery.ByParagraph))
        assertEquals(3, pieces(BuildDelivery.ByWord))
        assertEquals("Onetwothree".length, pieces(BuildDelivery.ByCharacter))
    }

    @Test
    fun aDeliveryTheElementHasNoPiecesForIsOnePiece() {
        val lines = slide(Build("code", delivery = BuildDelivery.ByLine))
        assertEquals(3, lines.builds.single().pieceCount(lines))

        // A code block has lines, not words: the build still reveals it, whole.
        val words = slide(Build("code", delivery = BuildDelivery.ByWord))
        assertEquals(1, words.builds.single().pieceCount(words))

        // And a build for an element the slide doesn't hold is one piece too.
        val nobody = slide(Build("nobody", delivery = BuildDelivery.ByWord))
        assertEquals(1, nobody.builds.single().pieceCount(nobody))
    }

    @Test
    fun theRangesAPieceCoversAreTheOnesItReveals() {
        // The blank line is a piece that reveals nothing and still takes its turn.
        assertEquals(listOf(0..6, IntRange.EMPTY, 9..13), body.pieces(BuildDelivery.ByLine))
        assertEquals(listOf(0..6, 9..13), body.pieces(BuildDelivery.ByParagraph))
        assertEquals(listOf(0..2, 4..6, 9..13), body.pieces(BuildDelivery.ByWord))
        assertEquals(11, body.pieces(BuildDelivery.ByCharacter).size)
        assertEquals(listOf(0..13), body.pieces(BuildDelivery.All))
    }

    @Test
    fun anOnClickBuildSpendsAStepOnEveryPiece() {
        val slide = slide(
            Build("code"),
            Build("body", delivery = BuildDelivery.ByParagraph),
        )

        val timeline = slide.buildTimeline()
        assertEquals(1 to 1, timeline[0].firstStep to timeline[0].lastStep)
        // Two paragraphs, so two clicks: steps 2 and 3.
        assertEquals(2 to 3, timeline[1].firstStep to timeline[1].lastStep)
        assertEquals(4, slide.stepCount())
    }

    @Test
    fun aRiddenTriggerLandsEveryPieceOnItsAnchorsStep() {
        val slide = slide(
            Build("code"),
            Build("body", delivery = BuildDelivery.ByWord, trigger = BuildTrigger.WithPrevious),
        )

        val timeline = slide.buildTimeline()
        assertEquals(1 to 1, timeline[1].firstStep to timeline[1].lastStep)
        assertEquals(2, slide.stepCount())
        // All three words at once: the build has no clicks of its own to spend.
        assertEquals(3, slide.piecesShownAt("body", step = 1))
    }

    @Test
    fun afterPreviousWaitsOutTheChainAheadOfIt() {
        val slide = slide(
            Build("code", durationMs = 500, delayMs = 100),
            Build("body", trigger = BuildTrigger.AfterPrevious, delayMs = 200),
        )

        val timeline = slide.buildTimeline()
        assertEquals(1, timeline[0].firstStep)
        assertEquals(100, timeline[0].delayMs)
        // Same step as the build it follows, its own delay on top of that
        // build's delay and duration.
        assertEquals(1, timeline[1].firstStep)
        assertEquals(800, timeline[1].delayMs)
        assertEquals(2, slide.stepCount())
        assertEquals(800, slide.entryBuildAt("body")?.delayMs)
    }

    @Test
    fun anOnClickBuildAfterPiecesOpensTheStepAfterTheLastOfThem() {
        val slide = slide(
            Build("body", delivery = BuildDelivery.ByParagraph),
            Build("code"),
        )

        assertEquals(1 to 2, slide.buildTimeline()[0].let { it.firstStep to it.lastStep })
        assertEquals(3, slide.buildTimeline()[1].firstStep)
        assertFalse(slide.isVisibleAt("code", step = 2))
        assertTrue(slide.isVisibleAt("code", step = 3))
    }

    @Test
    fun anInThenAnOutShowsTheElementBetweenThem() {
        val slide = slide(
            Build("body"),
            Build("body", kind = BuildKind.Out, effect = BuildEffect.Dissolve),
        )

        assertFalse(slide.isVisibleAt("body", step = 0))
        assertTrue(slide.isVisibleAt("body", step = 1))
        assertFalse(slide.isVisibleAt("body", step = 2))
        assertEquals(BuildKind.In, slide.entryBuild("body")?.kind)
        assertEquals(BuildEffect.Dissolve, slide.exitBuild("body")?.effect)
        // The In is what buildSteps answers for: when it appears, not when it goes.
        assertEquals(1, slide.buildSteps()["body"])
    }

    @Test
    fun anElementWithOnlyAnOutBuildStartsOnTheSlide() {
        val slide = slide(Build("body", kind = BuildKind.Out))

        assertTrue(slide.isVisibleAt("body", step = 0))
        assertFalse(slide.isVisibleAt("body", step = 1))
        assertNull(slide.entryBuild("body"))
        assertFalse("body" in slide.buildSteps())
        // Nothing brings the code block in either, and nothing takes it away.
        assertTrue(slide.isVisibleAt("code", step = 1))
    }

    @Test
    fun piecesShownCountsUpAndThenClamps() {
        val slide = slide(Build("body", delivery = BuildDelivery.ByWord))

        // Nothing has landed at step 0, so the element shows nothing at all.
        assertNull(slide.piecesShownAt("body", step = 0))
        assertEquals(1, slide.piecesShownAt("body", step = 1))
        assertEquals(2, slide.piecesShownAt("body", step = 2))
        assertEquals(3, slide.piecesShownAt("body", step = 3))
        // The build outlives its own last piece rather than starting over.
        assertEquals(3, slide.piecesShownAt("body", step = 9))
        // And an element handed over whole is never in pieces.
        assertNull(slide.piecesShownAt("code", step = 3))
    }

    @Test
    fun aBuildWrittenBeforeAnyOfThisStillDecodes() {
        val json = """
            {
              "id": "doc",
              "slides": [
                {
                  "id": "slide",
                  "builds": [{ "elementId": "x", "effect": "FadeUp" }]
                }
              ]
            }
        """.trimIndent()

        val build = decodeDocument(json).slides.single().builds.single()
        assertEquals(Build("x"), build)
        assertEquals(BuildKind.In, build.kind)
        assertEquals(BuildDelivery.All, build.delivery)
        assertEquals(BuildTrigger.OnClick, build.trigger)
        assertEquals(0, build.delayMs)
    }
}

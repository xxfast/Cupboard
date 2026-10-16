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
    fun aWithPreviousBuildRidesItsAnchorsOffsetThroughAChain() {
        val slide = slide(
            Build("code", kind = BuildKind.Out, durationMs = 500),
            Build("body", trigger = BuildTrigger.WithPrevious, durationMs = 500),
            Build("code", kind = BuildKind.Out, trigger = BuildTrigger.AfterPrevious, durationMs = 500),
            Build("body", trigger = BuildTrigger.WithPrevious, durationMs = 500),
            Build("code", kind = BuildKind.Out, trigger = BuildTrigger.AfterPrevious, durationMs = 500),
            Build("body", trigger = BuildTrigger.WithPrevious, durationMs = 500, delayMs = 50),
        )

        // Each pair plays one after the other: 0, 500, 1000, pair-mates alongside.
        assertEquals(listOf(0, 0, 500, 500, 1000, 1050), slide.buildTimeline().map { it.delayMs })
        assertEquals(2, slide.stepCount())
    }

    @Test
    fun aPureAfterPreviousChainStacksDurationsAndDelays() {
        val slide = slide(
            Build("code", durationMs = 500),
            Build("body", trigger = BuildTrigger.AfterPrevious, durationMs = 300, delayMs = 100),
            Build("code", trigger = BuildTrigger.AfterPrevious, durationMs = 200),
        )

        assertEquals(listOf(0, 600, 900), slide.buildTimeline().map { it.delayMs })
    }

    @Test
    fun aWithPreviousBuildOpeningTheSlideKeepsOnlyItsOwnDelay() {
        val slide = slide(Build("body", trigger = BuildTrigger.WithPrevious, delayMs = 250))

        val at = slide.buildTimeline().single()
        assertEquals(0, at.firstStep)
        assertEquals(250, at.delayMs)
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

        val build = loadedDocument(json).slides.single().builds.single()
        assertEquals(Build("x"), build)
        assertEquals(BuildKind.In, build.kind)
        assertEquals(BuildDelivery.All, build.delivery)
        assertEquals(BuildTrigger.OnClick, build.trigger)
        assertEquals(0, build.delayMs)
    }


    /** A block whose second version types sixty characters on: 1.5s of keys at [TypewriterMsPerChar]. */
    private val typed = CodeElement(
        id = "typed",
        frame = frame,
        code = "a",
        versions = listOf("a\n" + "x".repeat(60)),
        steps = listOf(CodeStep(version = 0), CodeStep(version = 1)),
    )

    private fun typingSlide(vararg builds: Build): Slide =
        Slide(id = "slide", elements = listOf(body, block, typed), builds = builds.toList())

    @Test
    fun anAfterPreviousBuildWaitsOutTheTypingNotTheTypewritersDuration() {
        val slide: Slide = typingSlide(
            Build("typed"),
            Build("typed", kind = BuildKind.Action, effect = BuildEffect.Typewriter, elementStep = 1),
            Build("body", trigger = BuildTrigger.AfterPrevious),
            Build("code", trigger = BuildTrigger.WithPrevious),
            Build("code", kind = BuildKind.Out, trigger = BuildTrigger.AfterPrevious, delayMs = 100),
        )
        val typing: Int = 60 * TypewriterMsPerChar
        assertEquals(typing, typed.typingMs(typed.steps[0], typed.steps[1], durationMs = 400))

        // Typing lands at 1.5s, not at the build's 400ms floor, and the build
        // riding along with the one behind it starts there too. The chain behind
        // them goes on counting from there, the ordinary way.
        val timeline: List<BuildAt> = slide.buildTimeline()
        assertEquals(listOf(0, 0, typing, typing, typing + 400 + 100), timeline.map { it.delayMs })
    }

    @Test
    fun aTypewriterStepBuildThatTypesNothingKeepsItsOwnLength() {
        // Joining the step build before it, it never types: its duration it is.
        val joined: Slide = typingSlide(
            Build("typed", kind = BuildKind.Action, elementStep = 0),
            Build("typed", kind = BuildKind.Action, effect = BuildEffect.Typewriter, trigger = BuildTrigger.WithPrevious, elementStep = 1),
            Build("body", trigger = BuildTrigger.AfterPrevious),
        )
        assertEquals(400, joined.buildTimeline().last().delayMs)

        // A spotlight moving within one version has no key to press, and plays
        // as a plain step change.
        val lit: CodeElement = typed.copy(
            steps = listOf(CodeStep(version = 1, highlight = listOf(LineRange(1, 1))), CodeStep(version = 1)),
        )
        val spotlight = Slide(
            id = "slide",
            elements = listOf(body, lit),
            builds = listOf(
                Build("typed", kind = BuildKind.Action, effect = BuildEffect.Typewriter, durationMs = 1000, elementStep = 1),
                Build("body", trigger = BuildTrigger.AfterPrevious),
            ),
        )
        assertEquals(CodeStepDuration, spotlight.buildTimeline().last().delayMs)
    }

    @Test
    fun anAfterPreviousBuildWaitsOutATerminalsTyping() {
        val terminal = TerminalElement(id = "term", frame = frame, text = "$ ${"x".repeat(40)}\nok")
        fun slide(effect: BuildEffect): Slide = Slide(
            id = "slide",
            elements = listOf(body, terminal),
            builds = listOf(
                Build("term", effect = effect, durationMs = 400),
                Build("body", trigger = BuildTrigger.AfterPrevious),
            ),
        )

        assertEquals(40 * TypewriterMsPerChar, slide(BuildEffect.Typewriter).buildTimeline().last().delayMs)
        assertEquals(400, slide(BuildEffect.Dissolve).buildTimeline().last().delayMs)
    }
}

package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The step model a code block is walked through: which state is showing when,
 * and which of its lines that state draws. All pure, all document-side, so the
 * renderer and anything that exports later read one interpretation.
 */
class CodeStepTest {
    private val frame = Frame(0f, 0f, 200f, 100f)

    /** Five lines, so a range can sit inside, across, or off the end of the block. */
    private val fiveLines = 5

    private fun steppedBlock(vararg steps: CodeStep): CodeElement =
        CodeElement(id = "code", frame = frame, code = "1\n2\n3\n4\n5", steps = steps.toList())

    @Test
    fun aDocumentWrittenBeforeCodeStepsStillDecodes() {
        val json = """
            {
              "id": "doc",
              "name": "Old",
              "slides": [
                {
                  "id": "slide",
                  "elements": [
                    {
                      "type": "code",
                      "id": "code",
                      "frame": { "x": 0.0, "y": 0.0, "width": 10.0, "height": 10.0 },
                      "code": "fun main() {}"
                    }
                  ],
                  "builds": [{ "elementId": "code" }]
                }
              ]
            }
        """.trimIndent()

        val slide = decodeDocument(json).slides.single()
        assertEquals(emptyList(), (slide.elements.single() as CodeElement).steps)
        assertNull(slide.builds.single().elementStep)
        // And an unstepped block is not stepped by anything the slide does.
        assertNull(slide.codeStepFor(slide.elements.single() as CodeElement, step = 1))
    }

    @Test
    fun serializationRoundTripsStepsAndTheirBuilds() {
        val code = steppedBlock(
            CodeStep(reveal = listOf(LineRange(1, 2))),
            CodeStep(highlight = listOf(LineRange(3, 4), LineRange(1, 1))),
        )
        val document = Document(
            slides = listOf(
                Slide(
                    elements = listOf(code),
                    builds = listOf(Build(code.id), Build(code.id, elementStep = 1)),
                ),
            ),
        )

        assertEquals(document, decodeDocument(document.encodeToString()))
    }

    @Test
    fun noCodeBuildHasPlayedYetIsNull() {
        val slide = Slide(
            elements = listOf(steppedBlock(CodeStep(), CodeStep())),
            builds = listOf(Build("code"), Build("code", elementStep = 1)),
        )

        // Step 0 is before every build; step 1 is the reveal, which carries none.
        assertNull(slide.elementStepAt("code", step = 0))
        assertNull(slide.elementStepAt("code", step = 1))
        assertEquals(1, slide.elementStepAt("code", step = 2))
    }

    @Test
    fun theLastCodeBuildAtOrBeforeTheStepWins() {
        val slide = Slide(
            elements = listOf(steppedBlock(CodeStep(), CodeStep(), CodeStep())),
            builds = listOf(
                Build("code", elementStep = 0),
                Build("other"),
                Build("code", elementStep = 1),
                Build("code", elementStep = 2),
            ),
        )

        assertEquals(0, slide.elementStepAt("code", step = 1))
        // Step 2 is the unrelated element's: the block holds what it had.
        assertEquals(0, slide.elementStepAt("code", step = 2))
        assertEquals(1, slide.elementStepAt("code", step = 3))
        assertEquals(2, slide.elementStepAt("code", step = 4))
        // Past the last build it stays where the last one left it.
        assertEquals(2, slide.elementStepAt("code", step = 99))
        assertNull(slide.elementStepAt("missing", step = 99))
    }

    @Test
    fun aWithPreviousCodeBuildLandsOnTheStepBeforeIt() {
        val slide = Slide(
            elements = listOf(steppedBlock(CodeStep(), CodeStep())),
            builds = listOf(
                Build("other"),
                Build("code", elementStep = 1, trigger = BuildTrigger.WithPrevious),
            ),
        )

        // One click, both builds: the block is already advanced at step 1.
        assertEquals(2, slide.stepCount())
        assertEquals(1, slide.elementStepAt("code", step = 1))
        assertNull(slide.elementStepAt("code", step = 0))
    }

    @Test
    fun anElementIsRevealedByItsFirstBuildAndOnlyChangedByTheRest() {
        val slide = Slide(
            elements = listOf(steppedBlock(CodeStep(), CodeStep(), CodeStep())),
            builds = listOf(
                Build("code"),
                Build("code", elementStep = 1),
                Build("code", elementStep = 2),
            ),
        )

        assertEquals(1, slide.buildSteps()["code"])
        assertFalse(slide.isVisibleAt("code", step = 0))
        // Visible from its first build onwards, through every later one.
        assertTrue(slide.isVisibleAt("code", step = 1))
        assertTrue(slide.isVisibleAt("code", step = 2))
        assertTrue(slide.isVisibleAt("code", step = 3))
    }

    @Test
    fun theResolvedStepDefaultsToTheFirstAndClampsToWhatExists() {
        val code = steppedBlock(CodeStep(reveal = listOf(LineRange(1, 1))), CodeStep())
        val slide = Slide(
            elements = listOf(code),
            builds = listOf(Build("code"), Build("code", elementStep = 7)),
        )

        // Visible with no code build behind it yet: the block's first state.
        assertEquals(code.steps[0], slide.codeStepFor(code, step = 1))
        // A build pointing past the end of the list clamps rather than throws.
        assertEquals(code.steps[1], slide.codeStepFor(code, step = 2))
        assertNull(slide.codeStepFor(code.copy(steps = emptyList()), step = 2))
    }

    @Test
    fun anEmptyRevealShowsEveryLine() {
        assertEquals((1..fiveLines).toList(), visibleLines(fiveLines, CodeStep()))
        assertEquals(emptyList(), visibleLines(0, CodeStep()))
    }

    @Test
    fun revealedLinesComeBackInOrderWithoutRepeats() {
        val step = CodeStep(reveal = listOf(LineRange(4, 5), LineRange(1, 2), LineRange(2, 4)))
        assertEquals(listOf(1, 2, 3, 4, 5), visibleLines(fiveLines, step))
    }

    @Test
    fun aReversedRangeRevealsNothing() {
        val step = CodeStep(reveal = listOf(LineRange(4, 2)))
        assertEquals(emptyList(), visibleLines(fiveLines, step))
        assertFalse(3 in LineRange(4, 2))
        // A single-line range is not reversed: it is that one line.
        assertEquals(listOf(3), visibleLines(fiveLines, CodeStep(reveal = listOf(LineRange(3, 3)))))
    }

    @Test
    fun rangesOffTheEndsOfTheBlockClampToIt() {
        val step = CodeStep(reveal = listOf(LineRange(-3, 2), LineRange(4, 40)))
        assertEquals(listOf(1, 2, 4, 5), visibleLines(fiveLines, step))
        assertEquals(emptyList(), visibleLines(fiveLines, CodeStep(reveal = listOf(LineRange(9, 12)))))
    }

    @Test
    fun highlightingNothingDimsNothing() {
        assertEquals(emptySet(), highlightedLines(fiveLines, CodeStep()))
        assertEquals(
            emptySet(),
            highlightedLines(fiveLines, CodeStep(reveal = listOf(LineRange(1, 2)))),
        )
    }

    @Test
    fun highlightedLinesClampAndMerge() {
        val step = CodeStep(highlight = listOf(LineRange(2, 3), LineRange(3, 90), LineRange(5, 1)))
        assertEquals(setOf(2, 3, 4, 5), highlightedLines(fiveLines, step))
        assertEquals(emptySet(), highlightedLines(0, step))
    }

    @Test
    fun theSampleDeckWalksItsCodeBlockThroughEveryStep() {
        val slide = sampleDocument().allSlides().first { it.title == "Slides as Data" }
        val code = slide.elements.filterIsInstance<CodeElement>().single()
        val lines = code.code.lines().size

        assertEquals(code.steps.size + 1, slide.stepCount())
        assertFalse(slide.isVisibleAt(code.id, step = 0))

        // Each click either fills more of the block in or spotlights part of it,
        // and the block never goes backwards.
        val revealed: List<Int> = (1..code.steps.size).map {
            visibleLines(lines, slide.codeStepFor(code, step = it)!!).size
        }
        assertEquals(revealed.sorted(), revealed)
        assertTrue(revealed.first() < lines)
        assertEquals(lines, revealed.last())

        val last: CodeStep = slide.codeStepFor(code, step = code.steps.size)!!
        assertTrue(highlightedLines(lines, last).isNotEmpty())
    }
}

package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The steps a code block holds, the edits that shape the list, and the promise
 * the slide-level edits make: whatever happens to the steps, every build keeps
 * playing the state it was written for.
 *
 * The slide carries three steps and one build per step, plus an entry build with
 * no step at all and a build for another element, so a remap has plenty to get
 * wrong.
 */
class CodeStepsTest {
    private val frame = Frame(0f, 0f, 200f, 100f)

    private fun block(): CodeElement = CodeElement(
        id = "code",
        frame = frame,
        code = "one",
        versions = listOf("two"),
        steps = listOf(
            CodeStep(reveal = listOf(LineRange(1, 1))),
            CodeStep(reveal = listOf(LineRange(1, 2))),
            CodeStep(version = 1),
        ),
    )

    private fun slide(): Slide = Slide(
        id = "one",
        elements = listOf(block(), TextElement(id = "text", frame = frame, text = "A")),
        builds = listOf(
            Build("code"),
            Build("code", elementStep = 0),
            Build("code", elementStep = 1),
            Build("code", elementStep = 2),
            Build("text", elementStep = 1),
        ),
    )

    private fun Slide.codeBlock(): CodeElement = elementById("code") as CodeElement

    /** The steps the builds play, in build order, entries and other elements left out. */
    private fun Slide.playedSteps(): List<Int?> =
        builds.filter { it.elementId == "code" }.map { it.elementStep }

    @Test
    fun addingAStepPutsItBehindTheOneNamedAndTheFrontIsMinusOne() {
        assertEquals(4, block().withStepAdded(1, CodeStep()).steps.size)
        assertEquals(CodeStep(version = 1), block().withStepAdded(0, CodeStep(version = 1)).steps[1])
        // -1 is the front, which is the only way to get in before step 0.
        assertEquals(CodeStep(version = 1), block().withStepAdded(-1, CodeStep(version = 1)).steps[0])
        // A block with no steps at all takes its first one at the front.
        val bare = CodeElement(frame = frame, code = "x")
        assertEquals(listOf(CodeStep()), bare.withStepAdded(-1, CodeStep()).steps)
    }

    @Test
    fun theElementEditsTurnAwayAnIndexTheBlockDoesNotHave() {
        val code: CodeElement = block()

        assertSame(code, code.withStepAdded(3, CodeStep()))
        assertSame(code, code.withStepAdded(-2, CodeStep()))
        assertSame(code, code.withStepRemoved(9))
        assertSame(code, code.withStepMoved(0, 7))
        assertSame(code, code.withStepMoved(1, 1))
        assertSame(code, code.withStepUpdated(-1, CodeStep()))
    }

    @Test
    fun removingAndMovingAndUpdatingShapeTheListAsAsked() {
        assertEquals(
            listOf(CodeStep(reveal = listOf(LineRange(1, 1))), CodeStep(version = 1)),
            block().withStepRemoved(1).steps,
        )
        assertEquals(
            listOf(CodeStep(version = 1)) + block().steps.take(2),
            block().withStepMoved(2, 0).steps,
        )
        assertEquals(CodeStep(version = 1), block().withStepUpdated(0, CodeStep(version = 1)).steps[0])
    }

    @Test
    fun addingAStepPushesTheBuildsAtAndBehindItAlong() {
        val grown: Slide = slide().addingCodeStep("code", after = 0, step = CodeStep())

        assertEquals(4, grown.codeBlock().steps.size)
        // The build on step 0 stays; the two behind the insertion move up.
        assertEquals(listOf(null, 0, 2, 3), grown.playedSteps())
        // Another element's builds are none of this element's business.
        assertEquals(1, grown.builds.first { it.elementId == "text" }.elementStep)
    }

    @Test
    fun removingAStepDropsTheBuildThatPlayedItAndPullsTheRestBack() {
        val shrunk: Slide = slide().removingCodeStep("code", index = 1)

        assertEquals(2, shrunk.codeBlock().steps.size)
        // Below stays, at it goes, above comes back one.
        assertEquals(listOf(null, 0, 1), shrunk.playedSteps())
        assertEquals(1, shrunk.builds.first { it.elementId == "text" }.elementStep)
    }

    @Test
    fun movingAStepTakesItsBuildWithIt() {
        val moved: Slide = slide().movingCodeStep("code", from = 2, to = 0)

        assertEquals(CodeStep(version = 1), moved.codeBlock().steps[0])
        // The build that played the last step now plays the first, and the two
        // it jumped over come back one each.
        assertEquals(listOf(null, 1, 2, 0), moved.playedSteps())
    }

    @Test
    fun updatingAStepLeavesEveryBuildExactlyWhereItWas() {
        val step = CodeStep(highlight = listOf(LineRange(2, 3)), version = 1)
        val edited: Slide = slide().updatingCodeStep("code", index = 1, step = step)

        assertEquals(step, edited.codeBlock().steps[1])
        assertEquals(slide().playedSteps(), edited.playedSteps())
    }

    @Test
    fun theSlideEditsTurnAwayAnIdItHoldsNoCodeBlockUnder() {
        val slide: Slide = slide()

        assertSame(slide, slide.addingCodeStep("text", after = -1, step = CodeStep()))
        assertSame(slide, slide.addingCodeStep("nobody", after = -1, step = CodeStep()))
        assertSame(slide, slide.removingCodeStep("code", index = 9))
        assertSame(slide, slide.movingCodeStep("code", from = 0, to = 0))
        assertSame(slide, slide.updatingCodeStep("text", index = 0, step = CodeStep()))
    }

    @Test
    fun elementStepsWalksACodeBlockLikeItWalksAGallery() {
        val steps: List<Build> = slide().elementSteps("code")

        // One click per step after the first: three steps, two builds.
        assertEquals(listOf(1, 2), steps.map { it.elementStep })
        assertEquals(listOf("code", "code"), steps.map { it.elementId })
        assertEquals(BuildKind.Action, steps.first().kind)
        assertEquals(BuildEffect.Dissolve, steps.first().effect)
        assertEquals(BuildTrigger.OnClick, steps.first().trigger)
        assertEquals(CodeStepBuildDuration, steps.first().durationMs)
    }

    @Test
    fun aBlockWithNothingToWalkThroughGetsNoBuilds() {
        val bare = CodeElement(id = "code", frame = frame, code = "x")
        val one = bare.copy(steps = listOf(CodeStep()))

        assertEquals(emptyList(), Slide(elements = listOf(bare)).elementSteps("code"))
        assertEquals(emptyList(), Slide(elements = listOf(one)).elementSteps("code"))
        // And a kind that holds no states of its own, and an id nothing answers to.
        assertEquals(emptyList(), slide().elementSteps("text"))
        assertEquals(emptyList(), slide().elementSteps("nobody"))
    }

    @Test
    fun elementStepsStillHandsAGalleryItsOwnWalk() {
        val gallery = GalleryElement(
            id = "gallery",
            frame = frame,
            images = listOf(GalleryImage("a"), GalleryImage("b"), GalleryImage("c")),
        )
        val slide = Slide(elements = listOf(gallery))

        assertEquals(slide.gallerySteps("gallery"), slide.elementSteps("gallery"))
        assertEquals(listOf(1, 2), slide.elementSteps("gallery").map { it.elementStep })
    }
}

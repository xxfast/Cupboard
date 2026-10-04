package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * [CodeStepsTest] for a diagram: the edits that shape its step list, and the
 * same promise from the slide-level ones, that every build keeps playing the
 * state it was written for.
 *
 * The slide carries three steps and one build per step, plus an entry build with
 * no step at all and a build for another element, so a remap has plenty to get
 * wrong.
 */
class DiagramStepsTest {
    private val frame = Frame(0f, 0f, 200f, 100f)

    private fun diagram(): DiagramElement = DiagramElement(
        id = "diagram",
        frame = frame,
        source = "graph LR\nA --> B --> C",
        steps = listOf(
            DiagramStep(reveal = listOf("A")),
            DiagramStep(reveal = listOf("A", "B")),
            DiagramStep(highlight = listOf("C")),
        ),
    )

    private fun slide(): Slide = Slide(
        id = "one",
        elements = listOf(diagram(), TextElement(id = "text", frame = frame, text = "A")),
        builds = listOf(
            Build("diagram"),
            Build("diagram", elementStep = 0),
            Build("diagram", elementStep = 1),
            Build("diagram", elementStep = 2),
            Build("text", elementStep = 1),
        ),
    )

    private fun Slide.chart(): DiagramElement = elementById("diagram") as DiagramElement

    /** The steps the builds play, in build order, entries and other elements left out. */
    private fun Slide.playedSteps(): List<Int?> =
        builds.filter { it.elementId == "diagram" }.map { it.elementStep }

    private val spotlight = DiagramStep(highlight = listOf("B"))

    @Test
    fun addingAStepPutsItBehindTheOneNamedAndTheFrontIsMinusOne() {
        assertEquals(4, diagram().withStepAdded(1, DiagramStep()).steps.size)
        assertEquals(spotlight, diagram().withStepAdded(0, spotlight).steps[1])
        // -1 is the front, which is the only way to get in before step 0.
        assertEquals(spotlight, diagram().withStepAdded(-1, spotlight).steps[0])
        // A diagram with no steps at all takes its first one at the front.
        val bare = DiagramElement(frame = frame, source = "graph LR\nA --> B")
        assertEquals(listOf(DiagramStep()), bare.withStepAdded(-1, DiagramStep()).steps)
    }

    @Test
    fun theElementEditsTurnAwayAnIndexTheDiagramDoesNotHave() {
        val chart: DiagramElement = diagram()

        assertSame(chart, chart.withStepAdded(3, DiagramStep()))
        assertSame(chart, chart.withStepAdded(-2, DiagramStep()))
        assertSame(chart, chart.withStepRemoved(9))
        assertSame(chart, chart.withStepMoved(0, 7))
        assertSame(chart, chart.withStepMoved(1, 1))
        assertSame(chart, chart.withStepUpdated(-1, DiagramStep()))
    }

    @Test
    fun removingAndMovingAndUpdatingShapeTheListAsAsked() {
        assertEquals(
            listOf(DiagramStep(reveal = listOf("A")), DiagramStep(highlight = listOf("C"))),
            diagram().withStepRemoved(1).steps,
        )
        assertEquals(
            listOf(DiagramStep(highlight = listOf("C"))) + diagram().steps.take(2),
            diagram().withStepMoved(2, 0).steps,
        )
        assertEquals(spotlight, diagram().withStepUpdated(0, spotlight).steps[0])
    }

    @Test
    fun addingAStepPushesTheBuildsAtAndBehindItAlong() {
        val grown: Slide = slide().addingDiagramStep("diagram", after = 0, step = DiagramStep())

        assertEquals(4, grown.chart().steps.size)
        // The build on step 0 stays, the two behind the insertion move up, and
        // the new step gets its click in between: the walk was queued already.
        assertEquals(listOf(null, 0, 1, 2, 3), grown.playedSteps())
        // Another element's builds are none of this element's business.
        assertEquals(1, grown.builds.first { it.elementId == "text" }.elementStep)
    }

    @Test
    fun removingAStepDropsTheBuildThatPlayedItAndPullsTheRestBack() {
        val shrunk: Slide = slide().removingDiagramStep("diagram", index = 1)

        assertEquals(2, shrunk.chart().steps.size)
        // Below stays, at it goes, above comes back one.
        assertEquals(listOf(null, 0, 1), shrunk.playedSteps())
        assertEquals(1, shrunk.builds.first { it.elementId == "text" }.elementStep)
    }

    @Test
    fun movingAStepTakesItsBuildWithIt() {
        val moved: Slide = slide().movingDiagramStep("diagram", from = 2, to = 0)

        assertEquals(DiagramStep(highlight = listOf("C")), moved.chart().steps[0])
        // The build that played the last step now plays the first, and the two
        // it jumped over come back one each.
        assertEquals(listOf(null, 1, 2, 0), moved.playedSteps())
    }

    @Test
    fun updatingAStepLeavesEveryBuildExactlyWhereItWas() {
        val edited: Slide = slide().updatingDiagramStep("diagram", index = 1, step = spotlight)

        assertEquals(spotlight, edited.chart().steps[1])
        assertEquals(slide().playedSteps(), edited.playedSteps())
    }

    @Test
    fun theSlideEditsTurnAwayAnIdItHoldsNoDiagramUnder() {
        val slide: Slide = slide()

        assertSame(slide, slide.addingDiagramStep("text", after = -1, step = DiagramStep()))
        assertSame(slide, slide.addingDiagramStep("nobody", after = -1, step = DiagramStep()))
        assertSame(slide, slide.removingDiagramStep("diagram", index = 9))
        assertSame(slide, slide.movingDiagramStep("diagram", from = 0, to = 0))
        assertSame(slide, slide.updatingDiagramStep("text", index = 0, step = DiagramStep()))
    }

    @Test
    fun theCodeStepEditsLeaveADiagramAloneAndTheOtherWayRound() {
        val slide: Slide = slide()

        // One kind's edits never reach into the other's steps, even though the
        // build half of both is the same code.
        assertSame(slide, slide.addingCodeStep("diagram", after = -1, step = CodeStep()))
        assertSame(slide, slide.removingCodeStep("diagram", index = 0))
    }

    @Test
    fun elementStepsWalksADiagramLikeItWalksACodeBlock() {
        val steps: List<Build> = slide().elementSteps("diagram")

        // One click per step after the first: three steps, two builds.
        assertEquals(listOf(1, 2), steps.map { it.elementStep })
        assertEquals(listOf("diagram", "diagram"), steps.map { it.elementId })
        assertEquals(BuildKind.Action, steps.first().kind)
        assertEquals(CodeStepBuildDuration, steps.first().durationMs)
    }

    @Test
    fun aStepAddedToADiagramNotYetQueuedGetsNoClick() {
        val quiet: Slide = slide().copy(builds = listOf(Build("diagram")))
        val grown: Slide = quiet.addingDiagramStep("diagram", after = 2, step = DiagramStep())

        assertEquals(4, grown.chart().steps.size)
        assertEquals(listOf(null), grown.playedSteps())
    }

    @Test
    fun aStepPutAtTheFrontHandsItsClickToTheOneItPushedBack() {
        val grown: Slide = slide().addingDiagramStep("diagram", after = -1, step = DiagramStep())

        // Step 0 never takes a click: the old first step, now at 1, does.
        assertEquals(listOf(null, 1, 1, 2, 3), grown.playedSteps())
    }
}

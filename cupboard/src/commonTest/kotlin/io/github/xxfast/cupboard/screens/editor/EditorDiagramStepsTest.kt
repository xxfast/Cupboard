package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.DiagramElement
import io.github.xxfast.cupboard.document.DiagramStep
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [EditorCodeStepsTest] for a diagram: which row is being edited, what the
 * canvas previews for it, the four edits that shape the list, the builds that
 * follow them, and the promise that one of them is one undo.
 *
 * Slide "one" carries a diagram with three steps, a locked diagram that refuses
 * every edit, and a shape that has no steps at all. The build order is an entry
 * build for the diagram plus one build per step after the first, which is what
 * Add Step Builds would have written.
 */
class EditorDiagramStepsTest {
    private val steps: List<DiagramStep> = listOf(
        DiagramStep(reveal = listOf("A")),
        DiagramStep(reveal = listOf("A", "B")),
        DiagramStep(highlight = listOf("C")),
    )

    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                elements = listOf(
                    DiagramElement(
                        id = "diagram",
                        frame = Frame(0f, 0f, 400f, 200f),
                        source = "graph LR\nC --> A --> B\nA --> C",
                        steps = steps,
                    ),
                    DiagramElement(
                        id = "locked",
                        frame = Frame(0f, 220f, 400f, 200f),
                        source = "graph LR\nX --> Y",
                        steps = listOf(DiagramStep(), DiagramStep()),
                        locked = true,
                    ),
                    ShapeElement(id = "shape", frame = Frame(0f, 440f, 100f, 100f)),
                ),
                builds = listOf(
                    Build("diagram"),
                    Build("diagram", elementStep = 1),
                    Build("diagram", elementStep = 2),
                ),
            ),
            Slide(id = "two", title = "Two"),
        ),
    )

    private fun EditorState.chart(id: String = "diagram"): DiagramElement =
        selectedSlide.elements.first { it.id == id } as DiagramElement

    /** The steps the diagram's builds play, in build order. */
    private fun EditorState.playedSteps(): List<Int?> =
        selectedSlide.builds.filter { it.elementId == "diagram" }.map { it.elementStep }

    private suspend fun TestScope.selected(): EditorViewModel {
        val viewModel: EditorViewModel = editor(document())
        viewModel.onSelectElement("diagram")
        viewModel.await { it.selectedElementIds == listOf("diagram") }
        return viewModel
    }

    @Test
    fun aFreshSelectionIsOnNoStepAndShowsTheWholeDiagram() = runTest {
        val state: EditorState = selected().await { it.selectedElementIds == listOf("diagram") }

        assertNull(state.diagramStep)
        assertNull(state.selectedDiagramStep)
        assertNull(state.shownDiagramStep(state.chart()))
    }

    @Test
    fun theNodeIdsAreTheSelectedDiagramsInSourceOrder() = runTest {
        val viewModel: EditorViewModel = selected()

        val state: EditorState = viewModel.await { it.selectedElementIds == listOf("diagram") }
        assertEquals(listOf("C", "A", "B"), state.diagramNodeIds)

        // Nothing to offer once no diagram is selected.
        viewModel.onSelectElement("shape")
        assertEquals(emptyList(), viewModel.await { it.selectedElementIds == listOf("shape") }.diagramNodeIds)
    }

    @Test
    fun pickingAStepPreviewsItOnTheCanvasAndIsNotAnEdit() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectDiagramStep(2)
        val state: EditorState = viewModel.await { it.diagramStep == 2 }
        assertEquals(steps[2], state.selectedDiagramStep)
        assertEquals(steps[2], state.shownDiagramStep(state.chart()))
        // Only the selected diagram previews: the rest of the slide is at rest.
        assertNull(state.shownDiagramStep(state.chart("locked")))
        assertFalse(state.canUndo, "looking is not editing")

        // And null picks no row, which is the whole diagram again.
        viewModel.onSelectDiagramStep(null)
        val cleared: EditorState = viewModel.await { it.diagramStep == null }
        assertNull(cleared.selectedDiagramStep)
        assertNull(cleared.shownDiagramStep(cleared.chart()))
    }

    @Test
    fun theStepBeingEditedIsDroppedWhenTheSelectionMoves() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectDiagramStep(2)
        viewModel.await { it.diagramStep == 2 }
        viewModel.onSelectElement("shape")

        val state: EditorState = viewModel.await { it.selectedElementIds == listOf("shape") }
        assertNull(state.diagramStep)
    }

    @Test
    fun pickingAStepTheDiagramDoesNotHaveChangesNothing() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectDiagramStep(1)
        viewModel.await { it.diagramStep == 1 }
        viewModel.onSelectDiagramStep(9)
        // Something that does land, so there is a state to wait for.
        viewModel.onUpdateDiagramStep("diagram", 0, DiagramStep(highlight = listOf("A")))

        val state: EditorState = viewModel.await { it.chart().steps[0].highlight == listOf("A") }
        assertEquals(1, state.diagramStep, "the row that was picked stays picked")
    }

    @Test
    fun addingAStepCopiesTheOneBeingEditedAndPicksIt() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectDiagramStep(0)
        viewModel.await { it.diagramStep == 0 }
        viewModel.onAddDiagramStep("diagram")

        val state: EditorState = viewModel.await { it.chart().steps.size == 4 }
        assertEquals(steps[0], state.chart().steps[1], "a new state starts from the one before it")
        assertEquals(1, state.diagramStep)
        // The builds behind the insertion move up with the steps they play, and
        // the new step is queued between them.
        assertEquals(listOf(null, 1, 2, 3), state.playedSteps())
        assertTrue(state.canUndo)

        viewModel.onUndo()
        val undone: EditorState = viewModel.await { it.chart().steps.size == 3 }
        assertEquals(document(), undone.document, "one edit, one history entry")

        viewModel.onRedo()
        assertEquals(4, viewModel.await { it.chart().steps.size == 4 }.chart().steps.size)
    }

    @Test
    fun addingAStepWithNoRowPickedLandsAtTheEndAsTheWholeDiagram() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onAddDiagramStep("diagram")

        val state: EditorState = viewModel.await { it.chart().steps.size == 4 }
        assertEquals(DiagramStep(), state.chart().steps.last())
        assertEquals(3, state.diagramStep)
        assertEquals(listOf(null, 1, 2, 3), state.playedSteps())
    }

    @Test
    fun removingAStepDropsItsBuildAndKeepsTheRowPickedOnSomethingReal() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectDiagramStep(2)
        viewModel.await { it.diagramStep == 2 }
        viewModel.onRemoveDiagramStep("diagram", 2)

        val state: EditorState = viewModel.await { it.chart().steps.size == 2 }
        // The build that played the step that went goes with it.
        assertEquals(listOf(null, 1), state.playedSteps())
        assertEquals(1, state.diagramStep, "the last row picked clamps onto the last one left")
        assertEquals(steps[1], state.selectedDiagramStep)
        assertTrue(state.canUndo)

        viewModel.onUndo()
        assertEquals(document(), viewModel.await { it.chart().steps.size == 3 }.document)
    }

    @Test
    fun removingTheLastStepLeavesNoRowPicked() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onRemoveDiagramStep("diagram", 2)
        viewModel.await { it.chart().steps.size == 2 }
        viewModel.onSelectDiagramStep(0)
        viewModel.await { it.diagramStep == 0 }
        viewModel.onRemoveDiagramStep("diagram", 1)
        viewModel.await { it.chart().steps.size == 1 }
        viewModel.onRemoveDiagramStep("diagram", 0)

        val state: EditorState = viewModel.await { it.chart().steps.isEmpty() }
        assertNull(state.diagramStep)
        assertEquals(listOf(null), state.playedSteps(), "no steps, no step builds")
    }

    @Test
    fun movingAStepTakesItsBuildAndTheRowPickedWithIt() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectDiagramStep(2)
        viewModel.await { it.diagramStep == 2 }
        viewModel.onMoveDiagramStep("diagram", 2, 0)

        val state: EditorState = viewModel.await { it.chart().steps.first() == steps[2] }
        assertEquals(listOf(steps[2], steps[0], steps[1]), state.chart().steps)
        assertEquals(0, state.diagramStep, "the row follows the step it was on")
        // The build that played the last step now plays the first.
        assertEquals(listOf(null, 2, 0), state.playedSteps())
        assertTrue(state.canUndo)

        viewModel.onUndo()
        assertEquals(document(), viewModel.await { it.chart().steps.first() == steps[0] }.document)
    }

    @Test
    fun updatingTheRowBeingEditedIsPreviewedAndKeepsIdsNoNodeCarries() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectDiagramStep(0)
        viewModel.await { it.diagramStep == 0 }

        // `Ser` is a half-typed id: kept as typed, ignored only when drawn.
        val edited = DiagramStep(reveal = listOf("A", "Ser"), highlight = listOf("A"))
        viewModel.onUpdateDiagramStep("diagram", 0, edited)
        val state: EditorState = viewModel.await { it.chart().steps[0] == edited }
        assertEquals(edited, state.selectedDiagramStep)
        assertEquals(edited, state.shownDiagramStep(state.chart()))
        // Steps are not builds: nothing in the build order moved.
        assertEquals(listOf(null, 1, 2), state.playedSteps())

        viewModel.onUpdateDiagramStep("diagram", 1, DiagramStep())
        viewModel.await { it.chart().steps[1] == DiagramStep() }
        viewModel.onUndo()
        viewModel.await { it.chart().steps[1] == steps[1] }
        viewModel.onUndo()
        assertEquals(
            document(),
            viewModel.await { it.chart().steps[0] == steps[0] }.document,
            "two edits, two history entries",
        )
    }

    @Test
    fun staleEventsChangeNothingAndCostNoHistory() = runTest {
        val viewModel: EditorViewModel = selected()

        // An id nothing answers to, a kind with no steps, and a locked diagram.
        viewModel.onAddDiagramStep("nobody")
        viewModel.onAddDiagramStep("shape")
        viewModel.onAddDiagramStep("locked")
        // Indices the diagram has no step at, and a move that lands where it started.
        viewModel.onRemoveDiagramStep("diagram", 9)
        viewModel.onMoveDiagramStep("diagram", 0, 0)
        viewModel.onMoveDiagramStep("diagram", 0, 7)
        viewModel.onUpdateDiagramStep("diagram", 9, DiagramStep())
        // And a step that already says exactly this.
        viewModel.onUpdateDiagramStep("diagram", 0, steps[0])

        // Something that does land, so there is a state to wait for.
        viewModel.onSelectDiagramStep(1)
        val state: EditorState = viewModel.await { it.diagramStep == 1 }
        assertEquals(document(), state.document)
        assertFalse(state.canUndo)
    }

    @Test
    fun addElementStepsWritesADiagramsWalk() = runTest {
        val start: Document = document().let { document ->
            val slide: Slide = document.slides.first()
            document.copy(slides = listOf(slide.copy(builds = listOf(Build("diagram"))), document.slides.last()))
        }
        val viewModel: EditorViewModel = editor(start)

        viewModel.onAddElementSteps("diagram")

        val state: EditorState = viewModel.await { it.selectedSlide.builds.size == 3 }
        assertEquals(listOf(null, 1, 2), state.playedSteps())
        assertTrue(state.canUndo)
    }
}

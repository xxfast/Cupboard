package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.LineRange
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
 * The steps of a code block, from the editor's side: which row is being edited,
 * the four edits that shape the list, the builds that follow them, and the
 * promise that one of them is one undo.
 *
 * Slide "one" carries a block with two versions and three steps, a locked block
 * that refuses every edit, and a shape that has no steps at all. The build order
 * is an entry build for the block plus one build per step after the first, which
 * is what Add Slide Steps would have written.
 */
class EditorCodeStepsTest {
    private val steps: List<CodeStep> = listOf(
        CodeStep(reveal = listOf(LineRange(1, 1))),
        CodeStep(reveal = listOf(LineRange(1, 2))),
        CodeStep(version = 1),
    )

    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                elements = listOf(
                    CodeElement(
                        id = "code",
                        frame = Frame(0f, 0f, 400f, 200f),
                        code = "one",
                        versions = listOf("two"),
                        steps = steps,
                    ),
                    CodeElement(
                        id = "locked",
                        frame = Frame(0f, 220f, 400f, 200f),
                        code = "nope",
                        steps = listOf(CodeStep(), CodeStep()),
                        locked = true,
                    ),
                    ShapeElement(id = "shape", frame = Frame(0f, 440f, 100f, 100f)),
                ),
                builds = listOf(
                    Build("code"),
                    Build("code", elementStep = 1),
                    Build("code", elementStep = 2),
                ),
            ),
            Slide(id = "two", title = "Two"),
        ),
    )

    private fun EditorState.block(id: String = "code"): CodeElement =
        selectedSlide.elements.first { it.id == id } as CodeElement

    /** The steps the block's builds play, in build order. */
    private fun EditorState.playedSteps(): List<Int?> =
        selectedSlide.builds.filter { it.elementId == "code" }.map { it.elementStep }

    private suspend fun TestScope.selected(): EditorViewModel {
        val viewModel: EditorViewModel = editor(document())
        viewModel.onSelectElement("code")
        viewModel.await { it.selectedElementIds == listOf("code") }
        return viewModel
    }

    @Test
    fun aFreshSelectionIsOnNoStepAtAll() = runTest {
        val state: EditorState = selected().await { it.selectedElementIds == listOf("code") }

        assertNull(state.codeStep)
        assertNull(state.selectedCodeStep)
    }

    @Test
    fun pickingAStepShowsTheVersionItPlaysAndIsNotAnEdit() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectCodeStep(2)
        val state: EditorState = viewModel.await { it.codeStep == 2 }
        assertEquals(steps[2], state.selectedCodeStep)
        // Step 2 is written against version 1, so that is what the canvas shows.
        assertEquals(1, state.codeVersion)
        assertEquals("two", state.shownSource(state.block()))
        assertFalse(state.canUndo, "looking is not editing")

        // And null picks no row, leaving the version where it was.
        viewModel.onSelectCodeStep(null)
        val cleared: EditorState = viewModel.await { it.codeStep == null }
        assertNull(cleared.selectedCodeStep)
        assertEquals(1, cleared.codeVersion)
    }

    @Test
    fun pickingAStepTheBlockDoesNotHaveChangesNothing() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectCodeStep(1)
        viewModel.await { it.codeStep == 1 }
        viewModel.onSelectCodeStep(9)
        viewModel.onSelectCodeVersion(0)

        val state: EditorState = viewModel.await { it.codeVersion == 0 }
        assertEquals(1, state.codeStep, "the row that was picked stays picked")
    }

    @Test
    fun theStepBeingEditedIsDroppedWhenTheSelectionMoves() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectCodeStep(2)
        viewModel.await { it.codeStep == 2 }
        viewModel.onSelectElement("shape")

        val state: EditorState = viewModel.await { it.selectedElementIds == listOf("shape") }
        assertNull(state.codeStep)
        assertEquals(0, state.codeVersion)
    }

    @Test
    fun addingAStepCopiesTheOneBeingEditedAndPicksIt() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectCodeStep(0)
        viewModel.await { it.codeStep == 0 }
        viewModel.onAddCodeStep("code")

        val state: EditorState = viewModel.await { it.block().steps.size == 4 }
        assertEquals(steps[0], state.block().steps[1], "a new state starts from the one before it")
        assertEquals(1, state.codeStep)
        // The builds behind the insertion move up with the steps they play, and
        // the new step is queued between them.
        assertEquals(listOf(null, 1, 2, 3), state.playedSteps())
        assertTrue(state.canUndo)

        viewModel.onUndo()
        val undone: EditorState = viewModel.await { it.block().steps.size == 3 }
        assertEquals(document(), undone.document, "one edit, one history entry")

        viewModel.onRedo()
        assertEquals(4, viewModel.await { it.block().steps.size == 4 }.block().steps.size)
    }

    @Test
    fun addingAStepWithNoRowPickedLandsAtTheEndOnTheVersionShowing() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectCodeVersion(1)
        viewModel.await { it.codeVersion == 1 }
        viewModel.onAddCodeStep("code")

        val state: EditorState = viewModel.await { it.block().steps.size == 4 }
        assertEquals(CodeStep(version = 1), state.block().steps.last())
        assertEquals(3, state.codeStep)
        // Nothing sat at or behind the end, so no build moved; the walk was
        // queued, so the new step is too.
        assertEquals(listOf(null, 1, 2, 3), state.playedSteps())
    }

    @Test
    fun addingAStepTakesTheVersionShowingNotTheOneOnTheRowPicked() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectCodeStep(0)
        viewModel.await { it.codeStep == 0 }
        // Versions + would do this: the canvas moves on, the picked row stays.
        viewModel.onSelectCodeVersion(1)
        viewModel.await { it.codeVersion == 1 }
        viewModel.onAddCodeStep("code")

        val state: EditorState = viewModel.await { it.block().steps.size == 4 }
        assertEquals(steps[0].copy(version = 1), state.block().steps[1])
    }

    @Test
    fun removingAStepDropsItsBuildAndKeepsTheRowPickedOnSomethingReal() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectCodeStep(2)
        viewModel.await { it.codeStep == 2 }
        viewModel.onRemoveCodeStep("code", 2)

        val state: EditorState = viewModel.await { it.block().steps.size == 2 }
        // The build that played the step that went goes with it.
        assertEquals(listOf(null, 1), state.playedSteps())
        assertEquals(1, state.codeStep, "the last row picked clamps onto the last one left")
        assertEquals(steps[1], state.selectedCodeStep)
        assertTrue(state.canUndo)

        viewModel.onUndo()
        assertEquals(document(), viewModel.await { it.block().steps.size == 3 }.document)
    }

    @Test
    fun removingTheLastStepLeavesNoRowPicked() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onRemoveCodeStep("code", 2)
        viewModel.await { it.block().steps.size == 2 }
        viewModel.onSelectCodeStep(0)
        viewModel.await { it.codeStep == 0 }
        viewModel.onRemoveCodeStep("code", 1)
        viewModel.await { it.block().steps.size == 1 }
        viewModel.onRemoveCodeStep("code", 0)

        val state: EditorState = viewModel.await { it.block().steps.isEmpty() }
        assertNull(state.codeStep)
        assertEquals(listOf(null), state.playedSteps(), "no steps, no step builds")
    }

    @Test
    fun movingAStepTakesItsBuildAndTheRowPickedWithIt() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectCodeStep(2)
        viewModel.await { it.codeStep == 2 }
        viewModel.onMoveCodeStep("code", 2, 0)

        val state: EditorState = viewModel.await { it.block().steps.first() == steps[2] }
        assertEquals(listOf(steps[2], steps[0], steps[1]), state.block().steps)
        assertEquals(0, state.codeStep, "the row follows the step it was on")
        // The build that played the last step now plays the first.
        assertEquals(listOf(null, 2, 0), state.playedSteps())
        assertTrue(state.canUndo)

        viewModel.onUndo()
        assertEquals(document(), viewModel.await { it.block().steps.first() == steps[0] }.document)
    }

    @Test
    fun updatingAStepWritesItAndFollowsItsVersionOnlyForTheRowBeingEdited() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onSelectCodeStep(0)
        viewModel.await { it.codeStep == 0 }

        // A row nobody is looking at: written, but the canvas stays put.
        viewModel.onUpdateCodeStep("code", 1, CodeStep(version = 1))
        val other: EditorState = viewModel.await { it.block().steps[1].version == 1 }
        assertEquals(0, other.codeVersion)

        // The row being edited: written, and the canvas follows it.
        val edited = CodeStep(highlight = listOf(LineRange(2, 2)), version = 1)
        viewModel.onUpdateCodeStep("code", 0, edited)
        val state: EditorState = viewModel.await { it.codeVersion == 1 }
        assertEquals(edited, state.selectedCodeStep)
        assertEquals("two", state.shownSource(state.block()))
        // Steps are not builds: nothing in the build order moved.
        assertEquals(listOf(null, 1, 2), state.playedSteps())

        viewModel.onUndo()
        viewModel.await { it.block().steps[0] == steps[0] }
        viewModel.onUndo()
        assertEquals(
            document(),
            viewModel.await { it.block().steps[1] == steps[1] }.document,
            "two edits, two history entries",
        )
    }

    @Test
    fun aStepPointingPastTheVersionsTheBlockHasLandsOnTheLastOneItDoes() = runTest {
        val viewModel: EditorViewModel = selected()

        viewModel.onUpdateCodeStep("code", 0, CodeStep(version = 9))

        val state: EditorState = viewModel.await { it.block().steps[0].version == 1 }
        assertEquals(1, state.block().steps[0].version)
    }

    @Test
    fun staleEventsChangeNothingAndCostNoHistory() = runTest {
        val viewModel: EditorViewModel = selected()

        // An id nothing answers to, a kind with no steps, and a locked block.
        viewModel.onAddCodeStep("nobody")
        viewModel.onAddCodeStep("shape")
        viewModel.onAddCodeStep("locked")
        // Indices the block has no step at, and a move that lands where it started.
        viewModel.onRemoveCodeStep("code", 9)
        viewModel.onMoveCodeStep("code", 0, 0)
        viewModel.onMoveCodeStep("code", 0, 7)
        // And a step that already says exactly this.
        viewModel.onUpdateCodeStep("code", 0, steps[0])

        // Something that does land, so there is a state to wait for.
        viewModel.onSelectCodeStep(1)
        val state: EditorState = viewModel.await { it.codeStep == 1 }
        assertEquals(document(), state.document)
        assertFalse(state.canUndo)
    }

    @Test
    fun addElementStepsWritesACodeBlocksWalkAndLeavesOtherElementsAlone() = runTest {
        val extra = Build("shape")
        val start: Document = document().let { document ->
            val slide: Slide = document.slides.first()
            document.copy(
                slides = listOf(
                    // A stale step build the block no longer has a state for,
                    // plus a build for another element that must survive.
                    slide.copy(builds = slide.builds + Build("code", elementStep = 7) + extra),
                    document.slides.last(),
                ),
            )
        }
        val viewModel: EditorViewModel = editor(start)

        viewModel.onAddElementSteps("code")

        val state: EditorState = viewModel.await { it.selectedSlide.builds.size == 4 }
        assertEquals(listOf(null, 1, 2), state.playedSteps(), "one set of steps, not two")
        assertTrue(extra in state.selectedSlide.builds)
        assertTrue(state.canUndo)

        viewModel.onUndo()
        assertEquals(start, viewModel.await { it.selectedSlide.builds.size == 5 }.document)
    }
}

package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.BuildDelivery
import io.github.xxfast.cupboard.document.BuildEffect
import io.github.xxfast.cupboard.document.BuildKind
import io.github.xxfast.cupboard.document.BuildTrigger
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The slide's build order, as the build list the Animate panel edits. */
class EditorBuildsTest {
    private val frame = Frame(0f, 0f, 100f, 40f)

    private fun document(vararg builds: Build): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                elements = listOf(
                    TextElement(id = "a", frame = frame, text = "A"),
                    TextElement(id = "b", frame = frame, text = "B"),
                    GroupElement(
                        id = "g",
                        frame = frame,
                        children = listOf(TextElement(id = "nested", frame = frame, text = "N")),
                    ),
                ),
                builds = builds.toList(),
            ),
        ),
    )

    private fun EditorState.builds(): List<Build> = selectedSlide.builds

    @Test
    fun addingABuildAppendsItAndCostsOneHistoryEntry() = runTest {
        val viewModel = editor(document(Build("a")))
        val added = Build("b", effect = BuildEffect.Pop, trigger = BuildTrigger.AfterPrevious)

        viewModel.onAddBuild(added)
        val state = viewModel.await { it.selectedSlide.builds.size == 2 }
        assertEquals(listOf(Build("a"), added), state.builds())
        assertTrue(state.canUndo)

        viewModel.onUndo()
        val undone = viewModel.await { it.selectedSlide.builds.size == 1 }
        assertEquals(document(Build("a")), undone.document)
        assertFalse(undone.canUndo)
    }

    @Test
    fun aBuildForAnElementTheSlideDoesntHoldIsANoOp() = runTest {
        val viewModel = editor(document(Build("a")))

        viewModel.onAddBuild(Build("nobody"))
        // A grouped element is on the slide, so a build may name one.
        viewModel.onAddBuild(Build("nested"))
        val state = viewModel.await { it.selectedSlide.builds.size == 2 }
        assertEquals(listOf("a", "nested"), state.builds().map { it.elementId })
        // One edit, one entry: the turned-away build made none.
        viewModel.onUndo()
        val undone = viewModel.await { it.selectedSlide.builds.size == 1 }
        assertFalse(undone.canUndo)
    }

    @Test
    fun updatingWritesTheBuildAtThatIndex() = runTest {
        val viewModel = editor(document(Build("a"), Build("b")))
        val changed = Build("b", delivery = BuildDelivery.ByWord, delayMs = 250)

        viewModel.onUpdateBuild(1, changed)
        val state = viewModel.await { it.selectedSlide.builds[1] == changed }
        assertEquals(listOf(Build("a"), changed), state.builds())

        viewModel.onUndo()
        val undone = viewModel.await { it.selectedSlide.builds[1] == Build("b") }
        assertEquals(document(Build("a"), Build("b")), undone.document)
    }

    @Test
    fun anUpdateThatChangesNothingIsANoOp() = runTest {
        val viewModel = editor(document(Build("a")))

        viewModel.onUpdateBuild(0, Build("a"))
        viewModel.onUpdateBuild(7, Build("a", kind = BuildKind.Out))
        viewModel.onUpdateBuild(0, Build("nobody"))
        // Nothing above may land, so the one that follows is the first edit.
        viewModel.onAddBuild(Build("b"))
        val state = viewModel.await { it.selectedSlide.builds.size == 2 }
        assertEquals(listOf(Build("a"), Build("b")), state.builds())

        viewModel.onUndo()
        val undone = viewModel.await { it.selectedSlide.builds.size == 1 }
        assertFalse(undone.canUndo)
    }

    @Test
    fun removingDropsThatBuildAlone() = runTest {
        val viewModel = editor(document(Build("a"), Build("b")))

        viewModel.onRemoveBuild(5)
        viewModel.onRemoveBuild(0)
        val state = viewModel.await { it.selectedSlide.builds.size == 1 }
        assertEquals(listOf(Build("b")), state.builds())
        // The element stays on the slide; only what animates it goes.
        assertEquals(3, state.selectedSlide.elements.size)

        viewModel.onUndo()
        val undone = viewModel.await { it.selectedSlide.builds.size == 2 }
        assertEquals(document(Build("a"), Build("b")), undone.document)
        assertFalse(undone.canUndo)
    }

    @Test
    fun movingReordersTheBuildsAndSoTheSteps() = runTest {
        val viewModel = editor(document(Build("a"), Build("b"), Build("nested")))

        viewModel.onMoveBuild(2, 0)
        val state = viewModel.await { it.selectedSlide.builds.first().elementId == "nested" }
        assertEquals(listOf("nested", "a", "b"), state.builds().map { it.elementId })

        viewModel.onMoveBuild(0, 0)
        viewModel.onMoveBuild(1, 9)
        viewModel.onUndo()
        val undone = viewModel.await { it.selectedSlide.builds.first().elementId == "a" }
        assertEquals(document(Build("a"), Build("b"), Build("nested")), undone.document)
        assertFalse(undone.canUndo)
    }
}

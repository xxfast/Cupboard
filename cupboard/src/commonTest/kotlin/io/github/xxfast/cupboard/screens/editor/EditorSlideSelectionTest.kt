package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Several slides selected at once, the way Keynote's navigator gathers them:
 * shift and command clicks, the arrow keys, and the slide verbs taking the lot.
 * The deck nests a run under "Two" so ranges and indents have a group to cross.
 */
class EditorSlideSelectionTest {
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(id = "one", title = "One"),
            Slide(id = "two", title = "Two"),
            Slide(id = "two-a", title = "Two A", depth = 1),
            Slide(id = "two-b", title = "Two B", depth = 1),
            Slide(id = "three", title = "Three"),
        ),
    )

    private fun EditorState.slideIds(): List<String> = document.slides.map { it.id }

    @Test
    fun aShiftClickSelectsTheRangeAndKeepsTheSlideOnTheCanvas() = runTest {
        val viewModel = editor(document())

        viewModel.onSelectSlide("two")
        viewModel.onExtendSlideSelection("three")
        val ranged = viewModel.await { it.alsoSelectedSlideIds.isNotEmpty() }
        assertEquals("two", ranged.selectedSlideId)
        assertEquals(listOf("two", "two-a", "two-b", "three"), ranged.selectedSlideIds)

        // Re-cut from the same end, not grown from the last click.
        viewModel.onExtendSlideSelection("one")
        val recut = viewModel.await { "one" in it.alsoSelectedSlideIds }
        assertEquals(listOf("one", "two"), recut.selectedSlideIds)
    }

    @Test
    fun aCommandClickTogglesAndAPlainClickCollapsesTheSelection() = runTest {
        val viewModel = editor(document())

        viewModel.onSelectSlide("one")
        viewModel.onToggleSlideSelection("three")
        val added = viewModel.await { "three" in it.alsoSelectedSlideIds }
        assertEquals(listOf("one", "three"), added.selectedSlideIds)

        // The selected slide out: the canvas goes to the one left.
        viewModel.onToggleSlideSelection("one")
        val handed = viewModel.await { it.selectedSlideId == "three" }
        assertEquals(listOf("three"), handed.selectedSlideIds)

        viewModel.onToggleSlideSelection("one")
        viewModel.await { "one" in it.alsoSelectedSlideIds }
        viewModel.onSelectSlide("three")
        val collapsed = viewModel.await { it.alsoSelectedSlideIds.isEmpty() }
        assertEquals(listOf("three"), collapsed.selectedSlideIds)
    }

    @Test
    fun shiftArrowsGrowAndShrinkFromTheSelectedSlide() = runTest {
        val viewModel = editor(document())

        viewModel.onSelectSlide("two")
        viewModel.onStepSlideSelection(1, extend = true)
        viewModel.onStepSlideSelection(1, extend = true)
        val grown = viewModel.await { it.alsoSelectedSlideIds.size == 2 }
        assertEquals(listOf("two", "two-a", "two-b"), grown.selectedSlideIds)

        viewModel.onStepSlideSelection(-1, extend = true)
        val shrunk = viewModel.await { it.alsoSelectedSlideIds.size == 1 }
        assertEquals(listOf("two", "two-a"), shrunk.selectedSlideIds)

        // Plain up leaves from the top of the selection.
        viewModel.onStepSlideSelection(-1, extend = false)
        val stepped = viewModel.await { it.selectedSlideId == "one" }
        assertTrue(stepped.alsoSelectedSlideIds.isEmpty())
    }

    @Test
    fun arrowsWalkVisibleRowsOnly() = runTest {
        val viewModel = editor(document())

        viewModel.onToggleCollapsed("two")
        viewModel.onSelectSlide("two")
        viewModel.onStepSlideSelection(1, extend = false)
        val stepped = viewModel.await { it.selectedSlideId != "two" }
        assertEquals("three", stepped.selectedSlideId)
    }

    @Test
    fun deletingASelectionIsOneHistoryEntry() = runTest {
        val viewModel = editor(document())

        viewModel.onSelectSlide("one")
        viewModel.onToggleSlideSelection("three")
        viewModel.await { "three" in it.alsoSelectedSlideIds }
        viewModel.onDelete()
        val deleted = viewModel.await { it.document.slides.size == 3 }
        assertEquals(listOf("two", "two-a", "two-b"), deleted.slideIds())
        assertTrue(deleted.alsoSelectedSlideIds.isEmpty())

        viewModel.onUndo()
        val undone = viewModel.await { it.document.slides.size == 5 }
        assertEquals(document(), undone.document)
        assertFalse(undone.canUndo)
    }

    @Test
    fun aVerbOnARowOutsideTheSelectionTakesThatRowAlone() = runTest {
        val viewModel = editor(document())

        viewModel.onSelectSlide("one")
        viewModel.onToggleSlideSelection("three")
        viewModel.await { "three" in it.alsoSelectedSlideIds }
        viewModel.onDeleteSlide("two-a")
        val deleted = viewModel.await { it.document.slides.size == 4 }
        assertEquals(listOf("one", "two", "two-b", "three"), deleted.slideIds())
    }

    @Test
    fun draggingASelectionLandsItInOrder() = runTest {
        val viewModel = editor(document())

        viewModel.onSelectSlide("one")
        viewModel.onToggleSlideSelection("two-b")
        viewModel.await { "two-b" in it.alsoSelectedSlideIds }
        viewModel.onMoveSlide("two-b", "three")
        val moved = viewModel.await { it.document.slides.last().id == "two-b" }
        assertEquals(listOf("two", "two-a", "three", "one", "two-b"), moved.slideIds())
        assertEquals(listOf("one", "two-b"), moved.selectedSlideIds)
        assertEquals("one", moved.selectedSlideId)

        viewModel.onUndo()
        val undone = viewModel.await { it.document.slides.first().id == "one" }
        assertEquals(document(), undone.document)
    }

    @Test
    fun tabIndentsTheSelectionWithItsRunAndStopsUnderTheSlideAbove() = runTest {
        val viewModel = editor(document())

        viewModel.onSelectSlide("two")
        viewModel.onIndentSlides(1)
        val indented = viewModel.await { it.canUndo }
        assertEquals(listOf(0, 1, 2, 2, 0), indented.document.slides.map { it.depth })

        // Already one under "one": nowhere further in, so no second entry.
        viewModel.onIndentSlides(1)
        viewModel.onIndentSlides(-1)
        val outdented = viewModel.await { it.document.slides[1].depth == 0 }
        assertEquals(document(), outdented.document)

        viewModel.onUndo()
        viewModel.onUndo()
        val undone = viewModel.await { !it.canUndo }
        assertEquals(document(), undone.document)
    }

    @Test
    fun copyingASelectionPastesItAll() = runTest {
        val viewModel = editor(document())

        viewModel.onSelectSlide("one")
        viewModel.onToggleSlideSelection("three")
        viewModel.await { "three" in it.alsoSelectedSlideIds }
        viewModel.onCopy()
        viewModel.onPaste()
        val pasted = viewModel.await { it.document.slides.size == 7 }
        assertEquals(listOf("One", "One", "Three"), pasted.document.slides.take(3).map { it.title })
    }

    @Test
    fun aDropAsksForALevelAndTheGapClampsIt() = runTest {
        val viewModel = editor(document())

        // After the last child: the top level is open, since "three" below is there.
        viewModel.onMoveSlideTo("one", "two-b", depth = 0)
        val out = viewModel.await { it.document.slides[3].id == "one" }
        assertEquals(listOf(0, 1, 1, 0, 0), out.document.slides.map { it.depth })
        viewModel.onUndo()
        viewModel.await { !it.canUndo }

        // Between a parent and its first child there is one level to land at.
        viewModel.onMoveSlideTo("three", "two", depth = 0)
        val joined = viewModel.await { it.document.slides[2].id == "three" }
        assertEquals(listOf(0, 0, 1, 1, 1), joined.document.slides.map { it.depth })
    }
}

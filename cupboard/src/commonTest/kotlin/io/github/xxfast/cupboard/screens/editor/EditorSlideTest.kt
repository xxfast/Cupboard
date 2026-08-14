package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Adding slides, and the insertion rule every "after this slide" edit shares:
 * past the anchor's deeper run, so a parent is never split from its children.
 * The deck nests a run under "Two" to give the rule something to step over.
 */
class EditorSlideTest {
    // Fixed ids, so a freshly built document compares equal to the opened one.
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

    private fun EditorState.depths(): List<Int> = document.slides.map { it.depth }

    @Test
    fun addingMakesABlankSelectedSiblingAfterTheAnchor() = runTest {
        val viewModel = editor(document())

        viewModel.onAddSlide("one")
        val added = viewModel.await { it.document.slides.size == 6 }
        val fresh: Slide = added.document.slides[1]
        assertEquals(listOf("one", fresh.id, "two", "two-a", "two-b", "three"), added.slideIds())
        assertTrue(fresh.elements.isEmpty())
        assertEquals(0, fresh.depth)
        assertEquals(fresh.id, added.selectedSlideId)
        assertTrue(added.canUndo)
    }

    @Test
    fun addingAfterAParentLandsPastItsChildren() = runTest {
        val viewModel = editor(document())

        viewModel.onAddSlide("two")
        val added = viewModel.await { it.document.slides.size == 6 }
        val fresh: Slide = added.document.slides[4]
        assertEquals(listOf("one", "two", "two-a", "two-b", fresh.id, "three"), added.slideIds())
        // The children still follow "Two", and the blank is its sibling.
        assertEquals(listOf(0, 0, 1, 1, 0, 0), added.depths())
    }

    @Test
    fun addingAfterAChildMakesASibling() = runTest {
        val viewModel = editor(document())

        viewModel.onAddSlide("two-a")
        val added = viewModel.await { it.document.slides.size == 6 }
        val fresh: Slide = added.document.slides[3]
        assertEquals(listOf("one", "two", "two-a", fresh.id, "two-b", "three"), added.slideIds())
        assertEquals(1, fresh.depth)
    }

    @Test
    fun undoingAnAddReanchorsTheSelectionToTheGap() = runTest {
        val viewModel = editor(document())
        val opened = viewModel.await { it.document.slides.size == 5 }

        viewModel.onAddSlide("one")
        viewModel.await { it.document.slides.size == 6 }

        viewModel.onUndo()
        val undone = viewModel.await { it.document.slides.size == 5 }
        assertEquals(opened.document, undone.document)
        // The blank's id no longer resolves; the selection lands where it sat.
        assertEquals("two", undone.selectedSlideId)
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)
    }

    @Test
    fun addingAfterAnUnknownSlideAddsNothing() = runTest {
        val viewModel = editor(document())

        viewModel.onAddSlide("nowhere")
        viewModel.onSelectSlide("three")
        val settled = viewModel.await { it.selectedSlideId == "three" }
        assertEquals(document(), settled.document)
        assertFalse(settled.canUndo)
    }

    @Test
    fun duplicatingAParentLeavesItsChildrenWithTheOriginal() = runTest {
        val viewModel = editor(document())

        viewModel.onDuplicateSlide("two")
        val duplicated = viewModel.await { it.document.slides.size == 6 }
        val copy: Slide = duplicated.document.slides[4]
        assertEquals(
            listOf("one", "two", "two-a", "two-b", copy.id, "three"),
            duplicated.slideIds(),
        )
        assertEquals(listOf(0, 0, 1, 1, 0, 0), duplicated.depths())
        assertEquals(copy.id, duplicated.selectedSlideId)
    }

    @Test
    fun pastingAfterAParentLeavesItsChildrenWithTheOriginal() = runTest {
        val viewModel = editor(document())

        viewModel.onCopySlide("three")
        viewModel.await { it.canPaste }
        viewModel.onSelectSlide("two")
        viewModel.await { it.selectedSlideId == "two" }

        viewModel.onPaste()
        val pasted = viewModel.await { it.document.slides.size == 6 }
        val copy: Slide = pasted.document.slides[4]
        assertEquals(listOf("one", "two", "two-a", "two-b", copy.id, "three"), pasted.slideIds())
        assertEquals("Three", copy.title)
        assertEquals(copy.id, pasted.selectedSlideId)
    }
}

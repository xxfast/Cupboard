package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.sampleDocument
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorViewModelTest {
    @Test
    fun opensOnTheFirstSlideWithContent() = runTest {
        val state = editor().states.value
        assertTrue(state.selectedSlide.elements.isNotEmpty())
        assertEquals(
            sampleDocument().slides.first { it.elements.isNotEmpty() }.title,
            state.selectedSlide.title,
        )
        assertNull(state.selectedElementId)
    }

    @Test
    fun selectSlideAtPicksByPresentationOrder() = runTest {
        val viewModel = editor()
        viewModel.onSelectSlideAt(4)
        val state = viewModel.await { it.selectedSlideIndex() == 4 }
        assertEquals(state.document.allSlides()[4].id, state.selectedSlideId)

        // Out of range is a no-op, not a crash.
        viewModel.onSelectSlideAt(99)
        assertEquals(4, viewModel.states.value.selectedSlideIndex())
    }

    @Test
    fun selectingASlideClearsTheElementSelection() = runTest {
        val viewModel = editor()
        val elementId = viewModel.states.value.selectedSlide.elements.first().id
        viewModel.onSelectElement(elementId)
        viewModel.await { it.selectedElementId == elementId }

        viewModel.onSelectSlideAt(1)
        val state = viewModel.await { it.selectedSlideIndex() == 1 }
        assertNull(state.selectedElementId)
    }

    @Test
    fun updateSlideReplacesItInTheDocument() = runTest {
        val viewModel = editor()
        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "Renamed"))
        val state = viewModel.await { it.document.allSlides().any { slide -> slide.title == "Renamed" } }
        assertEquals("Renamed", state.selectedSlide.title)
    }

    @Test
    fun toggleCollapsedHidesTheDeeperRunOfASlide() = runTest {
        val viewModel = editor()
        val slides = viewModel.states.value.document.slides
        assertEquals(slides.size, viewModel.states.value.outline().size)

        val whyKmp = slides.first { it.title == "Why KMP" }
        viewModel.onToggleCollapsed(whyKmp.id)
        val collapsed = viewModel.await { it.outline().first { row -> row.slideId == whyKmp.id }.collapsed }
        val entry = collapsed.outline().first { it.slideId == whyKmp.id }
        assertTrue(entry.hasChildren)
        // Numbering is absolute, so the visible rows keep their original indices.
        assertEquals(listOf(0, 1, 2, 6, 7, 8), collapsed.outline().map { it.slideIndex })

        viewModel.onToggleCollapsed(whyKmp.id)
        val expanded = viewModel.await { it.outline().size == slides.size }
        assertFalse(expanded.outline().first { it.slideId == whyKmp.id }.collapsed)
    }

    @Test
    fun aRejectedEventProducesNoNewState() = runTest {
        val viewModel = editor()
        val seen = mutableListOf<EditorState>()
        backgroundScope.launch(Dispatchers.Unconfined, start = UNDISPATCHED) {
            viewModel.states.collect { seen += it }
        }
        val opened = seen.size

        // Out of range: nothing to select, so no state comes out of it.
        viewModel.onSelectSlideAt(-1)
        // A valid event behind it. Events are ordered, so once this one lands
        // the rejected one has been through the presenter too and the count
        // below is not racing it.
        viewModel.onSelectSlideAt(2)
        viewModel.await { it.selectedSlideIndex() == 2 }

        assertEquals(opened + 1, seen.size)
    }

    @Test
    fun outlineCarriesDepthAndTitle() {
        val state = EditorState.opening(
            Document(slides = listOf(Slide(title = "A"), Slide(title = "A.1", depth = 1))),
        )
        val entries = state.outline()
        assertEquals(listOf("A", "A.1"), entries.map { it.title })
        assertEquals(listOf(0, 1), entries.map { it.depth })
        assertTrue(entries[0].hasChildren)
        assertFalse(entries[1].hasChildren)
    }
}

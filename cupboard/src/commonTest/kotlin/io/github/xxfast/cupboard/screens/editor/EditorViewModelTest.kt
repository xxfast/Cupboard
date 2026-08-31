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
        assertEquals(emptyList(), state.selectedElementIds)
        assertNull(state.primaryElement)
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
        viewModel.await { it.selectedElementIds == listOf(elementId) }

        viewModel.onSelectSlideAt(1)
        val state = viewModel.await { it.selectedSlideIndex() == 1 }
        assertEquals(emptyList(), state.selectedElementIds)
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
    fun theChromeStartsFullyOpen() = runTest {
        val state = editor().states.value
        assertTrue(state.sidebarOpen)
        assertTrue(state.inspectorOpen)
        assertTrue(state.showNotes)
        assertEquals(InspectorTab.Format, state.inspectorTab)
    }

    @Test
    fun togglingTheSidebarAndNotesFlipsThemBackAndForth() = runTest {
        val viewModel = editor()

        viewModel.onToggleSidebar()
        viewModel.onToggleNotes()
        val hidden = viewModel.await { !it.sidebarOpen && !it.showNotes }
        // The other panels are left alone.
        assertTrue(hidden.inspectorOpen)

        viewModel.onToggleSidebar()
        viewModel.onToggleNotes()
        viewModel.await { it.sidebarOpen && it.showNotes }
    }

    @Test
    fun pickingATabOpensTheInspectorOnIt() = runTest {
        val viewModel = editor()
        viewModel.onCloseInspector()
        val closed = viewModel.await { !it.inspectorOpen }
        // Closing leaves the tab where it was, so reopening lands on it again.
        assertEquals(InspectorTab.Format, closed.inspectorTab)

        viewModel.onSelectInspectorTab(InspectorTab.Animate)
        val opened = viewModel.await { it.inspectorTab == InspectorTab.Animate }
        assertTrue(opened.inspectorOpen)

        // Picking the tab that's already showing keeps it open: closing on a
        // second click is the shell's policy, and it sends CloseInspector for it.
        viewModel.onSelectInspectorTab(InspectorTab.Animate)
        viewModel.onSelectSlideAt(2)
        val again = viewModel.await { it.selectedSlideIndex() == 2 }
        assertTrue(again.inspectorOpen)
        assertEquals(InspectorTab.Animate, again.inspectorTab)
    }

    @Test
    fun togglingTheInspectorKeepsTheTabItWasOn() = runTest {
        val viewModel = editor()
        viewModel.onSelectInspectorTab(InspectorTab.Document)
        viewModel.await { it.inspectorTab == InspectorTab.Document }

        viewModel.onToggleInspector()
        val hidden = viewModel.await { !it.inspectorOpen }
        // Hiding is not a tab change: it comes back on the same one.
        assertEquals(InspectorTab.Document, hidden.inspectorTab)
        assertTrue(hidden.sidebarOpen)

        viewModel.onToggleInspector()
        val shown = viewModel.await { it.inspectorOpen }
        assertEquals(InspectorTab.Document, shown.inspectorTab)
    }

    @Test
    fun chromeEventsLeaveTheDocumentAndHistoryAlone() = runTest {
        val viewModel = editor()
        val opened = viewModel.states.value.document

        viewModel.onToggleSidebar()
        viewModel.onToggleNotes()
        viewModel.onSelectInspectorTab(InspectorTab.Document)
        viewModel.onToggleInspector()
        viewModel.onSelectInspectorTab(InspectorTab.Document)
        viewModel.onCloseInspector()
        val after = viewModel.await { !it.inspectorOpen }
        assertEquals(opened, after.document)
        assertFalse(after.canUndo)
        assertFalse(after.canRedo)
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

    @Test
    fun fullOutlineKeepsHiddenRowsAndFlagsThem() = runTest {
        val viewModel = editor()
        val slides = viewModel.states.value.document.slides
        val whyKmp = slides.first { it.title == "Why KMP" }

        viewModel.onToggleCollapsed(whyKmp.id)
        val state = viewModel.await { it.fullOutline().any { row -> !row.visible } }

        // Every slide stays in the full outline, only its visibility changes.
        assertEquals(slides.size, state.fullOutline().size)
        assertEquals(listOf(3, 4, 5), state.fullOutline().filter { !it.visible }.map { it.slideIndex })
        // The filtered view is exactly what the collapse-applied outline shows.
        assertEquals(state.outline(), state.fullOutline().filter { it.visible })
    }
}

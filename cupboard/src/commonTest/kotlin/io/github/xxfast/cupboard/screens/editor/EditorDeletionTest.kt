package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Deleting elements and slides, driven the way a shell drives it. Three elements
 * with a build each, so a deletion has builds to prune, and a deck of five
 * slides with a nested run under "Two", so a slide deletion has somewhere to
 * move the ones beneath it.
 */
class EditorDeletionTest {
    // Fixed ids, so a freshly built document compares equal to the opened one.
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                elements = listOf(
                    ShapeElement(id = "a", frame = Frame(0f, 0f, 100f, 100f)),
                    ShapeElement(id = "b", frame = Frame(120f, 0f, 100f, 100f)),
                    ShapeElement(id = "c", frame = Frame(240f, 0f, 100f, 100f)),
                ),
                builds = listOf(Build("a"), Build("b"), Build("c")),
            ),
            Slide(id = "two", title = "Two"),
            Slide(id = "two-a", title = "Two A", depth = 1),
            Slide(id = "two-b", title = "Two B", depth = 1),
            Slide(id = "three", title = "Three"),
        ),
    )

    private fun EditorState.element(id: String): Element =
        selectedSlide.elements.first { it.id == id }

    private fun EditorState.order(): List<String> = selectedSlide.elements.map { it.id }

    private fun EditorState.builtIds(): List<String> = selectedSlide.builds.map { it.elementId }

    private fun EditorState.slideIds(): List<String> = document.slides.map { it.id }

    private fun EditorState.depths(): List<Int> = document.slides.map { it.depth }

    @Test
    fun deletingTakesTheElementsAndTheirBuildsWithThem() = runTest {
        val viewModel = editor(document())

        viewModel.onDeleteElements(listOf("a", "c"))
        val deleted = viewModel.await { it.selectedSlide.elements.size == 1 }
        assertEquals(listOf("b"), deleted.order())
        assertEquals(listOf("b"), deleted.builtIds())
        assertTrue(deleted.canUndo)
    }

    @Test
    fun aLockedElementIsSkippedAndTheRestStillGo() = runTest {
        val viewModel = editor(document())
        viewModel.onSetElementsLocked(listOf("b"), locked = true)
        val locked = viewModel.await { it.element("b").locked }

        viewModel.onDeleteElements(listOf("a", "b", "c"))
        val deleted = viewModel.await { it.selectedSlide.elements.size == 1 }
        assertEquals(listOf("b"), deleted.order())
        assertEquals(listOf("b"), deleted.builtIds())

        // One entry for the delete: undoing lands back on the locked document.
        viewModel.onUndo()
        assertEquals(locked.document, viewModel.await { it.selectedSlide.elements.size == 3 }.document)
    }

    @Test
    fun deletingAGroupTakesItsChildrensBuildsToo() = runTest {
        val viewModel = editor(document())
        viewModel.onGroupElements(listOf("a", "b"))
        val grouped = viewModel.await { it.selectedSlide.elements.size == 2 }
        val group: GroupElement = grouped.selectedSlide.elements.filterIsInstance<GroupElement>().single()
        // Grouping leaves the builds alone: the children are still on the slide.
        assertEquals(listOf("a", "b", "c"), grouped.builtIds())

        viewModel.onDeleteElements(listOf(group.id))
        val deleted = viewModel.await { it.selectedSlide.elements.size == 1 }
        assertEquals(listOf("c"), deleted.order())
        assertEquals(listOf("c"), deleted.builtIds())
    }

    @Test
    fun deletedIdsLeaveTheSelection() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElements(listOf("a", "b", "c"))
        viewModel.await { it.selectedElementIds.size == 3 }

        viewModel.onDeleteElements(listOf("c", "a"))
        val deleted = viewModel.await { it.selectedElementIds.size == 1 }
        assertEquals(listOf("b"), deleted.selectedElementIds)
        assertEquals("b", deleted.primaryElement?.id)
    }

    @Test
    fun undoPutsTheDeletedElementsBack() = runTest {
        val viewModel = editor(document())
        val before = viewModel.states.value.document

        viewModel.onDeleteElements(listOf("a"))
        viewModel.await { it.selectedSlide.elements.size == 2 }

        viewModel.onUndo()
        val undone = viewModel.await { it.document == before }
        assertEquals(listOf("a", "b", "c"), undone.order())
        assertEquals(listOf("a", "b", "c"), undone.builtIds())
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)
    }

    @Test
    fun aDeleteWithNothingToRemoveMakesNoHistoryEntry() = runTest {
        val viewModel = editor(document())

        viewModel.onDeleteElements(listOf("gone"))
        viewModel.onDeleteElements(emptyList())
        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertEquals(document(), after.document)
        assertFalse(after.canUndo)
    }

    @Test
    fun clearAllEmptiesTheSlideButLeavesTheLockedOnes() = runTest {
        val viewModel = editor(document())
        viewModel.onSetElementsLocked(listOf("b"), locked = true)
        viewModel.await { it.element("b").locked }

        viewModel.onClearAll()
        val cleared = viewModel.await { it.selectedSlide.elements.size == 1 }
        assertEquals(listOf("b"), cleared.order())
        assertEquals(listOf("b"), cleared.builtIds())

        // Nothing unlocked left, so a second clear is not an edit.
        viewModel.onClearAll()
        viewModel.onToggleSidebar()
        val again = viewModel.await { !it.sidebarOpen }
        assertEquals(cleared.document, again.document)
    }

    @Test
    fun deletingAMiddleSlideSelectsTheOneThatTookItsIndex() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectSlide("two-a")
        viewModel.await { it.selectedSlideId == "two-a" }

        viewModel.onDeleteSlide("two-a")
        val deleted = viewModel.await { it.document.slides.size == 4 }
        assertEquals(listOf("one", "two", "two-b", "three"), deleted.slideIds())
        // Index 2 held "two-a", it now holds "two-b".
        assertEquals("two-b", deleted.selectedSlideId)
        assertEquals(emptyList(), deleted.selectedElementIds)
        assertTrue(deleted.canUndo)
    }

    @Test
    fun deletingTheLastSlideSelectsTheNewLastOne() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectSlide("three")
        viewModel.await { it.selectedSlideId == "three" }

        viewModel.onDeleteSlide("three")
        val deleted = viewModel.await { it.document.slides.size == 4 }
        assertEquals("two-b", deleted.selectedSlideId)
        assertEquals(3, deleted.selectedSlideIndex())
    }

    @Test
    fun deletingAnUnselectedSlideLeavesTheSelectionWhereItIs() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectSlide("three")
        viewModel.await { it.selectedSlideId == "three" }

        viewModel.onDeleteSlide("two-a")
        val deleted = viewModel.await { it.document.slides.size == 4 }
        assertEquals("three", deleted.selectedSlideId)
    }

    @Test
    fun deletingTheOnlySlideLeavesOneFreshBlankOne() = runTest {
        val only = Document(id = "doc", slides = listOf(Slide(id = "only", title = "Only")))
        val viewModel = editor(only)

        viewModel.onDeleteSlide("only")
        val deleted = viewModel.await { it.document.slides.single().id != "only" }
        val blank: Slide = deleted.document.slides.single()
        assertEquals(emptyList(), blank.elements)
        assertEquals(emptyList(), blank.builds)
        assertNotEquals("only", blank.id)
        // The deck always has a slide selected, and it is the one that is there.
        assertEquals(blank.id, deleted.selectedSlideId)

        // Undoing brings the old slide back, and the blank one's id is gone:
        // the selection re-anchors to the index instead of dangling.
        viewModel.onUndo()
        val undone = viewModel.await { it.document == only }
        assertEquals("only", undone.selectedSlideId)
    }

    @Test
    fun deletingACollapsedSlideTakesTheRunItWasHiding() = runTest {
        val viewModel = editor(document())
        viewModel.onToggleCollapsed("two")
        viewModel.await { it.document.slides.first { slide -> slide.id == "two" }.collapsed }

        viewModel.onDeleteSlide("two")
        val deleted = viewModel.await { it.document.slides.size == 2 }
        assertEquals(listOf("one", "three"), deleted.slideIds())
    }

    @Test
    fun deletingAnExpandedSlidePromotesTheRunOneLevel() = runTest {
        val viewModel = editor(document())

        viewModel.onDeleteSlide("two")
        val deleted = viewModel.await { it.document.slides.size == 4 }
        assertEquals(listOf("one", "two-a", "two-b", "three"), deleted.slideIds())
        assertEquals(listOf(0, 0, 0, 0), deleted.depths())
    }

    @Test
    fun redoingASlideDeletionReAnchorsTheSelection() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectSlide("two")
        viewModel.await { it.selectedSlideId == "two" }

        viewModel.onDeleteSlide("two")
        val deleted = viewModel.await { it.document.slides.size == 4 }
        assertEquals("two-a", deleted.selectedSlideId)

        viewModel.onUndo()
        val undone = viewModel.await { it.document == document() }
        // "two-a" is back, so the selection is left exactly where it was.
        assertEquals("two-a", undone.selectedSlideId)

        // Back onto the slide the redo is about to take away.
        viewModel.onSelectSlide("two")
        viewModel.await { it.selectedSlideId == "two" }
        viewModel.onRedo()
        val redone = viewModel.await { it.document.slides.size == 4 }
        assertEquals("two-a", redone.selectedSlideId)
    }

    @Test
    fun deletingASlideThatIsNotThereMakesNoHistoryEntry() = runTest {
        val viewModel = editor(document())

        viewModel.onDeleteSlide("gone")
        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertEquals(document(), after.document)
        assertFalse(after.canUndo)
        assertFalse(after.canRedo)
    }
}

package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.PlaceholderRole
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.placeholderRole
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Layout mode through the loop: the deck's masters edited with the very
 * reductions slides are edited with, and what a slide takes from the one it is
 * on. The deck is two slides on "wide", plus a second layout to move onto.
 */
class EditorLayoutsTest {
    private fun titlePlaceholder(id: String): TextElement = TextElement(
        id = id,
        frame = Frame(100f, 100f, 800f, 100f),
        text = "Title",
        fontSize = 90f,
        role = PlaceholderRole.Title,
    )

    // Fixed ids, so a freshly built document compares equal to the opened one.
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                layoutId = "wide",
                elements = listOf(
                    TextElement(
                        id = "one-title",
                        frame = Frame(0f, 0f, 10f, 10f),
                        text = "What I wrote",
                        role = PlaceholderRole.Title,
                    ),
                ),
            ),
            Slide(id = "two", title = "Two", layoutId = "wide"),
        ),
        layouts = listOf(
            Slide(id = "wide", title = "Wide", elements = listOf(titlePlaceholder("wide-title"))),
            Slide(id = "tall", title = "Tall", elements = listOf(titlePlaceholder("tall-title"))),
        ),
    )

    @Test
    fun enteringLayoutModeAndLeavingItComesBackToTheSlideYouWereOn() = runTest {
        val viewModel = editor(document())

        viewModel.onSelectSlide("two")
        viewModel.await { it.selectedSlideId == "two" }

        viewModel.onEditSlideLayouts()
        val editing = viewModel.await { it.isEditingLayouts }
        assertEquals("wide", editing.selectedSlideId)
        assertEquals("two", editing.slideBeforeLayouts)
        assertEquals(EditorPane.Navigator, editing.focusedPane)
        // Mode is a selection, not an edit.
        assertFalse(editing.canUndo)
        assertEquals(document(), editing.document)

        viewModel.onExitSlideLayouts()
        val back = viewModel.await { !it.isEditingLayouts }
        assertEquals("two", back.selectedSlideId)
        assertNull(back.slideBeforeLayouts)
        assertFalse(back.canUndo)
    }

    @Test
    fun theOutlineListsTheLayoutsWhileLayoutModeIsOpen() = runTest {
        val viewModel = editor(document())

        viewModel.onEditSlideLayouts()
        val editing = viewModel.await { it.isEditingLayouts }
        assertEquals(listOf("wide", "tall"), editing.outline().map { it.slideId })
        assertEquals(listOf("Wide", "Tall"), editing.outline().map { it.title })
        assertEquals(listOf(1, 2), editing.outline().map { it.number })
        assertTrue(editing.outline().none { it.hasChildren || it.collapsed || it.skipped })

        viewModel.onExitSlideLayouts()
        val back = viewModel.await { !it.isEditingLayouts }
        assertEquals(listOf("one", "two"), back.outline().map { it.slideId })
    }

    @Test
    fun anEditInLayoutModeLandsOnTheLayoutAndUndoes() = runTest {
        val viewModel = editor(document())

        viewModel.onEditSlideLayouts()
        val editing = viewModel.await { it.isEditingLayouts }

        val moved = titlePlaceholder("wide-title").copy(frame = Frame(1f, 2f, 3f, 4f))
        viewModel.onUpdateElements(listOf(moved))
        val edited = viewModel.await { it.canUndo }
        assertEquals(moved, edited.document.layouts.first().elements.single())
        // The deck itself is untouched: a layout lives in its own list.
        assertEquals(document().slides, edited.document.slides)
        assertEquals(editing.selectedSlide.id, edited.selectedSlide.id)

        viewModel.onUndo()
        val undone = viewModel.await { !it.canUndo }
        assertEquals(document(), undone.document)
    }

    @Test
    fun newSlideInheritsTheAnchorsLayoutWithItsPlaceholdersInstantiated() = runTest {
        val viewModel = editor(document())

        viewModel.onAddSlide("two")
        val added = viewModel.await { it.document.slides.size == 3 }
        val fresh: Slide = added.document.slides.last()

        assertEquals("wide", fresh.layoutId)
        assertEquals(fresh.id, added.selectedSlideId)
        assertEquals(listOf(PlaceholderRole.Title), fresh.elements.map { it.placeholderRole })
        // Its own copy of the placeholder, not the layout's element.
        assertNotEquals("wide-title", fresh.elements.single().id)
    }

    @Test
    fun applyingALayoutRedressesTheSlideInOneHistoryEntry() = runTest {
        val viewModel = editor(document())

        viewModel.onApplyLayout("one", "tall")
        val applied = viewModel.await { it.document.slides.first().layoutId == "tall" }
        val title = applied.document.slides.first().elements.single() as TextElement
        assertEquals("one-title", title.id)
        assertEquals("What I wrote", title.text)
        assertEquals(Frame(100f, 100f, 800f, 100f), title.frame)

        viewModel.onUndo()
        val undone = viewModel.await { !it.canUndo }
        assertEquals(document(), undone.document)
    }

    @Test
    fun applyingNoLayoutUnhooksTheSlideAndKeepsItsElements() = runTest {
        val viewModel = editor(document())

        viewModel.onApplyLayout("one", null)
        val applied = viewModel.await { it.document.slides.first().layoutId == null }
        assertEquals(document().slides.first().elements, applied.document.slides.first().elements)
        assertTrue(applied.canUndo)
    }

    @Test
    fun reapplyPutsAMovedPlaceholderBackAndDoesNothingWithoutALayout() = runTest {
        val viewModel = editor(document())

        viewModel.onReapplyLayout("one")
        val reapplied = viewModel.await { it.canUndo }
        assertEquals(
            Frame(100f, 100f, 800f, 100f),
            reapplied.document.slides.first().elements.single().frame,
        )

        // Nothing left to put back, so no second entry to undo past.
        viewModel.onReapplyLayout("one")
        viewModel.onUndo()
        val undone = viewModel.await { !it.canUndo }
        assertEquals(document(), undone.document)
    }

    /** Reapply is also how a placeholder someone deleted comes back. */
    @Test
    fun reapplyBringsBackAPlaceholderTheSlideNoLongerHas() = runTest {
        val viewModel = editor(document())

        viewModel.onReapplyLayout("two")
        val reapplied = viewModel.await { it.document.slides.last().elements.isNotEmpty() }
        val restored = reapplied.document.slides.last().elements.single()
        assertEquals(PlaceholderRole.Title, restored.placeholderRole)
        assertNotEquals("wide-title", restored.id)

        viewModel.onUndo()
        val undone = viewModel.await { !it.canUndo }
        assertEquals(document(), undone.document)
    }

    @Test
    fun deletingALayoutUnhooksTheSlidesThatWereOnItAndTheLastOneStays() = runTest {
        val viewModel = editor(document())

        viewModel.onDeleteSlide("wide")
        val deleted = viewModel.await { it.document.layouts.size == 1 }
        assertEquals("tall", deleted.document.layouts.single().id)
        assertTrue(deleted.document.slides.all { it.layoutId == null })
        // Content survives its layout going away.
        assertEquals(document().slides.first().elements, deleted.document.slides.first().elements)

        viewModel.onDeleteSlide("tall")
        viewModel.onSelectSlide("one")
        val after = viewModel.await { it.selectedSlideId == "one" }
        assertEquals(1, after.document.layouts.size)
    }

    @Test
    fun addingDuplicatingAndMovingLayoutsAllWorkOnTheLayoutList() = runTest {
        val viewModel = editor(document())

        viewModel.onEditSlideLayouts()
        viewModel.await { it.isEditingLayouts }

        viewModel.onAddSlide("wide")
        val added = viewModel.await { it.document.layouts.size == 3 }
        assertEquals("Layout 3", added.document.layouts[1].title)
        assertEquals(added.document.layouts[1].id, added.selectedSlideId)
        assertTrue(added.isEditingLayouts)

        viewModel.onDuplicateSlide("wide")
        val duplicated = viewModel.await { it.document.layouts.size == 4 }
        assertEquals("Wide", duplicated.document.layouts[1].title)
        assertEquals(duplicated.document.layouts[1].id, duplicated.selectedSlideId)

        viewModel.onMoveSlide("tall", null)
        val moved = viewModel.await { it.document.layouts.first().id == "tall" }
        assertEquals(0, moved.document.layouts.first().depth)
        assertEquals("tall", moved.selectedSlideId)
        assertEquals(document().slides, moved.document.slides)
    }

    @Test
    fun addPlaceholderPutsOneOnTheLayoutBeingEdited() = runTest {
        val viewModel = editor(document())

        // Not in layout mode, it is nobody's business to add one.
        viewModel.onAddPlaceholder(PlaceholderRole.Body)
        viewModel.onSelectSlide("two")
        val ignored = viewModel.await { it.selectedSlideId == "two" }
        assertEquals(document(), ignored.document)

        viewModel.onEditSlideLayouts()
        viewModel.await { it.isEditingLayouts }
        viewModel.onAddPlaceholder(PlaceholderRole.Body)
        val added = viewModel.await { it.document.layouts.first().elements.size == 2 }
        val placeholder = added.document.layouts.first().elements.last()
        assertEquals(PlaceholderRole.Body, placeholder.placeholderRole)
        assertEquals(listOf(placeholder.id), added.selectedElementIds)
        assertTrue(added.canUndo)
    }

    @Test
    fun renamingWorksForSlidesAndForLayouts() = runTest {
        val viewModel = editor(document())

        viewModel.onRenameSlide("one", "Opening")
        val slide = viewModel.await { it.document.slides.first().title == "Opening" }
        assertTrue(slide.canUndo)

        viewModel.onRenameSlide("wide", "Widescreen")
        val layout = viewModel.await { it.document.layouts.first().title == "Widescreen" }
        assertEquals("Opening", layout.document.slides.first().title)

        // Already what it is asked to be: no second entry to undo past.
        viewModel.onRenameSlide("wide", "Widescreen")
        viewModel.onUndo()
        viewModel.onUndo()
        val undone = viewModel.await { !it.canUndo }
        assertEquals(document(), undone.document)
    }

    @Test
    fun theSlideOnlyVerbsDoNothingToALayout() = runTest {
        val viewModel = editor(document())

        viewModel.onEditSlideLayouts()
        viewModel.await { it.isEditingLayouts }

        viewModel.onToggleCollapsed("wide")
        viewModel.onSetSlideSkipped("wide", true)
        viewModel.onCopySlide("wide")
        viewModel.onCutSlide("wide")
        viewModel.onPaste()

        // Something that does land, so the assertions below aren't racing the
        // events above through the loop.
        viewModel.onSelectSlide("tall")
        val settled = viewModel.await { it.selectedSlideId == "tall" }
        assertEquals(document(), settled.document)
        assertFalse(settled.canUndo)
        assertFalse(settled.canPaste)
    }

    @Test
    fun aCopiedSlidePastesOntoTheDeckRatherThanIntoTheLayouts() = runTest {
        val viewModel = editor(document())

        viewModel.onCopySlide("one")
        viewModel.await { it.canPaste }

        viewModel.onEditSlideLayouts()
        viewModel.await { it.isEditingLayouts }
        viewModel.onPaste()

        viewModel.onSelectSlide("tall")
        val settled = viewModel.await { it.selectedSlideId == "tall" }
        assertEquals(document(), settled.document)

        viewModel.onExitSlideLayouts()
        viewModel.onPaste()
        val pasted = viewModel.await { it.document.slides.size == 3 }
        assertEquals(2, pasted.document.layouts.size)
    }
}

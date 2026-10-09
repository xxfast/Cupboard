package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.document.elementById
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Object List, driven the way a shell drives it. A title under an oval
 * under a group of two, so the rows have an order to come out in and a group to
 * open.
 */
class EditorObjectListTest {
    private fun document(): Document = Document(
        slides = listOf(
            Slide(
                id = "slide",
                title = "Objects",
                elements = listOf(
                    TextElement(
                        id = "title",
                        frame = Frame(0f, 0f, 400f, 60f),
                        text = "\n  Hello  \nworld",
                    ),
                    ShapeElement(
                        id = "oval",
                        frame = Frame(0f, 100f, 100f, 100f),
                        kind = ShapeKind.Ellipse,
                    ),
                    GroupElement(
                        id = "group",
                        frame = Frame(200f, 100f, 220f, 100f),
                        children = listOf(
                            ShapeElement(
                                id = "back",
                                frame = Frame(200f, 100f, 100f, 100f),
                                label = "Back",
                            ),
                            TextElement(
                                id = "front",
                                frame = Frame(320f, 100f, 100f, 100f),
                                text = "Front",
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun EditorState.element(id: String): Element = selectedSlide.elementById(id)!!

    private fun EditorState.rowIds(): List<String> = objectRows().map { it.id }

    @Test
    fun rowsAreFrontToBackWithContentNames() = runTest {
        val viewModel = editor(document())
        val rows: List<ObjectRow> = viewModel.states.value.objectRows()

        assertEquals(listOf("group", "oval", "title"), rows.map { it.id })
        assertEquals(listOf("Group", "Oval", "Hello"), rows.map { it.name })
        assertEquals(listOf("Group", "Shape", "Text"), rows.map { it.kind })
        assertTrue(rows.all { it.depth == 0 && !it.expanded })
        assertTrue(rows.first().isGroup)
    }

    @Test
    fun expandingAGroupListsItsChildrenBeneathIt() = runTest {
        val viewModel = editor(document())

        viewModel.onToggleObjectExpanded("group")
        val expanded = viewModel.await { "group" in it.expandedGroupIds }
        assertEquals(listOf("group", "front", "back", "oval", "title"), expanded.rowIds())
        assertEquals(listOf(0, 1, 1, 0, 0), expanded.objectRows().map { it.depth })
        assertTrue(expanded.objectRows().first().expanded)
        assertFalse(expanded.canUndo)

        viewModel.onToggleObjectExpanded("group")
        val collapsed = viewModel.await { it.expandedGroupIds.isEmpty() }
        assertEquals(listOf("group", "oval", "title"), collapsed.rowIds())
    }

    @Test
    fun aFilterFlattensAndMatchesByDisplayNameIgnoringCase() = runTest {
        val viewModel = editor(document())
        viewModel.onToggleObjectExpanded("group")
        viewModel.await { "group" in it.expandedGroupIds }

        viewModel.onSetObjectListFilter("O")
        val filtered = viewModel.await { it.objectListFilter == "O" }
        // "Group", "Front", "Oval" and "Hello" all hold an o; "Back" doesn't.
        assertEquals(listOf("group", "front", "oval", "title"), filtered.rowIds())
        assertTrue(filtered.objectRows().all { it.depth == 0 && !it.expanded })

        viewModel.onSetObjectListFilter("back")
        assertEquals(listOf("back"), viewModel.await { it.objectListFilter == "back" }.rowIds())

        viewModel.onSetObjectListFilter(" ")
        val cleared = viewModel.await { it.objectListFilter == " " }
        assertEquals(listOf("group", "front", "back", "oval", "title"), cleared.rowIds())
    }

    @Test
    fun hidingIsOneUndoableEntryAndAHiddenElementStillSelects() = runTest {
        val viewModel = editor(document())

        viewModel.onSetElementsHidden(listOf("oval", "title"), hidden = true)
        val hidden = viewModel.await { it.element("oval").hidden }
        assertTrue(hidden.element("title").hidden)
        assertEquals(listOf(false, true, true), hidden.objectRows().map { it.hidden })

        viewModel.onSelectElement("oval")
        val selected = viewModel.await { it.selectedElementIds == listOf("oval") }
        assertEquals(listOf("oval"), selected.selectedElementIds)

        // A marquee goes straight past it, though: what isn't drawn isn't swept.
        viewModel.onPreviewMarquee(Frame(0f, 0f, 1000f, 1000f))
        val swept = viewModel.await { it.marquee != null }
        assertEquals(listOf("group"), swept.selectedElementIds)
        viewModel.onEndMarquee()

        viewModel.onUndo()
        val undone = viewModel.await { !it.element("oval").hidden }
        assertFalse(undone.element("title").hidden)
        assertFalse(undone.canUndo)
    }

    @Test
    fun hidingWhatIsAlreadyHiddenIsNotAnEdit() = runTest {
        val viewModel = editor(document())

        viewModel.onSetElementsHidden(listOf("oval"), hidden = true)
        viewModel.await { it.element("oval").hidden }
        viewModel.onSetElementsHidden(listOf("oval"), hidden = true)
        viewModel.onSetElementsHidden(listOf("gone"), hidden = true)

        viewModel.onUndo()
        val undone = viewModel.await { !it.element("oval").hidden }
        assertFalse(undone.canUndo)
    }

    @Test
    fun aBlankRenameFallsBackToTheDefaultName() = runTest {
        val viewModel = editor(document())

        viewModel.onRenameElement("oval", "Sun")
        val renamed = viewModel.await { it.element("oval").name == "Sun" }
        assertEquals("Sun", renamed.objectRows().first { it.id == "oval" }.name)

        viewModel.onRenameElement("oval", "   ")
        val reset = viewModel.await { it.element("oval").name == null }
        assertEquals("Oval", reset.objectRows().first { it.id == "oval" }.name)

        // Two edits, two entries; and blank onto null is nothing to undo.
        viewModel.onRenameElement("oval", null)
        viewModel.onUndo()
        val undone = viewModel.await { it.element("oval").name == "Sun" }
        assertEquals("Sun", undone.objectRows().first { it.id == "oval" }.name)
    }

    @Test
    fun renamingAndHidingReachALockedElement() = runTest {
        val viewModel = editor(document())
        viewModel.onSetElementsLocked(listOf("oval"), locked = true)
        viewModel.await { it.element("oval").locked }

        viewModel.onRenameElement("oval", "Sun")
        viewModel.onSetElementsHidden(listOf("oval"), hidden = true)
        val edited = viewModel.await { it.element("oval").hidden }
        assertEquals("Sun", edited.element("oval").name)
        assertTrue(edited.element("oval").locked)
        assertTrue(edited.objectRows().first { it.id == "oval" }.locked)
    }

    /** [document] with the group's own fields rewritten by [transform]. */
    private fun documentWithGroup(transform: (GroupElement) -> GroupElement): Document {
        val document: Document = document()
        val slide: Slide = document.slides.single()
        val elements: List<Element> = slide.elements.map { element ->
            if (element is GroupElement) transform(element) else element
        }
        return document.copy(slides = listOf(slide.copy(elements = elements)))
    }

    @Test
    fun aChildIdSelectsTheChildAndNotItsGroup() = runTest {
        val viewModel = editor(document())
        viewModel.onToggleObjectExpanded("group")
        viewModel.await { "group" in it.expandedGroupIds }

        viewModel.onSelectElement("front")
        val selected = viewModel.await { it.selectedElementIds == listOf("front") }
        assertEquals("front", selected.primaryElement?.id)
        assertEquals(listOf("front"), selected.objectRows().filter { it.selected }.map { it.id })

        // Adding the group takes its child out, and adding the child back takes
        // the group out: never an element and its ancestor at once.
        viewModel.onToggleElementSelection("group")
        assertEquals(
            listOf("group"),
            viewModel.await { it.selectedElementIds == listOf("group") }.selectedElementIds,
        )
        viewModel.onToggleElementSelection("back")
        val child = viewModel.await { it.selectedElementIds == listOf("back") }
        assertEquals(listOf("back"), child.objectRows().filter { it.selected }.map { it.id })

        viewModel.onSelectElements(listOf("front", "back", "oval"))
        viewModel.await { it.selectedElementIds == listOf("front", "back", "oval") }

        viewModel.onSelectElements(listOf("front", "oval", "group"))
        viewModel.await { it.selectedElementIds == listOf("oval", "group") }
    }

    @Test
    fun movingAChildRefitsItsGroupInOneEntry() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElement("front")
        val selected = viewModel.await { it.selectedElementIds == listOf("front") }

        val moved: Element = selected.element("front").update(frame = Frame(320f, 300f, 100f, 100f))
        viewModel.onPreviewElements(listOf(moved.update(frame = Frame(320f, 200f, 100f, 100f))))
        viewModel.onUpdateElements(listOf(moved))
        val updated = viewModel.await { it.element("front").frame == moved.frame }
        assertEquals(Frame(200f, 100f, 220f, 300f), updated.element("group").frame)
        assertEquals(Frame(200f, 100f, 100f, 100f), updated.element("back").frame)

        viewModel.onUndo()
        val undone = viewModel.await { it.element("front").frame == Frame(320f, 100f, 100f, 100f) }
        assertEquals(Frame(200f, 100f, 220f, 100f), undone.element("group").frame)
        assertFalse(undone.canUndo)
    }

    @Test
    fun renamingAChildDoesNotMoveItsGroup() = runTest {
        // A frame that isn't its children's bounds, so a refit would show.
        val padded = Frame(190f, 90f, 240f, 120f)
        val viewModel = editor(documentWithGroup { it.copy(frame = padded) })

        viewModel.onRenameElement("front", "Caption")
        val renamed = viewModel.await { it.element("front").name == "Caption" }
        assertEquals(padded, renamed.element("group").frame)

        // A restyle through the ordinary edit path leaves it alone too.
        viewModel.onUpdateElements(listOf(renamed.element("back").update(opacity = 0.5f)))
        val faded = viewModel.await { it.element("back").opacity == 0.5f }
        assertEquals(padded, faded.element("group").frame)
    }

    @Test
    fun aChildOfALockedGroupIgnoresEdits() = runTest {
        val viewModel = editor(document())
        viewModel.onSetElementsLocked(listOf("group"), locked = true)
        viewModel.await { it.element("group").locked }

        viewModel.onSelectElement("front")
        val selected = viewModel.await { it.selectedElementIds == listOf("front") }
        assertFalse(selected.canDelete)

        val front: Element = selected.element("front")
        viewModel.onUpdateElements(listOf(front.update(frame = Frame(0f, 0f, 10f, 10f))))
        viewModel.onFlipElements(listOf("front"), FlipAxis.Horizontal)
        viewModel.onDeleteElements(listOf("front"))
        viewModel.onReorderElements(listOf("front"), ZOrderMove.ToBack)
        viewModel.onMoveElement("front", null)
        // Something that does land, so the ones before it have had their turn.
        viewModel.onSetElementsHidden(listOf("oval"), hidden = true)
        val after = viewModel.await { it.element("oval").hidden }
        assertEquals(front, after.element("front"))
        assertEquals(listOf("back", "front"), (after.element("group") as GroupElement).children.map { it.id })

        viewModel.onUndo()
        viewModel.await { !it.element("oval").hidden }
        viewModel.onUndo()
        assertFalse(viewModel.await { !it.element("group").locked }.canUndo)
    }

    @Test
    fun deletingAChildOfATwoChildGroupLeavesTheOtherOnItsOwn() = runTest {
        // Flipped about its center x of 310, so "front" (center 370) lands at 250.
        val viewModel = editor(documentWithGroup { it.copy(flippedHorizontally = true, opacity = 0.5f) })
        viewModel.onSelectElement("back")
        viewModel.await { it.selectedElementIds == listOf("back") }

        viewModel.onDelete()
        val deleted = viewModel.await { it.selectedSlide.elementById("back") == null }
        assertEquals(listOf("title", "oval", "front"), deleted.selectedSlide.elements.map { it.id })
        assertEquals(Frame(200f, 100f, 100f, 100f), deleted.element("front").frame)
        assertTrue(deleted.element("front").flippedHorizontally)
        assertEquals(0.5f, deleted.element("front").opacity)
        assertEquals(emptyList(), deleted.selectedElementIds)

        viewModel.onUndo()
        val undone = viewModel.await { it.selectedSlide.elementById("back") != null }
        assertFalse(undone.canUndo)
    }

    @Test
    fun reorderingAChildLeavesTheTopLevelAlone() = runTest {
        val viewModel = editor(document())

        viewModel.onReorderElements(listOf("back"), ZOrderMove.ToFront)
        val reordered = viewModel.await { state ->
            (state.element("group") as GroupElement).children.first().id == "front"
        }
        assertEquals(listOf("title", "oval", "group"), reordered.selectedSlide.elements.map { it.id })
        assertTrue(reordered.canUndo)
    }

    @Test
    fun aDropMovesSelectsAndEndsTheDrag() = runTest {
        val viewModel = editor(document())

        viewModel.onPreviewObjectDrag("title", beforeId = "group", translationY = 12f)
        val dragging = viewModel.await { it.objectDrag != null }
        assertEquals(ObjectDrag("title", "group", 12f), dragging.objectDrag)
        assertFalse(dragging.canUndo)

        viewModel.onMoveElement("title", beforeId = "group")
        val dropped = viewModel.await { it.objectDrag == null }
        assertEquals(listOf("oval", "group", "title"), dropped.selectedSlide.elements.map { it.id })
        assertEquals(listOf("title"), dropped.selectedElementIds)
        assertEquals(listOf("title", "group", "oval"), dropped.rowIds())

        viewModel.onUndo()
        assertFalse(viewModel.await { it.selectedSlide.elements.first().id == "title" }.canUndo)
    }

    @Test
    fun aDropThatChangesNothingIsNoEdit() = runTest {
        val viewModel = editor(document())

        // Already directly in front of the oval.
        viewModel.onPreviewObjectDrag("group", beforeId = "oval")
        viewModel.await { it.objectDrag != null }
        viewModel.onMoveElement("group", beforeId = "oval")
        val dropped = viewModel.await { it.objectDrag == null }
        assertEquals(listOf("group"), dropped.selectedElementIds)
        assertFalse(dropped.canUndo)

        // Out of the group is no reorder either.
        viewModel.onPreviewObjectDrag("front", beforeId = "oval")
        viewModel.await { it.objectDrag != null }
        viewModel.onMoveElement("front", beforeId = "oval")
        val strayed = viewModel.await { it.objectDrag == null }
        assertEquals(listOf("title", "oval", "group"), strayed.selectedSlide.elements.map { it.id })
        assertFalse(strayed.canUndo)
    }

    @Test
    fun anObjectDragDoesNotEndATextEdit() = runTest {
        val viewModel = editor(document())
        viewModel.onBeginTextEdit("title")
        viewModel.await { it.isEditingText }

        viewModel.onPreviewObjectDrag("oval", beforeId = null, translationY = 4f)
        assertTrue(viewModel.await { it.objectDrag != null }.isEditingText)

        viewModel.onEndObjectDrag()
        assertTrue(viewModel.await { it.objectDrag == null }.isEditingText)
    }

    @Test
    fun lockingThroughAChildRowLocksTheChildOnly() = runTest {
        val viewModel = editor(document())

        viewModel.onSetElementsLocked(listOf("back"), locked = true)
        val locked = viewModel.await { it.element("back").locked }
        assertFalse(locked.element("group").locked)
        assertFalse(locked.element("front").locked)
        assertTrue(locked.canUndo)

        viewModel.onUndo()
        assertFalse(viewModel.await { !it.element("back").locked }.canUndo)
    }

    @Test
    fun theListOpensAndClosesWithoutTouchingHistory() = runTest {
        val viewModel = editor(document())
        assertFalse(viewModel.states.value.objectListOpen)

        viewModel.onToggleObjectList()
        assertFalse(viewModel.await { it.objectListOpen }.canUndo)

        viewModel.onToggleObjectList()
        assertFalse(viewModel.await { !it.objectListOpen }.canUndo)
    }
}

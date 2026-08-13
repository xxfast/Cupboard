package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.editor.AlignEdge
import io.github.xxfast.cupboard.editor.Axis
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Multi-selection, grouping and the batch edits that ride on them, driven the
 * way a shell drives them. Three elements in a staircase (a top-left, c
 * bottom-right) so aligning and distributing have somewhere to move things.
 */
class EditorMultiSelectTest {
    // Fixed id, so a freshly built document compares equal to the opened one.
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "slide",
                title = "Elements",
                elements = listOf(
                    ShapeElement(id = "a", frame = Frame(0f, 0f, 100f, 100f)),
                    ShapeElement(id = "b", frame = Frame(120f, 40f, 100f, 100f)),
                    ShapeElement(id = "c", frame = Frame(300f, 80f, 100f, 100f)),
                ),
            ),
        ),
    )

    private fun EditorState.element(id: String): Element =
        selectedSlide.elements.first { it.id == id }

    private fun EditorState.order(): List<String> = selectedSlide.elements.map { it.id }

    private fun EditorState.group(): GroupElement =
        selectedSlide.elements.filterIsInstance<GroupElement>().single()

    @Test
    fun selectingReplacesAndTogglingAddsOrRemoves() = runTest {
        val viewModel = editor(document())

        viewModel.onSelectElements(listOf("c", "a"))
        val selected = viewModel.await { it.selectedElementIds.size == 2 }
        // Selection order is the order it was made, so the primary is "c".
        assertEquals(listOf("c", "a"), selected.selectedElementIds)
        assertEquals("c", selected.primaryElement?.id)
        assertEquals(listOf("c", "a"), selected.selectedElements.map { it.id })

        viewModel.onToggleElementSelection("b")
        assertEquals(
            listOf("c", "a", "b"),
            viewModel.await { it.selectedElementIds.size == 3 }.selectedElementIds,
        )

        viewModel.onToggleElementSelection("a")
        assertEquals(
            listOf("c", "b"),
            viewModel.await { it.selectedElementIds.size == 2 }.selectedElementIds,
        )

        // A plain select replaces the lot.
        viewModel.onSelectElement("a")
        assertEquals(listOf("a"), viewModel.await { it.selectedElementIds == listOf("a") }.selectedElementIds)
    }

    @Test
    fun theMarqueeSelectsWhatItOverlapsInDocumentOrder() = runTest {
        val viewModel = editor(document())
        // Selected back to front first, so the marquee's own ordering shows.
        viewModel.onSelectElements(listOf("c", "b"))
        viewModel.await { it.selectedElementIds.size == 2 }

        viewModel.onPreviewMarquee(Frame(110f, 0f, 200f, 200f))
        val sweeping = viewModel.await { it.marquee != null }
        assertEquals(listOf("b", "c"), sweeping.selectedElementIds)
        assertEquals(Frame(110f, 0f, 200f, 200f), sweeping.marquee)

        // Pulled back over "a" alone: the selection follows the pointer live.
        viewModel.onPreviewMarquee(Frame(10f, 0f, 50f, 50f))
        assertEquals(listOf("a"), viewModel.await { it.selectedElementIds == listOf("a") }.selectedElementIds)

        // Touching an edge is not overlapping it.
        viewModel.onPreviewMarquee(Frame(-50f, 0f, 50f, 50f))
        val outside = viewModel.await { it.selectedElementIds.isEmpty() }
        assertEquals(emptyList(), outside.selectedElementIds)

        viewModel.onPreviewMarquee(Frame(110f, 0f, 200f, 200f))
        viewModel.await { it.selectedElementIds.size == 2 }
        viewModel.onEndMarquee()
        val ended = viewModel.await { it.marquee == null }
        assertEquals(listOf("b", "c"), ended.selectedElementIds)
        // None of that was an edit.
        assertFalse(ended.canUndo)
        assertEquals(document(), ended.document)
    }

    @Test
    fun aMarqueeSweepsUpLockedElementsToo() = runTest {
        val viewModel = editor(document())
        viewModel.onSetElementsLocked(listOf("b"), locked = true)
        viewModel.await { it.element("b").locked }

        viewModel.onPreviewMarquee(Frame(110f, 0f, 200f, 200f))
        assertEquals(listOf("b", "c"), viewModel.await { it.marquee != null }.selectedElementIds)
    }

    @Test
    fun aBatchUpdateIsOneHistoryEntry() = runTest {
        val viewModel = editor(document())
        val before = viewModel.states.value.document

        viewModel.onUpdateElements(
            listOf(
                viewModel.states.value.element("a").update(opacity = 0.5f),
                viewModel.states.value.element("c").update(opacity = 0.5f),
            ),
        )
        val edited = viewModel.await { it.element("c").opacity == 0.5f }
        assertEquals(0.5f, edited.element("a").opacity)
        assertEquals(1f, edited.element("b").opacity)

        viewModel.onUndo()
        val undone = viewModel.await { it.document == before }
        assertFalse(undone.canUndo)
    }

    @Test
    fun aBatchSkipsItsLockedMembersAndLandsForTheRest() = runTest {
        val viewModel = editor(document())
        viewModel.onSetElementsLocked(listOf("b"), locked = true)
        val locked = viewModel.await { it.element("b").locked }

        viewModel.onUpdateElements(
            listOf(
                locked.element("a").update(opacity = 0.5f),
                locked.element("b").update(opacity = 0.5f),
            ),
        )
        val edited = viewModel.await { it.element("a").opacity == 0.5f }
        assertEquals(1f, edited.element("b").opacity)

        // One entry for the batch: undoing lands back on the locked document.
        viewModel.onUndo()
        assertEquals(locked.document, viewModel.await { it.element("a").opacity == 1f }.document)
    }

    @Test
    fun aBatchWithNothingLeftToEditChangesNothing() = runTest {
        val viewModel = editor(document())
        viewModel.onSetElementsLocked(listOf("a", "b"), locked = true)
        val locked = viewModel.await { it.element("a").locked && it.element("b").locked }

        viewModel.onUpdateElements(
            listOf(
                locked.element("a").update(opacity = 0.5f),
                locked.element("b").update(opacity = 0.5f),
            ),
        )
        viewModel.onPreviewElements(listOf(locked.element("a").update(opacity = 0.2f)))
        viewModel.onUpdateElements(emptyList())

        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertEquals(locked.document, after.document)
        assertFalse(after.isPreviewing)
        // Only the lock is in the history, nothing the batch tried to add.
        viewModel.onUndo()
        assertFalse(viewModel.await { !it.element("a").locked }.canUndo)
    }

    @Test
    fun aBatchGestureCommitsAsOneEntry() = runTest {
        val viewModel = editor(document())
        val before = viewModel.states.value.document
        val a = viewModel.states.value.element("a")
        val b = viewModel.states.value.element("b")

        viewModel.onPreviewElements(listOf(a.update(frame = a.frame.translate(10f, 0f)), b))
        viewModel.onPreviewElements(listOf(a.update(frame = a.frame.translate(40f, 0f)), b))
        val previewing = viewModel.await { it.element("a").frame.x == 40f }
        assertTrue(previewing.isPreviewing)
        assertFalse(previewing.canUndo)

        viewModel.onUpdateElements(listOf(a.update(frame = a.frame.translate(50f, 0f)), b))
        val committed = viewModel.await { it.element("a").frame.x == 50f }
        assertFalse(committed.isPreviewing)
        assertTrue(committed.canUndo)

        viewModel.onUndo()
        assertFalse(viewModel.await { it.document == before }.canUndo)
    }

    @Test
    fun flipsAndReordersRunAcrossTheWholeSelection() = runTest {
        val viewModel = editor(document())

        viewModel.onFlipElements(listOf("a", "c"), FlipAxis.Horizontal)
        val flipped = viewModel.await { it.element("c").flippedHorizontally }
        assertTrue(flipped.element("a").flippedHorizontally)
        assertFalse(flipped.element("b").flippedHorizontally)

        viewModel.onReorderElements(listOf("a", "b"), ZOrderMove.ToFront)
        val reordered = viewModel.await { it.order().first() == "c" }
        assertEquals(listOf("c", "a", "b"), reordered.order())
    }

    @Test
    fun groupingSelectsTheGroupAndUndoPutsThePiecesBack() = runTest {
        val viewModel = editor(document())
        val before = viewModel.states.value.document

        viewModel.onGroupElements(listOf("a", "b"))
        val grouped = viewModel.await { it.selectedSlide.elements.size == 2 }
        val group = grouped.group()
        // At the topmost member's z-position, children in their old z-order.
        assertEquals(listOf(group.id, "c"), grouped.order())
        assertEquals(listOf("a", "b"), group.children.map { it.id })
        assertEquals(Frame(0f, 0f, 220f, 140f), group.frame)
        assertEquals(listOf(group.id), grouped.selectedElementIds)
        assertTrue(grouped.canUndo)

        viewModel.onUndo()
        val undone = viewModel.await { it.document == before }
        // The group's id is still selected but resolves to nothing, rather than
        // to some other element.
        assertNull(undone.primaryElement)

        viewModel.onRedo()
        assertEquals(group.id, viewModel.await { it.selectedSlide.elements.size == 2 }.group().id)
    }

    @Test
    fun groupingNeedsTwoMembersAndMakesNoHistoryWithoutThem() = runTest {
        val viewModel = editor(document())

        viewModel.onGroupElements(listOf("a"))
        viewModel.onGroupElements(listOf("a", "gone"))
        viewModel.onGroupElements(emptyList())

        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertEquals(document(), after.document)
        assertFalse(after.canUndo)
    }

    @Test
    fun aGroupDragsItsChildrenAlong() = runTest {
        val viewModel = editor(document())
        viewModel.onGroupElements(listOf("a", "b"))
        val group = viewModel.await { it.selectedSlide.elements.size == 2 }.group()

        viewModel.onUpdateElements(listOf(group.update(frame = group.frame.translate(60f, 20f))))
        val moved = viewModel.await { it.group().frame.x == 60f }.group()
        assertEquals(Frame(60f, 20f, 100f, 100f), moved.children[0].frame)
        assertEquals(Frame(180f, 60f, 100f, 100f), moved.children[1].frame)

        // Resizing scales them along both axes, from the group's origin.
        viewModel.onUpdateElements(listOf(moved.update(frame = Frame(60f, 20f, 440f, 140f))))
        val resized = viewModel.await { it.group().frame.width == 440f }.group()
        assertEquals(Frame(60f, 20f, 200f, 100f), resized.children[0].frame)
        assertEquals(Frame(300f, 60f, 200f, 100f), resized.children[1].frame)
    }

    @Test
    fun ungroupingSelectsTheChildrenItFrees() = runTest {
        val viewModel = editor(document())
        viewModel.onGroupElements(listOf("a", "b"))
        val group = viewModel.await { it.selectedSlide.elements.size == 2 }.group()

        // Turned a quarter turn first, so the children come out with it baked in.
        viewModel.onUpdateElements(listOf(group.update(rotation = 90f)))
        viewModel.await { it.group().rotation == 90f }

        viewModel.onUngroupElements(group.id)
        val freed = viewModel.await { it.selectedSlide.elements.size == 3 }
        assertEquals(listOf("a", "b", "c"), freed.order())
        assertEquals(listOf("a", "b"), freed.selectedElementIds)

        // Group bounds (0,0,220,140), center (110,70); "a" sat 60 left and 20 up
        // of it, so a quarter turn puts it 60 down and 20 right.
        assertEquals(90f, freed.element("a").rotation)
        assertEquals(130f, freed.element("a").frame.centerX, 0.001f)
        assertEquals(10f, freed.element("a").frame.centerY, 0.001f)
        assertTrue(freed.canUndo)
    }

    @Test
    fun aLockedGroupDoesNotUngroup() = runTest {
        val viewModel = editor(document())
        viewModel.onGroupElements(listOf("a", "b"))
        val group = viewModel.await { it.selectedSlide.elements.size == 2 }.group()

        viewModel.onSetElementsLocked(listOf(group.id), locked = true)
        val locked = viewModel.await { it.group().locked }

        viewModel.onUngroupElements(group.id)
        // A non-group id is no more ungroupable.
        viewModel.onUngroupElements("c")
        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertEquals(locked.document, after.document)
    }

    @Test
    fun groupsNestInsideGroups() = runTest {
        val viewModel = editor(document())
        viewModel.onGroupElements(listOf("a", "b"))
        val inner = viewModel.await { it.selectedSlide.elements.size == 2 }.group()

        viewModel.onGroupElements(listOf(inner.id, "c"))
        val outer = viewModel.await { it.selectedSlide.elements.size == 1 }.group()
        assertEquals(Frame(0f, 0f, 400f, 180f), outer.frame)

        val nested = outer.children.first() as GroupElement
        assertEquals(inner.id, nested.id)
        assertEquals(listOf("a", "b"), nested.children.map { it.id })

        // Ungrouping the outer one hands back the inner group, still whole.
        viewModel.onUngroupElements(outer.id)
        val freed = viewModel.await { it.selectedSlide.elements.size == 2 }
        assertEquals(listOf(inner.id, "c"), freed.selectedElementIds)
        assertEquals(listOf("a", "b"), freed.group().children.map { it.id })
    }

    @Test
    fun aligningMovesTheSelectionAndLeavesLockedMembersPut() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElements(listOf("a", "b", "c"))
        viewModel.await { it.selectedElementIds.size == 3 }

        viewModel.onAlignElements(AlignEdge.Top)
        val aligned = viewModel.await { it.element("c").frame.y == 0f }
        assertEquals(0f, aligned.element("b").frame.y)
        assertTrue(aligned.canUndo)

        // Aligned twice over is not an edit the second time.
        viewModel.onAlignElements(AlignEdge.Top)
        viewModel.onToggleSidebar()
        val again = viewModel.await { !it.sidebarOpen }
        assertEquals(aligned.document, again.document)

        // A locked member is out of the selection as far as the edit is
        // concerned, bounds included.
        viewModel.onSetElementsLocked(listOf("a"), locked = true)
        viewModel.await { it.element("a").locked }
        viewModel.onAlignElements(AlignEdge.Left)
        val left = viewModel.await { it.element("c").frame.x == 120f }
        assertEquals(0f, left.element("a").frame.x)
        assertEquals(120f, left.element("b").frame.x)
    }

    @Test
    fun aLoneSelectionAlignsToTheSlide() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElement("a")
        viewModel.await { it.selectedElementIds == listOf("a") }

        viewModel.onAlignElements(AlignEdge.CenterX)
        assertEquals(910f, viewModel.await { it.element("a").frame.x == 910f }.element("a").frame.x)
    }

    @Test
    fun distributingEqualizesTheGapsAndNeedsThree() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElements(listOf("a", "b"))
        viewModel.await { it.selectedElementIds.size == 2 }

        viewModel.onDistributeElements(Axis.Horizontal)
        viewModel.onToggleSidebar()
        val two = viewModel.await { !it.sidebarOpen }
        assertEquals(document(), two.document)
        assertFalse(two.canUndo)

        viewModel.onSelectElements(listOf("a", "b", "c"))
        viewModel.await { it.selectedElementIds.size == 3 }
        viewModel.onDistributeElements(Axis.Horizontal)
        // Span 0..400 over 300 of element: two gaps of 50, so "b" lands at 150.
        val spread = viewModel.await { it.element("b").frame.x == 150f }
        assertEquals(0f, spread.element("a").frame.x)
        assertEquals(300f, spread.element("c").frame.x)
        assertTrue(spread.canUndo)
    }
}

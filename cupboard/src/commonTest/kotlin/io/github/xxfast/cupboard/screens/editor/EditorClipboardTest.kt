package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextElement
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Cut, copy, paste, duplicate and the style clipboard, driven the way a shell
 * drives them. Slide "one" carries a styled text box, a shape, a plain text box
 * and a group of two, with builds on a top-level element and on a group child so
 * a copy has builds to remap. The deck underneath is the deletion test's, a
 * nested run under "Two", so a slide paste has depths to re-base.
 */
class EditorClipboardTest {
    // Fixed ids, so a freshly built document compares equal to the opened one.
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                elements = listOf(
                    TextElement(
                        id = "a",
                        frame = Frame(0f, 0f, 100f, 100f),
                        opacity = 0.5f,
                        text = "A",
                        fontSize = 30f,
                        fontWeight = 700,
                        color = 0xFFFF0000,
                        align = TextAlign.Center,
                    ),
                    ShapeElement(id = "b", frame = Frame(120f, 0f, 100f, 100f)),
                    TextElement(id = "c", frame = Frame(240f, 0f, 100f, 100f), text = "C"),
                    GroupElement(
                        id = "g",
                        frame = Frame(360f, 0f, 200f, 100f),
                        children = listOf(
                            ShapeElement(id = "g1", frame = Frame(360f, 0f, 100f, 100f)),
                            ShapeElement(id = "g2", frame = Frame(460f, 0f, 100f, 100f)),
                        ),
                    ),
                ),
                builds = listOf(Build("a"), Build("g1")),
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

    private fun EditorState.slideIds(): List<String> = document.slides.map { it.id }

    private fun EditorState.slide(id: String): Slide = document.slides.first { it.id == id }

    private fun Element.subtreeIds(): List<String> =
        listOf(id) + ((this as? GroupElement)?.children?.flatMap { it.subtreeIds() } ?: emptyList())

    private fun Slide.elementIds(): List<String> = elements.flatMap { it.subtreeIds() }

    @Test
    fun pastingACopyMakesFreshIdsAllTheWayDownAndSelectsThem() = runTest {
        val viewModel = editor(document())

        // Selection order is the reverse of z-order, so a paste that kept it
        // would come back with the group under the text box.
        viewModel.onCopyElements(listOf("g", "a"))
        assertTrue(viewModel.await { it.canPaste }.canPaste)

        viewModel.onPaste()
        val pasted = viewModel.await { it.selectedSlide.elements.size == 6 }
        val copies: List<Element> = pasted.selectedSlide.elements.takeLast(2)
        val text = copies.first() as TextElement
        val group = copies.last() as GroupElement

        assertEquals("A", text.text)
        assertEquals(2, group.children.size)
        // Nothing that came in shares an id with anything that was there.
        assertTrue(copies.flatMap { it.subtreeIds() }.none { it in document().slides.first().elementIds() })
        assertContentEquals(copies.map { it.id }, pasted.selectedElementIds)
        // The first paste lands exactly where the copy was taken.
        assertEquals(Frame(0f, 0f, 100f, 100f), text.frame)
        assertTrue(pasted.canUndo)
    }

    @Test
    fun theFirstPasteKeepsItsPlaceAndTheNextOneCascades() = runTest {
        val viewModel = editor(document())
        viewModel.onCopyElements(listOf("a"))
        viewModel.await { it.canPaste }

        viewModel.onPaste()
        val once = viewModel.await { it.selectedSlide.elements.size == 5 }
        assertEquals(Frame(0f, 0f, 100f, 100f), once.selectedSlide.elements.last().frame)

        viewModel.onPaste()
        val twice = viewModel.await { it.selectedSlide.elements.size == 6 }
        assertEquals(Frame(24f, 24f, 100f, 100f), twice.selectedSlide.elements.last().frame)
    }

    @Test
    fun cutThenPasteMovesAnElementToAnotherSlide() = runTest {
        val viewModel = editor(document())

        viewModel.onCutElements(listOf("a"))
        val cut = viewModel.await { it.selectedSlide.elements.size == 3 }
        assertEquals(listOf("b", "c", "g"), cut.order())
        // The build went with it, the group child's stayed.
        assertEquals(listOf("g1"), cut.slide("one").builds.map { it.elementId })

        viewModel.onSelectSlide("two")
        viewModel.await { it.selectedSlideId == "two" }
        viewModel.onPaste()
        val moved = viewModel.await { it.selectedSlide.elements.size == 1 }
        val text = moved.selectedSlide.elements.single() as TextElement
        assertEquals("A", text.text)
        assertEquals(Frame(0f, 0f, 100f, 100f), text.frame)
        assertNotEquals("a", text.id)
    }

    @Test
    fun cuttingLeavesTheLockedOnesWhereTheyAre() = runTest {
        val viewModel = editor(document())
        viewModel.onSetElementsLocked(listOf("b"), locked = true)
        viewModel.await { it.element("b").locked }

        viewModel.onCutElements(listOf("a", "b"))
        val cut = viewModel.await { it.selectedSlide.elements.size == 3 }
        assertEquals(listOf("b", "c", "g"), cut.order())

        // Only the unlocked one made it onto the clipboard.
        viewModel.onPaste()
        val pasted = viewModel.await { it.selectedSlide.elements.size == 4 }
        assertTrue(pasted.selectedSlide.elements.last() is TextElement)
    }

    @Test
    fun cuttingACollapsedSlideCarriesTheRunItWasHiding() = runTest {
        val viewModel = editor(document())
        viewModel.onToggleCollapsed("two")
        viewModel.await { it.slide("two").collapsed }

        viewModel.onCutSlide("two")
        val cut = viewModel.await { it.document.slides.size == 2 }
        assertEquals(listOf("one", "three"), cut.slideIds())

        // Selection sat on "one", so the group comes back where it was.
        viewModel.onPaste()
        val pasted = viewModel.await { it.document.slides.size == 5 }
        assertEquals(listOf("Two", "Two A", "Two B"), pasted.document.slides.subList(1, 4).map { it.title })
        assertEquals(listOf(0, 0, 1, 1, 0), pasted.document.slides.map { it.depth })
        assertEquals(pasted.document.slides[1].id, pasted.selectedSlideId)
        assertEquals(emptyList(), pasted.selectedElementIds)
    }

    @Test
    fun aPastedSlideRebasesItsDepthAndRemapsItsBuilds() = runTest {
        val viewModel = editor(document())
        viewModel.onCopySlide("one")
        viewModel.await { it.canPaste }

        viewModel.onSelectSlide("two-a")
        viewModel.await { it.selectedSlideId == "two-a" }
        viewModel.onPaste()
        val pasted = viewModel.await { it.document.slides.size == 6 }

        val copy: Slide = pasted.document.slides[3]
        assertEquals("One", copy.title)
        assertNotEquals("one", copy.id)
        // "one" sat at depth 0, "two-a" sits at depth 1: the paste follows the target.
        assertEquals(1, copy.depth)
        assertTrue(copy.elementIds().none { it in document().slides.first().elementIds() })
        // Two builds, both pointing at elements that are actually on the copy.
        assertEquals(2, copy.builds.size)
        assertTrue(copy.builds.all { it.elementId in copy.elementIds() })
        assertEquals(copy.id, pasted.selectedSlideId)
        // The original is untouched.
        assertEquals(document().slides.first(), pasted.slide("one"))
    }

    @Test
    fun duplicatingASlideRemapsItsBuildsAndSelectsTheCopy() = runTest {
        val viewModel = editor(document())

        viewModel.onDuplicateSlide("one")
        val duplicated = viewModel.await { it.document.slides.size == 6 }

        val copy: Slide = duplicated.document.slides[1]
        assertNotEquals("one", copy.id)
        assertEquals(0, copy.depth)
        assertTrue(copy.elementIds().none { it in document().slides.first().elementIds() })
        assertEquals(2, copy.builds.size)
        assertTrue(copy.builds.all { it.elementId in copy.elementIds() })
        assertEquals(copy.id, duplicated.selectedSlideId)
        assertTrue(duplicated.canUndo)
        // Duplicating never touches the clipboard.
        assertFalse(duplicated.canPaste)
    }

    @Test
    fun duplicatingElementsSkipsTheLockedOnesAndOffsetsTheRest() = runTest {
        val viewModel = editor(document())
        viewModel.onSetElementsLocked(listOf("b"), locked = true)
        viewModel.await { it.element("b").locked }

        viewModel.onDuplicateElements(listOf("a", "b"))
        val duplicated = viewModel.await { it.selectedSlide.elements.size == 5 }
        val copy: Element = duplicated.selectedSlide.elements.last()
        assertTrue(copy is TextElement && copy.text == "A")
        assertEquals(Frame(24f, 24f, 100f, 100f), copy.frame)
        assertEquals(listOf(copy.id), duplicated.selectedElementIds)
        assertFalse(duplicated.canPaste)
    }

    @Test
    fun aStylePasteCarriesTheLookAndNotTheContent() = runTest {
        val viewModel = editor(document())

        viewModel.onCopyStyle("a")
        assertTrue(viewModel.await { it.canPasteStyle }.canPasteStyle)

        viewModel.onPasteStyle(listOf("b", "c"))
        val styled = viewModel.await { it.element("c").opacity == 0.5f }

        // Same type: every style field crosses, the text does not.
        val text = styled.element("c") as TextElement
        assertEquals("C", text.text)
        assertEquals(30f, text.fontSize)
        assertEquals(700, text.fontWeight)
        assertEquals(0xFFFF0000, text.color)
        assertEquals(TextAlign.Center, text.align)
        assertEquals(Frame(240f, 0f, 100f, 100f), text.frame)

        // Across types: opacity, and nothing else.
        val shape = styled.element("b") as ShapeElement
        val original = document().slides.first().elements.first { it.id == "b" } as ShapeElement
        assertEquals(0.5f, shape.opacity)
        assertEquals(original.copy(opacity = 0.5f), shape)
        assertTrue(styled.canUndo)
    }

    @Test
    fun aStylePasteLeavesLockedElementsAlone() = runTest {
        val viewModel = editor(document())
        viewModel.onSetElementsLocked(listOf("c"), locked = true)
        val locked = viewModel.await { it.element("c").locked }

        viewModel.onCopyStyle("a")
        viewModel.onPasteStyle(listOf("c"))
        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertEquals(locked.document, after.document)
        // The lock made it a no-op, so there is nothing to undo but the lock.
        viewModel.onUndo()
        assertEquals(document(), viewModel.await { !it.canUndo }.document)
    }

    @Test
    fun undoingAPasteLeavesTheClipboardLoaded() = runTest {
        val viewModel = editor(document())
        viewModel.onCopyElements(listOf("a"))
        viewModel.await { it.canPaste }

        viewModel.onPaste()
        viewModel.await { it.selectedSlide.elements.size == 5 }

        viewModel.onUndo()
        val undone = viewModel.await { it.document == document() }
        assertEquals(listOf("a", "b", "c", "g"), undone.order())
        assertTrue(undone.canPaste)

        // Still loaded, so it pastes again.
        viewModel.onPaste()
        assertEquals(5, viewModel.await { it.selectedSlide.elements.size == 5 }.selectedSlide.elements.size)
    }

    @Test
    fun copyingIsNotAnEdit() = runTest {
        val viewModel = editor(document())

        viewModel.onCopyElements(listOf("a"))
        viewModel.onCopySlide("one")
        viewModel.onCopyStyle("a")
        // Nothing to resolve, so neither clipboard changes.
        viewModel.onCopyElements(listOf("gone"))
        viewModel.onCopySlide("gone")
        viewModel.onCopyStyle("gone")

        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertEquals(document(), after.document)
        assertFalse(after.canUndo)
        assertFalse(after.canRedo)
        assertTrue(after.canPaste)
        assertTrue(after.canPasteStyle)
    }

    @Test
    fun anEmptyClipboardPastesNothing() = runTest {
        val viewModel = editor(document())
        assertFalse(viewModel.states.value.canPaste)
        assertFalse(viewModel.states.value.canPasteStyle)

        viewModel.onPaste()
        viewModel.onPasteStyle(listOf("b"))
        viewModel.onCutElements(listOf("gone"))
        viewModel.onDuplicateSlide("gone")
        viewModel.onToggleSidebar()
        val after = viewModel.await { !it.sidebarOpen }
        assertEquals(document(), after.document)
        assertFalse(after.canUndo)
    }
}

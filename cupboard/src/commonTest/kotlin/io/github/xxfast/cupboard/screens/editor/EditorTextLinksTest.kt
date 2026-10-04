package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextLink
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Range links through the editor: the selection the caret's field reports, the
 * link put on it, and the links riding every edit to the text under them.
 */
class EditorTextLinksTest {
    private val home: LinkTarget = LinkTarget.Url("https://home")
    private val away: LinkTarget = LinkTarget.Slide("two")

    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                elements = listOf(
                    TextElement(id = "text", frame = Frame(0f, 0f, 200f, 40f), text = "Hello world"),
                    TextElement(id = "other", frame = Frame(0f, 60f, 200f, 40f), text = "Other"),
                    TextElement(
                        id = "locked",
                        frame = Frame(0f, 120f, 200f, 40f),
                        text = "Nope",
                        locked = true,
                    ),
                ),
            ),
            Slide(id = "two", title = "Two"),
        ),
    )

    private fun EditorState.box(id: String): TextElement =
        selectedSlide.elements.first { it.id == id } as TextElement

    @Test
    fun aRangeLinkIsOneUndoEntry() = runTest {
        val viewModel = editor(document())
        viewModel.onSetTextLink("text", 6, 11, home)
        val linked = viewModel.await { it.box("text").links.isNotEmpty() }
        assertEquals(listOf(TextLink(6, 11, home)), linked.box("text").links)
        assertTrue(linked.canUndo)

        viewModel.onUndo()
        val undone = viewModel.await { it.box("text").links.isEmpty() }
        assertFalse(undone.canUndo)

        viewModel.onRedo()
        viewModel.await { it.box("text").links == listOf(TextLink(6, 11, home)) }
    }

    @Test
    fun aNullTargetUnlinksTheRange() = runTest {
        val viewModel = editor(document())
        viewModel.onSetTextLink("text", 0, 11, home)
        viewModel.await { it.box("text").links.isNotEmpty() }

        viewModel.onSetTextLink("text", 5, 6, null)
        val cleared = viewModel.await { it.box("text").links.size == 2 }
        assertEquals(listOf(TextLink(0, 5, home), TextLink(6, 11, home)), cleared.box("text").links)
    }

    @Test
    fun aLockedBoxRefusesARangeLink() = runTest {
        val viewModel = editor(document())
        viewModel.onSetTextLink("locked", 0, 2, home)
        viewModel.onSetTextLink("text", 0, 2, home)
        val state = viewModel.await { it.box("text").links.isNotEmpty() }
        assertTrue(state.box("locked").links.isEmpty())
    }

    @Test
    fun theFieldsSelectionIsTrackedAndOnlyARealSelectionIsKeptForLinking() = runTest {
        val viewModel = editor(document())
        viewModel.onBeginTextEdit("text")
        viewModel.await { it.isEditingText }

        viewModel.onEditSelectionChanged("text", 6, 11)
        val selected = viewModel.await { it.textSelection?.start == 6 }
        assertEquals(TextSelection("text", 6, 11), selected.linkSelection)
        assertTrue(selected.canLinkSelection)

        // A bare caret moves the live selection but not the one a link goes on.
        viewModel.onEditSelectionChanged("text", 2, 2)
        val caret = viewModel.await { it.textSelection?.start == 2 }
        assertEquals(TextSelection("text", 6, 11), caret.linkSelection)

        // One for an element that isn't being edited says nothing.
        viewModel.onEditSelectionChanged("other", 0, 3)
        viewModel.onEditSelectionChanged("text", 3, 3)
        val ignored = viewModel.await { it.textSelection?.start == 3 }
        assertEquals("text", ignored.linkSelection?.elementId)
        assertFalse(ignored.canUndo)
    }

    @Test
    fun theLinkSelectionSurvivesTheSessionEndingAndGoesWithTheElementSelection() = runTest {
        val viewModel = editor(document())
        viewModel.onBeginTextEdit("text")
        viewModel.await { it.isEditingText }
        viewModel.onEditSelectionChanged("text", 0, 5)
        viewModel.await { it.linkSelection != null }

        viewModel.onEndTextEdit()
        val ended = viewModel.await { !it.isEditingText }
        assertNull(ended.textSelection)
        assertEquals(TextSelection("text", 0, 5), ended.linkSelection)
        assertTrue(ended.canLinkSelection)

        viewModel.onSelectElement("other")
        val moved = viewModel.await { it.selectedElementIds == listOf("other") }
        assertNull(moved.linkSelection)
        assertFalse(moved.canLinkSelection)
    }

    @Test
    fun linkingMidSessionSettlesTheTypingFirstSoEachIsItsOwnEntry() = runTest {
        val viewModel = editor(document())
        viewModel.onBeginTextEdit("text")
        viewModel.await { it.isEditingText }
        viewModel.onPreviewElements(listOf(viewModel.states.value.box("text").copy(text = "Hello world!")))
        viewModel.await { it.box("text").text == "Hello world!" }
        viewModel.onEditSelectionChanged("text", 6, 11)
        viewModel.await { it.linkSelection != null }

        viewModel.onSetTextLink("text", 6, 11, home)
        val linked = viewModel.await { it.box("text").links.isNotEmpty() }
        assertFalse(linked.isEditingText)
        assertEquals("Hello world!", linked.box("text").text)
        assertEquals(TextSelection("text", 6, 11), linked.linkSelection)
        assertEquals(TextLink(6, 11, home), linked.selectionLink)

        // The link comes off first, and the typing with the next undo.
        viewModel.onUndo()
        val unlinked = viewModel.await { it.box("text").links.isEmpty() }
        assertEquals("Hello world!", unlinked.box("text").text)
        viewModel.onUndo()
        viewModel.await { it.box("text").text == "Hello world" }
    }

    @Test
    fun typingCarriesTheLinksAndTheSelectionWithTheText() = runTest {
        val viewModel = editor(document())
        viewModel.onSetTextLink("text", 6, 11, away)
        viewModel.await { it.box("text").links.isNotEmpty() }

        viewModel.onBeginTextEdit("text")
        viewModel.await { it.isEditingText }
        viewModel.onEditSelectionChanged("text", 6, 11)
        viewModel.await { it.linkSelection != null }

        // A field that knows nothing of ranges: the element as it stands, retyped.
        viewModel.onPreviewElements(listOf(viewModel.states.value.box("text").copy(text = "Oh, Hello world")))
        val typed = viewModel.await { it.box("text").text == "Oh, Hello world" }
        assertEquals(listOf(TextLink(10, 15, away)), typed.box("text").links)
        assertEquals(TextSelection("text", 10, 15), typed.linkSelection)

        viewModel.onEndTextEdit()
        viewModel.await { !it.isEditingText }
        viewModel.onUndo()
        val undone = viewModel.await { it.box("text").text == "Hello world" }
        assertEquals(listOf(TextLink(6, 11, away)), undone.box("text").links)
    }

    @Test
    fun anEditOutsideTheSessionReanchorsTheLinkSelection() = runTest {
        val viewModel = editor(document())
        viewModel.onBeginTextEdit("text")
        viewModel.await { it.isEditingText }
        viewModel.onEditSelectionChanged("text", 6, 11)
        viewModel.await { it.linkSelection != null }
        viewModel.onEndTextEdit()
        viewModel.await { !it.isEditingText }

        viewModel.onUpdateElements(listOf(viewModel.states.value.box("text").copy(text = "Hey world")))
        val moved = viewModel.await { it.box("text").text == "Hey world" }
        assertEquals(TextSelection("text", 4, 9), moved.linkSelection)

        // Every selected character gone: nothing left to link.
        viewModel.onUpdateElements(listOf(viewModel.states.value.box("text").copy(text = "Hey ")))
        val gone = viewModel.await { it.box("text").text == "Hey " }
        assertNull(gone.linkSelection)
    }

    @Test
    fun theInspectorRowsListTheLinksWithAnExcerpt() = runTest {
        val long = "x".repeat(40)
        val viewModel = editor(
            document().let { document ->
                val slide = document.slides[0]
                val text = (slide.elements[0] as TextElement).copy(
                    text = "Hello $long",
                    links = listOf(TextLink(0, 5, home), TextLink(6, 46, away)),
                )
                document.copy(slides = listOf(slide.copy(elements = listOf(text)), document.slides[1]))
            },
        )
        viewModel.onSelectElement("text")
        val state = viewModel.await { it.selectedElementIds == listOf("text") }
        assertEquals(
            listOf(
                TextLinkRow(0, 5, "Hello", home),
                TextLinkRow(6, 46, "x".repeat(TextLinkExcerptLength) + "…", away),
            ),
            state.textLinkRows,
        )
    }

    @Test
    fun aDuplicatedBoxKeepsItsLinks() = runTest {
        val viewModel = editor(document())
        viewModel.onSetTextLink("text", 0, 5, home)
        viewModel.await { it.box("text").links.isNotEmpty() }
        viewModel.onSelectElement("text")
        viewModel.await { it.selectedElementIds == listOf("text") }

        viewModel.onDuplicate()
        val duplicated = viewModel.await { it.selectedSlide.elements.size == 4 }
        val copy = duplicated.selectedSlide.elements.last() as TextElement
        assertEquals(listOf(TextLink(0, 5, home)), copy.links)
    }
}

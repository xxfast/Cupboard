package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.BoldWeight
import io.github.xxfast.cupboard.document.CharacterStyle
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.PlaceholderRole
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextBaseline
import io.github.xxfast.cupboard.document.TextCapitalization
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextLink
import io.github.xxfast.cupboard.document.TextRangeStyle
import io.github.xxfast.cupboard.document.TextRun
import io.github.xxfast.cupboard.document.boldToggled
import io.github.xxfast.cupboard.document.characterStyle
import io.github.xxfast.cupboard.document.withCharacterStyle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Styled runs through the editor: a style put on the selected words, the
 * session it lands in, what the inspector reads back, and the runs riding the
 * typing that follows.
 */
class EditorTextRunsTest {
    private val red: Long = 0xFFFF0000

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
        ),
    )

    private fun EditorState.box(id: String): TextElement =
        selectedSlide.elements.first { it.id == id } as TextElement

    private suspend fun EditorViewModel.editing(id: String, start: Int, end: Int): EditorState {
        onBeginTextEdit(id)
        await { it.isEditingText }
        onEditSelectionChanged(id, start, end)
        return await { it.textSelection == TextSelection(id, start, end) }
    }

    @Test
    fun aStyleOnARangeIsOneUndoEntry() = runTest {
        val viewModel = editor(document())
        viewModel.onStyleTextRange("text", 0, 5, TextRun(color = red))
        val styled = viewModel.await { it.box("text").runs.isNotEmpty() }
        assertEquals(listOf(TextRun(0, 5, color = red)), styled.box("text").runs)
        assertTrue(styled.canUndo)

        viewModel.onUndo()
        val undone = viewModel.await { it.box("text").runs.isEmpty() }
        assertFalse(undone.canUndo)

        viewModel.onRedo()
        viewModel.await { it.box("text").runs == listOf(TextRun(0, 5, color = red)) }
    }

    @Test
    fun aLockedBoxRefusesAStyleAndOneThatChangesNothingCostsNothing() = runTest {
        val viewModel = editor(document())
        viewModel.onStyleTextRange("locked", 0, 2, TextRun(color = red))
        viewModel.onStyleTextRange("text", 2, 2, TextRun(color = red))
        viewModel.onStyleTextRange("text", 0, 2, TextRun(color = red))
        val state = viewModel.await { it.box("text").runs.isNotEmpty() }
        assertTrue(state.box("locked").runs.isEmpty())

        viewModel.onUndo()
        val undone = viewModel.await { it.box("text").runs.isEmpty() }
        assertFalse(undone.canUndo)
    }

    @Test
    fun stylingMidSessionKeepsTheCaretAndSettlesTheTypingFirst() = runTest {
        val viewModel = editor(document())
        viewModel.editing("text", 0, 0)
        viewModel.onPreviewElements(listOf(viewModel.states.value.box("text").copy(text = "Hello world!")))
        viewModel.await { it.box("text").text == "Hello world!" }
        viewModel.onEditSelectionChanged("text", 6, 11)
        viewModel.await { it.styleSelection == TextSelection("text", 6, 11) }

        viewModel.onStyleText { TextRun(fontSize = 30f) }
        val styled = viewModel.await { it.box("text").runs.isNotEmpty() }
        assertTrue(styled.isEditingText)
        assertEquals(TextSelection("text", 6, 11), styled.styleSelection)
        assertEquals(listOf(TextRun(6, 11, fontSize = 30f)), styled.box("text").runs)

        // The style comes off first, and the typing with the next undo.
        viewModel.onEndTextEdit()
        viewModel.await { !it.isEditingText }
        viewModel.onUndo()
        val unstyled = viewModel.await { it.box("text").runs.isEmpty() }
        assertEquals("Hello world!", unstyled.box("text").text)
        viewModel.onUndo()
        viewModel.await { it.box("text").text == "Hello world" }
    }

    @Test
    fun withNoWordsSelectedAStyleIsTheWholeBoxs() = runTest {
        val viewModel = editor(document())
        viewModel.onStyleTextRange("text", 0, 5, TextRun(fontWeight = BoldWeight))
        viewModel.await { it.box("text").runs.isNotEmpty() }

        // A bare caret selects nothing, so the whole box goes bold.
        viewModel.editing("text", 3, 3)
        viewModel.onStyleText { it.boldToggled() }
        val bolded = viewModel.await { it.box("text").fontWeight == BoldWeight }
        assertEquals(emptyList(), bolded.box("text").runs)
    }

    @Test
    fun theInspectorReadsTheSelectedWordsElseTheWholeBox() = runTest {
        val viewModel = editor(document())
        viewModel.onStyleTextRange("text", 0, 5, TextRun(fontSize = 30f))
        viewModel.await { it.box("text").runs.isNotEmpty() }
        viewModel.onSelectElement("text")
        val whole: TextRangeStyle? = viewModel.await { it.selectedElementIds == listOf("text") }.inspectedTextStyle
        assertNull(whole?.fontSize)
        assertEquals(0xFFFFFFFF, whole?.color)

        val hello: TextRangeStyle? = viewModel.editing("text", 0, 5).inspectedTextStyle
        assertEquals(30f, hello?.fontSize)

        val world: TextRangeStyle? = viewModel.editing("text", 6, 11).inspectedTextStyle
        assertEquals(15f, world?.fontSize)

        viewModel.onEndTextEdit()
        val ended = viewModel.await { !it.isEditingText }
        assertNull(ended.styleSelection)
        assertNull(ended.inspectedTextStyle?.fontSize)
    }

    @Test
    fun typingOnAfterAStyledWordCarriesItsStyle() = runTest {
        val viewModel = editor(document())
        viewModel.onStyleTextRange("text", 0, 5, TextRun(color = red))
        viewModel.await { it.box("text").runs.isNotEmpty() }
        viewModel.onSetTextLink("text", 6, 11, LinkTarget.Url("https://home"))
        viewModel.await { it.box("text").links.isNotEmpty() }

        // A field that knows nothing of ranges: the element as it stands, retyped.
        viewModel.editing("text", 5, 5)
        viewModel.onPreviewElements(listOf(viewModel.states.value.box("text").copy(text = "Hello, world")))
        val typed = viewModel.await { it.box("text").text == "Hello, world" }
        assertEquals(listOf(TextRun(0, 6, color = red)), typed.box("text").runs)
        assertEquals(listOf(TextLink(7, 12, LinkTarget.Url("https://home"))), typed.box("text").links)
    }

    @Test
    fun aCharacterStyleAndACasingGoOnTheSelectedWords() = runTest {
        val viewModel = editor(document())
        viewModel.editing("text", 0, 5)

        viewModel.onStyleText { it.withCharacterStyle(CharacterStyle.Emphasis) }
        val emphasised = viewModel.await { it.box("text").runs.isNotEmpty() }
        assertTrue(emphasised.isEditingText)
        assertEquals(CharacterStyle.Emphasis, emphasised.inspectedTextStyle?.characterStyle)

        viewModel.onStyleText { TextRun(capitalization = TextCapitalization.AllCaps) }
        val capitalised = viewModel.await { it.box("text").runs.single().capitalization != null }
        assertEquals(
            listOf(TextRun(0, 5, italic = true, capitalization = TextCapitalization.AllCaps)),
            capitalised.box("text").runs,
        )
        assertEquals(TextCapitalization.AllCaps, capitalised.inspectedTextStyle?.capitalization)

        viewModel.onEndTextEdit()
        val whole = viewModel.await { !it.isEditingText }
        assertNull(whole.inspectedTextStyle?.capitalization)
        assertNull(whole.inspectedTextStyle?.characterStyle)
    }

    @Test
    fun aBaselineOnTheWholeBoxIsOneUndoEntry() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElement("text")
        viewModel.await { it.selectedElementIds == listOf("text") }

        viewModel.onStyleText { TextRun(baseline = TextBaseline.Superscript) }
        val raised = viewModel.await { it.box("text").baseline == TextBaseline.Superscript }
        assertEquals(TextBaseline.Normal, raised.box("other").baseline)

        viewModel.onUndo()
        viewModel.await { it.box("text").baseline == TextBaseline.Normal }
    }

    private fun laidOut(): Document {
        val title = TextElement(
            id = "placeholder",
            frame = Frame(0f, 0f, 800f, 80f),
            fontSize = 60f,
            fontWeight = BoldWeight,
            role = PlaceholderRole.Title,
        )
        val base: Document = document()
        return base.copy(
            slides = base.slides.map { it.copy(layoutId = "layout") },
            layouts = listOf(Slide(id = "layout", title = "Layout", elements = listOf(title))),
        )
    }

    @Test
    fun aParagraphStyleSetsEverySelectedBoxWhole() = runTest {
        val viewModel = editor(laidOut())
        viewModel.onStyleTextRange("text", 0, 5, TextRun(color = red))
        viewModel.await { it.box("text").runs.isNotEmpty() }
        viewModel.onSelectElement("text")
        val selected = viewModel.await { it.selectedElementIds == listOf("text") }
        assertEquals(listOf(PlaceholderRole.Title, PlaceholderRole.Body), selected.paragraphStyles.map { it.role })
        assertEquals(PlaceholderRole.Body, selected.inspectedParagraphStyle)

        viewModel.onSetParagraphStyle(PlaceholderRole.Title)
        val titled = viewModel.await { it.box("text").fontSize == 60f }
        assertEquals(BoldWeight, titled.box("text").fontWeight)
        assertEquals(emptyList(), titled.box("text").runs)
        assertEquals(PlaceholderRole.Title, titled.inspectedParagraphStyle)
        assertEquals(15f, titled.box("other").fontSize)

        viewModel.onUndo()
        val undone = viewModel.await { it.box("text").fontSize == 15f }
        assertEquals(listOf(TextRun(0, 5, color = red)), undone.box("text").runs)
    }
}

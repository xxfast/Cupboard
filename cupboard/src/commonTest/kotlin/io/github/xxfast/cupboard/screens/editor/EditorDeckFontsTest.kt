package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.DeckFont
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextFont
import io.github.xxfast.cupboard.document.formatText
import io.github.xxfast.cupboard.document.withFont
import io.github.xxfast.cupboard.document.withGenericFont
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fonts through the loop: a pick rides the format edit every other text change
 * already goes through, and the deck's own fonts are an event each, one undo
 * entry apiece.
 */
class EditorDeckFontsTest {
    private val box = Frame(0f, 0f, 400f, 100f)

    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                elements = listOf(
                    TextElement(id = "a", frame = box, text = "A", fontFamily = TextFont.Serif),
                    TextElement(id = "b", frame = box, text = "B"),
                ),
            ),
        ),
    )

    private fun EditorState.text(id: String): TextElement =
        selectedSlide.elements.first { it.id == id } as TextElement

    @Test
    fun aPickIsOneFormatEditAndTheInspectorReadsItBack() = runTest {
        val viewModel = editor(document())
        viewModel.onSelectElements(listOf("a", "b"))
        val selected = viewModel.await { it.selectedElementIds.size == 2 }
        assertEquals("Serif", selected.selectedFontDisplayName)

        viewModel.onUpdateElements(selected.selectedElements.formatText { it.withFont("Inter") })
        val named = viewModel.await { it.text("b").fontName == "Inter" }
        assertEquals("Inter", named.text("a").fontName)
        assertEquals(TextFont.Serif, named.text("a").fontFamily, "the fallback stays")
        assertEquals("Inter", named.selectedFontDisplayName)

        viewModel.onUpdateElements(named.selectedElements.formatText { it.withGenericFont(TextFont.Monospace) })
        val generic = viewModel.await { it.text("a").fontFamily == TextFont.Monospace }
        assertNull(generic.text("a").fontName)
        assertEquals("Monospace", generic.selectedFontDisplayName)

        viewModel.onUndo()
        val undone = viewModel.await { it.text("a").fontName == "Inter" }
        assertEquals(TextFont.Serif, undone.text("a").fontFamily)
    }

    @Test
    fun aPastedStyleAndUseAsDefaultCarryTheName() = runTest {
        val viewModel = editor(document())
        val a: TextElement = viewModel.states.value.text("a")
        viewModel.onUpdateElements(listOf(a.withFont("Inter")))
        viewModel.await { it.text("a").fontName == "Inter" }

        viewModel.onCopyStyle("a")
        viewModel.onPasteStyle(listOf("b"))
        val pasted = viewModel.await { it.text("b").fontName == "Inter" }
        assertEquals("B", pasted.text("b").text)

        viewModel.onUseAsDefaultTextStyle("a")
        val defaulted = viewModel.await { it.defaults.textFontName == "Inter" }
        assertEquals(TextFont.Serif, defaulted.defaults.textFont)
    }

    @Test
    fun addingAndRemovingAFontAreOneUndoEach() = runTest {
        val viewModel = editor(document())
        assertEquals(emptyList(), viewModel.states.value.deckFontFamilies)

        viewModel.onAddDeckFont("inter.ttf", "Inter", 400, false)
        val added = viewModel.await { it.document.fonts.isNotEmpty() }
        assertEquals(listOf(DeckFont("inter.ttf", "Inter")), added.document.fonts)
        assertEquals(listOf("Inter"), added.deckFontFamilies)
        assertTrue(added.canUndo)

        viewModel.onAddDeckFont("bold.ttf", "Inter", 700, false)
        viewModel.onAddDeckFont("aleo.otf", "Aleo", 400, true)
        val three = viewModel.await { it.document.fonts.size == 3 }
        assertEquals(listOf("Aleo", "Inter"), three.deckFontFamilies)

        viewModel.onRemoveDeckFont("bold.ttf")
        val removed = viewModel.await { it.document.fonts.size == 2 }
        assertEquals(listOf("inter.ttf", "aleo.otf"), removed.document.fonts.map { it.assetId })

        viewModel.onUndo()
        viewModel.await { it.document.fonts.size == 3 }
        viewModel.onUndo()
        viewModel.await { it.document.fonts.size == 2 }
        viewModel.onUndo()
        viewModel.onUndo()
        val back = viewModel.await { !it.canUndo }
        assertEquals(emptyList(), back.document.fonts)
    }

    @Test
    fun theSameCutReplacesAndNoOpsCostNoHistory() = runTest {
        val viewModel = editor(document())
        viewModel.onAddDeckFont("old.ttf", "Inter", 700, true)
        viewModel.await { it.document.fonts.isNotEmpty() }

        viewModel.onAddDeckFont("new.ttf", "Inter", 700, true)
        val replaced = viewModel.await { it.document.fonts.single().assetId == "new.ttf" }
        assertEquals(1, replaced.document.fonts.size)

        // Neither of these changes anything: the next undo goes back past the replace.
        viewModel.onAddDeckFont("new.ttf", "Inter", 700, true)
        viewModel.onRemoveDeckFont("nothing.ttf")
        viewModel.onUndo()
        val undone = viewModel.await { it.document.fonts.singleOrNull()?.assetId == "old.ttf" }
        viewModel.onUndo()
        val empty = viewModel.await { it.document.fonts.isEmpty() }
        assertFalse(empty.canUndo)
        assertTrue(undone.canUndo)
    }
}

package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.BoldWeight
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.RegularWeight
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextFont
import io.github.xxfast.cupboard.document.applyingStyle
import io.github.xxfast.cupboard.document.formatText
import io.github.xxfast.cupboard.document.indentLine
import io.github.xxfast.cupboard.document.isBold
import io.github.xxfast.cupboard.document.listMarkers
import io.github.xxfast.cupboard.document.outdentLine
import io.github.xxfast.cupboard.document.toggleBold
import io.github.xxfast.cupboard.document.toggleItalic
import io.github.xxfast.cupboard.document.toggleStrikethrough
import io.github.xxfast.cupboard.document.toggleUnderline
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Formatting is a pure function over text elements, so most of this needs no
 * editor at all. The one that does is the last: what the helper hands back is
 * what the loop commits, and a whole toolbar press is worth one undo.
 */
class EditorTextFormattingTest {
    // Fixed ids, so a freshly built document compares equal to the opened one.
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                elements = listOf(
                    TextElement(id = "text", frame = Frame(0f, 0f, 200f, 40f), text = "Hello"),
                    TextElement(id = "other", frame = Frame(0f, 60f, 200f, 40f), text = "World"),
                ),
            ),
        ),
    )

    private fun EditorState.element(id: String): TextElement =
        selectedSlide.elements.first { it.id == id } as TextElement

    @Test
    fun formattingReachesUnlockedTopLevelTextAndNothingElse() {
        val text = TextElement(id = "text", frame = Frame(0f, 0f, 10f, 10f))
        val locked = TextElement(id = "locked", frame = Frame(0f, 0f, 10f, 10f), locked = true)
        val shape = ShapeElement(id = "shape", frame = Frame(0f, 0f, 10f, 10f))
        val group = GroupElement(
            id = "group",
            frame = Frame(0f, 0f, 10f, 10f),
            children = listOf(TextElement(id = "nested", frame = Frame(0f, 0f, 10f, 10f))),
        )

        val formatted = listOf(text, locked, shape, group).formatText { it.toggleBold() }
        assertEquals(listOf("text"), formatted.map { it.id })
        assertTrue((formatted.single() as TextElement).isBold)
    }

    @Test
    fun aFormatThatChangesNothingCommitsNothing() {
        val text =
            TextElement(id = "text", frame = Frame(0f, 0f, 10f, 10f), align = TextAlign.Center)
        assertTrue(listOf(text).formatText { it.copy(align = TextAlign.Center) }.isEmpty())
    }

    @Test
    fun boldIsAWeightAndAnythingHeavyEnoughIsAlreadyBold() {
        val plain = TextElement(id = "text", frame = Frame(0f, 0f, 10f, 10f))
        assertFalse(plain.isBold)
        assertEquals(BoldWeight, plain.toggleBold().fontWeight)
        assertEquals(RegularWeight, plain.toggleBold().toggleBold().fontWeight)

        // Imported at 600: it reads as bold, so the toggle takes it off.
        val semiBold = plain.copy(fontWeight = 600)
        assertTrue(semiBold.isBold)
        assertEquals(RegularWeight, semiBold.toggleBold().fontWeight)
    }

    @Test
    fun theOtherThreeAreFlags() {
        val plain = TextElement(id = "text", frame = Frame(0f, 0f, 10f, 10f))
        assertTrue(plain.toggleItalic().italic)
        assertTrue(plain.toggleUnderline().underline)
        assertTrue(plain.toggleStrikethrough().strikethrough)
        assertFalse(plain.toggleItalic().toggleItalic().italic)
    }

    @Test
    fun bulletsAlternateWithNestingAndSkipBlankLines() {
        val text = "Alpha\n\tBeta\n\t\tGamma\n\nDelta"
        assertEquals(
            listOf("•", "◦", "•", "", "•"),
            listMarkers(text, ListStyle.Bullet),
        )
        assertEquals(List(5) { "" }, listMarkers(text, ListStyle.None))
    }

    @Test
    fun numberingCyclesPerLevelAndRestartsWhenTheListComesBackIn() {
        assertEquals(
            listOf("1.", "a.", "i.", "", "2."),
            listMarkers("Alpha\n\tBeta\n\t\tGamma\n\nDelta", ListStyle.Numbered),
        )

        // The sub-list under Delta starts over at a., the top level carries on at 3.
        assertEquals(
            listOf("1.", "a.", "b.", "2.", "a.", "3."),
            listMarkers("A\n\tB\n\tC\nD\n\tE\nF", ListStyle.Numbered),
        )
    }

    @Test
    fun indentingAndOutdentingALineRoundTrips() {
        val text = "Alpha\nBeta"
        val indented = text.indentLine(1)
        assertEquals("Alpha\n\tBeta", indented)
        assertEquals(listOf("1.", "a."), listMarkers(indented, ListStyle.Numbered))
        assertEquals(text, indented.outdentLine(1))

        // Already at the margin, and past the end: the same string back, untouched.
        assertTrue(text === text.outdentLine(0))
        assertTrue(text === text.indentLine(7))
    }

    @Test
    fun aStyleCarriesTheFormattingButNotTheLink() {
        val source = TextElement(
            id = "source",
            frame = Frame(0f, 0f, 10f, 10f),
            text = "Source",
            fontWeight = BoldWeight,
            fontFamily = TextFont.Serif,
            italic = true,
            underline = true,
            strikethrough = true,
            listStyle = ListStyle.Bullet,
            link = "https://kotlinlang.org",
        )
        val target = TextElement(id = "target", frame = Frame(50f, 50f, 10f, 10f), text = "Target")

        val styled = target.applyingStyle(source) as TextElement
        assertEquals(TextFont.Serif, styled.fontFamily)
        assertTrue(styled.italic)
        assertTrue(styled.underline)
        assertTrue(styled.strikethrough)
        assertEquals(ListStyle.Bullet, styled.listStyle)
        assertEquals(BoldWeight, styled.fontWeight)

        // Content, not style: neither the text nor where the box points travels.
        assertNull(styled.link)
        assertEquals("Target", styled.text)
        assertEquals(target.frame, styled.frame)
    }

    @Test
    fun committingAFormatCostsOneUndoEntry() = runTest {
        val viewModel = editor(document())
        val opened = viewModel.await { it.selectedSlide.elements.size == 2 }

        viewModel.onUpdateElements(opened.selectedSlide.elements.formatText { it.toggleBold() })
        val bolded = viewModel.await { it.element("text").isBold }
        assertTrue(bolded.element("other").isBold)
        assertTrue(bolded.canUndo)

        // Both elements went in one event, so one undo takes both back.
        viewModel.onUndo()
        val undone = viewModel.await { !it.element("text").isBold }
        assertFalse(undone.element("other").isBold)
        assertFalse(undone.canUndo)
    }
}

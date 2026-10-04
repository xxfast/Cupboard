package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextLink
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A deck with range links goes out through every writer: the words arrive, and
 * the links, which none of them carries as links, cost nothing on the way.
 */
class TextLinksExportTest {
    private val deck: Document = Document(
        name = "Linked deck",
        slides = listOf(
            Slide(
                id = "linked",
                title = "Linked",
                elements = listOf(
                    TextElement(
                        id = "text",
                        frame = Frame(0f, 0f, 400f, 80f),
                        text = "Read the docs",
                        links = listOf(TextLink(9, 13, LinkTarget.Url("https://docs"))),
                    ),
                    TextElement(
                        id = "list",
                        frame = Frame(0f, 100f, 400f, 80f),
                        text = "One\n\tTwo",
                        listStyle = ListStyle.Bullet,
                        links = listOf(TextLink(5, 8, LinkTarget.Slide("linked"))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun everyWriterTakesADeckWithRangeLinks() {
        assertTrue(exportPptx(deck, FakeRasterizer()).isNotEmpty())
        assertTrue(exportPdf(deck, FakeRasterizer()).isNotEmpty())
        assertTrue(exportHtmlPlayer(deck, FakeRasterizer()).contains("<img"))

        val sources: String = deck.toCupProject().joinToString("\n") { it.contents }
        assertTrue("Read the docs" in sources)
    }
}

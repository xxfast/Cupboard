package io.github.xxfast.cupboard.document

import io.github.xxfast.cupboard.screens.editor.EditorState
import io.github.xxfast.cupboard.screens.editor.await
import io.github.xxfast.cupboard.screens.editor.editor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * How a deck plays and where its elements point, and what a document written
 * before either existed opens as.
 */
class PlaybackTest {

    /**
     * A deck from before playback and link targets: no `playback`, and a text box
     * whose link is the bare URL string it has always been. Written out here
     * rather than round-tripped, because the point is what an old file on disk
     * does when it is opened.
     */
    private val oldDocument: String = """
        {
          "id": "doc",
          "name": "Old",
          "slides": [
            {
              "id": "one",
              "title": "One",
              "elements": [
                {
                  "type": "text",
                  "id": "t",
                  "frame": { "x": 0.0, "y": 0.0, "width": 100.0, "height": 50.0 },
                  "text": "Cupboard",
                  "link": "https://example.com"
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun aDocumentWrittenBeforePlaybackOpensOnTheDefaults() {
        val document: Document = loadedDocument(oldDocument)

        assertEquals(PlaybackSettings(), document.playback)
        assertEquals(PlaybackType.Normal, document.playback.type)
    }

    @Test
    fun anOldTextLinkIsStillAUrl() {
        val document: Document = loadedDocument(oldDocument)
        val text: TextElement = document.slides[0].elements[0] as TextElement

        // The string is all the file carried, and a whole-box URL is what it meant.
        assertNull(text.linkTarget)
        assertEquals(LinkTarget.Url("https://example.com"), text.resolvedLink())
    }

    @Test
    fun aTextBoxResolvesItsLinkOffWhicheverFieldItHas() {
        val bare = TextElement(frame = Frame(0f, 0f, 10f, 10f))
        val stringOnly = bare.copy(link = "https://example.com")
        val targeted = bare.copy(linkTarget = LinkTarget.Next)
        // Both set: the target is the more specific answer, so it wins.
        val both = bare.copy(link = "https://example.com", linkTarget = LinkTarget.First)

        assertNull(bare.resolvedLink())
        assertEquals(LinkTarget.Url("https://example.com"), stringOnly.resolvedLink())
        assertEquals(LinkTarget.Next, targeted.resolvedLink())
        assertEquals(LinkTarget.First, both.resolvedLink())
    }

    @Test
    fun shapesAndImagesPointOffTheirOwnField() {
        val shape = ShapeElement(frame = Frame(0f, 0f, 10f, 10f), link = LinkTarget.Last)
        val image = ImageElement(frame = Frame(0f, 0f, 10f, 10f), link = LinkTarget.ExitShow)
        val code = CodeElement(frame = Frame(0f, 0f, 10f, 10f))

        assertEquals(LinkTarget.Last, shape.resolvedLink())
        assertEquals(LinkTarget.ExitShow, image.resolvedLink())
        // A kind that holds no link at all points nowhere.
        assertNull(code.resolvedLink())
    }

    @Test
    fun linkingATextBoxKeepsItsUrlStringInStep() = runTest {
        val document = Document(
            id = "doc",
            slides = listOf(
                Slide(
                    id = "slide",
                    elements = listOf(TextElement(id = "t", frame = Frame(0f, 0f, 10f, 10f))),
                ),
            ),
        )
        val viewModel = editor(document)

        viewModel.onSetElementLinks(listOf("t"), LinkTarget.Url("https://example.com"))
        val linked = viewModel.await { it.text("t").linkTarget != null }
        assertEquals("https://example.com", linked.text("t").link)

        // A destination inside the deck is not an address, so the string goes.
        viewModel.onSetElementLinks(listOf("t"), LinkTarget.Slide("slide"))
        val jumped = viewModel.await { it.text("t").linkTarget is LinkTarget.Slide }
        assertNull(jumped.text("t").link)

        viewModel.onSetElementLinks(listOf("t"), null)
        val unlinked = viewModel.await { it.text("t").linkTarget == null }
        assertNull(unlinked.text("t").link)
        assertNull(unlinked.text("t").resolvedLink())
    }
}

private fun EditorState.text(id: String): TextElement =
    selectedSlide.elements.first { it.id == id } as TextElement

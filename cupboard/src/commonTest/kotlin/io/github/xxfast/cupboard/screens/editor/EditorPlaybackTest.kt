package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.PlaybackSettings
import io.github.xxfast.cupboard.document.PlaybackType
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.resolvedLink
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The two edits playback adds: how the deck plays, and where its elements point. */
class EditorPlaybackTest {
    // Fixed ids, so a freshly built document compares equal to the opened one.
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "slide",
                title = "Links",
                elements = listOf(
                    TextElement(id = "text", frame = Frame(0f, 0f, 100f, 50f), text = "Go"),
                    ShapeElement(id = "shape", frame = Frame(0f, 80f, 100f, 50f)),
                    ImageElement(id = "image", frame = Frame(0f, 160f, 100f, 50f)),
                    ShapeElement(id = "locked", frame = Frame(0f, 240f, 100f, 50f), locked = true),
                ),
            ),
            Slide(id = "other", title = "Other"),
        ),
    )

    private fun EditorState.element(id: String): Element =
        selectedSlide.elements.first { it.id == id }

    @Test
    fun settingPlaybackCostsOneHistoryEntry() = runTest {
        val viewModel = editor(document())
        val settings = PlaybackSettings(
            type = PlaybackType.SelfPlaying,
            autoAdvanceMs = 3000,
            loop = true,
        )

        viewModel.onSetPlayback(settings)
        val playing = viewModel.await { it.document.playback.type == PlaybackType.SelfPlaying }
        assertEquals(settings, playing.document.playback)
        assertTrue(playing.canUndo)

        viewModel.onUndo()
        val undone = viewModel.await { it.document.playback.type == PlaybackType.Normal }
        assertEquals(document(), undone.document)
        assertFalse(undone.canUndo)
    }

    @Test
    fun settingThePlaybackTheDeckAlreadyHasMakesNoHistoryEntry() = runTest {
        val viewModel = editor(document())

        viewModel.onSetPlayback(PlaybackSettings(type = PlaybackType.LinksOnly))
        val set = viewModel.await { it.document.playback.type == PlaybackType.LinksOnly }
        assertTrue(set.canUndo)

        viewModel.onSetPlayback(PlaybackSettings(type = PlaybackType.LinksOnly))
        viewModel.onSelectSlide("other")
        val settled = viewModel.await { it.selectedSlideId == "other" }
        assertEquals(set.document, settled.document)

        viewModel.onUndo()
        val undone = viewModel.await { it.document.playback.type == PlaybackType.Normal }
        assertFalse(undone.canUndo)
    }

    @Test
    fun linkingABatchSkipsTheLockedOneAndCostsOneUndo() = runTest {
        val viewModel = editor(document())
        val target = LinkTarget.Slide("other")

        viewModel.onSetElementLinks(listOf("text", "shape", "image", "locked"), target)
        val linked = viewModel.await { it.element("shape").resolvedLink() != null }

        assertEquals(target, linked.element("text").resolvedLink())
        assertEquals(target, linked.element("shape").resolvedLink())
        assertEquals(target, linked.element("image").resolvedLink())
        // A lock is the document's answer, and a batch is not a way around it.
        assertNull(linked.element("locked").resolvedLink())

        // Three elements changed, and the batch is still one thing the user did.
        assertTrue(linked.canUndo)
        viewModel.onUndo()
        val undone = viewModel.await { it.element("shape").resolvedLink() == null }
        assertEquals(document(), undone.document)
        assertFalse(undone.canUndo)
    }

    @Test
    fun linkingNothingThatCanHoldOneMakesNoHistoryEntry() = runTest {
        val viewModel = editor(document())

        // Only the locked element and an id the slide doesn't hold: nothing lands.
        viewModel.onSetElementLinks(listOf("locked", "nobody"), LinkTarget.Next)
        viewModel.onSelectSlide("other")
        val settled = viewModel.await { it.selectedSlideId == "other" }

        assertEquals(document(), settled.document)
        assertFalse(settled.canUndo)
    }
}

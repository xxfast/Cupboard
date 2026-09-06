package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideTransition
import io.github.xxfast.cupboard.document.TransitionKind
import io.github.xxfast.cupboard.document.TransitionTrigger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The slide's own transition, the one thing the inspector's Animate tab sets. */
class EditorTransitionsTest {
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(id = "one", title = "One"),
            Slide(id = "two", title = "Two"),
        ),
    )

    @Test
    fun dressingASlideCostsOneHistoryEntry() = runTest {
        val viewModel = editor(document())
        val transition = SlideTransition(
            kind = TransitionKind.MagicMove,
            durationMs = 900,
            trigger = TransitionTrigger.Automatic,
            delayMs = 1500,
        )

        viewModel.onSetSlideTransition("two", transition)
        val dressed = viewModel.await { it.document.slides[1].transition != null }
        assertEquals(transition, dressed.document.slides[1].transition)
        // The transition belongs to the slide it plays out of, and to no other.
        assertNull(dressed.document.slides[0].transition)
        assertTrue(dressed.canUndo)

        viewModel.onUndo()
        val undone = viewModel.await { it.document.slides[1].transition == null }
        assertEquals(document(), undone.document)
        assertFalse(undone.canUndo)
    }

    @Test
    fun clearingATransitionPutsTheSlideBackOnTheDeckDefault() = runTest {
        val viewModel = editor(document())

        viewModel.onSetSlideTransition("one", SlideTransition(kind = TransitionKind.Wipe))
        viewModel.await { it.document.slides[0].transition != null }

        viewModel.onSetSlideTransition("one", null)
        val cleared = viewModel.await { it.document.slides[0].transition == null }
        assertEquals(document(), cleared.document)
    }

    @Test
    fun settingTheTransitionASlideAlreadyWearsMakesNoHistoryEntry() = runTest {
        val viewModel = editor(document())
        val transition = SlideTransition(kind = TransitionKind.Push)

        viewModel.onSetSlideTransition("one", transition)
        val dressed = viewModel.await { it.document.slides[0].transition != null }
        assertTrue(dressed.canUndo)

        viewModel.onSetSlideTransition("one", transition)
        viewModel.onSetSlideTransition("nobody", transition)
        viewModel.onSelectSlide("two")
        val settled = viewModel.await { it.selectedSlideId == "two" }

        assertEquals(dressed.document, settled.document)
        viewModel.onUndo()
        val undone = viewModel.await { it.document.slides[0].transition == null }
        assertFalse(undone.canUndo)
    }
}

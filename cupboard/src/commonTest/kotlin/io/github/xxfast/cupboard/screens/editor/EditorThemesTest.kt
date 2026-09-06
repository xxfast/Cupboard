package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.BuiltInThemes
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.ElementDefaults
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.PlaceholderRole
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.TextElement
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Themes through the loop: the deck put on one, its own look saved back out, and
 * what each of those costs in history.
 */
class EditorThemesTest {
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                layoutId = "code",
                elements = listOf(
                    TextElement(
                        id = "one-title",
                        frame = Frame(0f, 0f, 10f, 10f),
                        text = "What I wrote",
                        role = PlaceholderRole.Title,
                    ),
                ),
            ),
        ),
        layouts = listOf(Slide(id = "code", title = "Code")),
    )

    @Test
    fun changingTheThemeIsOneEditAndOneUndo() = runTest {
        val viewModel = editor(document())

        viewModel.onChangeTheme("Nord")
        val themed = viewModel.await { it.document.themeName == "Nord" }
        assertEquals(BuiltInThemes.Nord.defaults, themed.defaults)
        assertEquals(BuiltInThemes.Nord.background, themed.document.background)
        assertTrue(themed.canUndo)
        // The slide followed its layout by name and kept what it said.
        assertEquals(
            "What I wrote",
            themed.document.slides.first().elements
                .filterIsInstance<TextElement>()
                .first { it.role == PlaceholderRole.Title }
                .text,
        )

        viewModel.onUndo()
        val back = viewModel.await { it.document.themeName == "Cupboard" }
        assertEquals(document(), back.document)
        assertFalse(back.canUndo)
    }

    @Test
    fun aThemeNoOneHasIsANoOp() = runTest {
        val viewModel = editor(document())

        viewModel.onChangeTheme("Nord")
        viewModel.await { it.document.themeName == "Nord" }
        viewModel.onChangeTheme("Chartreuse")
        // Nothing to wait for, so wait for the event after it instead.
        viewModel.onToggleNotes()
        val settled = viewModel.await { !it.showNotes }

        assertEquals("Nord", settled.document.themeName)
    }

    @Test
    fun savingTheDecksLookAddsAThemeAndPutsTheDeckOnIt() = runTest {
        val viewModel = editor(document())

        viewModel.onSetDocumentBackground(SlideBackground.Color(0xFF102030))
        viewModel.await { it.document.background != null }

        viewModel.onSaveAsTheme("Mine")
        val saved = viewModel.await { it.userThemes.any { theme -> theme.name == "Mine" } }

        assertEquals("Mine", saved.document.themeName)
        assertTrue(saved.themes.any { it.name == "Mine" })
        assertEquals(BuiltInThemes.all.size + 1, saved.themes.size)

        val mine = saved.userThemes.first { it.name == "Mine" }
        assertEquals(SlideBackground.Color(0xFF102030), mine.background)
        assertEquals(saved.document.layouts, mine.layouts)

        // The name is a document edit, the library entry is not: undo takes the
        // name back and leaves the saved theme where it is.
        viewModel.onUndo()
        val undone = viewModel.await { it.document.themeName == "Cupboard" }
        assertTrue(undone.userThemes.any { it.name == "Mine" })
    }

    @Test
    fun savingTheSameNameTwiceReplacesRatherThanPilesUp() = runTest {
        val viewModel = editor(document())

        viewModel.onSaveAsTheme("Mine")
        viewModel.await { it.userThemes.size == 1 }

        viewModel.onChangeTheme("Terminal")
        viewModel.await { it.document.themeName == "Terminal" }
        viewModel.onSaveAsTheme("Mine")
        val saved = viewModel.await {
            it.userThemes.singleOrNull()?.defaults == BuiltInThemes.Terminal.defaults
        }

        assertEquals(1, saved.userThemes.size)
    }

    @Test
    fun deletingAUserThemeTouchesNothingButTheLibrary() = runTest {
        val viewModel = editor(document())

        viewModel.onSaveAsTheme("Mine")
        val saved = viewModel.await { it.userThemes.isNotEmpty() }

        viewModel.onDeleteUserTheme("Mine")
        val deleted = viewModel.await { it.userThemes.isEmpty() }

        // The deck stays on the theme it was saved as, look and all: deleting the
        // library entry is not undoing the save.
        assertEquals("Mine", deleted.document.themeName)
        assertEquals(saved.document, deleted.document)
        assertEquals(BuiltInThemes.all.size, deleted.themes.size)
        // No history entry of its own, so undo still reaches the save.
        assertTrue(deleted.canUndo)
    }

    @Test
    fun theDeckWideBackgroundIsOneUndo() = runTest {
        val viewModel = editor(document())
        val background = SlideBackground.Gradient(start = 0xFF111111, end = 0xFF222222)

        viewModel.onSetDocumentBackground(background)
        val set = viewModel.await { it.document.background != null }
        assertEquals(background, set.document.background)
        assertTrue(set.canUndo)

        viewModel.onUndo()
        val back = viewModel.await { it.document.background == null }
        assertNull(back.document.background)
        assertFalse(back.canUndo)
    }

    /** What a shell dresses an insertion from: the deck's, and so the theme's. */
    @Test
    fun theDefaultsAShellInsertsWithAreTheThemes() = runTest {
        val viewModel = editor(document())
        assertEquals(ElementDefaults(), viewModel.states.value.defaults)

        viewModel.onChangeTheme("Terminal")
        val themed = viewModel.await { it.document.themeName == "Terminal" }
        assertEquals(BuiltInThemes.Terminal.defaults, themed.defaults)
    }
}

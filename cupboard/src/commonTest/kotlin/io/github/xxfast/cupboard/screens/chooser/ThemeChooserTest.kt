package io.github.xxfast.cupboard.screens.chooser

import io.github.xxfast.cupboard.document.BuiltInThemes
import io.github.xxfast.cupboard.document.SlideSizePreset
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** The new-deck chooser: three picks, and the one rule about keeping a theme across categories. */
class ThemeChooserTest {
    /** Wired the way a shell wires one: something has to collect before events land. */
    private fun TestScope.chooser(): ThemeChooserViewModel {
        val viewModel = ThemeChooserViewModel()
        backgroundScope.launch(Dispatchers.Unconfined, start = UNDISPATCHED) {
            try {
                viewModel.states.collect { }
            } finally {
                viewModel.close()
            }
        }
        return viewModel
    }

    private suspend fun ThemeChooserViewModel.await(
        predicate: (ThemeChooserState) -> Boolean,
    ): ThemeChooserState = withContext(Dispatchers.Default) {
        withTimeout(5.seconds) { states.first(predicate) }
    }

    @Test
    fun opensOnCupboardAtWidescreen() = runTest {
        val state: ThemeChooserState = chooser().states.value

        assertEquals("Cupboard", state.selectedCategory?.name)
        assertEquals(BuiltInThemes.Cupboard, state.selectedTheme)
        assertEquals(SlideSizePreset.Widescreen, state.size)
    }

    @Test
    fun anotherCategorySelectsItsFirstTheme() = runTest {
        val viewModel: ThemeChooserViewModel = chooser()

        viewModel.onSelectCategory("Basic")
        val state: ThemeChooserState = viewModel.await { it.category == "Basic" }

        assertEquals(BuiltInThemes.BasicWhite, state.selectedTheme)
    }

    @Test
    fun aCategoryThatHoldsTheSelectedThemeKeepsIt() = runTest {
        val viewModel: ThemeChooserViewModel = chooser()
        viewModel.onSelectTheme("Nord")
        viewModel.await { it.theme == "Nord" }

        // Events fold in order, so the size landing means the category has too.
        viewModel.onSelectCategory("Cupboard")
        viewModel.onSelectSize(SlideSizePreset.Standard)
        val state: ThemeChooserState = viewModel.await { it.size == SlideSizePreset.Standard }

        assertEquals(BuiltInThemes.Nord, state.selectedTheme)
    }

    @Test
    fun aCategoryThatDoesNotHoldItMovesThePickToItsFirst() = runTest {
        val viewModel: ThemeChooserViewModel = chooser()
        viewModel.onSelectCategory("Basic")
        viewModel.onSelectTheme("Black")
        viewModel.await { it.theme == "Black" }

        viewModel.onSelectCategory("Cupboard")
        val state: ThemeChooserState = viewModel.await { it.category == "Cupboard" }

        assertEquals(BuiltInThemes.Cupboard, state.selectedTheme)
    }

    @Test
    fun anUnknownCategoryChangesNothing() = runTest {
        val viewModel: ThemeChooserViewModel = chooser()

        viewModel.onSelectCategory("Premium")
        viewModel.onSelectSize(SlideSizePreset.Standard)
        val state: ThemeChooserState = viewModel.await { it.size == SlideSizePreset.Standard }

        assertEquals("Cupboard", state.category)
        assertEquals("Cupboard", state.theme)
    }
}

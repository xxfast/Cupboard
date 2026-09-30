package io.github.xxfast.cupboard.screens.chooser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.ThemeCategory
import io.github.xxfast.cupboard.screens.chooser.ThemeChooserEvent.SelectCategory
import io.github.xxfast.cupboard.screens.chooser.ThemeChooserEvent.SelectSize
import io.github.xxfast.cupboard.screens.chooser.ThemeChooserEvent.SelectTheme
import kotlinx.coroutines.flow.Flow

/**
 * The chooser's whole loop: three picks folded into state, nothing else.
 *
 * No side effects. Create is the shell's, because what it does next (a bundle,
 * a window) is hosting, and Cancel is closing the sheet.
 */
@Composable
fun ThemeChooserPresenter(
    initialState: ThemeChooserState,
    events: Flow<ThemeChooserEvent>,
): ThemeChooserState {
    var state: ThemeChooserState by remember { mutableStateOf(initialState) }

    LaunchedEffect(Unit) {
        events.collect { event ->
            state = when (event) {
                is SelectCategory -> {
                    val category: ThemeCategory = state.categories
                        .firstOrNull { it.name == event.name }
                        ?: return@collect

                    val theme: Theme? = category.themes
                        .firstOrNull { it.name == state.theme }
                        ?: category.themes.firstOrNull()

                    state.copy(category = category.name, theme = theme?.name ?: state.theme)
                }

                is SelectTheme -> state.copy(theme = event.name)

                is SelectSize -> state.copy(size = event.preset)
            }
        }
    }

    return state
}

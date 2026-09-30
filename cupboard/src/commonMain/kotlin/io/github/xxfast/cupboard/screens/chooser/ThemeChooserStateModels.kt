package io.github.xxfast.cupboard.screens.chooser

import io.github.xxfast.cupboard.document.BuiltInThemes
import io.github.xxfast.cupboard.document.SlideSizePreset
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.ThemeCategory
import kotlinx.serialization.Serializable

/**
 * Everything the new-deck chooser shows, as one value: Keynote's File > New.
 *
 * The picks are names rather than objects, so a state restored against a later
 * build's themes still points at the same ones. [selectedCategory] and
 * [selectedTheme] are the lookups, computed and never stored.
 */
@Serializable
data class ThemeChooserState(
    val categories: List<ThemeCategory> = BuiltInThemes.categories,
    val category: String = "Cupboard",
    val theme: String = "Cupboard",
    /** Wide or Standard: what the thumbnails are drawn at, and what Create makes. */
    val size: SlideSizePreset = SlideSizePreset.Widescreen,
) {
    val selectedCategory: ThemeCategory?
        get() = categories.firstOrNull { it.name == category }

    /** The theme Create puts the new deck on; null when the category shown doesn't hold it. */
    val selectedTheme: Theme?
        get() = selectedCategory?.themes?.firstOrNull { it.name == theme }
}

sealed interface ThemeChooserEvent {
    /**
     * Keeps the selected theme when this category holds it too, and otherwise
     * moves the selection to the category's first: a grid with nothing ringed is
     * a Create button that makes nothing.
     */
    data class SelectCategory(val name: String) : ThemeChooserEvent

    data class SelectTheme(val name: String) : ThemeChooserEvent

    data class SelectSize(val preset: SlideSizePreset) : ThemeChooserEvent
}

package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.text.font.FontFamily
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextFont

/**
 * The face [element] draws in: the deck's own family under its
 * [TextElement.fontName] where the deck carries one, else the system's family of
 * that name, else its [TextElement.fontFamily] the way [fontFamilyOf] resolves it.
 *
 * The one resolution every text draw site goes through (the renderers, the
 * in-place field, thumbnails, exports and play), so a box reads the same in all
 * of them. [deck] is [LocalDeckFonts] at the call site.
 */
internal fun fontFamilyOf(element: TextElement, deck: DeckFontFamilies): FontFamily =
    element.fontName
        ?.let { name -> deck[name] ?: systemFontFamily(name) }
        ?: fontFamilyOf(element.fontFamily)

/**
 * The face [font] draws in on this platform: the system's own face for a named
 * one where it is installed, its [TextFont.generic] family everywhere else.
 *
 * Platform because finding a system face by name is: Skia's font manager answers
 * it on every skiko target, and Android has no such lookup, so it always draws
 * the generic family.
 */
internal expect fun fontFamilyOf(font: TextFont): FontFamily

/**
 * The installed family called [name], null where the system has none by that
 * name. Android, with no lookup by name, never has one.
 */
internal expect fun systemFontFamily(name: String): FontFamily?

/**
 * Every family this machine has installed, by the name a picker shows and a
 * [TextElement.fontName] stores: sorted case aside, once each, without the
 * hidden system faces (the ones whose names start with a dot).
 *
 * Empty on Android, which has no way to list them. What the Compose font picker
 * offers; the macOS shell asks `NSFontManager` itself.
 */
expect fun systemFontFamilies(): List<String>

/**
 * Whether text set in [name] draws in it here: the deck carries the family
 * ([deckFamilies], `EditorState.deckFontFamilies`) or the system has it
 * installed. False is what an inspector flags as missing, since that text is
 * drawing in its fallback.
 */
fun isFontAvailable(name: String, deckFamilies: List<String>): Boolean =
    deckFamilies.any { it.equals(name, ignoreCase = true) } || systemFontFamily(name) != null

/** The three generic families, which every platform has. */
internal fun genericFontFamily(font: TextFont): FontFamily = when (font.generic) {
    TextFont.Serif -> FontFamily.Serif
    TextFont.Monospace -> FontFamily.Monospace
    else -> FontFamily.SansSerif
}

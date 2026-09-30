package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.text.font.FontFamily
import io.github.xxfast.cupboard.document.TextFont

/**
 * The face [font] draws in on this platform: the system's own face for a named
 * one where it is installed, its [TextFont.generic] family everywhere else.
 *
 * Platform because finding a system face by name is: Skia's font manager answers
 * it on every skiko target, and Android has no such lookup, so it always draws
 * the generic family.
 */
internal expect fun fontFamilyOf(font: TextFont): FontFamily

/** The three generic families, which every platform has. */
internal fun genericFontFamily(font: TextFont): FontFamily = when (font.generic) {
    TextFont.Serif -> FontFamily.Serif
    TextFont.Monospace -> FontFamily.Monospace
    else -> FontFamily.SansSerif
}

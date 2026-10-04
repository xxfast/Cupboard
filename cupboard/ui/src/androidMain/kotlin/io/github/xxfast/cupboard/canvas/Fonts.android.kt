package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import io.github.xxfast.cupboard.document.DeckFont
import io.github.xxfast.cupboard.document.TextFont

/** No lookup by family name here, so every named face draws as its generic one. */
internal actual fun fontFamilyOf(font: TextFont): FontFamily = genericFontFamily(font)

/** No lookup by family name, so no name is ever installed: text falls back. */
internal actual fun systemFontFamily(name: String): FontFamily? = null

/** No way to list the system's families here, so the picker offers the deck's alone. */
actual fun systemFontFamilies(): List<String> = emptyList()

/**
 * Not loaded here: an Android font from bytes wants a file on disk, which this
 * canvas has nowhere to put. Deck fonts fall back like missing ones.
 */
internal actual fun deckFontOf(font: DeckFont, bytes: ByteArray): Font? = null

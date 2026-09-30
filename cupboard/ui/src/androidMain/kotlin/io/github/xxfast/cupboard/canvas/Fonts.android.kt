package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.text.font.FontFamily
import io.github.xxfast.cupboard.document.TextFont

/** No lookup by family name here, so every named face draws as its generic one. */
internal actual fun fontFamilyOf(font: TextFont): FontFamily = genericFontFamily(font)

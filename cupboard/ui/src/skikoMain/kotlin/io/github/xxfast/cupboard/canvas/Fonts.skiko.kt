package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont
import io.github.xxfast.cupboard.document.TextFont
import org.jetbrains.skia.FontMgr

/**
 * Every face resolved once: whether a named family is installed doesn't change
 * under a running app, and asking the font manager per text box would be a
 * system call per recomposition.
 */
private val families: Map<TextFont, FontFamily> by lazy {
    TextFont.entries.associateWith { font -> resolve(font) }
}

internal actual fun fontFamilyOf(font: TextFont): FontFamily = families.getValue(font)

/**
 * A named family as one [SystemFont] per weight a theme sets it in, so a
 * [FontWeight] on the text picks the matching cut rather than a synthesised
 * bold. A family the font manager has no faces for (the web, most Linux, a Mac
 * that never downloaded it) is its generic family instead.
 */
@OptIn(ExperimentalTextApi::class)
private fun resolve(font: TextFont): FontFamily {
    val name: String = font.familyName ?: return genericFontFamily(font)
    val installed: Boolean = FontMgr.default.matchFamily(name).count() > 0
    if (!installed) return genericFontFamily(font)

    return FontFamily(Weights.map { weight -> SystemFont(name, weight) })
}

private val Weights: List<FontWeight> = listOf(
    FontWeight.Light,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
)

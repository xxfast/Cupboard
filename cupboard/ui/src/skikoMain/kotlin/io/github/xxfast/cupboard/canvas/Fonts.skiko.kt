@file:OptIn(ExperimentalAtomicApi::class)

package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont
import io.github.xxfast.cupboard.document.DeckFont
import io.github.xxfast.cupboard.document.TextFont
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.FontStyle as SkiaFontStyle
import androidx.compose.ui.text.platform.Font as BytesFont

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
 * Every name looked up so far, installed or not, for [families]' reason. A free
 * name can be anything, so this fills as names are asked for rather than up
 * front. Swapped whole rather than written in place: the canvas and an export
 * on another thread can both be asking, and a lost write only costs a second
 * lookup.
 */
private val named: AtomicReference<Map<String, FontFamily?>> = AtomicReference(emptyMap())

internal actual fun systemFontFamily(name: String): FontFamily? {
    val known: Map<String, FontFamily?> = named.load()
    if (name in known) return known[name]

    val family: FontFamily? = installed(name)
    named.store(known + (name to family))
    return family
}

actual fun systemFontFamilies(): List<String> {
    val manager: FontMgr = FontMgr.default
    return (0 until manager.familiesCount)
        .map { index -> manager.getFamilyName(index) }
        .filterNot { it.isBlank() || it.startsWith(".") }
        .distinctBy { it.lowercase() }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
}

/**
 * Whichever face the font manager falls back to for an emoji-only character
 * (Apple Color Emoji, Segoe UI Emoji, Noto Color Emoji), found once. Asked by
 * character rather than by name so every platform finds its own.
 */
private val emoji: FontFamily? by lazy {
    val face: Typeface = FontMgr.default
        .matchFamilyStyleCharacter(null, SkiaFontStyle.NORMAL, null, 0x1F600)
        ?: return@lazy null
    installed(face.familyName)
}

internal actual fun emojiFontFamily(): FontFamily? = emoji

internal actual fun deckFontOf(font: DeckFont, bytes: ByteArray): Font? {
    // Made once up front to prove the bytes load: the text engine makes its own
    // typeface lazily, mid-draw, where a file that turned out to be garbage
    // would take the frame down with it.
    FontMgr.default.makeFromData(Data.makeFromBytes(bytes)) ?: return null
    return BytesFont(
        identity = font.assetId,
        data = bytes,
        weight = FontWeight(font.weight.coerceIn(1, 1000)),
        style = if (font.italic) FontStyle.Italic else FontStyle.Normal,
    )
}

/**
 * A named family as one [SystemFont] per weight a theme sets it in, so a
 * [FontWeight] on the text picks the matching cut rather than a synthesised
 * bold. A family the font manager has no faces for (the web, most Linux, a Mac
 * that never downloaded it) is its generic family instead.
 */
private fun resolve(font: TextFont): FontFamily {
    val name: String = font.familyName ?: return genericFontFamily(font)
    return installed(name) ?: genericFontFamily(font)
}

/** [name] as a family of system cuts, null where the font manager has no faces for it. */
@OptIn(ExperimentalTextApi::class)
private fun installed(name: String): FontFamily? {
    if (FontMgr.default.matchFamily(name).count() == 0) return null
    return FontFamily(Weights.map { weight -> SystemFont(name, weight) })
}

private val Weights: List<FontWeight> = listOf(
    FontWeight.Light,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
)

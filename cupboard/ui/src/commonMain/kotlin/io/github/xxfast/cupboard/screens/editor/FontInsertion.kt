package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.FontInfo
import io.github.xxfast.cupboard.document.newId
import io.github.xxfast.cupboard.document.parseFontInfo

/**
 * [bytes], a font file, written into the deck and added to its fonts, so text set
 * in its family draws in it wherever the deck is opened.
 *
 * Here rather than in a shell for [insertImage]'s reason: a file dialog and a
 * drop both end with bytes and an extension, and what happens next (read the
 * file for its family, write the asset, add the font) is the same three steps.
 * A cut the deck already has (the same family, weight and slant) is replaced.
 *
 * False is a file that is no TrueType or OpenType font (WOFF included), which
 * writes nothing and adds nothing.
 */
suspend fun EditorViewModel.addDeckFont(bytes: ByteArray, extension: String): Boolean {
    val info: FontInfo = parseFontInfo(bytes) ?: return false
    val assetId: String = "${newId()}.${extension.lowercase()}"
    assets.write(assetId, bytes)
    onAddDeckFont(assetId, info.family, info.weight, info.italic)
    return true
}

package io.github.xxfast.cupboard.canvas

import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.instantAlpha
import io.github.xxfast.cupboard.document.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * [assetId] with the region around ([seedX], [seedY]) rubbed out, written back as
 * a new asset, and that asset's id.
 *
 * A new id rather than a rewrite of the old one: the element still points at the
 * original until its caller moves it, so the edit is one document change that
 * undo puts back for free, and two elements sharing a picture don't both lose its
 * background because one of them was clicked. The bytes left behind go when the
 * bundle sweeps its unreferenced assets.
 *
 * The seed is in the image's own pixels, and [tolerance] is `instantAlpha`'s
 * 0..1. An id the store has nothing under comes back unchanged: nothing to rub
 * out is not an error.
 */
suspend fun AssetStore.removeBackground(
    assetId: String,
    seedX: Int,
    seedY: Int,
    tolerance: Float,
): String {
    val bytes: ByteArray = read(assetId) ?: return assetId

    // Every step of this is megabytes of pixels: decode, fill and re-encode all
    // stay off whichever thread asked for them.
    val png: ByteArray = withContext(Dispatchers.Default) {
        val image = bytes.decodeToImageBitmap()
        val cleared: IntArray =
            instantAlpha(image.argbPixels(), image.width, image.height, seedX, seedY, tolerance)
        encodePng(argbImageBitmap(cleared, image.width, image.height))
    }

    val id = "${newId()}.png"
    write(id, png)
    return id
}

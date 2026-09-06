package io.github.xxfast.cupboard.screens.editor

import androidx.compose.ui.graphics.ImageBitmap
import io.github.xxfast.cupboard.document.DefaultImageHeight
import io.github.xxfast.cupboard.document.DefaultImageWidth
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.fitted
import io.github.xxfast.cupboard.document.imageElement
import io.github.xxfast.cupboard.document.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The image files an insert accepts, lowercase and without the dot: what a file
 * dialog filters on and what a drop is sifted for.
 */
val ImageExtensions: List<String> = listOf("png", "jpg", "jpeg", "gif", "webp")

/** How much of the slide a freshly inserted image is allowed to take. */
private const val InsertedImageFraction: Float = 0.6f

/**
 * [bytes] written into the deck and put on the selected slide as an image.
 *
 * Here rather than in a shell because every way an image arrives ends the same:
 * a file dialog, a drop and a paste all have bytes and an extension, and what
 * happens next (decode for the natural size, write the asset, insert an element
 * fitted to it) is the same three steps whichever of them asked.
 *
 * [insertionFrame] is `EditorState.insertionFrame`: the caller owns where an
 * insertion lands, this only says how big a box to ask for. The element is then
 * fitted so no picture arrives covering the slide.
 *
 * False is a file the toolkit could not decode, which inserts nothing and writes
 * nothing rather than taking the window down: a shell hands over whatever was
 * dropped on it, and not all of that is an image.
 */
suspend fun EditorViewModel.insertImage(
    bytes: ByteArray,
    extension: String,
    insertionFrame: (Float, Float) -> Frame,
): Boolean {
    val image: ImageBitmap = decodeImage(bytes) ?: return false
    val assetId: String = "${newId()}.${extension.lowercase()}"
    assets.write(assetId, bytes)

    val state: EditorState = states.value
    onInsertElement(
        imageElement(
            frame = insertionFrame(DefaultImageWidth, DefaultImageHeight),
            assetId = assetId,
            naturalWidth = image.width,
            naturalHeight = image.height,
            defaults = state.defaults,
        ).fitted(
            maxWidth = state.document.slideWidth * InsertedImageFraction,
            maxHeight = state.document.slideHeight * InsertedImageFraction,
        ),
    )
    return true
}

/**
 * [element] pointed at [bytes] instead, written in as a new asset.
 *
 * The box stays where it is: replacing a picture is swapping what is inside a
 * frame you already placed. The mask goes, since the window it named was a
 * window onto the old picture, and the adjustments and the caption stay, since
 * neither says anything about which bytes are behind them.
 */
suspend fun EditorViewModel.replaceImage(
    element: ImageElement,
    bytes: ByteArray,
    extension: String,
): Boolean {
    val image: ImageBitmap = decodeImage(bytes) ?: return false
    val assetId: String = "${newId()}.${extension.lowercase()}"
    assets.write(assetId, bytes)

    onUpdateElements(
        listOf(
            element.copy(
                assetId = assetId,
                naturalWidth = image.width,
                naturalHeight = image.height,
                mask = null,
            ),
        ),
    )
    return true
}

/** Off the caller's thread: a full-slide photo is megabytes even to size. */
private suspend fun decodeImage(bytes: ByteArray): ImageBitmap? = withContext(Dispatchers.Default) {
    runCatching { bytes.decodeToImageBitmap() }.getOrNull()
}

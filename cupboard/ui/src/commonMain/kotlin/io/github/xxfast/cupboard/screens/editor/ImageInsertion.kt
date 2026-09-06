package io.github.xxfast.cupboard.screens.editor

import androidx.compose.ui.graphics.ImageBitmap
import io.github.xxfast.cupboard.document.DefaultImageHeight
import io.github.xxfast.cupboard.document.DefaultImageWidth
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GalleryElement
import io.github.xxfast.cupboard.document.GalleryImage
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.fitted
import io.github.xxfast.cupboard.document.galleryElement
import io.github.xxfast.cupboard.document.imageElement
import io.github.xxfast.cupboard.document.newId
import kotlin.math.min
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

/**
 * [files] written into the deck and put on the selected slide as one gallery.
 *
 * The multi-file twin of [insertImage], and the same three steps per file: decode
 * for the natural size, write the asset, then one element for the lot of them.
 * Several pictures arriving together are usually one thing (a run of screenshots,
 * a set of frames), so they land as one box that is stepped through rather than
 * as a pile of images to be tidied up by hand.
 *
 * Files the toolkit could not decode drop out rather than taking the insert down,
 * for [insertImage]'s reason: a shell hands over whatever was dropped on it. All
 * of them undecodable is nothing to insert, which is false and no element.
 *
 * The box is capped the way an inserted image is, and [galleryElement] fits the
 * first picture's aspect inside it: a gallery has to be one shape, and the one
 * the audience sees first is the one worth fitting.
 */
suspend fun EditorViewModel.insertGallery(
    files: List<Pair<ByteArray, String>>,
    insertionFrame: (Float, Float) -> Frame,
): Boolean {
    val images: List<GalleryImage> = writeGalleryImages(files)
    if (images.isEmpty()) return false

    val state: EditorState = states.value
    onInsertElement(
        galleryElement(
            frame = insertionFrame(
                min(DefaultImageWidth, state.document.slideWidth * InsertedImageFraction),
                min(DefaultImageHeight, state.document.slideHeight * InsertedImageFraction),
            ),
            images = images,
            defaults = state.defaults,
        ),
    )
    return true
}

/**
 * [element] with [files] added to the end of it, written in as new assets.
 *
 * The box stays where it is, the way a replaced image's does: adding to a gallery
 * is putting more pictures in a frame you already placed, and re-fitting it to a
 * picture nobody has seen yet would move a box the user positioned.
 *
 * The current image stays where it is too. Adding at the end changes nothing
 * about the one being authored.
 */
suspend fun EditorViewModel.addGalleryImages(
    element: GalleryElement,
    files: List<Pair<ByteArray, String>>,
): Boolean {
    val images: List<GalleryImage> = writeGalleryImages(files)
    if (images.isEmpty()) return false

    onUpdateElements(listOf(element.copy(images = element.images + images)))
    return true
}

/** Every one of [files] that decoded, written into the deck as an asset apiece. */
private suspend fun EditorViewModel.writeGalleryImages(
    files: List<Pair<ByteArray, String>>,
): List<GalleryImage> = files.mapNotNull { (bytes, extension) ->
    val image: ImageBitmap = decodeImage(bytes) ?: return@mapNotNull null
    val assetId: String = "${newId()}.${extension.lowercase()}"
    assets.write(assetId, bytes)

    GalleryImage(
        assetId = assetId,
        naturalWidth = image.width,
        naturalHeight = image.height,
    )
}

/** Off the caller's thread: a full-slide photo is megabytes even to size. */
private suspend fun decodeImage(bytes: ByteArray): ImageBitmap? = withContext(Dispatchers.Default) {
    runCatching { bytes.decodeToImageBitmap() }.getOrNull()
}

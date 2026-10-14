package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.InMemoryAssetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Where the canvas resolves the asset ids the document carries.
 *
 * A composition local rather than a parameter threaded through every renderer:
 * an element deep inside a group needs the store as much as a top-level one does,
 * and nothing between them has any business knowing about bytes. Each shell
 * provides its own document's store around the canvas.
 *
 * The default is an empty in-memory store, so a preview, a thumbnail or a test
 * that has no bytes to show draws every image as its placeholder rather than
 * failing to compose.
 */
val LocalAssetStore = compositionLocalOf<AssetStore> { InMemoryAssetStore() }

/**
 * The decoded images shared across the canvas. Provided at the composition root
 * by each shell so that a slide, its thumbnail and the presenter's copy of it all
 * decode the same bytes once.
 */
val LocalAssetImages = staticCompositionLocalOf { AssetImageCache() }

/**
 * Decoded images kept by asset id, the least recently used dropped first.
 *
 * Small on purpose: a deck's images are large, and what this is for is the handful
 * on screen at once being decoded once rather than once per composable that draws
 * them. Not thread-safe, and read only from the composition, which is one thread.
 *
 * [onPut] hears of every image as it lands. A one frame render (a thumbnail)
 * draws a placeholder for whatever is not held yet, and nothing about the slide
 * changes when the decode the canvas started finishes, so that landing is the
 * only news there is.
 */
class AssetImageCache(
    private val capacity: Int = 24,
    private val onPut: (id: String) -> Unit = {},
) {
    private val images: LinkedHashMap<String, ImageBitmap> = LinkedHashMap()

    /** Where [get] notes what it came up empty for, while [recordingMisses] is running. */
    private var misses: MutableSet<String>? = null

    /** The image under [id] if it is held, promoted to most recently used. */
    fun get(id: String): ImageBitmap? {
        val image: ImageBitmap? = images.remove(id)
        if (image == null) {
            misses?.add(id)
            return null
        }

        images[id] = image
        return image
    }

    fun put(id: String, image: ImageBitmap) {
        images.remove(id)
        images[id] = image
        while (images.size > capacity) images.remove(images.keys.first())
        onPut(id)
    }

    /**
     * [draw], with every id it asked for and this did not hold added to [into]:
     * the pictures a one frame render drew as placeholders, which is what it is
     * waiting on. Walks no document, so it is right for every kind of element
     * that draws a picture, inside a group or out.
     */
    fun <T> recordingMisses(into: MutableSet<String>, draw: () -> T): T {
        misses = into
        try {
            return draw()
        } finally {
            misses = null
        }
    }
}

/**
 * The decoded image behind [assetId], or null while it is being read: null for a
 * null id, and null for an id the store has nothing under.
 *
 * A cache hit is the initial value rather than something a frame later, so an
 * image that is already decoded draws on the first composition and a slide never
 * flashes its placeholder on the way back to a picture it just drew.
 *
 * Keyed on the cache as well as the id: a file swapped on disk under the open
 * bundle is the same id with new bytes, so the id alone never asks for a second
 * read. The screen provides a fresh cache per `EditorState.assetGeneration`, and
 * a fresh cache is what makes this decode again.
 */
@Composable
fun rememberAssetImage(assetId: String?): ImageBitmap? {
    val store: AssetStore = LocalAssetStore.current
    val cache: AssetImageCache = LocalAssetImages.current
    var image: ImageBitmap? by remember(assetId, store, cache) {
        mutableStateOf(assetId?.let { cache.get(it) })
    }

    LaunchedEffect(assetId, store, cache) {
        if (assetId == null || cache.get(assetId) != null) return@LaunchedEffect
        // Off the composition's thread: a full-slide photo is megabytes, and a
        // decode on the main thread is a dropped frame every time one lands.
        val decoded: ImageBitmap = withContext(Dispatchers.Default) {
            store.read(assetId)?.decodeToImageBitmap()
        } ?: return@LaunchedEffect

        cache.put(assetId, decoded)
        image = decoded
    }

    return image
}

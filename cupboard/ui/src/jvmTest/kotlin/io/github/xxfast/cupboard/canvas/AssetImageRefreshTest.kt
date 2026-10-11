package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.InMemoryAssetStore
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * A file swapped on disk under the open bundle keeps its asset id, so the only
 * thing that tells [rememberAssetImage] to read it again is a new
 * [AssetImageCache], which the editor screen provides once per
 * `EditorState.assetGeneration`.
 *
 * A scene rendered frame after frame rather than `renderComposeScene`'s one: the
 * decode is a hop to `Dispatchers.Default` and back, and the point here is the
 * read that happens after the first one.
 */
class AssetImageRefreshTest {
    private val red: Int = 0xFFE5484D.toInt()
    private val blue: Int = 0xFF3E63DD.toInt()

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun aNewCacheReadsTheSameIdAgain() {
        val store = CountingAssetStore(InMemoryAssetStore())
        runBlocking { store.write(ASSET, flat(red)) }

        var cache: AssetImageCache by mutableStateOf(AssetImageCache())
        var image: ImageBitmap? = null
        val scene = ImageComposeScene(width = 1, height = 1)
        scene.setContent {
            CompositionLocalProvider(
                LocalAssetStore provides store,
                LocalAssetImages provides cache,
            ) {
                image = rememberAssetImage(ASSET)
            }
        }

        try {
            scene.renderUntil { image?.argbPixels()?.first() == red }

            runBlocking { store.write(ASSET, flat(blue)) }
            cache = AssetImageCache()
            scene.renderUntil { image?.argbPixels()?.first() == blue }

            assertEquals(2, store.reads.get(), "one read per cache, not one per id")
        } finally {
            scene.close()
        }
    }

    private fun flat(color: Int): ByteArray =
        encodePng(argbImageBitmap(IntArray(4 * 4) { color }, 4, 4))

    private fun ImageComposeScene.renderUntil(condition: () -> Boolean) {
        var frame = 0L
        repeat(200) {
            render(frame++ * FRAME_NANOS)
            if (condition()) return
            Thread.sleep(10)
        }
        fail("the image never showed the bytes the store holds")
    }

    private class CountingAssetStore(private val store: AssetStore) : AssetStore by store {
        val reads = AtomicInteger()

        override suspend fun read(id: String): ByteArray? = store.read(id).also { reads.incrementAndGet() }
    }

    private companion object {
        const val ASSET: String = "swapped.png"
        const val FRAME_NANOS: Long = 16_000_000L
    }
}

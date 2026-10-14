package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.renderComposeScene
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.VideoElement
import io.github.xxfast.cupboard.document.withImageFill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A navigator thumbnail is one frame, so a picture the canvas has not decoded
 * yet draws as its placeholder there. [AssetImageCache.recordingMisses] is how
 * the host learns which pictures a thumbnail went without, and its `onPut` is
 * how it learns one of them has landed.
 */
class AssetImageMissesTest {
    private val image: ImageBitmap = argbImageBitmap(IntArray(4 * 4) { 0xFFE5484D.toInt() }, 4, 4)

    /** A picture fill, a picture background and a movie's poster: one of each way a slide shows one. */
    private val slide = Slide(
        background = SlideBackground.Image("background.png"),
        elements = listOf(
            ShapeElement(frame = Frame(10f, 10f, 80f, 80f)).withImageFill("fill.png"),
            VideoElement(
                frame = Frame(100f, 10f, 80f, 45f),
                assetId = "talk.mp4",
                posterAssetId = "poster.png",
            ),
        ),
    )

    @Test
    fun aRenderNotesEveryPictureItDrewWithout() {
        val cache = AssetImageCache()
        cache.put("fill.png", image)

        val missing: Set<String> = cache.missesDrawing()

        assertEquals(setOf("background.png", "poster.png"), missing)
    }

    @Test
    fun aRenderWithEverythingHeldMissesNothing() {
        val cache = AssetImageCache()
        listOf("background.png", "fill.png", "poster.png").forEach { cache.put(it, image) }

        assertTrue(cache.missesDrawing().isEmpty())
    }

    @Test
    fun onlyAskingInsideTheRecordingCounts() {
        val cache = AssetImageCache()
        val missing: MutableSet<String> = mutableSetOf()

        cache.recordingMisses(missing) { cache.get("inside.png") }
        cache.get("outside.png")

        assertEquals(setOf("inside.png"), missing)
    }

    @Test
    fun aPutIsHeardAsItLands() {
        val landed: MutableList<String> = mutableListOf()
        val cache = AssetImageCache(onPut = { landed += it })

        cache.put("fill.png", image)

        assertEquals(listOf("fill.png"), landed)
    }

    /** [slide] drawn once, the way the macOS host draws a thumbnail. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun AssetImageCache.missesDrawing(): Set<String> {
        val missing: MutableSet<String> = mutableSetOf()
        recordingMisses(missing) {
            renderComposeScene(200, 200) {
                CompositionLocalProvider(LocalAssetImages provides this) {
                    SlideView(slide, slideWidth = 200f, slideHeight = 200f)
                }
            }
        }
        return missing
    }
}

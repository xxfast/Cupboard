package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GalleryElement
import io.github.xxfast.cupboard.document.GalleryImage
import io.github.xxfast.cupboard.document.InMemoryAssetStore
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which picture of a gallery is on screen, read back a pixel at a time.
 *
 * Two flat 64x48 PNGs, one per image, so the colour at the centre of the box says
 * which of them the step landed on. The cache is primed before the scene renders
 * for `ImageRenderTest`'s reason: `renderComposeScene` draws one frame, and a
 * decode is a suspend away.
 */
class GalleryRenderTest {
    private val red: Int = 0xFFE5484D.toInt()
    private val blue: Int = 0xFF3E63DD.toInt()

    private val bytes: Map<String, ByteArray> = mapOf(
        FIRST to encodePng(argbImageBitmap(IntArray(64 * 48) { red }, 64, 48)),
        SECOND to encodePng(argbImageBitmap(IntArray(64 * 48) { blue }, 64, 48)),
    )

    private val element = GalleryElement(
        frame = Frame(20f, 20f, 160f, 120f),
        images = listOf(GalleryImage(FIRST, 64, 48), GalleryImage(SECOND, 64, 48)),
    )

    @Test
    fun aGalleryDrawsThePictureItsStepNames() {
        assertEquals(red, render(element, imageIndex = 0).getColor(100, 80), "the first picture")
        assertEquals(blue, render(element, imageIndex = 1).getColor(100, 80), "the second")
    }

    @Test
    fun theEditorDrawsThePictureBeingAuthored() {
        // No index at all is the editor, which shows `current` rather than a step.
        val second: GalleryElement = element.copy(current = 1)

        assertEquals(red, render(element, imageIndex = null).getColor(100, 80))
        assertEquals(blue, render(second, imageIndex = null).getColor(100, 80))
    }

    /**
     * [element] drawn on a 200x200 slide at native size, so one document unit is
     * one pixel of the result and the assertions can name coordinates.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(element: GalleryElement, imageIndex: Int?): Bitmap {
        val store = InMemoryAssetStore()
        val cache = AssetImageCache()
        for ((id, png) in bytes) {
            runBlocking { store.write(id, png) }
            cache.put(id, png.decodeToImageBitmap())
        }

        val image: Image = renderComposeScene(200, 200, density = Density(1f)) {
            CompositionLocalProvider(
                LocalAssetStore provides store,
                LocalAssetImages provides cache,
            ) {
                SlideSurface(zoom = 1f, slideWidth = 200f, slideHeight = 200f) {
                    ElementView(element, galleryIndex = imageIndex)
                }
            }
        }

        val pixels = Bitmap()
        pixels.allocN32Pixels(image.width, image.height)
        image.readPixels(pixels)
        return pixels
    }

    private companion object {
        const val FIRST: String = "one.png"
        const val SECOND: String = "two.png"
    }
}

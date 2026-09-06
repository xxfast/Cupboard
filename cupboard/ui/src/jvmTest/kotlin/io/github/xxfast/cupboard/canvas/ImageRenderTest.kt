package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ImageAdjust
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.ImageMask
import io.github.xxfast.cupboard.document.InMemoryAssetStore
import io.github.xxfast.cupboard.document.ShapeKind
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * What an image actually draws, read back a pixel at a time.
 *
 * The deck's own sample stays asset-free, so the bytes here are made on the spot:
 * a 64x48 PNG of one flat colour, which is enough to say where the picture is and
 * where it is not. The cache is primed before the scene renders because
 * `renderComposeScene` draws one frame and a decode is a suspend away, so an
 * un-primed image would only ever be caught mid-load as its placeholder.
 */
class ImageRenderTest {
    private val red: Int = 0xFFE5484D.toInt()
    private val png: ByteArray = encodePng(argbImageBitmap(IntArray(64 * 48) { red }, 64, 48))

    /** Where the element sits in a 200x200 slide, and so in the rendered pixels. */
    private val box = Frame(20f, 20f, 160f, 120f)

    @Test
    fun anImageDrawsItsBytesAcrossItsBox() {
        val element = ImageElement(
            frame = box,
            assetId = ASSET,
            naturalWidth = 64,
            naturalHeight = 48,
            mask = ImageMask(frame = Frame(0f, 0f, 1f, 1f)),
        )

        val pixels: Bitmap = render(element)

        assertEquals(red, pixels.getColor(100, 80), "the centre of the image")
        // A rectangular mask crops nothing, so the corners are the picture too.
        assertEquals(red, pixels.getColor(22, 22), "the top-left of the image")
    }

    @Test
    fun anEllipseMaskCutsTheCornersBackToTheSlide() {
        val element = ImageElement(
            frame = box,
            assetId = ASSET,
            naturalWidth = 64,
            naturalHeight = 48,
            mask = ImageMask(ShapeKind.Ellipse, Frame(0f, 0f, 1f, 1f)),
        )

        val pixels: Bitmap = render(element)
        val background: Bitmap = render(element = null)

        assertEquals(red, pixels.getColor(100, 80), "the centre of the image")
        assertNotEquals(red, pixels.getColor(22, 22), "the corner outside the ellipse")
        assertEquals(
            background.getColor(22, 22),
            pixels.getColor(22, 22),
            "the corner is the slide showing through",
        )
    }

    @Test
    fun aMaskWindowShowsOnlyThePartOfTheImageItNames() {
        // The bytes are two colours side by side, so which half is on screen says
        // whether the window was honoured.
        val blue = 0xFF3E63DD.toInt()
        val split: ByteArray = encodePng(
            argbImageBitmap(IntArray(64 * 48) { if (it % 64 < 32) red else blue }, 64, 48),
        )

        val element = ImageElement(
            frame = box,
            assetId = ASSET,
            naturalWidth = 64,
            naturalHeight = 48,
            // The right half only, which is entirely blue.
            mask = ImageMask(frame = Frame(0.5f, 0f, 0.5f, 1f)),
        )

        val pixels: Bitmap = render(element, bytes = split)

        assertEquals(blue, pixels.getColor(40, 80), "the left of the box")
        assertEquals(blue, pixels.getColor(160, 80), "the right of the box")
    }

    @Test
    fun anAdjustmentRepaintsThePixelsWithoutTouchingTheBytes() {
        val element = ImageElement(
            frame = box,
            assetId = ASSET,
            naturalWidth = 64,
            naturalHeight = 48,
            adjust = ImageAdjust(saturation = 0f),
        )

        val drained: Int = render(element).getColor(100, 80)
        val plain: Int = render(element.copy(adjust = ImageAdjust())).getColor(100, 80)

        assertEquals(red, plain, "the identity adjustment leaves the colour alone")
        assertNotEquals(red, drained, "no saturation is not the colour it started as")
        // Grey: the three channels have been mixed to one value.
        assertEquals((drained shr 16) and 0xFF, (drained shr 8) and 0xFF)
        assertEquals((drained shr 8) and 0xFF, drained and 0xFF)
    }

    @Test
    fun removingABackgroundWritesANewAssetAndClearsTheSeededRegion() = runBlocking {
        val store = InMemoryAssetStore()
        store.write(ASSET, png)

        val id: String = store.removeBackground(ASSET, seedX = 0, seedY = 0, tolerance = 0.05f)

        assertNotEquals(ASSET, id, "the edit lands under an id of its own")
        assertEquals(png.toList(), store.read(ASSET)?.toList(), "the original is left alone")

        val cleared = store.read(id)!!.decodeToImageBitmap().argbPixels()
        assertEquals(0, cleared[0] ushr 24, "the whole flat image was the seed's region")
    }

    /**
     * [element] drawn on a 200x200 slide at native size, so one document unit is
     * one pixel of the result and the assertions can name coordinates.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(element: ImageElement?, bytes: ByteArray = png): Bitmap {
        val store = InMemoryAssetStore()
        val cache = AssetImageCache()
        runBlocking { store.write(ASSET, bytes) }
        cache.put(ASSET, bytes.decodeToImageBitmap())

        val image: Image = renderComposeScene(200, 200, density = Density(1f)) {
            CompositionLocalProvider(
                LocalAssetStore provides store,
                LocalAssetImages provides cache,
            ) {
                SlideSurface(zoom = 1f, slideWidth = 200f, slideHeight = 200f) {
                    if (element != null) ElementView(element)
                }
            }
        }

        val pixels = Bitmap()
        pixels.allocN32Pixels(image.width, image.height)
        image.readPixels(pixels)
        return pixels
    }

    private companion object {
        const val ASSET: String = "test.png"
    }
}

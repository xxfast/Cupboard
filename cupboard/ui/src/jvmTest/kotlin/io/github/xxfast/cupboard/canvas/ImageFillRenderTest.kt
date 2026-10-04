package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ImageFillMode
import io.github.xxfast.cupboard.document.InMemoryAssetStore
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.withFillMode
import io.github.xxfast.cupboard.document.withImageFill
import io.github.xxfast.cupboard.export.SlideRasterizer
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Pictures in shapes and behind slides, read back a pixel at a time, the way
 * [ImageRenderTest] reads an image element: one flat colour of bytes made on the
 * spot, the cache primed so the one frame `renderComposeScene` draws has them.
 */
class ImageFillRenderTest {
    private val red: Int = 0xFFE5484D.toInt()
    private val blue: Long = 0xFF3E63DD
    private val green: Long = 0xFF30A46C

    /** A square of red, so a fit into a wide box leaves bands either side. */
    private val png: ByteArray = encodePng(argbImageBitmap(IntArray(40 * 40) { red }, 40, 40))

    /** Wide, so a square picture fitted into it leaves room either side. */
    private val box = Frame(20f, 40f, 160f, 80f)

    @Test
    fun aShapeFilledWithAPictureDrawsItInsideItsOutline() {
        val plain =
            ShapeElement(frame = box, kind = ShapeKind.Ellipse, fill = blue, strokeWidth = 0f)
        val filled: ShapeElement = plain.withImageFill(ASSET)

        val pixels: Bitmap = onSlide { ElementView(filled) }
        val unfilled: Bitmap = onSlide { ElementView(plain) }

        assertEquals(red, pixels.getColor(100, 80), "the middle of the shape is the picture")
        assertEquals(blue.toInt(), unfilled.getColor(100, 80), "and was the solid before it")
        // A fill overhangs a wide box top and bottom, and the ellipse cuts its
        // corners: what is outside the outline is the slide, not the picture.
        assertNotEquals(red, pixels.getColor(24, 44), "the corner outside the ellipse")
        assertEquals(unfilled.getColor(24, 44), pixels.getColor(24, 44))
    }

    @Test
    fun aFittedPictureLeavesTheSolidShowingEitherSide() {
        val fitted: ShapeElement = ShapeElement(frame = box, fill = blue, strokeWidth = 0f)
            .withImageFill(ASSET)
            .withFillMode(ImageFillMode.ScaleToFit)

        val pixels: Bitmap = onSlide { ElementView(fitted) }

        assertEquals(red, pixels.getColor(100, 80), "the picture, centred")
        assertEquals(blue.toInt(), pixels.getColor(30, 80), "the band to its left")
        assertEquals(blue.toInt(), pixels.getColor(170, 80), "the band to its right")
    }

    @Test
    fun aShapeWhosePictureIsMissingDrawsItsFillInstead() {
        val missing: ShapeElement = ShapeElement(frame = box, fill = blue, strokeWidth = 0f)
            .withImageFill("nowhere.png")

        val pixels: Bitmap = onSlide { ElementView(missing) }

        assertEquals(blue.toInt(), pixels.getColor(100, 80))
    }

    @Test
    fun aPictureBackgroundCoversTheSlide() {
        val slide = Slide(background = SlideBackground.Image(ASSET))

        val pixels: Bitmap = render { SlideView(slide, slideWidth = 200f, slideHeight = 200f) }
        val plain: Bitmap = render { SlideView(Slide(), slideWidth = 200f, slideHeight = 200f) }

        assertEquals(red, pixels.getColor(100, 100))
        assertNotEquals(plain.getColor(100, 100), pixels.getColor(100, 100))
    }

    @Test
    fun whatAPictureBackgroundLeavesShowsTheNextOneDown() {
        // The deck's green under the slide's picture: a wide slide fitting a
        // square picture shows it either side, and all over when the bytes are gone.
        val deck = SlideBackground.Color(green)
        val fitted = Slide(background = SlideBackground.Image(ASSET, ImageFillMode.ScaleToFit))
        val missing = Slide(background = SlideBackground.Image("nowhere.png"))

        val pixels: Bitmap = render(width = 200, height = 100) {
            SlideView(fitted, background = deck, slideWidth = 200f, slideHeight = 100f)
        }
        val fallback: Bitmap = render(width = 200, height = 100) {
            SlideView(missing, background = deck, slideWidth = 200f, slideHeight = 100f)
        }

        assertEquals(red, pixels.getColor(100, 50), "the picture, centred")
        assertEquals(green.toInt(), pixels.getColor(20, 50), "the deck to its left")
        assertEquals(green.toInt(), fallback.getColor(100, 50), "the deck in its place")
    }

    @Test
    fun theRasterizerDrawsAPictureBackgroundFromAWarmedCache() {
        val document = Document(
            slideWidth = 200f,
            slideHeight = 200f,
            slides = listOf(Slide(background = SlideBackground.Image(ASSET, ImageFillMode.Tile))),
        )
        val cache = AssetImageCache()
        cache.put(ASSET, png.decodeToImageBitmap())

        val frame = SlideRasterizer(document, width = 200, images = cache).renderPixels(0, 0)

        assertEquals(red, frame.argb[100 * 200 + 100])
    }

    /** [content] on a bare 200x200 slide, for an element drawn without a [Slide] around it. */
    private fun onSlide(content: @Composable () -> Unit): Bitmap = render {
        SlideSurface(zoom = 1f, slideWidth = 200f, slideHeight = 200f) { content() }
    }

    /** [content] drawn at native size, so one document unit is one pixel of the result. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(
        width: Int = 200,
        height: Int = 200,
        content: @Composable () -> Unit,
    ): Bitmap {
        val store = InMemoryAssetStore()
        val cache = AssetImageCache()
        runBlocking { store.write(ASSET, png) }
        cache.put(ASSET, png.decodeToImageBitmap())

        val image: Image = renderComposeScene(width, height, density = Density(1f)) {
            CompositionLocalProvider(
                LocalAssetStore provides store,
                LocalAssetImages provides cache,
            ) { content() }
        }

        val pixels = Bitmap()
        pixels.allocN32Pixels(image.width, image.height)
        image.readPixels(pixels)
        return pixels
    }

    private companion object {
        const val ASSET: String = "fill.png"
    }
}

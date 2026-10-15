package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.renderComposeScene
import io.github.xxfast.cupboard.document.BuiltInThemes
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.InMemoryAssetStore
import io.github.xxfast.cupboard.document.ShowcaseNightId
import io.github.xxfast.cupboard.document.ShowcasePictureId
import io.github.xxfast.cupboard.document.ShowcaseTileId
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideFields
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.layoutOf
import io.github.xxfast.cupboard.document.slideFields
import io.github.xxfast.cupboard.document.showcaseAssets
import io.github.xxfast.cupboard.document.showcaseDocument
import io.github.xxfast.cupboard.document.stepCount
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The showcase deck rendered to PNGs, for a human to look at.
 *
 * The deck exists to be looked at, so this is mostly a writer rather than an
 * assertion: what is wrong with a slide is almost never something a number would
 * have caught. The one thing asserted is that every step of every slide draws at
 * all, which is [io.github.xxfast.cupboard.play.PlayFidelityTest]'s check over a
 * deck that carries considerably more build settings than the sample one.
 *
 * Drawn over [showcaseAssets], the way a laid-down showcase bundle is, with the
 * pictures decoded up front: `renderComposeScene` draws one frame, and a decode
 * that lands after it would leave every picture as its fallback.
 */
class ShowcaseSnapshotTest {

    /** Where every picture this test writes goes. */
    private val directory: File =
        File(System.getProperty("snapshots.out") ?: "build/snapshots", "showcase")

    /**
     * Every slide at its last step: what the audience is left looking at once the
     * builds have all played.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun renderEveryShowcaseSlideToPng() {
        directory.mkdirs()
        val document: Document = showcaseDocument()
        for ((index, slide) in document.slides.withIndex()) {
            val image: Image = document.render(slide, slide.stepCount() - 1, document.slideFields(index))
            val png: ByteArray = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            val out = File(directory, "${index.toString().padStart(2, '0')}-${slide.slug()}.png")
            out.writeBytes(png)
            println("snapshot: ${out.absolutePath}")
        }
    }

    /**
     * The Theme sampler under every built-in theme, which is the one slide built
     * to be read against a palette.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun renderTheThemeSamplerUnderEveryThemeToPng() {
        val themes = File(directory, "themes")
        themes.mkdirs()

        for (theme: Theme in BuiltInThemes.all) {
            val document: Document = showcaseDocument(theme)
            val slide: Slide = document.slides.first { it.title == "Theme Sampler" }
            val png: ByteArray = document.render(slide, slide.stepCount() - 1, null)
                .encodeToData(EncodedImageFormat.PNG)!!
                .bytes
            val out = File(themes, "${theme.name.lowercase()}.png")
            out.writeBytes(png)
            println("snapshot: ${out.absolutePath}")
        }
    }

    /**
     * Every picture the showcase ships decodes through the codec the canvas
     * reads assets with, at the size it was generated at. A GIF the canvas could
     * not read would draw every one of them as its fallback, and the snapshots
     * above would look like a deck laid down before the bytes shipped.
     */
    @Test
    fun everyShowcasePictureDecodesOnTheCanvas() {
        val assets: Map<String, ByteArray> = showcaseAssets()
        val sizes: Map<String, Pair<Int, Int>> = mapOf(
            ShowcasePictureId to (240 to 180),
            ShowcaseNightId to (240 to 180),
            ShowcaseTileId to (48 to 48),
        )

        for ((id, size) in sizes) {
            val image: ImageBitmap = assets.getValue(id).decodeToImageBitmap()
            assertEquals(size, image.width to image.height, id)
        }
    }

    /**
     * Every step of every slide draws, at the size it was asked for. The deck
     * carries every build effect, every delivery, every trigger and both action
     * shapes, so a step of it that throws is a build setting the canvas has
     * stopped honouring.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun everyStepOfEveryShowcaseSlideRenders() {
        val document: Document = showcaseDocument()

        for (slide in document.slides) {
            for (step in 0 until slide.stepCount()) {
                val image: Image = document.render(slide, step, null)
                assertTrue(
                    image.width == 1920 && image.height == 1080,
                    "${slide.title} step $step rendered ${image.width}x${image.height}",
                )
            }
        }
    }
}

/** The showcase's bytes, in a store and a cache that already holds them decoded. */
private val ShowcaseStore = InMemoryAssetStore(showcaseAssets())
private val ShowcaseImages: AssetImageCache = AssetImageCache().apply {
    for (id in listOf(ShowcasePictureId, ShowcaseNightId, ShowcaseTileId)) {
        put(id, showcaseAssets().getValue(id).decodeToImageBitmap())
    }
}

/** One slide of this deck at [step], drawn on its own layout and background. */
@OptIn(ExperimentalComposeUiApi::class)
private fun Document.render(slide: Slide, step: Int, fields: SlideFields?): Image =
    renderComposeScene(1920, 1080) {
        CompositionLocalProvider(
            LocalAssetStore provides ShowcaseStore,
            LocalAssetImages provides ShowcaseImages,
        ) {
            SlideView(
                slide = slide,
                layout = layoutOf(slide),
                step = step,
                fields = fields,
                background = background,
                slideWidth = slideWidth,
                slideHeight = slideHeight,
            )
        }
    }

/** The slide's title as a file name: lowercase, and words joined by dashes. */
private fun Slide.slug(): String = title
    .lowercase()
    .map { if (it.isLetterOrDigit()) it else '-' }
    .joinToString("")
    .trim('-')
    .replace(Regex("-+"), "-")
    .ifEmpty { "untitled" }

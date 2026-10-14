package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.DocumentLoad
import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.InMemoryAssetStore
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.decodeDocument
import io.github.xxfast.cupboard.document.layoutOf
import io.github.xxfast.cupboard.document.stepCount
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/**
 * Scratch: every slide of a deck at every build step, landed, as PNGs.
 *
 * Runs only with `DECK_PATH` (a `.cupboard` folder) set, in the environment or as
 * `-Ddeck.path`; `DECK_OUT` / `-Ddeck.out` is where `frames/` goes.
 */
class DeckWalkthroughRender {
    @Test
    fun renderEveryStep() {
        val path: String = setting("deck.path", "DECK_PATH") ?: return println("deck.path not set, skipped")
        val out = File(setting("deck.out", "DECK_OUT") ?: "build/deck-walkthrough", "frames")
        out.mkdirs()

        val bundle = File(path)
        val document: Document = when (val load = decodeDocument(File(bundle, "document.json").readText())) {
            is DocumentLoad.Loaded -> load.document
            else -> error("could not load the deck: $load")
        }
        // The bundle's assets folder, one file per asset id, read up front.
        val files: List<File> = File(bundle, "assets").listFiles()?.toList().orEmpty()
        val assets = InMemoryAssetStore(files.filter { it.isFile }.associate { it.name to it.readBytes() })
        val fonts = DeckFontCache()
        runBlocking { fonts.warm(document.fonts, assets) }
        val images = AssetImageCache()

        var count = 0
        document.slides.forEachIndexed { index, slide ->
            for (step in 0 until slide.stepCount()) {
                val png: ByteArray = landed(document, slide, step, assets, fonts, images)
                count++
                val name = "%04d-slide%02d-step%02d.png".format(count, index + 1, step)
                File(out, name).writeBytes(png)
            }
        }
        println("deck walkthrough: $count frames in ${out.absolutePath}")
    }

    private fun landed(
        document: Document,
        slide: Slide,
        step: Int,
        assets: AssetStore,
        fonts: DeckFontCache,
        images: AssetImageCache,
    ): ByteArray {
        val width: Int = document.slideWidth.toInt()
        val height: Int = document.slideHeight.toInt()
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f))
        scene.setContent {
            CompositionLocalProvider(
                LocalAssetStore provides assets,
                LocalAssetImages provides images,
                LocalDeckFontCache provides fonts,
                LocalDeckFonts provides fonts.families(document.fonts),
            ) {
                SlideView(
                    slide = slide,
                    layout = document.layoutOf(slide),
                    step = step,
                    background = document.background,
                    slideWidth = document.slideWidth,
                    slideHeight = document.slideHeight,
                )
            }
        }
        try {
            // Ticked like a display: an animation's clock starts on the first frame
            // it sees. Images decode off-thread, so a short sleep between frames.
            var last: ByteArray? = null
            var image: Image = scene.render(0)
            var still = 0
            var frame = 1
            while (frame < MAX_FRAMES) {
                Thread.sleep(5)
                image = scene.render(frame * FRAME_NANOS)
                val pixels: ByteArray? = Bitmap.makeFromImage(image).readPixels()
                still = if (last != null && pixels != null && pixels.contentEquals(last)) still + 1 else 0
                last = pixels
                if (frame >= MIN_FRAMES && still >= STILL_FRAMES) break
                frame++
            }
            return image.encodeToData(EncodedImageFormat.PNG)!!.bytes
        } finally {
            scene.close()
        }
    }

    private fun setting(property: String, env: String): String? =
        System.getProperty(property) ?: System.getenv(env)

    private companion object {
        const val FRAME_NANOS: Long = 50_000_000L
        const val MIN_FRAMES: Int = 10
        const val STILL_FRAMES: Int = 3
        const val MAX_FRAMES: Int = 120
    }
}

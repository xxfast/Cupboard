package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import io.github.xxfast.cupboard.document.BuiltInThemes
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.layoutOf
import io.github.xxfast.cupboard.document.presentationNumbers
import io.github.xxfast.cupboard.document.showcaseDocument
import io.github.xxfast.cupboard.document.stepCount
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The showcase deck rendered to PNGs, for a human to look at.
 *
 * The deck exists to be looked at, so this is mostly a writer rather than an
 * assertion: what is wrong with a slide is almost never something a number would
 * have caught. The one thing asserted is that every step of every slide draws at
 * all, which is [io.github.xxfast.cupboard.play.PlayFidelityTest]'s check over a
 * deck that carries considerably more build settings than the sample one.
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
        val numbers: List<Int?> = document.presentationNumbers()

        for ((index, slide) in document.slides.withIndex()) {
            val image: Image = document.render(slide, slide.stepCount() - 1, numbers[index])
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

/** One slide of this deck at [step], drawn on its own layout and background. */
@OptIn(ExperimentalComposeUiApi::class)
private fun Document.render(slide: Slide, step: Int, number: Int?): Image =
    renderComposeScene(1920, 1080) {
        SlideView(
            slide = slide,
            layout = layoutOf(slide),
            step = step,
            number = number,
            background = background,
            slideWidth = slideWidth,
            slideHeight = slideHeight,
        )
    }

/** The slide's title as a file name: lowercase, and words joined by dashes. */
private fun Slide.slug(): String = title
    .lowercase()
    .map { if (it.isLetterOrDigit()) it else '-' }
    .joinToString("")
    .trim('-')
    .replace(Regex("-+"), "-")
    .ifEmpty { "untitled" }

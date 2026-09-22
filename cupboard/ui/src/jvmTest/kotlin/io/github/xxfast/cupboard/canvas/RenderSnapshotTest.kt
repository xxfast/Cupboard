package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import io.github.xxfast.cupboard.document.BuiltInThemes
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.codeBoxElement
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.document.shapeElement
import io.github.xxfast.cupboard.document.stepCount
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

class RenderSnapshotTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun renderSampleSlideToPng() {
        val slide = sampleDocument().allSlides().first { it.builds.isNotEmpty() }
        val image = renderComposeScene(1920, 1080) {
            SlideView(slide)
        }
        val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
        val out = File(System.getProperty("snapshot.out") ?: "build/snapshot.png")
        out.parentFile.mkdirs()
        out.writeBytes(png)
        println("snapshot: ${out.absolutePath}")
    }

    /**
     * Every slide of the sample deck, each at its last step, for a human to look
     * at. Not an assertion of anything: it is the one way to see that the code,
     * terminal, diagram and equation slides draw what they are meant to, since
     * what is wrong with a picture is rarely something a number would have caught.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun renderEverySampleSlideToPng() {
        val directory = File(System.getProperty("snapshots.out") ?: "build/snapshots")
        directory.mkdirs()

        for ((index, slide) in sampleDocument().allSlides().withIndex()) {
            val image = renderComposeScene(1920, 1080) {
                SlideView(slide, step = slide.stepCount() - 1, number = index + 1)
            }
            val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            val out = File(directory, "${index.toString().padStart(2, '0')}-${slide.slug()}.png")
            out.writeBytes(png)
            println("snapshot: ${out.absolutePath}")
        }
    }

    /**
     * The built slide at every step it has, which is the build order as the
     * audience walks it: the stages arriving one click at a time, the body handed
     * over a paragraph at a time, and the image leaving on the Out build. Not an
     * assertion either: `PlayFidelityTest` says the steps differ, and these say
     * how.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun renderEveryStepOfTheBuiltSlideToPng() {
        val directory = File(System.getProperty("snapshots.out") ?: "build/snapshots")
        directory.mkdirs()

        val slides: List<Slide> = sampleDocument().allSlides()
        val index: Int = slides.indexOfFirst { it.title == "Rendering Pipeline" }
        val slide: Slide = slides[index]

        for (step in 0 until slide.stepCount()) {
            val image = renderComposeScene(1920, 1080) {
                SlideView(slide, step = step, number = index + 1)
            }
            val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            val name = "${index.toString().padStart(2, '0')}-${slide.slug()}-step$step.png"
            val out = File(directory, name)
            out.writeBytes(png)
            println("snapshot: ${out.absolutePath}")
        }
    }

    /**
     * Every built-in theme on its own "Title & Body" layout, with a shape in its
     * default dress, one filled with its accent, and a code block. For a human
     * again: whether a palette is any good is not a thing to assert.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun renderEveryBuiltInThemeToPng() {
        val directory = File(System.getProperty("snapshots.out") ?: "build/snapshots", "themes")
        directory.mkdirs()

        for (theme in BuiltInThemes.all) {
            val layout: Slide = theme.layouts.first { it.title == "Title & Body" }
            val shape = shapeElement(ShapeKind.Rectangle, Frame(560f, 300f, 150f, 80f), theme.defaults)
            val slide: Slide = layout.copy(
                elements = layout.elements + listOf(
                    shape.copy(label = "Filled"),
                    shape.copy(
                        frame = Frame(730f, 300f, 150f, 80f),
                        fill = theme.defaults.accent,
                        strokeColor = theme.defaults.accent,
                        label = "Accent",
                    ),
                    codeBoxElement(Frame(560f, 400f, 320f, 100f), theme.defaults),
                ),
            )

            val image = renderComposeScene(1920, 1080) {
                SlideView(slide, background = theme.background)
            }
            val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            val out = File(directory, "${theme.name.lowercase()}.png")
            out.writeBytes(png)
            println("snapshot: ${out.absolutePath}")
        }
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

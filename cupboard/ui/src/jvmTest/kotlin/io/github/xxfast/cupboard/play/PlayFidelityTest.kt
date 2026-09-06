package io.github.xxfast.cupboard.play

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import io.github.xxfast.cupboard.canvas.SlideView
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.buildTimeline
import io.github.xxfast.cupboard.document.layoutOf
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.document.stepCount
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The player walked a step at a time: every slide of the sample deck rendered at
 * every step it has, and the step model the renderer reads checked against the
 * one CuP is handed.
 *
 * The deck is the fidelity fixture on purpose. Between them its slides carry
 * every transition kind, every trigger, piece delivery, an Out build and a pair
 * of action builds, so a step of it that throws or that CuP never offers a click
 * for is a build setting the player has stopped honouring.
 */
class PlayFidelityTest {

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun everyStepOfEverySlideRenders() {
        val document = sampleDocument()

        for (slide in document.slides) {
            for (step in 0 until slide.stepCount()) {
                val image: Image = document.render(slide, step)
                assertTrue(
                    image.width == 1920 && image.height == 1080,
                    "${slide.title} step $step rendered ${image.width}x${image.height}",
                )
            }
        }
    }

    /**
     * CuP hands out one click per step, so the count it is given is the whole of
     * the build order it plays. Derived off the timeline here rather than read off
     * `stepCount()`, which is the thing under test.
     */
    @Test
    fun everySlideOffersAClickPerStepOfItsBuildOrder() {
        val document = sampleDocument()

        for ((slide, cup) in document.playOrder().zip(document.toCupSlides())) {
            val steps: Int = 1 + (slide.buildTimeline().maxOfOrNull { it.lastStep } ?: 0)
            assertEquals(steps, cup.stepCount, "${slide.title} plays the wrong number of steps")
            assertEquals(steps, slide.stepCount())
        }
    }

    /**
     * The builds actually draw something: the slide that walks four stages in, out
     * of a paragraph delivery and past an Out build cannot look the same on its
     * last step as it does on its first.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun theBuiltSlideLooksDifferentOnItsLastStep() {
        val document = sampleDocument()
        val slide: Slide = document.slides.first { it.title == "Rendering Pipeline" }

        assertTrue(slide.stepCount() > 1)
        assertNotEquals(
            document.render(slide, 0).samples(),
            document.render(slide, slide.stepCount() - 1).samples(),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Document.render(slide: Slide, step: Int): Image = renderComposeScene(1920, 1080) {
    SlideView(
        slide = slide,
        layout = layoutOf(slide),
        step = step,
        background = background,
        slideWidth = slideWidth,
        slideHeight = slideHeight,
    )
}

/** A grid of pixels off the render: enough of the picture to tell two steps apart. */
private fun Image.samples(): List<Int> {
    val bitmap: Bitmap = Bitmap.makeFromImage(this)
    return buildList {
        for (y in 0 until bitmap.height step 20) {
            for (x in 0 until bitmap.width step 20) add(bitmap.getColor(x, y))
        }
    }
}

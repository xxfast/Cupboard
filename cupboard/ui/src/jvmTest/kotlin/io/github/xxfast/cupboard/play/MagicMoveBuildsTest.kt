package io.github.xxfast.cupboard.play

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.BuildEffect
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideTransition
import io.github.xxfast.cupboard.document.TransitionKind
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A Magic Move only pairs what is on screen either side of the cut. The red box
 * on the second slide builds in on a click, so it is never on screen while the
 * deck moves between the two slides, and no frame of either move may show it:
 * the blue box on the first slide, the same kind of shape, used to set off from
 * it in its colours.
 */
class MagicMoveBuildsTest {
    private val document = Document(
        slides = listOf(
            Slide(
                id = "a",
                transition = SlideTransition(kind = TransitionKind.MagicMove),
                elements = listOf(ShapeElement(id = "blue", frame = Frame(50f, 50f, 100f, 100f), fill = 0xFF0000FF)),
            ),
            Slide(
                id = "b",
                elements = listOf(ShapeElement(id = "red", frame = Frame(300f, 150f, 300f, 200f), fill = 0xFFFF0000)),
                builds = listOf(Build(elementId = "red", effect = BuildEffect.Dissolve)),
            ),
        ),
    )

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun anElementStillToBuildInNeverShowsAcrossTheMove() {
        lateinit var controller: PlayerController
        ImageComposeScene(944, 531).use { scene ->
            scene.setContent {
                controller = rememberPlayerController()
                PresentationPlayer(document = document, controller = controller)
            }

            var time = 0L
            // A second of frames, well past the move's 600ms.
            fun reds(): List<Int> = List(60) {
                time += 16_000_000L
                scene.render(time).redPixels()
            }

            reds()
            // The controller mirrors the position a frame later, so it is read
            // after the frames rather than before them.
            controller.next()
            assertEquals(List(60) { 0 }, reds(), "forward onto the slide")
            assertEquals(1, controller.slideIndex)

            controller.previous()
            assertEquals(List(60) { 0 }, reds(), "back off the slide")
            assertEquals(0, controller.slideIndex)
        }
    }
}

/** How many of every fourth pixel each way are the red box's red. */
private fun Image.redPixels(): Int {
    val bitmap: Bitmap = Bitmap.makeFromImage(this)
    var count = 0
    for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) {
        val color: Int = bitmap.getColor(x, y)
        val red: Boolean = (color shr 16 and 0xFF) > 200 && (color shr 8 and 0xFF) < 60 && (color and 0xFF) < 60
        if (red) count++
    }
    return count
}

package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.BuildEffect
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeShadow
import io.github.xxfast.cupboard.document.Slide
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A shape's shadow, read back a pixel at a time just under its bottom edge, where
 * nothing but the shadow can have painted.
 */
class ShapeShadowRenderTest {
    private val box = Frame(60f, 60f, 80f, 80f)
    private val plain = ShapeElement(frame = box, fill = 0xFF3E63DD, strokeWidth = 0f)

    /** White, so it shows against a dark slide. */
    private val shadowed: ShapeElement =
        plain.copy(shadow = ShapeShadow(color = 0xFFFFFFFF, blur = 16f))

    @Test
    fun aShapeAtRestCastsItsShadow() {
        val with: Bitmap = onSlide { ElementView(shadowed) }
        val without: Bitmap = onSlide { ElementView(plain) }

        assertNotEquals(without.getColor(100, 146), with.getColor(100, 146))
    }

    /** A fade dims the shadow with the shape; it does not hold it back until the shape lands. */
    @Test
    fun aShapeHalfwayThroughAFadeStillCastsItsShadow() {
        val with: Bitmap = onSlide {
            ElementView(shadowed, transform = shadowed.fadingTransform(0.5f))
        }
        val without: Bitmap = onSlide {
            ElementView(plain, transform = plain.fadingTransform(0.5f))
        }

        assertNotEquals(without.getColor(100, 146), with.getColor(100, 146))
    }

    /** The same, for a shape that is translucent in the document rather than mid-fade. */
    @Test
    fun aTranslucentShapeCastsItsShadow() {
        val with: Bitmap = onSlide { ElementView(shadowed.copy(opacity = 0.5f)) }
        val without: Bitmap = onSlide { ElementView(plain.copy(opacity = 0.5f)) }

        assertNotEquals(without.getColor(100, 146), with.getColor(100, 146))
    }

    /**
     * A shadow long enough to run off the slide is an overhang, not a bigger
     * element: the slide must not squeeze the shape to make room for it.
     */
    @Test
    fun aShadowReachingPastTheSlideLeavesTheShapeWhereItSits() {
        val long: ShapeElement = shadowed.copy(shadow = ShapeShadow(blur = 40f))

        val pixels: Bitmap = onSlide { ElementView(long) }

        assertEquals(plain.fill.toInt(), pixels.getColor(70, 70), "its top left")
        assertEquals(plain.fill.toInt(), pixels.getColor(130, 130), "its bottom right")
    }

    /** A build fades the element from a wrapper around it, which is a second layer to get past. */
    @Test
    fun aShapeHalfwayThroughItsEnterBuildStillCastsItsShadow() {
        val with: Bitmap = building(shadowed, BuildEffect.Dissolve)
        val without: Bitmap = building(plain, BuildEffect.Dissolve)

        assertNotEquals(without.getColor(100, 100), plain.fill.toInt(), "still fading")
        assertNotEquals(without.getColor(100, 146), with.getColor(100, 146))
    }

    /**
     * The overhang is the shadow's room, not part of the element an effect
     * measures: a Move In starts one element's width away, shadow or no shadow.
     * Read on the way in's first frames, before either has moved.
     */
    @Test
    fun aMoveInStartsAShadowedShapeWhereItStartsAPlainOne() {
        val with: Bitmap = building(shadowed, BuildEffect.MoveIn, frame = 3)
        val without: Bitmap = building(plain, BuildEffect.MoveIn, frame = 3)

        assertTrue(without.lastFilled() < box.x, "still off to the left")
        assertEquals(without.lastFilled(), with.lastFilled())
    }

    /** The last column of the middle row the shape's fill reaches. */
    private fun Bitmap.lastFilled(): Int =
        (0 until width).last { getColor(it, 100) == plain.fill.toInt() }

    /** [element] on a slide of its own, [frame] frames into arriving under [effect]. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun building(
        element: ShapeElement,
        effect: BuildEffect,
        frame: Int = MID_BUILD_FRAME,
    ): Bitmap {
        val build = Build(elementId = element.id, effect = effect, durationMs = 1_000)
        val slide = Slide(elements = listOf(element), builds = listOf(build))
        var step: Int by mutableStateOf(0)

        val scene = ImageComposeScene(width = 200, height = 200, density = Density(1f))
        scene.setContent { SlideView(slide, step = step, slideWidth = 200f, slideHeight = 200f) }

        try {
            scene.render(0)
            step = 1
            // Frame by frame, the way a display would ask: an animation's clock
            // starts on the first frame it sees, so one long jump lands nowhere.
            for (earlier in 1 until frame) scene.render(earlier * FRAME_NANOS)
            return scene.render(frame * FRAME_NANOS).pixels()
        } finally {
            scene.close()
        }
    }

    private fun onSlide(content: @Composable () -> Unit): Bitmap = render {
        SlideSurface(zoom = 1f, slideWidth = 200f, slideHeight = 200f) { content() }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(content: @Composable () -> Unit): Bitmap =
        renderComposeScene(200, 200, density = Density(1f)) { content() }.pixels()

    private fun Image.pixels(): Bitmap {
        val pixels = Bitmap()
        pixels.allocN32Pixels(width, height)
        readPixels(pixels)
        return pixels
    }

    private companion object {
        const val FRAME_NANOS: Long = 16_000_000

        /** About half a second into a build that takes a whole one. */
        const val MID_BUILD_FRAME: Int = 32
    }
}

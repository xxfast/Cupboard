package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.ShapePath
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Path shapes read back a pixel at a time, the way [ImageFillRenderTest] reads a
 * picture fill: the element drawn at native size on a bare slide, so one document
 * unit is one pixel and a point in path space lands somewhere we can work out.
 */
class PathShapeRenderTest {
    private val blue: Long = 0xFF3E63DD
    private val red: Long = 0xFFE5484D

    /** 160 wide, so a path drawn in a 100 unit space is scaled by 1.6 into it. */
    private val box = Frame(20f, 20f, 160f, 160f)
    private val space = Frame(0f, 0f, 100f, 100f)

    @Test
    fun aFilledPathLandsWhereItsBoundsSayItDoes() {
        // Path space 10..60 across and 10..40 down, on a 1.6 scale from (20, 20):
        // x 36..116 and y 36..84.
        val path = ShapePath("M10 10 L60 10 L60 40 L10 40 Z", space)
        val shape = ShapeElement(
            frame = box,
            kind = ShapeKind.Path,
            fill = blue,
            strokeWidth = 0f,
            path = path,
        )

        val pixels: Bitmap = onSlide { ElementView(shape) }

        assertEquals(blue.toInt(), pixels.getColor(70, 60), "inside the outline")
        assertNotEquals(blue.toInt(), pixels.getColor(130, 60), "right of it")
        assertNotEquals(blue.toInt(), pixels.getColor(70, 100), "below it")
        assertNotEquals(blue.toInt(), pixels.getColor(30, 30), "above and left of it")
    }

    @Test
    fun boundsThatShowOnlyASliceOfThePathStretchThatSliceOverTheFrame() {
        // The same square, but the bounds are just its own box: it fills the frame.
        val path = ShapePath("M10 10 L60 10 L60 40 L10 40 Z", Frame(10f, 10f, 50f, 30f))
        val shape = ShapeElement(
            frame = box,
            kind = ShapeKind.Path,
            fill = blue,
            strokeWidth = 0f,
            path = path,
        )

        val pixels: Bitmap = onSlide { ElementView(shape) }

        assertEquals(blue.toInt(), pixels.getColor(30, 30), "the frame's top left corner")
        assertEquals(blue.toInt(), pixels.getColor(170, 170), "and its bottom right")
    }

    @Test
    fun aCompoundPathWithAHoleLeavesTheHoleUnpainted() {
        // Two squares wound the same way: only even-odd cuts the inner one out.
        val data = "M0 0 L100 0 L100 100 L0 100 Z M25 25 L75 25 L75 75 L25 75 Z"
        val holed = ShapeElement(
            frame = box,
            kind = ShapeKind.Path,
            fill = blue,
            strokeWidth = 0f,
            path = ShapePath(data, space, evenOdd = true),
        )
        val solid: ShapeElement = holed.copy(path = ShapePath(data, space, evenOdd = false))

        val pixels: Bitmap = onSlide { ElementView(holed) }
        val filled: Bitmap = onSlide { ElementView(solid) }

        assertEquals(blue.toInt(), pixels.getColor(30, 100), "the ring around the hole")
        assertNotEquals(blue.toInt(), pixels.getColor(100, 100), "the hole")
        assertEquals(blue.toInt(), filled.getColor(100, 100), "nonzero fills it in")
    }

    @Test
    fun aStrokeOnlyOpenPathPaintsItsStrokeCentredOnTheLine() {
        // A horizontal line at path y 50, which is y 100 in the frame. Ten wide, so
        // the stroke runs 95..105: a border would inset and draw nothing on it.
        val line = ShapeElement(
            frame = box,
            kind = ShapeKind.Path,
            fill = 0x00000000,
            strokeColor = red,
            strokeWidth = 10f,
            path = ShapePath("M10 50 L90 50", space),
        )

        val pixels: Bitmap = onSlide { ElementView(line) }

        assertEquals(red.toInt(), pixels.getColor(100, 100), "on the line")
        assertEquals(red.toInt(), pixels.getColor(100, 96), "half a stroke above it")
        assertEquals(red.toInt(), pixels.getColor(100, 103), "and below it")
        assertNotEquals(red.toInt(), pixels.getColor(100, 91), "clear of the stroke above")
        assertNotEquals(red.toInt(), pixels.getColor(100, 109), "and below")
        assertNotEquals(red.toInt(), pixels.getColor(20, 100), "before the line starts")
    }

    @Test
    fun aPathWithNoExtentInOneAxisDoesNotDivideByZero() {
        val flat = ShapePath("M0 5 L100 5", Frame(0f, 5f, 100f, 0f))
        val shape = ShapeElement(
            frame = box,
            kind = ShapeKind.Path,
            fill = 0x00000000,
            strokeColor = red,
            strokeWidth = 10f,
            path = flat,
        )

        val pixels: Bitmap = onSlide { ElementView(shape) }

        assertEquals(red.toInt(), pixels.getColor(100, 22), "collapsed onto the frame's top edge")
    }

    /** [content] on a bare 200x200 slide, for an element drawn without a slide around it. */
    private fun onSlide(content: @Composable () -> Unit): Bitmap = render {
        SlideSurface(zoom = 1f, slideWidth = 200f, slideHeight = 200f) { content() }
    }

    /** [content] drawn at native size, so one document unit is one pixel of the result. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(content: @Composable () -> Unit): Bitmap {
        val image: Image = renderComposeScene(200, 200, density = Density(1f)) { content() }

        val pixels = Bitmap()
        pixels.allocN32Pixels(image.width, image.height)
        image.readPixels(pixels)
        return pixels
    }
}

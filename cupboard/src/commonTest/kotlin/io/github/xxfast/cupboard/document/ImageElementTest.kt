package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * An image is document data like any other element: the mask, the adjustment and
 * the flood fill are all pure functions over it, and none of them needs a canvas.
 * What the pixels end up looking like is `ImageRenderTest`'s job.
 */
class ImageElementTest {
    private val box = Frame(0f, 0f, 400f, 300f)

    @Test
    fun serializationRoundTripsTheWholeImage() {
        val document = Document(
            slides = listOf(
                Slide(
                    elements = listOf(
                        ImageElement(
                            frame = box,
                            assetId = "cover.png",
                            naturalWidth = 1600,
                            naturalHeight = 900,
                            mask = ImageMask(ShapeKind.Ellipse, Frame(0.1f, 0.2f, 0.6f, 0.5f)),
                            adjust = ImageAdjust(0.2f, 1.4f, 0.9f),
                            caption = "The canvas, mid render",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(document, loadedDocument(document.encodeToString()))
    }

    /** Every deck written before images had bytes still opens, as a placeholder. */
    @Test
    fun anImageWithoutBytesKeepsItsPlaceholder() {
        val element = ImageElement(frame = box)

        assertEquals(null, element.assetId)
        assertEquals(null, element.mask)
        assertEquals(ImageAdjust(), element.adjust)
        assertEquals("", element.caption)
        assertEquals(Frame(0f, 0f, 0f, 0f), element.sourceRect())
    }

    @Test
    fun noMaskSourcesTheWholeImage() {
        val element = ImageElement(frame = box, naturalWidth = 800, naturalHeight = 600)
        assertEquals(Frame(0f, 0f, 800f, 600f), element.sourceRect())
    }

    @Test
    fun aMaskSourcesItsWindowInNaturalPixels() {
        val element = ImageElement(
            frame = box,
            naturalWidth = 800,
            naturalHeight = 600,
            mask = ImageMask(frame = Frame(0.25f, 0.5f, 0.5f, 0.25f)),
        )

        assertEquals(Frame(200f, 300f, 400f, 150f), element.sourceRect())
    }

    @Test
    fun aRectangularMaskContainsItsOwnBox() {
        val mask = ImageMask(frame = Frame(0.2f, 0.2f, 0.6f, 0.6f))

        assertTrue(mask.contains(0.5f, 0.5f))
        assertTrue(mask.contains(0.21f, 0.21f))
        assertEquals(false, mask.contains(0.1f, 0.5f))
        assertEquals(false, mask.contains(0.5f, 0.9f))
    }

    @Test
    fun anEllipticalMaskDropsTheCornersOfItsBox() {
        val mask = ImageMask(ShapeKind.Ellipse, Frame(0f, 0f, 1f, 1f))

        assertTrue(mask.contains(0.5f, 0.5f))
        assertTrue(mask.contains(0.5f, 0.01f))
        // Inside the box, outside the ellipse inscribed in it.
        assertEquals(false, mask.contains(0.02f, 0.02f))
    }

    @Test
    fun theDefaultAdjustmentIsTheIdentityMatrix() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )

        assertContentEquals(identity, ImageAdjust().colorMatrix())
    }

    @Test
    fun noSaturationMixesEveryChannelToTheSameLuminance() {
        val matrix: FloatArray = ImageAdjust(saturation = 0f).colorMatrix()

        // The three colour rows are identical, which is what grey means.
        assertContentEquals(matrix.copyOfRange(0, 3), matrix.copyOfRange(5, 8))
        assertContentEquals(matrix.copyOfRange(0, 3), matrix.copyOfRange(10, 13))
        assertEquals(1f, matrix[0] + matrix[1] + matrix[2], absoluteTolerance = 1e-5f)
    }

    @Test
    fun exposureAndContrastOnlyMoveTheOffsets() {
        val matrix: FloatArray = ImageAdjust(exposure = 0.5f, contrast = 1f).colorMatrix()

        assertEquals(1f, matrix[0])
        assertEquals(127.5f, matrix[4])
        assertEquals(127.5f, matrix[9])
        assertEquals(127.5f, matrix[14])
        // Alpha is never touched, whatever the numbers are.
        assertContentEquals(floatArrayOf(0f, 0f, 0f, 1f, 0f), matrix.copyOfRange(15, 20))
    }

    @Test
    fun instantAlphaClearsTheRegionTheSeedSitsInAndNothingElse() {
        // Left half white, right half black, so the fill has a wall to stop at.
        val width = 4
        val height = 2
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val pixels = IntArray(width * height) { if (it % width < 2) white else black }

        val cleared: IntArray = instantAlpha(pixels, width, height, 0, 0, tolerance = 0.05f)

        for (index in cleared.indices) {
            val expected: Int = if (index % width < 2) 0x00FFFFFF else black
            assertEquals(expected, cleared[index], "pixel $index")
        }
        // The input is never touched: the function is pure.
        assertEquals(white, pixels[0])
    }

    @Test
    fun aToleranceWideEnoughSwallowsTheWholeImage() {
        val pixels = IntArray(9) { if (it == 4) 0xFF00FF00.toInt() else 0xFFFFFFFF.toInt() }
        val cleared: IntArray = instantAlpha(pixels, 3, 3, 0, 0, tolerance = 1f)

        assertTrue(cleared.all { (it ushr 24) == 0 })
        assertEquals(0x0000FF00, cleared[4])
    }

    @Test
    fun aSeedOffTheImageChangesNothing() {
        val pixels = IntArray(4) { 0xFF123456.toInt() }
        assertContentEquals(pixels, instantAlpha(pixels, 2, 2, 5, 0, tolerance = 1f))
    }

    @Test
    fun anInsertedImageKeepsItsAspectInsideTheBoxItIsGiven() {
        val element = imageElement(
            frame = Frame(0f, 0f, 400f, 400f),
            assetId = "a.png",
            naturalWidth = 800,
            naturalHeight = 400,
        )

        assertEquals(400f, element.frame.width)
        assertEquals(200f, element.frame.height)
        // Centred in the box it was offered.
        assertEquals(100f, element.frame.y)
        assertEquals(800, element.naturalWidth)
    }

    @Test
    fun fittingShrinksAboutTheCentreAndNeverEnlarges() {
        val element = ImageElement(frame = Frame(100f, 100f, 400f, 200f))

        val shrunk: ImageElement = element.fitted(200f, 200f)
        assertEquals(200f, shrunk.frame.width)
        assertEquals(100f, shrunk.frame.height)
        assertEquals(element.frame.centerX, shrunk.frame.centerX)
        assertEquals(element.frame.centerY, shrunk.frame.centerY)

        assertEquals(element, element.fitted(4000f, 4000f))
    }

    @Test
    fun twoImagesOfTheSameBytesTravelTogetherAcrossACut() {
        val here = ImageElement(frame = box, assetId = "a.png")
        val there = ImageElement(frame = Frame(500f, 0f, 100f, 80f), assetId = "a.png")

        assertEquals(here.matchKey(), there.matchKey())
        assertNotEquals(here.matchKey(), here.copy(assetId = "b.png").matchKey())
        // With no bytes yet, the placeholder is still what an image is matched by.
        assertEquals(
            ImageElement(frame = box).matchKey(),
            ImageElement(frame = there.frame).matchKey(),
        )
    }

    @Test
    fun aPastedStyleCarriesTheAdjustmentAndLeavesThePictureAlone() {
        val source = ImageElement(
            frame = box,
            assetId = "a.png",
            adjust = ImageAdjust(exposure = 0.3f, saturation = 1.5f),
            caption = "Source",
            mask = ImageMask(frame = Frame(0f, 0f, 0.5f, 0.5f)),
        )
        val target = ImageElement(frame = box, assetId = "b.png", caption = "Target")

        val styled = target.applyingStyle(source) as ImageElement

        assertEquals(source.adjust, styled.adjust)
        assertEquals("b.png", styled.assetId)
        assertEquals("Target", styled.caption)
        assertEquals(null, styled.mask)
    }
}

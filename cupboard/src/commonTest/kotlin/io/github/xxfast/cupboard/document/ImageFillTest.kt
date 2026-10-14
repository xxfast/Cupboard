package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Pictures in shapes and behind slides: the model, the precedence and the mode math. */
class ImageFillTest {
    private val box = Frame(0f, 0f, 100f, 50f)
    private val gradient = ShapeGradient(start = 0xFF000000, end = 0xFFFFFFFF)

    @Test
    fun aPictureFillAndAPictureBackgroundSurviveARoundTrip() {
        val document = Document(
            id = "doc",
            background = SlideBackground.Image("deck.png", ImageFillMode.Tile),
            slides = listOf(
                Slide(
                    id = "slide",
                    background = SlideBackground.Image("slide.png"),
                    elements = listOf(
                        ShapeElement(
                            id = "shape",
                            frame = box,
                            gradient = gradient,
                            imageFill = ImageFill("shape.png", ImageFillMode.ScaleToFit),
                        ),
                    ),
                ),
            ),
        )

        val json: String = document.encodeToString()

        assertEquals(document, loadedDocument(json))
        assertTrue("\"type\": \"image\"" in json, "the background is tagged as an image")
    }

    /**
     * Every deck on disk was written before either field, so one without them has
     * to keep opening as it did. Hand-written for the reason `DocumentTest` gives.
     */
    @Test
    fun aDeckWrittenBeforePictureFillsStillDecodes() {
        val json = """
            {
              "id": "doc",
              "background": { "type": "gradient", "start": 4278190080, "end": 4294967295 },
              "slides": [
                {
                  "id": "slide",
                  "background": { "type": "color", "color": 4278190080 },
                  "elements": [
                    {
                      "type": "shape",
                      "id": "shape",
                      "frame": { "x": 0.0, "y": 0.0, "width": 10.0, "height": 10.0 }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val document: Document = loadedDocument(json)
        val shape = document.slides.single().elements.single() as ShapeElement

        assertNull(shape.imageFill)
        assertEquals(ShapeFillKind.Color, shape.fillKind)
        assertEquals(SlideBackground.Color(0xFF000000), document.slides.single().background)
    }

    @Test
    fun aPictureWithNoModeWrittenScalesToFill() {
        val json = """
            {
              "id": "doc",
              "background": { "type": "image", "assetId": "a.png" },
              "slides": []
            }
        """.trimIndent()

        assertEquals(SlideBackground.Image("a.png"), loadedDocument(json).background)
        assertEquals(ImageFillMode.ScaleToFill, SlideBackground.Image("a.png").mode)
    }

    @Test
    fun aPictureWinsOverAGradientWhichWinsOverTheSolid() {
        val plain = ShapeElement(frame = box)
        val graded: ShapeElement = plain.withGradient(gradient)
        val pictured: ShapeElement = graded.withImageFill("a.png")

        assertEquals(ShapeFillKind.Color, plain.fillKind)
        assertEquals(ShapeFillKind.Gradient, graded.fillKind)
        assertEquals(ShapeFillKind.Image, pictured.fillKind)

        // The gradient stays under the picture, so dropping it goes back to it.
        assertEquals(gradient, pictured.gradient)
        assertEquals(graded, pictured.withoutImageFill())
    }

    @Test
    fun switchingToAColourOrAGradientDropsThePicture() {
        val pictured: ShapeElement = ShapeElement(frame = box, gradient = gradient)
            .withImageFill("a.png")

        val solid: ShapeElement = pictured.withSolidFill(0xFF112233)
        assertNull(solid.imageFill)
        assertNull(solid.gradient)
        assertEquals(0xFF112233, solid.fill)

        val graded: ShapeElement = pictured.withGradient(gradient.copy(angle = 90f))
        assertNull(graded.imageFill)
        assertEquals(90f, graded.gradient?.angle)
    }

    @Test
    fun replacingThePictureKeepsItsModeAndAModeNeedsAPicture() {
        val fitted: ShapeElement = ShapeElement(frame = box)
            .withImageFill("a.png")
            .withFillMode(ImageFillMode.ScaleToFit)

        assertEquals(
            ImageFill("b.png", ImageFillMode.ScaleToFit),
            fitted.withImageFill("b.png").imageFill,
        )

        val plain = ShapeElement(frame = box)
        assertSame(plain, plain.withFillMode(ImageFillMode.Tile))
    }

    @Test
    fun aPastedStyleLeavesThePictureWhereItWas() {
        val source = ShapeElement(frame = box, fill = 0xFF112233, gradient = gradient)
            .withImageFill("source.png")
        val target = ShapeElement(frame = box).withImageFill("target.png")
        val plain = ShapeElement(frame = box)

        val pasted = target.applyingStyle(source) as ShapeElement
        assertEquals("target.png", pasted.imageFill?.assetId)
        assertEquals(gradient, pasted.gradient)

        // Nor does a picture travel onto a shape that never had one.
        assertNull((plain.applyingStyle(source) as ShapeElement).imageFill)
    }

    @Test
    fun anObjectStyleReplacesThePictureAndASavedOneHasNone() {
        val pictured = ShapeElement(frame = box, fill = 0xFF112233).withImageFill("a.png")
        val style = ObjectStyle(
            name = "Plain",
            fill = 0xFF445566,
            strokeColor = 0,
            strokeWidth = 0f,
            cornerRadius = 0f,
        )

        val styled: ShapeElement = pictured.applyingObjectStyle(style)
        assertNull(styled.imageFill)
        assertEquals(0xFF445566, styled.fill)

        val saved: ObjectStyle = pictured.asObjectStyle("Saved")
        assertEquals(0xFF112233, saved.fill)
        assertNull(saved.gradient)
    }

    @Test
    fun aPictureRidesThroughAResizeUntouched() {
        val document = Document(
            background = SlideBackground.Image("deck.png"),
            slides = listOf(
                Slide(
                    background = SlideBackground.Image("slide.png", ImageFillMode.Original),
                    elements = listOf(ShapeElement(frame = box).withImageFill("a.png")),
                ),
            ),
        )

        val resized: Document = document.resized(1920f, 1080f, scaleContent = true)
        val shape = resized.slides.single().elements.single() as ShapeElement

        assertEquals(document.background, resized.background)
        assertEquals(document.slides.single().background, resized.slides.single().background)
        assertEquals(ImageFill("a.png"), shape.imageFill)
    }

    @Test
    fun theCascadeIsEveryBackgroundSetNearestFirst() {
        val layout = Slide(background = SlideBackground.Color(0xFF000000))
        val slide = Slide(background = SlideBackground.Image("a.png"))
        val deck = SlideBackground.Gradient(0xFF000000, 0xFFFFFFFF)

        assertEquals(
            listOf(SlideBackground.Image("a.png"), layout.background, deck),
            slide.backgroundCascade(layout, deck),
        )
        assertEquals(listOf(deck), Slide().backgroundCascade(null, deck))
        assertEquals(
            slide.effectiveBackground(layout, deck),
            slide.backgroundCascade(layout, deck).first(),
        )
    }

    @Test
    fun scaleToFillCoversTheBoxAndCropsTheOverhang() {
        // A square into a 2:1 box: scaled to the width, overhanging top and bottom.
        val frames: List<Frame> = ImageFillMode.ScaleToFill.imageFrames(10f, 10f, 100f, 50f)

        assertEquals(listOf(Frame(0f, -25f, 100f, 100f)), frames)
    }

    @Test
    fun scaleToFitShowsAllOfItCentred() {
        val frames: List<Frame> = ImageFillMode.ScaleToFit.imageFrames(10f, 10f, 100f, 50f)

        assertEquals(listOf(Frame(25f, 0f, 50f, 50f)), frames)
    }

    @Test
    fun stretchTakesTheBoxsOwnAspect() {
        val frames: List<Frame> = ImageFillMode.Stretch.imageFrames(10f, 10f, 100f, 50f)

        assertEquals(listOf(Frame(0f, 0f, 100f, 50f)), frames)
    }

    @Test
    fun originalIsNaturalSizeCentredAndCropped() {
        assertEquals(
            listOf(Frame(-50f, -50f, 200f, 150f)),
            ImageFillMode.Original.imageFrames(200f, 150f, 100f, 50f),
        )
        assertEquals(
            listOf(Frame(45f, 20f, 10f, 10f)),
            ImageFillMode.Original.imageFrames(10f, 10f, 100f, 50f),
        )
    }

    @Test
    fun tilesRepeatFromTheTopLeftUntilTheBoxIsCovered() {
        val frames: List<Frame> = ImageFillMode.Tile.imageFrames(40f, 30f, 100f, 50f)

        // Three across (the last overhanging) by two down.
        assertEquals(6, frames.size)
        assertEquals(Frame(0f, 0f, 40f, 30f), frames.first())
        assertEquals(Frame(80f, 0f, 40f, 30f), frames[2])
        assertEquals(Frame(80f, 30f, 40f, 30f), frames.last())
    }

    @Test
    fun nothingWithNoAreaHasAnywhereToGo() {
        for (mode in ImageFillMode.entries) {
            assertEquals(emptyList(), mode.imageFrames(0f, 10f, 100f, 50f), "$mode, empty picture")
            assertEquals(emptyList(), mode.imageFrames(10f, 10f, 100f, 0f), "$mode, empty box")
        }
    }
}

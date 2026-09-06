package io.github.xxfast.cupboard.document

import io.github.xxfast.cupboard.screens.editor.EditorState
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import io.github.xxfast.cupboard.screens.editor.await
import io.github.xxfast.cupboard.screens.editor.editor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A gallery is document data: which pictures it holds, which of them a step is
 * showing, and the builds that walk it through them. All pure, so the renderer
 * and the shells read one interpretation. What the pixels look like is
 * `GalleryRenderTest`'s job.
 */
class GalleryElementTest {
    private val box = Frame(0f, 0f, 400f, 300f)

    private val images: List<GalleryImage> = listOf(
        GalleryImage("one.png", 1600, 900, "Opening"),
        GalleryImage("two.png", 1600, 900, "Middle"),
        GalleryImage("three.png", 1600, 900, "Closing"),
    )

    private fun gallery(vararg pictures: GalleryImage): GalleryElement =
        GalleryElement(id = "g", frame = box, images = pictures.toList())

    /** The gallery, with the builds that walk it through its pictures. */
    private fun walked(gallery: GalleryElement): Slide {
        val slide = Slide(id = "one", elements = listOf(gallery))
        return slide.copy(builds = slide.gallerySteps(gallery.id))
    }

    @Test
    fun serializationRoundTripsTheWholeGallery() {
        val document = Document(
            slides = listOf(
                Slide(
                    elements = listOf(
                        GalleryElement(
                            frame = box,
                            images = images,
                            current = 2,
                            showCaptions = false,
                            adjust = ImageAdjust(0.2f, 1.4f, 0.9f),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(document, loadedDocument(document.encodeToString()))
    }

    @Test
    fun aGalleryWithoutPicturesIsTheEmptyOneItReadsAs() {
        val empty = GalleryElement(frame = box)

        assertEquals(emptyList(), empty.images)
        assertEquals(0, empty.current)
        assertTrue(empty.showCaptions)
        assertEquals(ImageAdjust(), empty.adjust)
        assertEquals(0, Slide(elements = listOf(empty)).galleryImageAt(empty, step = 3))
    }

    @Test
    fun aStepBuildShowsThePictureItPointsAtAndClampsPastTheEnd() {
        val gallery: GalleryElement = gallery(*images.toTypedArray())
        val slide: Slide = walked(gallery)

        // Nothing has stepped it yet, so the gallery opens on its first picture.
        assertEquals(0, slide.galleryImageAt(gallery, step = 0))
        assertEquals(1, slide.galleryImageAt(gallery, step = 1))
        assertEquals(2, slide.galleryImageAt(gallery, step = 2))

        // A build can outlive the picture it pointed at, so an index past the
        // end clamps rather than throwing.
        val stale: Slide = slide.copy(builds = listOf(Build("g", elementStep = 7)))
        assertEquals(2, stale.galleryImageAt(gallery, step = 1))
    }

    @Test
    fun theStepsAreOneClickPerPictureAfterTheFirst() {
        val steps: List<Build> = walked(gallery(*images.toTypedArray())).builds

        assertEquals(listOf(1, 2), steps.map { it.elementStep })
        assertTrue(steps.all { it.elementId == "g" })
        assertTrue(steps.all { it.trigger == BuildTrigger.OnClick })
        assertTrue(steps.all { it.effect == BuildEffect.Dissolve && it.durationMs == 400 })
        // Each one is a click of its own, so a three-picture gallery is three steps.
        assertEquals(3, walked(gallery(*images.toTypedArray())).stepCount())
    }

    @Test
    fun aGalleryOfOnePictureHasNothingToWalkThrough() {
        assertEquals(emptyList(), walked(gallery(images[0])).builds)
        val text = TextElement(id = "t", frame = box, text = "A")
        assertEquals(emptyList(), Slide(elements = listOf(text)).gallerySteps("t"))
    }

    @Test
    fun theStepsLeaveTheGalleryOnTheSlideFromTheStartItOpensOn() {
        val slide: Slide = walked(gallery(*images.toTypedArray()))

        assertTrue(slide.isVisibleAt("g", step = 0), "the gallery opens with the slide")
        assertTrue(slide.isVisibleAt("g", step = 2))
    }

    @Test
    fun addingGalleryStepsReplacesTheOnesItAlreadyHadAndCostsOneUndo() = runTest {
        val gallery: GalleryElement = gallery(*images.toTypedArray())
        val document = Document(
            id = "doc",
            slides = listOf(
                Slide(
                    id = "one",
                    elements = listOf(TextElement(id = "t", frame = box, text = "A"), gallery),
                    // What a shorter gallery left behind: one entry build, which
                    // stays, and one stale step build, which does not.
                    builds = listOf(Build("g"), Build("g", elementStep = 4)),
                ),
            ),
        )
        val viewModel: EditorViewModel = editor(document)

        // An id that names no gallery changes nothing at all.
        viewModel.onAddGallerySteps("t")
        viewModel.onAddGallerySteps("g")

        val state: EditorState = viewModel.await { it.selectedSlide.builds.size == 3 }
        assertEquals(listOf(null, 1, 2), state.selectedSlide.builds.map { it.elementStep })
        assertTrue(state.canUndo)

        viewModel.onUndo()
        val undone: EditorState = viewModel.await { it.selectedSlide.builds.size == 2 }
        assertEquals(document, undone.document, "one edit, one history entry")
    }

    @Test
    fun aPastedStyleCarriesTheAdjustmentAndLeavesThePicturesAlone() {
        val source = GalleryElement(
            frame = box,
            images = images,
            adjust = ImageAdjust(exposure = 0.3f, saturation = 1.5f),
            showCaptions = false,
        )
        val target = GalleryElement(frame = box, images = listOf(images[0]), current = 0)

        val styled = target.applyingStyle(source) as GalleryElement

        assertEquals(source.adjust, styled.adjust)
        assertEquals(false, styled.showCaptions)
        assertEquals(listOf(images[0]), styled.images)
    }

    @Test
    fun aCopyKeepsThePicturesUnderAFreshId() {
        val element: GalleryElement = gallery(*images.toTypedArray()).copy(current = 1)

        val copy = element.withNewIds() as GalleryElement

        assertNotEquals(element.id, copy.id)
        assertEquals(element.images, copy.images)
        assertEquals(1, copy.current)
    }

    @Test
    fun twoGalleriesOfTheSameFirstPictureTravelTogetherAcrossACut() {
        val here: GalleryElement = gallery(*images.toTypedArray())
        val there: GalleryElement = gallery(images[0], images[2]).copy(current = 1)

        assertEquals(here.matchKey(), there.matchKey())
        assertNotEquals(here.matchKey(), gallery(images[1]).matchKey())
    }

    @Test
    fun anInsertedGalleryKeepsItsFirstPicturesAspect() {
        val element: GalleryElement = galleryElement(
            frame = Frame(0f, 0f, 400f, 400f),
            images = listOf(GalleryImage("a.png", 800, 400)),
        )

        assertEquals(400f, element.frame.width)
        assertEquals(200f, element.frame.height)
        assertEquals(100f, element.frame.y, "centred in the box it was offered")
    }
}

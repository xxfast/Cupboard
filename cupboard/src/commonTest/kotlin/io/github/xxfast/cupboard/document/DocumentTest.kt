package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentTest {
    @Test
    fun serializationRoundTripsTheSampleDocument() {
        val document = sampleDocument()
        val decoded = decodeDocument(document.encodeToString())
        assertEquals(document, decoded)
    }

    @Test
    fun serializationRoundTripsDepthAndCollapsed() {
        val document = Document(
            slides = listOf(
                Slide(title = "Parent", collapsed = true),
                Slide(title = "Child", depth = 1),
            ),
        )
        val decoded = decodeDocument(document.encodeToString())
        assertEquals(document, decoded)
        assertTrue(decoded.slides[0].collapsed)
        assertEquals(1, decoded.slides[1].depth)
    }

    @Test
    fun allSlidesIsTheFlatPresentationOrder() {
        val document = sampleDocument()
        assertEquals(
            listOf("Cupboard", "Agenda", "Why KMP", "Rendering Pipeline", "Scene Graph", "Native Interop", "Benchmarks", "Roadmap"),
            document.allSlides().map { it.title },
        )
    }

    @Test
    fun updateSlideReplacesById() {
        val document = sampleDocument()
        val target = document.allSlides().first { it.title == "Rendering Pipeline" }
        val renamed = target.copy(title = "Pipeline, Renamed")
        val updated = document.updateSlide(renamed)
        assertTrue(updated.allSlides().any { it.title == "Pipeline, Renamed" })
        assertFalse(updated.allSlides().any { it.title == "Rendering Pipeline" })
    }

    @Test
    fun hasChildrenLooksAtTheNextSlideDepth() {
        val document = sampleDocument()
        val whyKmp = document.slides.indexOfFirst { it.title == "Why KMP" }
        assertTrue(document.hasChildren(whyKmp))
        assertFalse(document.hasChildren(0))
        assertFalse(document.hasChildren(document.slides.lastIndex))
    }

    @Test
    fun collapsingHidesTheDeeperRunButNeverRenumbers() {
        val document = sampleDocument()
        assertEquals(document.slides.indices.toList(), document.visibleIndices())

        val whyKmp = document.slides.first { it.title == "Why KMP" }
        val collapsed = document.toggleCollapsed(whyKmp.id)
        // Indices 3 (Rendering Pipeline) and 4 (Scene Graph) hidden; the rest keep
        // their absolute indices, so numbering (index + 1) is unchanged.
        assertEquals(listOf(0, 1, 2, 5, 6, 7), collapsed.visibleIndices())

        val expanded = collapsed.toggleCollapsed(whyKmp.id)
        assertEquals(document.slides.indices.toList(), expanded.visibleIndices())
    }

    @Test
    fun nestedCollapseStaysHiddenInsideACollapsedParent() {
        val document = Document(
            slides = listOf(
                Slide(title = "A", collapsed = true),
                Slide(title = "A.1", depth = 1, collapsed = true),
                Slide(title = "A.1.a", depth = 2),
                Slide(title = "A.2", depth = 1),
                Slide(title = "B"),
            ),
        )
        // A collapsed hides its whole deeper run, including the collapsed A.1.
        assertEquals(listOf(0, 4), document.visibleIndices())

        // Expanding A reveals A.1 and A.2, but A.1 keeps its own children hidden.
        val expanded = document.toggleCollapsed(document.slides[0].id)
        assertEquals(listOf(0, 1, 3, 4), expanded.visibleIndices())
    }

    @Test
    fun stepCountCountsOnClickBuildsOnly() {
        val slide = sampleDocument().allSlides().first { it.title == "Rendering Pipeline" }
        // 4 OnClick stage builds; WithPrevious arrows don't add steps
        assertEquals(5, slide.stepCount())
    }

    @Test
    fun withPreviousJoinsThePrecedingStep() {
        val slide = sampleDocument().allSlides().first { it.title == "Rendering Pipeline" }
        val steps = slide.buildSteps()
        val stageIds = slide.elements.filterIsInstance<ShapeElement>().map { it.id }
        val arrowIds = slide.elements.filterIsInstance<TextElement>().filter { it.text == "→" }.map { it.id }
        assertEquals(1, steps[stageIds[0]])
        assertEquals(1, steps[arrowIds[0]])
        assertEquals(2, steps[stageIds[1]])
        assertEquals(4, steps[stageIds[3]])
    }

    @Test
    fun elementsWithoutBuildsAreAlwaysVisible() {
        val slide = sampleDocument().allSlides().first { it.title == "Rendering Pipeline" }
        val titleId = slide.elements.first().id
        assertTrue(slide.isVisibleAt(titleId, step = 0))
        val firstStageId = slide.elements.filterIsInstance<ShapeElement>().first().id
        assertFalse(slide.isVisibleAt(firstStageId, step = 0))
        assertTrue(slide.isVisibleAt(firstStageId, step = 1))
    }
}

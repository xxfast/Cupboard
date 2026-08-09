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
    fun allSlidesWalksGroupsDepthFirst() {
        val document = sampleDocument()
        assertEquals(
            listOf("Cupboard", "Agenda", "Why KMP", "Rendering Pipeline", "Scene Graph", "Native Interop", "Benchmarks", "Roadmap"),
            document.allSlides().map { it.title },
        )
    }

    @Test
    fun updateSlideReplacesNestedSlide() {
        val document = sampleDocument()
        val target = document.allSlides().first { it.title == "Rendering Pipeline" }
        val renamed = target.copy(title = "Pipeline, Renamed")
        val updated = document.updateSlide(renamed)
        assertTrue(updated.allSlides().any { it.title == "Pipeline, Renamed" })
        assertFalse(updated.allSlides().any { it.title == "Rendering Pipeline" })
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

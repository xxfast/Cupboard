package io.github.xxfast.cupboard.play

import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.document.setSlideSkipped
import io.github.xxfast.cupboard.document.stepCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CupCompilerTest {

    @Test
    fun compilesEverySlide() {
        val document = sampleDocument()
        val cupSlides = document.toCupSlides()
        assertTrue(document.slides.isNotEmpty())
        assertEquals(document.slides.size, cupSlides.size)
    }

    @Test
    fun skippedSlidesAreLeftOutOfPlay() {
        val document = sampleDocument()
        val skipped = document.slides[1]
        val cupSlides = document.setSlideSkipped(skipped.id, true).toCupSlides()
        assertEquals(document.slides.size - 1, cupSlides.size)
        assertFalse(cupSlides.any { it.name == skipped.id })
    }

    @Test
    fun aDeckWithEverySlideSkippedPlaysWholeRatherThanEmpty() {
        val document: Document = sampleDocument().let { deck ->
            deck.slides.fold(deck) { acc, slide -> acc.setSlideSkipped(slide.id, true) }
        }
        assertEquals(document.slides.size, document.toCupSlides().size)
    }

    @Test
    fun namesAreOurSlideIds() {
        val document = sampleDocument()
        val cupSlides = document.toCupSlides()
        for ((ours, cup) in document.slides.zip(cupSlides)) {
            assertEquals(ours.id, cup.name)
        }
    }

    @Test
    fun stepCountsMatchOurBuildModel() {
        val document = sampleDocument()
        val cupSlides = document.toCupSlides()
        for ((ours, cup) in document.slides.zip(cupSlides)) {
            assertEquals(ours.stepCount(), cup.stepCount)
        }
    }

    /**
     * Code steps ride the ordinary build order, so the compiler needs to know
     * nothing about them: a slide whose only builds walk one code block through
     * its states still gets a step per click.
     */
    @Test
    fun aCodeBlocksStepsAreStepsOfTheSlide() {
        val document = sampleDocument()
        val slide = document.slides.first { it.title == "Slides as Data" }
        val code = slide.elements.filterIsInstance<CodeElement>().single()
        val cup = document.toCupSlides().first { it.name == slide.id }

        assertTrue(code.steps.size > 1)
        assertEquals(code.steps.size + 1, cup.stepCount)
    }
}

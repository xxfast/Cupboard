package io.github.xxfast.cupboard.play

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
}

package io.github.xxfast.cupboard.play

import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.document.stepCount
import kotlin.test.Test
import kotlin.test.assertEquals
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

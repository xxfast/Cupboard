package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.setSlideSkipped
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PngSequenceTest {
    @Test
    fun oneFilePerSlideNumberedInPresentationOrder() {
        val files: List<ExportedBytes> = exportPngs(plainDeck(3), FakeRasterizer())
        assertEquals(listOf("slide-01.png", "slide-02.png", "slide-03.png"), files.map { it.path })
        assertTrue(files.all { it.bytes.decodeToString(1, 4) == "PNG" })
    }

    @Test
    fun theStepsAreNumberedFromTheSlideTheyBelongTo() {
        val files: List<ExportedBytes> = exportPngs(builtDeck(), FakeRasterizer(), everyBuild = true)
        assertEquals(
            listOf("slide-01.png", "slide-01-step-2.png", "slide-01-step-3.png"),
            files.map { it.path },
        )
    }

    @Test
    fun skippedSlidesAreLeftOutAndTheRestCloseTheGap() {
        val deck: Document = plainDeck(3).setSlideSkipped("slide-1", true)
        assertEquals(
            listOf("slide-01.png", "slide-02.png"),
            exportPngs(deck, FakeRasterizer()).map { it.path },
        )
    }
}

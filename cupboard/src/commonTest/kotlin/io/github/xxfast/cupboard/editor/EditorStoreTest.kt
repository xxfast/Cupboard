package io.github.xxfast.cupboard.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.sampleDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorStoreTest {
    @Test
    fun startsOnTheFirstSlideWithContent() {
        val store = EditorStore(sampleDocument())
        assertTrue(store.selectedSlide.elements.isNotEmpty())
        assertEquals(sampleDocument().slides.first { it.elements.isNotEmpty() }.title, store.selectedSlide.title)
        assertNull(store.selectedElementId)
    }

    @Test
    fun selectSlideAtPicksByPresentationOrder() {
        val store = EditorStore(sampleDocument())
        store.selectSlideAt(4)
        assertEquals(store.document.allSlides()[4].id, store.selectedSlideId)
        assertEquals(4, store.selectedSlideIndex())

        // Out of range is a no-op, not a crash.
        store.selectSlideAt(99)
        assertEquals(4, store.selectedSlideIndex())
    }

    @Test
    fun selectingASlideClearsTheElementSelection() {
        val store = EditorStore(sampleDocument())
        val elementId = store.selectedSlide.elements.first().id
        store.selectElement(elementId)
        assertEquals(elementId, store.selectedElementId)

        store.selectSlideAt(1)
        assertNull(store.selectedElementId)
    }

    @Test
    fun updateSlideReplacesItInTheDocument() {
        val store = EditorStore(sampleDocument())
        store.updateSlide(store.selectedSlide.copy(title = "Renamed"))
        assertEquals("Renamed", store.selectedSlide.title)
        assertTrue(store.document.allSlides().any { it.title == "Renamed" })
    }

    @Test
    fun outlineHidesTheDeeperRunOfACollapsedSlide() {
        val store = EditorStore(sampleDocument())
        assertEquals(store.document.slides.size, store.outline().size)

        val whyKmp = store.document.slides.first { it.title == "Why KMP" }
        store.toggleCollapsed(whyKmp.id)
        val entry = store.outline().first { it.slideId == whyKmp.id }
        assertTrue(entry.collapsed)
        assertTrue(entry.hasChildren)
        // Numbering is absolute, so the visible rows keep their original indices.
        assertEquals(listOf(0, 1, 2, 6, 7, 8), store.outline().map { it.slideIndex })

        store.toggleCollapsed(whyKmp.id)
        assertEquals(store.document.slides.size, store.outline().size)
        assertFalse(store.outline().first { it.slideId == whyKmp.id }.collapsed)
    }

    @Test
    fun outlineCarriesDepthAndTitle() {
        val store = EditorStore(
            Document(slides = listOf(Slide(title = "A"), Slide(title = "A.1", depth = 1))),
        )
        val entries = store.outline()
        assertEquals(listOf("A", "A.1"), entries.map { it.title })
        assertEquals(listOf(0, 1), entries.map { it.depth })
        assertTrue(entries[0].hasChildren)
        assertFalse(entries[1].hasChildren)
    }

    @Test
    fun subscribeFiresOncePerIntentUntilUnsubscribed() {
        val store = EditorStore(sampleDocument())
        var changes = 0
        val unsubscribe = store.subscribe { changes++ }

        store.selectSlideAt(2)
        assertEquals(1, changes)
        store.selectElement(null)
        assertEquals(2, changes)
        store.updateSlide(store.selectedSlide.copy(title = "Edited"))
        assertEquals(3, changes)
        store.toggleCollapsed(store.selectedSlideId)
        assertEquals(4, changes)

        unsubscribe()
        store.selectSlideAt(0)
        assertEquals(4, changes)
    }

    @Test
    fun rejectedIntentsDoNotNotify() {
        val store = EditorStore(sampleDocument())
        var changes = 0
        store.subscribe { changes++ }
        store.selectSlideAt(-1)
        assertEquals(0, changes)
    }
}

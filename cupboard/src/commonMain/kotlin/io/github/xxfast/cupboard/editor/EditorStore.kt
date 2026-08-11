package io.github.xxfast.cupboard.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.hasChildren
import io.github.xxfast.cupboard.document.toggleCollapsed
import io.github.xxfast.cupboard.document.updateSlide
import io.github.xxfast.cupboard.document.visibleIndices

/** One navigator row, already resolved for the shell that renders it. */
data class OutlineEntry(
    val slideId: String,
    val title: String,
    val depth: Int,
    val slideIndex: Int,
    val hasChildren: Boolean,
    val collapsed: Boolean,
)

/**
 * The editor screen's state, shared by every shell.
 *
 * The pattern: state and the mutations that drive it live here, in common code,
 * once. Platform shells are thin adapters over it. A Compose shell reads the
 * snapshot properties straight from a composable and recomposes for free. A
 * non-Compose shell (the SwiftUI host) can't see snapshot writes, so it calls
 * [subscribe] and pulls fresh values whenever the callback fires. Every intent
 * ends by notifying, which means edits made inside the Compose canvas reach the
 * native chrome too, not just the other way around.
 *
 * Not thread safe: drive it from the main thread, like any UI state.
 */
class EditorStore(initial: Document) {
    var document: Document by mutableStateOf(initial)
        private set

    /** Starts on the first slide with content, so the canvas opens on something. */
    var selectedSlideId: String by mutableStateOf(
        initial.allSlides().firstOrNull { it.elements.isNotEmpty() }?.id
            ?: initial.allSlides().firstOrNull()?.id
            ?: "",
    )
        private set

    var selectedElementId: String? by mutableStateOf(null)
        private set

    private val listeners: MutableList<() -> Unit> = mutableListOf()

    /** The selected slide, falling back to the first one if the id went stale. */
    val selectedSlide: Slide
        get() = document.allSlides().firstOrNull { it.id == selectedSlideId }
            ?: document.slides.first()

    /** Index of [selectedSlide] in presentation order, -1 when the document is empty. */
    fun selectedSlideIndex(): Int = document.allSlides().indexOfFirst { it.id == selectedSlide.id }

    /**
     * Navigator rows, collapse rules already applied: a collapsed slide hides the
     * following run of deeper slides. [OutlineEntry.slideIndex] is the absolute
     * index, so numbering (index + 1) survives collapsing.
     */
    fun outline(): List<OutlineEntry> = document.visibleIndices().map { index ->
        val slide = document.slides[index]
        OutlineEntry(
            slideId = slide.id,
            title = slide.title,
            depth = slide.depth,
            slideIndex = index,
            hasChildren = document.hasChildren(index),
            collapsed = slide.collapsed,
        )
    }

    fun selectSlide(id: String) {
        selectedSlideId = id
        selectedElementId = null
        notifyChanged()
    }

    /** Selects by index in presentation order; out of range indices are ignored. */
    fun selectSlideAt(index: Int) {
        val slide = document.allSlides().getOrNull(index) ?: return
        selectSlide(slide.id)
    }

    fun selectElement(id: String?) {
        selectedElementId = id
        notifyChanged()
    }

    fun updateSlide(slide: Slide) {
        document = document.updateSlide(slide)
        notifyChanged()
    }

    fun toggleCollapsed(slideId: String) {
        document = document.toggleCollapsed(slideId)
        notifyChanged()
    }

    /**
     * Registers [onChange], called synchronously after every intent, and returns
     * the unsubscribe. Only for hosts outside Compose: Compose shells read the
     * state properties directly and let the snapshot system do this job.
     */
    fun subscribe(onChange: () -> Unit): () -> Unit {
        listeners += onChange
        return { listeners -= onChange }
    }

    private fun notifyChanged() {
        // Copy first: a listener is allowed to unsubscribe while being notified.
        for (listener in listeners.toList()) listener()
    }
}

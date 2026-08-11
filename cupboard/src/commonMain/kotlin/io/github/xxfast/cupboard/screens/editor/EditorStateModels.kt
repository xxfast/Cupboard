package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.hasChildren
import io.github.xxfast.cupboard.document.visibleIndices
import kotlinx.serialization.Serializable

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
 * Everything the editor screen shows, as one value.
 *
 * Serializable on purpose: the whole screen is this state plus pure derivations
 * of it, so undo/redo, saved state restoration and crash recovery are all just
 * keeping old copies around.
 *
 * The derivations below (selected slide, index, outline) are computed, never
 * stored: two sources of truth for the same thing is how selection drifts out
 * of sync with the document.
 */
@Serializable
data class EditorState(
    val document: Document,
    val selectedSlideId: String,
    val selectedElementId: String? = null,
) {
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

    companion object {
        /**
         * The state a freshly opened [document] starts in: the first slide with
         * content, so the canvas opens on something rather than a title card.
         */
        fun opening(document: Document): EditorState = EditorState(
            document = document,
            selectedSlideId = document.allSlides().firstOrNull { it.elements.isNotEmpty() }?.id
                ?: document.allSlides().firstOrNull()?.id
                ?: "",
        )
    }
}

sealed interface EditorEvent {
    data class SelectSlide(val id: String) : EditorEvent
    /** Selects by index in presentation order; out of range indices are ignored. */
    data class SelectSlideAt(val index: Int) : EditorEvent
    data class SelectElement(val id: String?) : EditorEvent
    data class UpdateSlide(val slide: Slide) : EditorEvent
    data class ToggleCollapsed(val slideId: String) : EditorEvent
}

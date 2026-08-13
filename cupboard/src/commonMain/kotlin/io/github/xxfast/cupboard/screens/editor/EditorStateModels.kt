package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.hasChildren
import io.github.xxfast.cupboard.document.visibleIndices
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** One navigator row, already resolved for the shell that renders it. */
data class OutlineEntry(
    val slideId: String,
    val title: String,
    val depth: Int,
    val slideIndex: Int,
    val hasChildren: Boolean,
    val collapsed: Boolean,
    /** False for a slide hidden inside a collapsed group. Always true in [EditorState.outline]. */
    val visible: Boolean = true,
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
    /** Whether Edit > Undo / Redo are live. The history itself stays in the
     * presenter: shells only need to know what to grey out, and a state that
     * carried its own past would serialize every version of the document. */
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Whether the navigator is showing. Chrome visibility is view state, but it
     * is the editor's view state: every shell has the same three panels, and a
     * shell that kept its own copy would lose it on the next window it opens. */
    val sidebarOpen: Boolean = true,
    val inspectorOpen: Boolean = true,
    val inspectorTab: InspectorTab = InspectorTab.Format,
    val showNotes: Boolean = true,
    /**
     * True between the first preview of a gesture and its commit, so a shell can
     * tell a mid-drag document from a settled one. Anything expensive that only
     * needs the settled document (the navigator's rasterized thumbnails) skips
     * the in-between frames instead of paying for every pointer sample.
     *
     * Transient: a state restored from disk is by definition not mid-gesture.
     */
    @Transient val isPreviewing: Boolean = false,
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
    fun outline(): List<OutlineEntry> = fullOutline().filter { entry -> entry.visible }

    /**
     * Every slide as a navigator row, hidden ones included: [OutlineEntry.visible]
     * is false inside a collapsed group. For shells that animate collapsing, where
     * an exiting row has to stay in the tree to animate out.
     */
    fun fullOutline(): List<OutlineEntry> {
        val visibleIndices: Set<Int> = document.visibleIndices().toSet()

        return document.slides.mapIndexed { index, slide ->
            OutlineEntry(
                slideId = slide.id,
                title = slide.title,
                depth = slide.depth,
                slideIndex = index,
                hasChildren = document.hasChildren(index),
                collapsed = slide.collapsed,
                visible = index in visibleIndices,
            )
        }
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

/** Which pane of the inspector is showing. */
@Serializable
enum class InspectorTab { Format, Animate, Document }

sealed interface EditorEvent {
    data class SelectSlide(val id: String) : EditorEvent
    /** Selects by index in presentation order; out of range indices are ignored. */
    data class SelectSlideAt(val index: Int) : EditorEvent
    data class SelectElement(val id: String?) : EditorEvent
    data class UpdateSlide(val slide: Slide) : EditorEvent
    /** An in-flight gesture sample: folds into the document so the canvas can
     * render it, but makes no history entry and clears no redo stack. */
    data class PreviewSlide(val slide: Slide) : EditorEvent
    /** A cancelled gesture never happened: restores the document from before
     * the gesture's first preview. */
    data object CancelPreview : EditorEvent
    data class ToggleCollapsed(val slideId: String) : EditorEvent
    data object Undo : EditorEvent
    data object Redo : EditorEvent
    data object ToggleSidebar : EditorEvent
    data object ToggleNotes : EditorEvent
    /** Picking a tab shows the inspector: a tab you can't see is not a choice. */
    data class SelectInspectorTab(val tab: InspectorTab) : EditorEvent
    data object CloseInspector : EditorEvent
}

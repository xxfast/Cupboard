package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.hasChildren
import io.github.xxfast.cupboard.document.visibleIndices
import io.github.xxfast.cupboard.editor.AlignEdge
import io.github.xxfast.cupboard.editor.Axis
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
    /** The element selection, in the order it was made. Empty when nothing is selected. */
    val selectedElementIds: List<String> = emptyList(),
    /** Whether Edit > Undo / Redo are live. The history itself stays in the
     * presenter: shells only need to know what to grey out, and a state that
     * carried its own past would serialize every version of the document. */
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Whether Edit > Paste and Edit > Paste Style are live. The clipboard itself
     * stays in the presenter for the same reasons history does, and it is an
     * app-session thing rather than a document one: a restored session opens with
     * nothing to paste, hence [Transient] on both. */
    @Transient val canPaste: Boolean = false,
    @Transient val canPasteStyle: Boolean = false,
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
    /**
     * The marquee rectangle being dragged out, null when none is. View state,
     * but it rides through the loop like every other gesture: a canvas that kept
     * it locally would be writing snapshot state from a pointer handler, which
     * is what the drag-freeze bug was (see ROADMAP.md).
     *
     * Transient for the same reason as [isPreviewing].
     */
    @Transient val marquee: Frame? = null,
) {
    /** The selected slide, falling back to the first one if the id went stale. */
    val selectedSlide: Slide
        get() = document.allSlides().firstOrNull { it.id == selectedSlideId }
            ?: document.slides.first()

    /**
     * The selected elements, in selection order. Ids that no longer resolve are
     * dropped rather than carried: an undo can take a group away underneath its
     * own selection, and a selection of ghosts is worse than a short one.
     */
    val selectedElements: List<Element>
        get() = selectedElementIds.mapNotNull { id ->
            selectedSlide.elements.firstOrNull { it.id == id }
        }

    /**
     * The one element a single-element control speaks for: the first of the
     * selection, which is what the inspector shows and what a multi-selection
     * aligns the rest to in every editor that has an opinion.
     */
    val primaryElement: Element? get() = selectedElements.firstOrNull()

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

/** Which way an element flips. Both flips are around the frame's center. */
enum class FlipAxis { Horizontal, Vertical }

sealed interface EditorEvent {
    data class SelectSlide(val id: String) : EditorEvent
    /** Selects by index in presentation order; out of range indices are ignored. */
    data class SelectSlideAt(val index: Int) : EditorEvent
    /** Replaces the whole element selection with [id], or clears it when null. */
    data class SelectElement(val id: String?) : EditorEvent
    /** Replaces the whole element selection, in the order given. */
    data class SelectElements(val ids: List<String>) : EditorEvent
    /** Shift-click: adds [id] to the selection, or takes it back out. */
    data class ToggleElementSelection(val id: String) : EditorEvent
    /**
     * An in-flight marquee sample: keeps the rectangle for the canvas to draw
     * and reselects everything it overlaps, so the selection is live under the
     * pointer rather than settled on release. Locked elements included, they
     * select like anything else.
     */
    data class PreviewMarquee(val rect: Frame) : EditorEvent
    /** The marquee is over: the rectangle goes, what it selected stays. */
    data object EndMarquee : EditorEvent
    data class UpdateSlide(val slide: Slide) : EditorEvent
    /** An in-flight gesture sample: folds into the document so the canvas can
     * render it, but makes no history entry and clears no redo stack. */
    data class PreviewSlide(val slide: Slide) : EditorEvent
    /** A cancelled gesture never happened: restores the document from before
     * the gesture's first preview. */
    data object CancelPreview : EditorEvent
    /** A settled property edit on a set of elements: a typed-in number, a flip, a
     * released slider, a dropped drag. Rotation rides here too, it needs no event
     * of its own. One history entry however many elements it carries. */
    data class UpdateElements(val elements: List<Element>) : EditorEvent
    /** An in-flight sample from a continuous control, [PreviewSlide]'s semantics
     * at element granularity: it folds into the document, makes no history entry
     * and is undone wholesale by [CancelPreview]. */
    data class PreviewElements(val elements: List<Element>) : EditorEvent
    data class ReorderElements(val ids: List<String>, val move: ZOrderMove) : EditorEvent
    /** The only event a locked element answers to. Undoable, like Keynote's.
     * The shell decides what the toggle means for a mixed selection. */
    data class SetElementsLocked(val ids: List<String>, val locked: Boolean) : EditorEvent
    data class FlipElements(val ids: List<String>, val axis: FlipAxis) : EditorEvent
    /** Wraps the selection into one group and selects it. Needs two unlocked members. */
    data class GroupElements(val ids: List<String>) : EditorEvent
    /** Breaks the group apart and selects the children it frees. Not for a locked group. */
    data class UngroupElements(val id: String) : EditorEvent
    /** Lines the selection up: two or more on their own bounds, a lone one on the slide. */
    data class AlignElements(val edge: AlignEdge) : EditorEvent
    /** Equalizes the gaps across the selection. Needs three unlocked members. */
    data class DistributeElements(val axis: Axis) : EditorEvent
    /**
     * Takes the elements [ids] resolves to off the selected slide, along with
     * every build that pointed at one: a group goes as a whole, children and
     * their builds included. Locked elements are skipped one by one like every
     * other batch edit, and a delete left with nothing to remove is a no-op
     * rather than an empty history entry. Deleted ids leave the selection.
     */
    data class DeleteElements(val ids: List<String>) : EditorEvent
    /**
     * Empties the selected slide of everything unlocked, builds included. Locked
     * elements and their builds stay: the lock is what says "not this one", and
     * clearing the slide is no more allowed around it than any other edit.
     */
    data object ClearAll : EditorEvent
    /**
     * Removes the slide, and whatever it was hiding: a collapsed slide takes its
     * run of deeper slides with it, an expanded one lets that run out one level.
     * Deleting the last slide leaves a fresh blank one rather than a deck with
     * nothing to show. Deleting the selected slide moves the selection to
     * whatever now sits at its index.
     */
    data class DeleteSlide(val id: String) : EditorEvent
    /**
     * Puts the elements [ids] resolves to on the clipboard, in z-order rather
     * than selection order: the slide's order is the one a paste has to keep.
     * Locked elements copy like any other, a copy is not an edit. Makes no
     * history entry, and resolving to nothing leaves the clipboard alone.
     */
    data class CopyElements(val ids: List<String>) : EditorEvent
    /**
     * [CopyElements] and [DeleteElements] as one edit: what it takes is what it
     * stores, so locked elements are neither cut nor copied. One history entry,
     * and nothing unlocked to take leaves both the document and the clipboard
     * as they were.
     */
    data class CutElements(val ids: List<String>) : EditorEvent
    /**
     * Puts the slide on the clipboard, verbatim. A collapsed slide goes with the
     * run it hides, the way it goes everywhere else: what the navigator shows as
     * one row copies as one row. No history entry.
     */
    data class CopySlide(val id: String) : EditorEvent
    /** [CopySlide] and [DeleteSlide] as one edit, selection re-anchored the same
     * way a deletion re-anchors it. One history entry. */
    data class CutSlide(val id: String) : EditorEvent
    /**
     * Pastes whatever the clipboard holds; an empty clipboard is a no-op.
     *
     * Elements land on the selected slide, on top, under fresh ids, and become
     * the selection. The first paste lands exactly where the copy was taken, so
     * cut then paste is how an element moves to another slide, and each further
     * paste of the same clipboard cascades another 24 units down and right.
     *
     * Slides land right after the selected slide, under fresh ids all the way
     * down with their builds remapped, re-based to the selected slide's depth
     * with their relative depths kept. The first of them becomes the selected
     * slide. One history entry either way.
     */
    data object Paste : EditorEvent
    /** A copy and a paste in one, without going through the clipboard: fresh
     * ids, offset 24 units down and right, on top, selected. Locked elements are
     * skipped, and nothing unlocked to duplicate is a no-op. */
    data class DuplicateElements(val ids: List<String>) : EditorEvent
    /** [DuplicateSlide]'s slide equivalent: a deep copy right after the original
     * at the same depth, hidden run and all, and selected. Leaves the clipboard
     * alone; an id this document doesn't hold is a no-op. */
    data class DuplicateSlide(val id: String) : EditorEvent
    /**
     * Remembers [id]'s look for [PasteStyle]. Its own clipboard, independent of
     * the one above: copying an element must not cost you the style you were
     * carrying, and Keynote keeps the two apart the same way. No history entry.
     */
    data class CopyStyle(val id: String) : EditorEvent
    /**
     * Dresses the unlocked elements [ids] resolves to in the remembered style.
     * What transfers is `Element.applyingStyle`'s business: appearance only,
     * never content, never geometry. One history entry; no style remembered, or
     * nothing unlocked to dress, is a no-op.
     */
    data class PasteStyle(val ids: List<String>) : EditorEvent
    data class ToggleCollapsed(val slideId: String) : EditorEvent
    data object Undo : EditorEvent
    data object Redo : EditorEvent
    data object ToggleSidebar : EditorEvent
    data object ToggleNotes : EditorEvent
    /** Picking a tab shows the inspector: a tab you can't see is not a choice. */
    data class SelectInspectorTab(val tab: InspectorTab) : EditorEvent
    data object CloseInspector : EditorEvent
}

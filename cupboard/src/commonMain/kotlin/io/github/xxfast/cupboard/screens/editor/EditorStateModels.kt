package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.BuiltInThemes
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.ElementDefaults
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.Guide
import io.github.xxfast.cupboard.document.GuideAxis
import io.github.xxfast.cupboard.document.ObjectStyle
import io.github.xxfast.cupboard.document.PlaceholderRole
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.hasChildren
import io.github.xxfast.cupboard.document.isLayout
import io.github.xxfast.cupboard.document.layoutOf
import io.github.xxfast.cupboard.document.presentationNumbers
import io.github.xxfast.cupboard.document.slideById
import io.github.xxfast.cupboard.document.takesCaret
import io.github.xxfast.cupboard.document.visibleIndices
import io.github.xxfast.cupboard.editor.AlignEdge
import io.github.xxfast.cupboard.editor.Axis
import io.github.xxfast.cupboard.editor.SnapKind
import io.github.xxfast.cupboard.editor.SnapLine
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * How far the layout margins sit in from the slide's edges, as a fraction of the
 * slide: 5%, which on a 1920x1080 slide is 96 by 54 units.
 */
private const val LayoutMarginFraction: Float = 0.05f

/** One navigator row, already resolved for the shell that renders it. */
data class OutlineEntry(
    val slideId: String,
    val title: String,
    val depth: Int,
    val slideIndex: Int,
    /**
     * The number the row shows: this slide's place in the presentation, null when
     * it is skipped and so has no place in one.
     */
    val number: Int?,
    val hasChildren: Boolean,
    val collapsed: Boolean,
    /** Kept in the deck, left out of the presentation. Drawn dimmed, Keynote-style. */
    val skipped: Boolean = false,
    /** False for a slide hidden inside a collapsed group. Always true in [EditorState.outline]. */
    val visible: Boolean = true,
)

/**
 * A navigator row on the move, and the gap the pointer is over: after
 * [afterId]'s visible row, or above the first row when null.
 *
 * View state, but it rides through the loop like the marquee and every other
 * gesture: a navigator that kept it locally would be writing snapshot state from
 * a pointer handler, which is what the drag-freeze bug was (see ROADMAP.md).
 */
data class SlideDrag(
    val slideId: String,
    val afterId: String?,
    /** Over [afterId]'s row body rather than the gap under it: the drop nests. */
    val nest: Boolean = false,
    /**
     * How far the pointer has carried the row from where it was picked up, in
     * the navigator's pixels: the row (and its run) draws displaced by this, so
     * the thumbnail travels with the cursor the way Keynote's does.
     */
    val translationY: Float = 0f,
)

/**
 * A user guide on the move and where it currently sits, in doc units: an x for a
 * vertical guide, a y for a horizontal one. [id] is null while a fresh guide is
 * still being pulled out of a ruler and has nothing in the document yet.
 *
 * [SlideDrag]'s rule at guide granularity: what a drag shows comes back through
 * the loop, never out of the canvas's own snapshot state.
 */
data class GuideDrag(val id: String?, val axis: GuideAxis, val position: Float)

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
    /** Whether the canvas draws its rulers, and whether the user's guides show
     * at all. View toggles like [showNotes], and stored next to it for the same
     * reason: they belong to the editor, not to whichever shell is drawing. */
    val showRulers: Boolean = false,
    val showGuides: Boolean = true,
    /** What a dragged element settles onto, one switch per [SnapKind]. All four
     * on by default, which is where Keynote leaves them. */
    val snapToCenter: Boolean = true,
    val snapToEdges: Boolean = true,
    val snapToObjects: Boolean = true,
    val snapToGuides: Boolean = true,
    /**
     * Which pane the keyboard is in, and so what the Edit menu's verbs act on:
     * the navigator's slide or the canvas's elements, the way Keynote's do.
     *
     * Serialized like [inspectorTab], not [Transient] like the clipboard flags:
     * where you were working is part of where you left the editor, so a restored
     * session comes back to the pane it was in.
     */
    val focusedPane: EditorPane = EditorPane.Canvas,
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
    /**
     * The navigator row being dragged and the gap it is over, null when none is.
     * The marquee's rule at slide granularity: what a drag shows comes back
     * through the loop, never out of the navigator's own snapshot state.
     *
     * Transient for the same reason as [marquee].
     */
    @Transient val slideDrag: SlideDrag? = null,
    /**
     * The guide being dragged out of a ruler or along the slide, null when none
     * is. [marquee]'s rule again: the line the pointer is carrying is drawn from
     * what comes back through the loop.
     *
     * Transient for the same reason as [marquee]. The guides themselves live in
     * the document, which is where a saved deck keeps them.
     */
    @Transient val guideDrag: GuideDrag? = null,
    /**
     * The text element the caret is in, null when nothing is being edited in
     * place. The typing itself rides the loop as ordinary element previews; this
     * only says which element the caret sits in.
     *
     * Transient for the same reason as [marquee]: a restored session is not
     * mid-edit, however it was left.
     */
    @Transient val editingElementId: String? = null,
    /**
     * Where the selection was when layout mode was entered, so leaving comes back
     * to the slide you were on rather than to the top of the deck. Null whenever
     * layout mode isn't open.
     *
     * Stored rather than [Transient]: which slide you were editing is part of
     * where you left the editor, the same argument [focusedPane] makes.
     */
    val slideBeforeLayouts: String? = null,
    /**
     * The themes the user has saved, on top of the five built-ins. Kept in its
     * own file rather than in the deck ([themes] is what a picker offers), so a
     * theme saved out of one document is there in the next.
     *
     * Transient: the library is a property of the machine, not of the document,
     * and a state restored from disk gets it back from the presenter's load
     * rather than from whatever was serialized alongside a deck.
     */
    @Transient val userThemes: List<Theme> = emptyList(),
) {
    /** Every theme this editor can put the deck on: the built-ins, then the user's. */
    val themes: List<Theme> get() = BuiltInThemes.all + userThemes

    /**
     * What a fresh element is dressed in, which is the deck's and so the theme's.
     * The one thing an insert call site needs off the theme, hoisted here so a
     * shell reaches for `state.defaults` rather than for `state.document.defaults`.
     */
    val defaults: ElementDefaults get() = document.defaults

    /** The shape looks a style picker offers, which are the deck's. [defaults]' twin. */
    val objectStyles: List<ObjectStyle> get() = document.objectStyles

    /**
     * The selected slide, which in layout mode is a layout: both lists answer to
     * one id, so every reduction from the element edits down works on a layout
     * without knowing it is on one. Falls back to the first slide if the id went
     * stale.
     */
    val selectedSlide: Slide
        get() = document.slideById(selectedSlideId) ?: document.slides.first()

    /**
     * Whether the navigator is showing layouts rather than slides, which is the
     * whole of what "layout mode" is: the selection naming a layout.
     */
    val isEditingLayouts: Boolean get() = document.isLayout(selectedSlideId)

    /**
     * The layout the selected slide is built on, null when it is on none. Null in
     * layout mode too, and for free: layouts never stack, so a layout is on none.
     */
    val selectedLayout: Slide? get() = document.layoutOf(selectedSlide)

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

    /**
     * Whether Use As Default has something to take the deck's text look off, and
     * [canUseAsDefaultShapeStyle] the same for its shape look.
     *
     * The primary element's type and nothing else: a locked element donates its
     * look like any other, because neither verb edits the element it reads.
     */
    val canUseAsDefaultTextStyle: Boolean get() = primaryElement is TextElement

    val canUseAsDefaultShapeStyle: Boolean get() = primaryElement is ShapeElement

    /**
     * The element the caret is in, null when none is: an id that no longer
     * resolves, or one that resolves to something with no text or code to edit,
     * is no more an edit session than no id at all.
     *
     * A text box, a code block and a terminal are the three that take a caret.
     * They edit differently on the canvas, one a plain field and the others
     * dressed ones, but a session is a session either way.
     */
    val editingElement: Element?
        get() = editingElementId
            ?.let { id -> selectedSlide.elements.firstOrNull { it.id == id } }
            ?.takeIf { it.takesCaret }

    /** Whether the caret is in an element, and so whether keys are text rather than commands. */
    val isEditingText: Boolean get() = editingElement != null

    /**
     * Whether Edit > Cut is live, focus already resolved.
     *
     * The navigator always has something to take: deleting the last slide
     * leaves a blank one rather than an empty document, so the verb never has
     * nothing to act on. The canvas needs an unlocked element in the selection,
     * since the lock is what says "not this one" to every edit.
     *
     * Computed, unlike the stored [canPaste] and [canPasteStyle] flags next to
     * it: what those answer for is the presenter's clipboard, what this answers
     * for is right here in the state.
     *
     * A caret in an element greys it, and greys [canCopy], [canDuplicate] and
     * [canDelete] with it: mid-edit those verbs belong to the text, so the menu
     * item and the Delete key fall through to the field instead of taking the
     * element out from under it.
     */
    val canCut: Boolean
        get() = when (focusedPane) {
            EditorPane.Navigator -> true
            EditorPane.Canvas -> !isEditingText && selectedElements.any { !it.locked }
        }

    /** [canCut]'s rule verbatim: what may be taken away may also be copied in place. */
    val canDuplicate: Boolean get() = canCut

    /** [canCut]'s rule verbatim: cutting is a copy and a delete at once. */
    val canDelete: Boolean get() = canCut

    /**
     * Whether Edit > Copy is live. Looser than [canCut] on the canvas: a copy
     * is not an edit, so a locked element copies like any other and only an
     * empty selection greys it.
     */
    val canCopy: Boolean
        get() = when (focusedPane) {
            EditorPane.Navigator -> true
            EditorPane.Canvas -> !isEditingText && selectedElements.isNotEmpty()
        }

    /**
     * A [width] by [height] box in the middle of the slide: where an insertion
     * that was not pointed at a spot lands.
     *
     * The document's own slide size, not the constant, so a deck that ever
     * carries another aspect ratio still inserts in the middle of its own slide.
     */
    fun insertionFrame(width: Float, height: Float): Frame = Frame(
        x = (document.slideWidth - width) / 2,
        y = (document.slideHeight - height) / 2,
        width = width,
        height = height,
    )

    /**
     * Every line a dragged frame may settle on right now, per the four snap
     * settings. [exclude] is the ids being dragged: an element's own edges are no
     * snap target for itself, or it would stick to where it started.
     *
     * The layout guides are in here rather than in the document: the slide's two
     * center lines and its 5% margins are a property of the slide's shape, they
     * are drawn only while something snaps to them, and nothing about them is
     * worth saving to a file.
     */
    fun snapTargets(exclude: Set<String> = emptySet()): List<SnapLine> {
        val width: Float = document.slideWidth
        val height: Float = document.slideHeight
        val lines: MutableList<SnapLine> = mutableListOf()

        if (snapToCenter) {
            lines += SnapLine(GuideAxis.Vertical, width / 2, SnapKind.Center)
            lines += SnapLine(GuideAxis.Horizontal, height / 2, SnapKind.Center)
        }

        if (snapToEdges) {
            val insetX: Float = width * LayoutMarginFraction
            val insetY: Float = height * LayoutMarginFraction
            for (x in listOf(0f, insetX, width - insetX, width)) {
                lines += SnapLine(GuideAxis.Vertical, x, SnapKind.Edges)
            }
            for (y in listOf(0f, insetY, height - insetY, height)) {
                lines += SnapLine(GuideAxis.Horizontal, y, SnapKind.Edges)
            }
        }

        if (snapToObjects) {
            for (element in selectedSlide.elements) {
                if (element.id in exclude) continue
                val frame: Frame = element.frame
                lines += SnapLine(GuideAxis.Vertical, frame.x, SnapKind.Objects)
                lines += SnapLine(GuideAxis.Vertical, frame.centerX, SnapKind.Objects)
                lines += SnapLine(GuideAxis.Vertical, frame.x + frame.width, SnapKind.Objects)
                lines += SnapLine(GuideAxis.Horizontal, frame.y, SnapKind.Objects)
                lines += SnapLine(GuideAxis.Horizontal, frame.centerY, SnapKind.Objects)
                lines += SnapLine(GuideAxis.Horizontal, frame.y + frame.height, SnapKind.Objects)
            }
        }

        if (snapToGuides) {
            for (guide: Guide in document.guides) {
                lines += SnapLine(guide.axis, guide.position, SnapKind.Guides)
            }
        }

        return lines
    }

    /** Index of [selectedSlide] in presentation order, -1 when the document is empty. */
    fun selectedSlideIndex(): Int = document.allSlides().indexOfFirst { it.id == selectedSlide.id }

    /**
     * The number the slide with [id] draws on itself: its place in the
     * presentation, null when it is skipped or when the id doesn't resolve.
     */
    fun slideNumber(id: String): Int? {
        val index: Int = document.slides.indexOfFirst { it.id == id }
        return if (index == -1) null else document.presentationNumbers()[index]
    }

    /**
     * Navigator rows, collapse rules already applied: a collapsed slide hides the
     * following run of deeper slides. [OutlineEntry.slideIndex] is the absolute
     * index, so collapsing shuffles no row's place in the deck.
     */
    fun outline(): List<OutlineEntry> = fullOutline().filter { entry -> entry.visible }

    /**
     * Every slide as a navigator row, hidden ones included: [OutlineEntry.visible]
     * is false inside a collapsed group. For shells that animate collapsing, where
     * an exiting row has to stay in the tree to animate out.
     *
     * In layout mode the rows are the layouts, flat: they never nest, never
     * collapse and are never skipped, so all a row carries is its place in the
     * list. One outline for both means a navigator draws layout mode with the
     * rows it already has.
     */
    fun fullOutline(): List<OutlineEntry> {
        if (isEditingLayouts) return document.layouts.mapIndexed { index, layout ->
            OutlineEntry(
                slideId = layout.id,
                title = layout.title,
                depth = 0,
                slideIndex = index,
                number = index + 1,
                hasChildren = false,
                collapsed = false,
            )
        }

        val visibleIndices: Set<Int> = document.visibleIndices().toSet()
        val numbers: List<Int?> = document.presentationNumbers()

        return document.slides.mapIndexed { index, slide ->
            OutlineEntry(
                slideId = slide.id,
                title = slide.title,
                depth = slide.depth,
                slideIndex = index,
                number = numbers[index],
                hasChildren = document.hasChildren(index),
                collapsed = slide.collapsed,
                skipped = slide.skipped,
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

/**
 * Which pane holds the keyboard focus, and so which layer the Edit menu's
 * verbs speak for: whole slides in the navigator, elements on the canvas.
 */
@Serializable
enum class EditorPane { Navigator, Canvas }

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
     * A secondary click on the canvas, [elementId] being what it landed on, null
     * for empty slide space. Settles what the menu about to open will be about,
     * the way every editor's right-click does: an element outside the selection
     * takes the selection over, an element already in it leaves the selection
     * exactly as it is, order included, so a menu can act on all of it, and empty
     * space clears it. Never a history entry, and it touches no document.
     *
     * The menu itself is the shell's business; only the selection rides the loop.
     * It rides it for the same reason the marquee does: a canvas that answered a
     * right-click with a selection write of its own would be writing snapshot
     * state from a pointer handler, which is what the drag-freeze bug was (see
     * ROADMAP.md).
     */
    data class ContextClick(val elementId: String?) : EditorEvent
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
    /**
     * Puts [element] on top of the selected slide and selects it, alone: what a
     * shape from the catalog, a text box or a code block comes down to, whichever
     * button reached for it.
     *
     * The element arrives fully built, frame included, so the loop has no opinion
     * on where an insertion lands or what it looks like. `EditorState.insertionFrame`
     * is there for the common case of dropping one in the middle of the slide.
     * One history entry, and the canvas takes the focus.
     */
    data class InsertElement(val element: Element) : EditorEvent
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
     * A fresh blank slide after the one with [afterId], at its depth, past its
     * deeper run so a parent keeps its children, and selected: what New Slide
     * means everywhere it appears. An id that doesn't resolve adds nothing.
     */
    data class AddSlide(val afterId: String) : EditorEvent
    /**
     * A dropped slide drag: the row [id] names lands in the gap under [afterId],
     * null being the gap above the first row.
     *
     * A collapsed row travels with what it hides and an expanded one lets its
     * children out, per Document.moveSlide, which also decides the depth it
     * lands at. One history entry, the moved slide selected, and the drag
     * cleared either way: a drop that changes nothing is still a drop, it just
     * costs no history entry.
     */
    data class MoveSlide(val id: String, val afterId: String?, val nest: Boolean = false) : EditorEvent
    /**
     * An in-flight slide drag sample: the spot for the navigator to mark and
     * how far the row has been carried. Touches no document and makes no
     * history entry, unlike the element previews: a slide is only moved once,
     * on release.
     */
    data class PreviewSlideDrag(
        val slideId: String,
        val afterId: String?,
        val nest: Boolean = false,
        val translationY: Float = 0f,
    ) : EditorEvent
    /** The drag is over without a drop, cancelled or let go outside: the gap goes. */
    data object EndSlideDrag : EditorEvent
    /**
     * Takes the slide in or out of the presentation. Per slide, not per row: a
     * collapsed parent's hidden run keeps its own answer. One history entry, and
     * a slide already like this is a no-op.
     */
    data class SetSlideSkipped(val id: String, val skipped: Boolean) : EditorEvent
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
     * Puts the keyboard focus in [pane] and nothing else.
     *
     * Most of the time focus is not sent at all: the events above that speak
     * for a pane move it themselves, so selecting a slide focuses the navigator
     * and selecting an element focuses the canvas without a shell saying so.
     * This is for the interactions none of them cover, a click on the
     * navigator's empty background or a shell moving focus with the keyboard.
     * Never a history entry, and no focus change ever is.
     */
    data class FocusPane(val pane: EditorPane) : EditorEvent
    /**
     * Edit > Cut, focus resolved: [CutSlide] on the selected slide when the
     * navigator holds the focus, [CutElements] on the element selection when
     * the canvas does. Cmd+X, in other words.
     *
     * [Copy], [Duplicate] and [Delete] below are the same idea over
     * [CopySlide]/[CopyElements], [DuplicateSlide]/[DuplicateElements] and
     * [DeleteSlide]/[DeleteElements]. All four are the specific event and
     * nothing more: the same history entry, and the same no-op when the
     * pane they land in has nothing to act on.
     */
    data object Cut : EditorEvent
    data object Copy : EditorEvent
    data object Duplicate : EditorEvent
    data object Delete : EditorEvent
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
    /**
     * Puts the caret in the text element or code block [id], the way a double
     * click does: it becomes the whole selection, the canvas takes the focus,
     * and the keys that follow are text rather than commands.
     *
     * Nothing in the document changes, so no history entry. A locked element
     * refuses the caret like it refuses every other edit, and an id that is not
     * an unlocked text or code element on the selected slide begins nothing.
     */
    data class BeginTextEdit(val id: String) : EditorEvent
    /**
     * The caret leaves, and whatever was typed settles: the previews the typing
     * streamed are committed as one history entry, or discarded when the text
     * came back the way it started. One edit session is one undo.
     *
     * Sent by the shell (Escape, a click elsewhere), and folded in first by
     * every event that isn't allowed to land mid-edit.
     */
    data object EndTextEdit : EditorEvent
    data class ToggleCollapsed(val slideId: String) : EditorEvent
    /**
     * Edit Slide Layouts: the navigator swaps its slides for the deck's layouts
     * and the canvas edits one of them, Keynote's master mode.
     *
     * Selects the layout the current slide is on, or the first layout when it is
     * on none, remembers where to come back to in [EditorState.slideBeforeLayouts],
     * drops the element selection and puts the focus in the navigator. Nothing in
     * the document changes, so no history entry: mode is not an edit. Already in
     * layout mode, it does nothing, so re-entering can't lose the way back.
     */
    data object EditSlideLayouts : EditorEvent
    /**
     * Done: back to the slide layout mode was entered from, or to the first slide
     * when that one is gone. [EditSlideLayouts]' kind, and no history entry either.
     */
    data object ExitSlideLayouts : EditorEvent
    /**
     * Puts the slide on [layoutId], or on no layout at all when that is null.
     *
     * What the slide keeps and what it takes is `Slide.applyingLayout`'s business:
     * content survives, geometry and look come from the layout. One history entry,
     * and a slide already exactly like this is a no-op.
     */
    data class ApplyLayout(val slideId: String, val layoutId: String?) : EditorEvent
    /**
     * Reapply Layout: puts every placeholder instance back where the layout the
     * slide is already on says it goes. One history entry; a slide on no layout,
     * and one nothing has moved on, are both no-ops.
     */
    data class ReapplyLayout(val slideId: String) : EditorEvent
    /**
     * A fresh empty layout after the selected one, and selected. One history
     * entry: layouts live in the document, so making one is an edit like any
     * other. What [AddSlide] does while layout mode is open.
     */
    data object AddLayout : EditorEvent
    /**
     * Renames the slide, or the layout, with [id]: the navigator's rename, and the
     * only way a layout gets a name. One history entry, and a title that is
     * already what it is asked to be is a no-op.
     */
    data class RenameSlide(val id: String, val title: String) : EditorEvent
    /**
     * Puts a placeholder of [role] on the layout being edited, dressed the way
     * `defaultLayouts` dresses that role, and selects it. One history entry.
     *
     * Layout mode only: a placeholder on a slide is an instance of one, which is
     * what applying a layout makes, never something a slide adds for itself.
     */
    data class AddPlaceholder(val role: PlaceholderRole) : EditorEvent
    /**
     * Puts the deck on the theme called [name], looked up in [EditorState.themes]:
     * background, element defaults and layouts all at once.
     *
     * What each slide keeps and what it takes is `Document.applyingTheme`'s
     * business: content survives, and a slide on a layout the new theme has no
     * name for comes off layouts rather than losing anything. One history entry,
     * and a name no theme answers to is a no-op.
     */
    data class ChangeTheme(val name: String) : EditorEvent
    /**
     * Saves the deck's own look as a theme called [name] and puts the deck on it:
     * Save Theme. Replaces the user theme of that name if there is one, so saving
     * twice updates rather than piles up.
     *
     * The library is not the document, so adding to it is no history entry. The
     * deck's theme name is, and it is one edit: undo takes the name back and
     * leaves the saved theme where it is, which is what a save means.
     */
    data class SaveAsTheme(val name: String) : EditorEvent
    /** Drops the user theme called [name]. The library only, so no history entry
     * and nothing on any slide moves. A name no user theme answers to is a no-op. */
    data class DeleteUserTheme(val name: String) : EditorEvent
    /**
     * Dresses the unlocked shapes [ids] resolves to in the saved style [styleId]:
     * its fill, its border and its shadow, never their kind, label or frame.
     *
     * Shapes only, because that is all an `ObjectStyle` describes: anything else in
     * the selection is skipped one by one, the way locked elements are. One history
     * entry however many shapes it dresses, and a style no one answers to, or a
     * selection with no unlocked shape in it, is a no-op.
     */
    data class ApplyObjectStyle(val ids: List<String>, val styleId: String) : EditorEvent
    /**
     * Saves the shape [shapeId]'s look into the deck's library under [name]: Save
     * Style. Appends rather than replaces, so saving twice is two entries; the six
     * defaults are the ones that regenerate in place.
     *
     * The library is the document here, unlike the theme library, so this is one
     * history entry like any other edit. An id that is not a shape on the selected
     * slide saves nothing. A locked shape saves like any other: reading a look is
     * not editing the element.
     */
    data class SaveObjectStyle(val shapeId: String, val name: String) : EditorEvent
    /**
     * Drops the saved style [styleId]. Nothing already wearing it changes: a style
     * is applied by copy, so the shapes keep the look they were given. One history
     * entry, and an id the deck doesn't hold is a no-op.
     *
     * The library may empty out completely. A deck with no saved styles is a deck
     * that saves its own, and a floor of one would only ever be one in the way.
     */
    data class DeleteObjectStyle(val styleId: String) : EditorEvent
    /** Renames the saved style [styleId]. One history entry, and a style already
     * called this is a no-op. */
    data class RenameObjectStyle(val styleId: String, val name: String) : EditorEvent
    /**
     * Use As Default: writes the text element [id]'s look into the deck's
     * [ElementDefaults], so the next fresh text box arrives set that way.
     *
     * What travels is `ElementDefaults.fromText`'s business: colour, font, size,
     * weight, alignment and line height. Nothing already on a slide moves. One
     * history entry, and an id that is not a text element, or one the deck is
     * already dressed like, is a no-op.
     */
    data class UseAsDefaultTextStyle(val id: String) : EditorEvent
    /** [UseAsDefaultTextStyle] for a shape, over `ElementDefaults.fromShape`: fill,
     * stroke and label colour. The border and the shadow are an [ApplyObjectStyle]
     * away and deliberately no part of this. */
    data class UseAsDefaultShapeStyle(val id: String) : EditorEvent
    /**
     * What sits behind every slide that asks for none of its own, null being the
     * app's dark gradient: the deck-wide end of the background fallback, under
     * the layout's and the slide's. One history entry, and a deck already like
     * this is a no-op.
     */
    data class SetDocumentBackground(val background: SlideBackground?) : EditorEvent
    /**
     * The shape of every slide in the deck, in document units, clamped to what a
     * slide may be. [scaleContent] carries the deck's content across with it;
     * left false, the frames stay where they are and the new edges fall where
     * they fall. See `Document.resized`.
     *
     * One history entry, and a deck already on this size is a no-op.
     */
    data class SetSlideSize(
        val width: Float,
        val height: Float,
        val scaleContent: Boolean,
    ) : EditorEvent
    data object Undo : EditorEvent
    data object Redo : EditorEvent
    data object ToggleSidebar : EditorEvent
    data object ToggleNotes : EditorEvent
    /** View toggles, [ToggleNotes]' kind: no document, no history entry. */
    data object ToggleRulers : EditorEvent
    data object ToggleGuides : EditorEvent
    /** Turns one snap rule on or off. What it changes is what [EditorState.snapTargets]
     * offers the next drag; nothing already placed moves. */
    data class SetSnap(val kind: SnapKind, val enabled: Boolean) : EditorEvent
    /**
     * An in-flight guide drag sample: the line for the canvas to draw, and where
     * it currently sits. [id] is null for a guide being pulled out of a ruler,
     * which is nowhere in the document until it lands.
     *
     * [PreviewSlideDrag]'s kind rather than [PreviewElements]': it touches no
     * document and makes no history entry, because a guide moves once, on release.
     */
    data class PreviewGuide(val id: String?, val axis: GuideAxis, val position: Float) : EditorEvent
    /**
     * A dropped guide drag: a null [id] adds a guide, an id that resolves moves
     * that one. One history entry however many samples the drag streamed, and the
     * drag is cleared either way, the way [MoveSlide] clears its own.
     */
    data class CommitGuide(val id: String?, val axis: GuideAxis, val position: Float) : EditorEvent
    /** A guide dragged off the slide is a guide thrown away: one history entry,
     * and the drag cleared. An id the document doesn't hold only clears it. */
    data class RemoveGuide(val id: String) : EditorEvent
    /** The guide drag is over without a drop: the line goes, nothing else does. */
    data object EndGuideDrag : EditorEvent
    /** Picking a tab shows the inspector: a tab you can't see is not a choice. */
    data class SelectInspectorTab(val tab: InspectorTab) : EditorEvent
    data object CloseInspector : EditorEvent
    /**
     * Shows or hides the inspector without picking a tab: the View-menu switch,
     * [ToggleSidebar]'s twin. Reopening comes back on whatever tab was last up,
     * which is why this isn't a [SelectInspectorTab] the shell has to guess.
     */
    data object ToggleInspector : EditorEvent
}

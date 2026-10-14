package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.AudioElement
import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.BuiltInThemes
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.ColourPalette
import io.github.xxfast.cupboard.document.DiagramElement
import io.github.xxfast.cupboard.document.DiagramStep
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.TextFont
import io.github.xxfast.cupboard.document.fontDisplayName
import io.github.xxfast.cupboard.document.fontFamilies
import io.github.xxfast.cupboard.document.palette
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.ElementDefaults
import io.github.xxfast.cupboard.document.EquationElement
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GalleryElement
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.Guide
import io.github.xxfast.cupboard.document.GuideAxis
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.ObjectStyle
import io.github.xxfast.cupboard.document.PlaybackSettings
import io.github.xxfast.cupboard.document.PlaceholderRole
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TerminalElement
import io.github.xxfast.cupboard.document.VideoElement
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.SlideTransition
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextLink
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.boundingFrame
import io.github.xxfast.cupboard.document.rotateVector
import io.github.xxfast.cupboard.document.drawnBounds
import io.github.xxfast.cupboard.document.displayName
import io.github.xxfast.cupboard.document.hasChildren
import io.github.xxfast.cupboard.document.elementById
import io.github.xxfast.cupboard.document.isLayout
import io.github.xxfast.cupboard.document.isLocked
import io.github.xxfast.cupboard.document.kindName
import io.github.xxfast.cupboard.document.layoutOf
import io.github.xxfast.cupboard.document.parseDiagram
import io.github.xxfast.cupboard.document.presentationNumbers
import io.github.xxfast.cupboard.document.reorderElements
import io.github.xxfast.cupboard.document.slideById
import io.github.xxfast.cupboard.document.sourceAt
import io.github.xxfast.cupboard.document.sources
import io.github.xxfast.cupboard.document.takesCaret
import io.github.xxfast.cupboard.document.visibleIndices
import io.github.xxfast.cupboard.editor.AlignEdge
import io.github.xxfast.cupboard.editor.AlignmentOverlay
import io.github.xxfast.cupboard.editor.DragSample
import io.github.xxfast.cupboard.editor.Axis
import io.github.xxfast.cupboard.editor.SnapKind
import io.github.xxfast.cupboard.editor.SnapLine
import io.github.xxfast.cupboard.editor.alignmentOverlay
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * How far the layout margins sit in from the slide's edges, as a fraction of the
 * slide: 5%, which on a 1920x1080 slide is 96 by 54 units.
 */
private const val LayoutMarginFraction: Float = 0.05f

/** The grid [EditorState.insertionFrame] searches for a clear spot on, and its cascade step. */
private const val InsertionGrid: Float = 20f

private fun distanceSquared(a: Frame, b: Frame): Float {
    val dx: Float = a.centerX - b.centerX
    val dy: Float = a.centerY - b.centerY
    return dx * dx + dy * dy
}

/** The axis-aligned box the element covers once its rotation is applied. */
private fun Element.rotatedBounds(): Frame {
    if (rotation == 0f) return frame
    val corners: List<Pair<Float, Float>> = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)
        .map { (sx, sy) -> rotateVector(sx * frame.width / 2, sy * frame.height / 2, rotation) }
    val xs: List<Float> = corners.map { it.first }
    val ys: List<Float> = corners.map { it.second }
    return Frame(
        x = frame.centerX + xs.min(),
        y = frame.centerY + ys.min(),
        width = xs.max() - xs.min(),
        height = ys.max() - ys.min(),
    )
}

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

/** One Object List row, already resolved for the shell that renders it. */
data class ObjectRow(
    val id: String,
    /** [Element.displayName]. */
    val name: String,
    /** [Element.kindName], for the row icon. */
    val kind: String,
    /** 0 top-level, 1 inside a group; always 0 while filtering. */
    val depth: Int,
    val isGroup: Boolean,
    /** Only meaningful when [isGroup] and not filtering. */
    val expanded: Boolean,
    val hidden: Boolean,
    val locked: Boolean,
    /** [id] itself is selected. A selected group's children aren't, and read as not. */
    val selected: Boolean,
)

/**
 * An Object List row on the move, and where it would land: immediately before
 * [beforeId]'s row in the list's front-to-back order, which is directly in front
 * of that element in the z-order. Null is after the last row, the very back.
 *
 * [SlideDrag]'s rule at element granularity: what a drag shows comes back
 * through the loop, never out of the list's own snapshot state.
 */
data class ObjectDrag(
    val id: String,
    val beforeId: String?,
    /**
     * How far the pointer has carried the row from where it was picked up, in
     * the list's pixels: the row draws displaced by this, so it travels with the
     * cursor.
     */
    val translationY: Float = 0f,
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
 * A stretch of a text box's text, `[start, end)` in chars of [TextElement.text],
 * as the caret's field last reported it. Collapsed when [start] is [end], which
 * is a caret with nothing selected.
 *
 * Plain ints and an id rather than a range type, so the shells over the ObjC and
 * .NET boundaries read it field by field like everything else in the state.
 */
data class TextSelection(val elementId: String, val start: Int, val end: Int) {
    val isCollapsed: Boolean get() = start == end
}

/**
 * One of the selected text box's range links, as an inspector lists it: where
 * it runs, the words it covers (cut short past [TextLinkExcerptLength] chars) and
 * where it points.
 */
data class TextLinkRow(val start: Int, val end: Int, val excerpt: String, val target: LinkTarget)

/** How much of a linked run's words a [TextLinkRow] shows before it trails off. */
const val TextLinkExcerptLength: Int = 30

/** The speaker-notes strip's height until the user drags its top edge, in points. */
const val NotesDefaultHeight: Float = 122f

/** The shortest the strip drags to, Keynote's: past it the drag hides the strip. */
const val NotesMinHeight: Float = 99f

/** The tallest the strip drags to, as a fraction of the editor's height. Near Keynote's. */
const val NotesMaxFraction: Float = 0.45f

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
    /**
     * The slides selected alongside [selectedSlideId], which stays the one on
     * the canvas: Keynote's navigator, where shift and command clicks gather
     * rows around the slide being edited and the slide verbs take them all.
     * Never holds [selectedSlideId] itself, and always empty in layout mode.
     *
     * Not part of where you left the editor, so not stored: a deck reopens on
     * its slide, not on a half-made selection.
     */
    @Transient val alsoSelectedSlideIds: Set<String> = emptySet(),
    /** The element selection, in the order it was made. Empty when nothing is selected. */
    val selectedElementIds: List<String> = emptyList(),
    /**
     * Which version of the selected code block the editor is showing and typing
     * into ([CodeElement.versions]). 0 for every other element, and back to 0
     * whenever the selection moves to another block or another slide, since a
     * version index only means anything against the block it indexes.
     *
     * Serialized like [inspectorTab]: which version you were writing is part of
     * where you left the editor.
     */
    val codeVersion: Int = 0,
    /**
     * Which of the selected code block's steps the inspector is editing
     * ([CodeElement.steps]), null being none of them, which is where a fresh
     * selection starts. Follows [codeVersion] exactly: null again whenever the
     * selection moves, since a step index only means anything against the block
     * it indexes.
     *
     * Selecting a step also moves [codeVersion] to the version that step plays,
     * so the canvas shows the source being stepped through rather than a version
     * the row has nothing to do with.
     */
    val codeStep: Int? = null,
    /**
     * Which of the selected diagram's steps the inspector is editing
     * ([DiagramElement.steps]), null being none of them. [codeStep]'s twin, and
     * dropped whenever the selection moves for the same reason.
     *
     * The canvas previews the step it names ([shownDiagramStep]), so the reveal
     * and highlight being typed are on show while they are typed. Null is the
     * whole diagram, which is what a fresh selection sees.
     */
    val diagramStep: Int? = null,
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
    /**
     * Whether an edit is still on its way to disk: true from the moment the
     * document changes, false once the debounced autosave has written it.
     *
     * What a shell puts its edited dot in the title bar behind, and what a close
     * confirmation asks about. Not "is this deck dirty" in the Keynote sense:
     * everything is saved, this only says the last few keystrokes are still in
     * flight, which is the honest thing to show for an editor that autosaves.
     *
     * Transient: a state read back off disk was written by that very save.
     */
    @Transient val savePending: Boolean = false,
    /**
     * A counter, bumped by [EditorEvent.RefreshAssets]: the bytes under the
     * bundle's `assets/` changed while the ids pointing at them did not. Shells
     * key their decoded-image caches on it, so a swapped file repaints instead of
     * showing the picture that was decoded before it.
     *
     * Transient like [savePending]: it is a fact about this session, not the deck.
     */
    @Transient val assetGeneration: Int = 0,
    /** Whether the navigator is showing. Chrome visibility is view state, but it
     * is the editor's view state: every shell has the same three panels, and a
     * shell that kept its own copy would lose it on the next window it opens. */
    val sidebarOpen: Boolean = true,
    /** Whether the Object List is showing. Serialized for [sidebarOpen]'s reason. */
    val objectListOpen: Boolean = false,
    /**
     * What the Object List's search field holds. Non-blank flattens the list to
     * the matching elements, see [objectRows]. Transient: a search is for now.
     */
    @Transient val objectListFilter: String = "",
    /** The groups the Object List has open, by id. Transient like the filter. */
    @Transient val expandedGroupIds: Set<String> = emptySet(),
    val inspectorOpen: Boolean = true,
    val inspectorTab: InspectorTab = InspectorTab.Format,
    /**
     * The Format segment the user last picked, which is a preference rather than
     * what is showing: a selection that doesn't offer it falls back for as long
     * as it lasts, and the preference comes back when one that offers it does.
     * [activeFormatSegment] is the one on screen.
     *
     * Serialized like [inspectorTab], and for the same reason: which segment you
     * were in is part of where you left the editor.
     */
    val formatSegment: FormatSegment = FormatSegment.Style,
    /** The Animate segment showing. No fallback: every element offers all three. */
    val animateSegment: AnimateSegment = AnimateSegment.BuildIn,
    /**
     * Which of the inspector's collapsible sections are open. Empty by default:
     * Keynote's start collapsed, and the expansion is remembered across
     * selections rather than reset by every click on the canvas.
     */
    val expandedSections: Set<InspectorSection> = emptySet(),
    val showNotes: Boolean = true,
    /**
     * How tall the speaker-notes strip is, in points. View state like
     * [showNotes], and kept while the strip is hidden so it comes back the
     * height it was left at. Never under [NotesMinHeight]; how tall it may get
     * depends on the window, so the shells cap that end.
     */
    val notesHeight: Float = NotesDefaultHeight,
    /** Whether the canvas draws its rulers, and whether the user's guides show
     * at all. View toggles like [showNotes], and stored next to it for the same
     * reason: they belong to the editor, not to whichever shell is drawing. */
    val showRulers: Boolean = false,
    val showGuides: Boolean = true,
    /**
     * The canvas's zoom and scroll. View state like [showRulers], and through the
     * loop like [marquee]: the canvas reports its size and its scrolls, and draws
     * from [canvasViewport] rather than from anything it kept itself.
     *
     * Serialized, bar the view size: a restored session comes back at the zoom
     * it was left at, and measures its window again.
     */
    val viewport: CanvasViewport = CanvasViewport(),
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
     * True while the caret is in the speaker notes, from [BeginNotesEdit] to the
     * [EndNotesEdit] that settles what was typed. The notes' [editingElementId].
     *
     * Transient for the same reason as [isPreviewing].
     */
    @Transient val isEditingNotes: Boolean = false,
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
     * The Object List row being dragged and where it would land, null when none
     * is. [slideDrag]'s twin for elements.
     *
     * Transient for the same reason as [marquee].
     */
    @Transient val objectDrag: ObjectDrag? = null,
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
     * What the canvas draws over the move or resize in flight: the guides the
     * dragged frame sits on, the gaps and sizes it matches, and the readout that
     * follows the pointer. Null between gestures. [marquee]'s rule once more:
     * derived here from each preview, never from the gesture's own state.
     *
     * Transient for the same reason as [marquee].
     */
    @Transient val alignment: AlignmentOverlay? = null,
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
     * Where the caret and its selection are in the text being edited, null with
     * no session open. The field's own, reported through
     * [EditorEvent.EditSelectionChanged] on every move; nothing here is drawn
     * from it, it is only what the inspector asks about the link under the caret.
     *
     * Transient for the same reason as [editingElementId].
     */
    @Transient val textSelection: TextSelection? = null,
    /**
     * The last stretch of the selected text box that was actually selected:
     * what a range link would go on. [textSelection]'s last non-collapsed value,
     * and unlike it, kept when the session ends, because the click on an
     * inspector control that applies a link is often the very click that took
     * the caret out of the text.
     *
     * Dropped when the selection moves to another element, and carried along
     * like a link (`shiftedLinks`) when the box's text changes under it.
     *
     * Transient for the same reason as [editingElementId].
     */
    @Transient val linkSelection: TextSelection? = null,
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
     * The themes the user has saved, on top of the built-ins. Kept in its
     * own file rather than in the deck ([themes] is what a picker offers), so a
     * theme saved out of one document is there in the next.
     *
     * Transient: the library is a property of the machine, not of the document,
     * and a state restored from disk gets it back from the presenter's load
     * rather than from whatever was serialized alongside a deck.
     */
    @Transient val userThemes: List<Theme> = emptyList(),
) {
    /**
     * What a window puts in its title bar, which is the deck's name and nothing
     * else: the bundle it came out of is named after it, so a shell that showed
     * the file name would be showing the same string the long way round.
     * Renamed through [EditorEvent.RenameDocument].
     */
    val title: String get() = document.name

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

    /** What every colour well's popover offers: the swatches [defaults] imply. */
    val palette: ColourPalette get() = defaults.palette()

    /**
     * The families the deck carries in its bundle, once each and sorted: the
     * font picker's first section, ahead of whatever the system has.
     */
    val deckFontFamilies: List<String> get() = document.fontFamilies

    /**
     * The face the primary text box is set in, as a picker names it: its font
     * name, or its [TextFont]'s display name. Null when the primary element is
     * not a text box.
     *
     * Whether that face is actually there to draw in is no question for the core,
     * which has no font manager: `isFontAvailable` in `:cupboard:ui` answers it.
     */
    val selectedFontDisplayName: String?
        get() = (primaryElement as? TextElement)?.fontDisplayName

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
     * The selected elements, in selection order, at whatever depth each sits: an
     * id selects exactly what it names, a group's child included. Ids that no
     * longer resolve are dropped rather than carried: an undo can take a group
     * away underneath its own selection, and a selection of ghosts is worse than
     * a short one.
     */
    val selectedElements: List<Element>
        get() = selectedElementIds.mapNotNull { id -> selectedSlide.elementById(id) }

    /**
     * The one element a single-element control speaks for: the first of the
     * selection, which is what the inspector shows and what a multi-selection
     * aligns the rest to in every editor that has an opinion.
     */
    val primaryElement: Element? get() = selectedElements.firstOrNull()

    /** The viewport the canvas draws from: [viewport] with its pan kept on the slide. */
    val canvasViewport: CanvasViewport
        get() = viewport.clamped(document.slideWidth, document.slideHeight)

    /** Screen dp per document unit, null for a Fit the canvas hasn't measured yet. */
    val canvasScale: Float? get() = viewport.scale(document.slideWidth, document.slideHeight)

    /** The zoom pill's percent, Fit included, null until Fit has a view to fit. */
    val effectiveZoomPercent: Int?
        get() = viewport.effectivePercent(document.slideWidth, document.slideHeight)

    /** Zoom to Selection needs something to zoom to. */
    val canZoomToSelection: Boolean get() = selectedElements.isNotEmpty()

    /**
     * What Zoom to Fit Content fits: the slide and every visible element on it,
     * ones hanging off its edges included. Top-level elements are enough, since a
     * group's frame already bounds its children.
     */
    val contentFrame: Frame
        get() = boundingFrame(
            listOf(Frame(0f, 0f, document.slideWidth, document.slideHeight)) +
                selectedSlide.elements
                    .filter { !it.hidden }
                    .map { it.drawnBounds() },
        )

    /**
     * The code block [codeVersion] speaks for: the selection when it is exactly
     * one code block, null otherwise. A version picker has nothing to point at
     * until a single block is selected.
     */
    val selectedCodeElement: CodeElement? get() = selectedElementIds.singleOrNull()
        ?.let { id -> selectedSlide.elementById(id) }
        as? CodeElement

    /** How many versions the version picker offers, 0 when no code block is selected. */
    val codeVersionCount: Int get() = selectedCodeElement?.sources?.size ?: 0

    /**
     * The step [codeStep] names, null when no row is picked and when the one
     * that was picked is no longer there: an index outliving the step it pointed
     * at is the same stale-pointer story [sourceAt] tells, and the inspector
     * shows no row rather than the wrong one.
     */
    val selectedCodeStep: CodeStep? get() {
        val index: Int = codeStep ?: return null
        return selectedCodeElement?.steps?.getOrNull(index)
    }

    /**
     * Which version of [element] the canvas draws, which is [codeVersion] for the
     * selected block and version 0 for every other one: one block at a time is
     * being written, and the rest of the slide shows the code it opens on.
     *
     * Computed rather than stored, like every other derivation here: the version
     * showing is a function of the selection, and a second copy of it would be a
     * second thing to keep true.
     */
    fun shownVersion(element: CodeElement): Int =
        if (element.id == selectedCodeElement?.id) codeVersion.coerceIn(element.sources.indices)
        else 0

    /** [shownVersion]'s text: what the canvas renders and what the caret types into. */
    fun shownSource(element: CodeElement): String = element.sourceAt(shownVersion(element))

    /**
     * The diagram [diagramStep] speaks for: the selection when it is exactly one
     * diagram, null otherwise, the way [selectedCodeElement] is for code.
     */
    val selectedDiagramElement: DiagramElement? get() = selectedElementIds.singleOrNull()
        ?.let { id -> selectedSlide.elementById(id) }
        as? DiagramElement

    /**
     * The step [diagramStep] names, null when no row is picked and when the one
     * that was picked is no longer there, for [selectedCodeStep]'s reason.
     */
    val selectedDiagramStep: DiagramStep? get() {
        val index: Int = diagramStep ?: return null
        return selectedDiagramElement?.steps?.getOrNull(index)
    }

    /**
     * The selected diagram's node ids in the order its source first mentions
     * them, empty when no diagram is selected: what the step fields can name, for
     * the inspector to offer as a hint beside them.
     *
     * Parsed on every read rather than kept, since the ids are a function of the
     * source and a stored copy would go stale on the next keystroke into it.
     */
    val diagramNodeIds: List<String> get() =
        selectedDiagramElement?.let { element -> parseDiagram(element.source).nodes.map { it.id } }
            ?: emptyList()

    /**
     * Which step [element] draws in on the editor canvas: [selectedDiagramStep]
     * for the selected diagram, and null, the whole diagram, for every other one
     * and for the selected one with no row picked.
     *
     * [shownVersion]'s twin: the row being written is previewed where it will
     * play, and the rest of the slide stays at rest.
     */
    fun shownDiagramStep(element: DiagramElement): DiagramStep? =
        if (element.id == selectedDiagramElement?.id) selectedDiagramStep else null

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
     * The Format segments this selection offers, in the order they sit in the
     * segmented control. Empty when nothing is selected, which is the shells'
     * cue to show slide formatting instead.
     *
     * A multi-selection that agrees keeps its segments; one that doesn't falls
     * back to what every kind shares, the way Keynote's does.
     */
    val formatSegments: List<FormatSegment>
        get() {
            val offered: List<List<FormatSegment>> = selectedElements.map { it.formatSegments() }
            if (offered.isEmpty()) return emptyList()
            return offered.distinct().singleOrNull()
                ?: listOf(FormatSegment.Style, FormatSegment.Arrange)
        }

    /**
     * The Format segment on screen: [formatSegment] when this selection offers
     * it, the first one it does offer otherwise, null when it offers none.
     *
     * The fallback never writes [formatSegment] back, which is what lets Arrange
     * survive shape to image to line, and Text come back after a detour through
     * an image.
     */
    val activeFormatSegment: FormatSegment?
        get() = formatSegments.let { segments ->
            if (formatSegment in segments) formatSegment else segments.firstOrNull()
        }

    /**
     * Whether Bring Forward has anywhere to go, and [canSendBackward] the same
     * for Send Backward: what the Arrange pane greys out at the top and the
     * bottom of the z-order.
     *
     * Asked of `Slide.reorderElements` rather than worked out here, so the
     * enablement can't drift from the move: it returns the slide it was given
     * when the move changes nothing, locked members included. Locked counts the
     * way every edit counts it, inherited from a locked group (`Slide.isLocked`).
     */
    val canBringForward: Boolean get() = canReorder(ZOrderMove.Forward)

    val canSendBackward: Boolean get() = canReorder(ZOrderMove.Backward)

    private fun canReorder(move: ZOrderMove): Boolean {
        val slide: Slide = selectedSlide
        val movable: List<String> = selectedElements
            .filter { !slide.isLocked(it.id) }
            .map { it.id }
        if (movable.isEmpty()) return false
        return slide.reorderElements(movable, move) !== slide
    }

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
            ?.let { id -> selectedSlide.elementById(id) }
            ?.takeIf { it.takesCaret }

    /** Whether the caret is in an element, and so whether keys are text rather than commands. */
    val isEditingText: Boolean get() = editingElement != null

    /** Whether there is a caret anywhere, in an element or in the speaker notes. */
    val isTyping: Boolean get() = isEditingText || isEditingNotes

    /** The selection when it is exactly one text box, null otherwise: what range links are read off. */
    val selectedTextElement: TextElement? get() = selectedElementIds.singleOrNull()
        ?.let { id -> selectedSlide.elementById(id) }
        as? TextElement

    /** [selectedTextElement]'s range links as an inspector lists them, empty with none selected. */
    val textLinkRows: List<TextLinkRow> get() {
        val element: TextElement = selectedTextElement ?: return emptyList()
        return element.links.map { link ->
            val words: String = element.text.substring(link.start, link.end)
            val excerpt: String =
                if (words.length <= TextLinkExcerptLength) words
                else words.take(TextLinkExcerptLength).trimEnd() + "…"
            TextLinkRow(link.start, link.end, excerpt, link.target)
        }
    }

    /**
     * Whether a range link has words to go on: [linkSelection] names a stretch
     * of [selectedTextElement] that is still inside its text, and the box is
     * unlocked. What an inspector's Link Selection control greys out behind.
     */
    val canLinkSelection: Boolean get() {
        val element: TextElement =
            selectedTextElement?.takeIf { !selectedSlide.isLocked(it.id) } ?: return false
        val selection: TextSelection = linkSelection?.takeIf { it.elementId == element.id } ?: return false
        return selection.start < selection.end && selection.end <= element.text.length
    }

    /**
     * The range link the caret is in, or the first one the selection reaches
     * into, null when neither touches one. Read off the live [textSelection]
     * mid-session and off [linkSelection] once the caret has gone, so the
     * inspector keeps showing the link it was showing when the click on it
     * ended the session.
     */
    val selectionLink: TextLink? get() {
        val element: TextElement = selectedTextElement ?: return null
        val selection: TextSelection = (textSelection ?: linkSelection)
            ?.takeIf { it.elementId == element.id }
            ?: return null
        if (selection.isCollapsed) return element.links.firstOrNull {
            selection.start >= it.start && selection.start < it.end
        }
        return element.links.firstOrNull { it.start < selection.end && it.end > selection.start }
    }

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
     * element out from under it. A caret in the speaker notes does the same in
     * either pane: there the verbs would take a slide.
     */
    val canCut: Boolean
        get() = !isEditingNotes && when (focusedPane) {
            EditorPane.Navigator -> true
            EditorPane.Canvas ->
                !isEditingText && selectedElements.any { !selectedSlide.isLocked(it.id) }
        }

    /**
     * Whether an arrow key is the canvas's to take, moving the selection: the
     * canvas has the focus, nothing has a caret, and something selected isn't
     * locked. A shell asks before it takes the key, so one it turns down still
     * reaches whatever else wants arrows.
     */
    val canNudge: Boolean
        get() = focusedPane == EditorPane.Canvas && !isTyping &&
            selectedElements.any { !selectedSlide.isLocked(it.id) }

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
        get() = !isEditingNotes && when (focusedPane) {
            EditorPane.Navigator -> true
            EditorPane.Canvas -> !isEditingText && selectedElements.isNotEmpty()
        }

    /**
     * A [width] by [height] box where an insertion that was not pointed at a
     * spot lands: the middle of the slide when that is clear, so a new element
     * never sits on a layout's placeholders and a drag grabs the thing just made.
     *
     * Clear means no element on the slide overlaps it, measured by each
     * element's rotated bounds. Otherwise the nearest clear spot to the middle on
     * a [InsertionGrid] grid, ties going higher then lefter. A slide with no
     * clear spot cascades: the middle, nudged by [InsertionGrid] per element
     * already sitting on the spot, so repeated inserts don't stack exactly.
     *
     * The document's own slide size, not the constant, so a deck that ever
     * carries another aspect ratio still inserts in the middle of its own slide.
     */
    fun insertionFrame(width: Float, height: Float): Frame {
        val slideWidth: Float = document.slideWidth
        val slideHeight: Float = document.slideHeight
        val occupied: List<Frame> = selectedSlide.elements.map { it.rotatedBounds() }
        val centred = Frame((slideWidth - width) / 2, (slideHeight - height) / 2, width, height)
        if (occupied.none { it.overlaps(centred) }) return centred

        val maxX: Float = slideWidth - width
        val maxY: Float = slideHeight - height
        val free: Frame? = generateSequence(0f) { it + InsertionGrid }
            .takeWhile { it <= maxY }
            .flatMap { y ->
                generateSequence(0f) { it + InsertionGrid }
                    .takeWhile { it <= maxX }
                    .map { x -> Frame(x, y, width, height) }
            }
            .filter { candidate -> occupied.none { it.overlaps(candidate) } }
            .minWithOrNull(
                compareBy<Frame> { distanceSquared(it, centred) }.thenBy { it.y }.thenBy { it.x }
            )
        if (free != null) return free

        val origins: List<Pair<Float, Float>> = selectedSlide.elements.map { it.frame.x to it.frame.y }
        var step = 0
        while (origins.contains(centred.x + step * InsertionGrid to centred.y + step * InsertionGrid)) {
            step++
        }
        return centred.copy(
            x = (centred.x + step * InsertionGrid).coerceIn(0f, maxOf(maxX, 0f)),
            y = (centred.y + step * InsertionGrid).coerceIn(0f, maxOf(maxY, 0f)),
        )
    }

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
    /**
     * The frames a drag measures its spacing and sizes against: every element
     * on the slide that is not moving and not hidden. [exclude] as in [snapTargets].
     */
    fun neighbourFrames(exclude: Set<String>): List<Frame> =
        selectedSlide.elements.filter { it.id !in exclude && !it.hidden }.map { it.frame }

    /**
     * What to draw over the canvas drag that previewed [elements], null when the
     * preview was no drag at all. Measured against the state the preview has
     * already folded into, so the frames are the ones on screen.
     */
    fun alignmentFor(elements: List<Element>, drag: DragSample?): AlignmentOverlay? {
        if (drag == null) return null
        val ids: Set<String> = elements.map { it.id }.toSet()
        return alignmentOverlay(
            frame = boundingFrame(elements.map { it.frame }),
            lines = snapTargets(ids),
            others = neighbourFrames(ids),
            drag = drag,
        )
    }

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

    /**
     * Every selected slide, in deck order: [selectedSlideId] and whatever is
     * selected alongside it. What a slide verb takes when it lands on any of them.
     */
    val selectedSlideIds: List<String>
        get() =
            if (alsoSelectedSlideIds.isEmpty()) listOf(selectedSlideId)
            else document.slides
                .filter { it.id == selectedSlideId || it.id in alsoSelectedSlideIds }
                .map { it.id }

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
     * The selected slide's elements as Object List rows, front to back: the
     * topmost first, which is the reverse of [Slide.elements]. An expanded group
     * lists its children beneath it, one level deep; a group inside a group is a
     * row of its own with nothing under it.
     *
     * A non-blank [objectListFilter] lists every element at any depth whose
     * display name holds it, flat and in the same order, and ignores expansion.
     */
    fun objectRows(): List<ObjectRow> {
        val slide: Slide = selectedSlide
        val selected: Set<String> = selectedElementIds.toSet()

        fun row(element: Element, depth: Int, expanded: Boolean): ObjectRow = ObjectRow(
            id = element.id,
            name = element.displayName,
            kind = element.kindName,
            depth = depth,
            isGroup = element is GroupElement,
            expanded = expanded,
            hidden = element.hidden,
            locked = element.locked,
            selected = element.id in selected,
        )

        if (objectListFilter.isNotBlank()) {
            fun matches(elements: List<Element>): List<ObjectRow> = elements
                .asReversed()
                .flatMap { element ->
                    val children: List<Element> = (element as? GroupElement)?.children.orEmpty()
                    val self: List<ObjectRow> =
                        if (element.displayName.contains(objectListFilter, ignoreCase = true)) {
                            listOf(row(element, depth = 0, expanded = false))
                        } else {
                            emptyList()
                        }

                    return@flatMap self + matches(children)
                }

            return matches(slide.elements)
        }

        return slide.elements
            .asReversed()
            .flatMap { element ->
                val expanded: Boolean = element is GroupElement && element.id in expandedGroupIds
                val top: ObjectRow = row(element, depth = 0, expanded = expanded)
                if (!expanded) return@flatMap listOf(top)

                val children: List<Element> = (element as GroupElement).children.asReversed()
                return@flatMap listOf(top) + children.map { row(it, depth = 1, expanded = false) }
            }
    }

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
 * A segment of the Format tab's segmented control. Style and Arrange are on
 * every selection; the middle one is the selected kind's own.
 *
 * [Movie] and [Audio] are Keynote's names for the video and audio segments, which
 * is what the user reads on them.
 */
@Serializable
enum class FormatSegment {
    Style, Text, Image, Code, Terminal, Diagram, Equation, Gallery, Movie, Audio, Arrange,
}

/** A segment of the Animate tab's segmented control, shown whenever an element
 * is selected. Nothing selected is Transitions, which is no segment at all. */
@Serializable
enum class AnimateSegment { BuildIn, Action, BuildOut }

/** A collapsible section of the Format tab. See [EditorState.expandedSections]. */
@Serializable
enum class InspectorSection { Fill, Border, Shadow, Spacing, Lists }

/**
 * The Format segments this element alone would offer. A line is a shape with no
 * inside and no label, so it offers neither Style's fill nor Text; a group has
 * nothing of its own to format.
 */
private fun Element.formatSegments(): List<FormatSegment> = when (this) {
    is ShapeElement ->
        if (kind == ShapeKind.Line) listOf(FormatSegment.Style, FormatSegment.Arrange)
        else listOf(FormatSegment.Style, FormatSegment.Text, FormatSegment.Arrange)

    is TextElement -> listOf(FormatSegment.Style, FormatSegment.Text, FormatSegment.Arrange)
    is ImageElement -> listOf(FormatSegment.Style, FormatSegment.Image, FormatSegment.Arrange)
    is CodeElement -> listOf(FormatSegment.Style, FormatSegment.Code, FormatSegment.Arrange)
    is TerminalElement -> listOf(FormatSegment.Style, FormatSegment.Terminal, FormatSegment.Arrange)
    is DiagramElement -> listOf(FormatSegment.Style, FormatSegment.Diagram, FormatSegment.Arrange)
    is EquationElement -> listOf(FormatSegment.Style, FormatSegment.Equation, FormatSegment.Arrange)
    is GalleryElement -> listOf(FormatSegment.Style, FormatSegment.Gallery, FormatSegment.Arrange)
    is VideoElement -> listOf(FormatSegment.Style, FormatSegment.Movie, FormatSegment.Arrange)
    is AudioElement -> listOf(FormatSegment.Style, FormatSegment.Audio, FormatSegment.Arrange)
    is GroupElement -> listOf(FormatSegment.Style, FormatSegment.Arrange)
}

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
    /**
     * A shift-click on the row [id] names: every visible row from the selected
     * slide to this one joins the selection. The selected slide stays the one on
     * the canvas, so a second shift-click re-cuts the range from the same end.
     * A plain select in layout mode, where rows are picked one at a time.
     */
    data class ExtendSlideSelection(val id: String) : EditorEvent
    /**
     * A command-click on the row [id] names: in if it was out, out if it was in.
     * Taking the selected slide itself out hands the canvas to the first slide
     * left, and the last one standing stays, since a deck always shows a slide.
     */
    data class ToggleSlideSelection(val id: String) : EditorEvent
    /** Select All with the navigator focused: every slide, the canvas's unmoved. */
    data object SelectAllSlides : EditorEvent
    /**
     * An arrow key in the navigator: [delta] visible rows down, negative for up.
     * Plain, the selection collapses to the row past its end in that direction.
     * With [extend] (shift), the range grows or shrinks at its moving end while
     * the selected slide anchors the other, the way a list's does.
     */
    data class StepSlideSelection(val delta: Int, val extend: Boolean = false) : EditorEvent
    /**
     * Tab and Shift+Tab in the navigator: the selected slides go one level in
     * ([delta] 1) or out (-1), each with the run beneath it. One history entry,
     * none when no slide had anywhere to go. See `Document.indentSlide`.
     */
    data class IndentSlides(val delta: Int) : EditorEvent
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
     * and is undone wholesale by [CancelPreview]. [drag] is set by a canvas
     * move or resize and nothing else: it is what the alignment overlay is
     * drawn from, and a slider or a keystroke has no pointer to report. */
    data class PreviewElements(val elements: List<Element>, val drag: DragSample? = null) : EditorEvent
    /**
     * An arrow key going down, or repeating, on the canvas: the unlocked part of
     * the selection moves [dx], [dy] as one box, [PreviewElements]' semantics.
     * A held run adds up, each repeat moving on from where the last one left
     * the selection. No-op while there is a caret: the arrows are the text's.
     */
    data class NudgeSelection(val dx: Float, val dy: Float) : EditorEvent
    /**
     * The arrow key coming back up: the run of [NudgeSelection]s settles as one
     * history entry, the way a drag's release does. No-op with no run in flight.
     */
    data object CommitNudge : EditorEvent
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
    /** The only event that unlocks, and with [SetElementsHidden] and [RenameElement] the only
     * ones a locked element answers to. Undoable, like Keynote's. The shell decides what the
     * toggle means for a mixed selection. */
    data class SetElementsLocked(val ids: List<String>, val locked: Boolean) : EditorEvent
    /**
     * Hides or shows elements at any depth: a hidden one stays in the document
     * and in the Object List, and still selects from there. Applies to locked
     * elements like [SetElementsLocked] does. One history entry, none when
     * nothing changes.
     */
    data class SetElementsHidden(val ids: List<String>, val hidden: Boolean) : EditorEvent
    /**
     * Names the element [id] names, at any depth, locked or not. A blank or null
     * [name] goes back to the default one read off the content. One history
     * entry, none when nothing changes.
     */
    data class RenameElement(val id: String, val name: String?) : EditorEvent
    data class FlipElements(val ids: List<String>, val axis: FlipAxis) : EditorEvent
    /** Wraps the selection into one group and selects it. Needs two unlocked members. */
    data class GroupElements(val ids: List<String>) : EditorEvent
    /** Breaks the group apart and selects the children it frees. Not for a locked group. */
    data class UngroupElements(val id: String) : EditorEvent
    /**
     * Breaks a compound shape into one shape per contour and selects the pieces.
     * Not for a locked shape, and a no-op for anything `isCompoundShape` is false
     * for. One history entry.
     */
    data class BreakApartElement(val id: String) : EditorEvent
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
     *
     * A null [layoutId] builds it on the anchor's layout; a set one builds it on
     * that layout instead, the way Keynote's "Add a Slide" picker does. An id
     * that resolves to no layout falls back to the anchor's.
     */
    data class AddSlide(val afterId: String, val layoutId: String? = null) : EditorEvent
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
    data class MoveSlide(
        val id: String,
        val afterId: String?,
        val nest: Boolean = false,
        /** The level asked for, clamped to what the gap allows. Null lets the gap decide. */
        val depth: Int? = null,
    ) : EditorEvent
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
     * A dropped Object List drag: the element [id] names lands directly in front
     * of [beforeId] in the z-order, immediately before its row, or at the very
     * back of its siblings when null. Siblings only, per `Slide.moveElement`, so
     * a drop never takes an element in or out of a group.
     *
     * One history entry, the dropped element selected, and the drag cleared
     * either way: a drop that changes nothing is still a drop, the way
     * [MoveSlide]'s is, it just costs no history entry.
     */
    data class MoveElement(val id: String, val beforeId: String?) : EditorEvent
    /**
     * An in-flight Object List drag sample: where the row would land and how far
     * it has been carried. [PreviewSlideDrag]'s kind: no document, no history.
     */
    data class PreviewObjectDrag(
        val id: String,
        val beforeId: String?,
        val translationY: Float = 0f,
    ) : EditorEvent
    /** The Object List drag is over without a drop: the marker goes. */
    data object EndObjectDrag : EditorEvent
    /**
     * Takes the slide in or out of the presentation. Per slide, not per row: a
     * collapsed parent's hidden run keeps its own answer. One history entry, and
     * a slide already like this is a no-op.
     */
    data class SetSlideSkipped(val id: String, val skipped: Boolean) : EditorEvent
    /**
     * Dresses the slide in the transition that plays on the way out of it, null
     * putting it back on the deck's default. One history entry, and a slide
     * already wearing this transition is a no-op.
     */
    data class SetSlideTransition(
        val slideId: String,
        val transition: SlideTransition?,
    ) : EditorEvent
    /**
     * How the whole deck plays: its kind of show, and what loops and restarts.
     * One history entry, and settings the deck already carries are a no-op.
     */
    data class SetPlayback(val settings: PlaybackSettings) : EditorEvent
    /**
     * Points every one of [ids] at [target], null unlinking them. One history
     * entry for the batch, the way every element edit is.
     *
     * Locked elements are left out, and so are the kinds that hold no link at
     * all. A text box's older URL string is kept in step with its target: written
     * for a [LinkTarget.Url] and cleared for anything else, so nothing reads a
     * stale address off a box that now jumps to a slide.
     */
    data class SetElementLinks(val ids: List<String>, val target: LinkTarget?) : EditorEvent
    /**
     * Points `[start, end)` of text box [elementId]'s text at [target], null
     * unlinking that stretch: `withLink`, so a link it overlaps is trimmed or
     * split around it. One history entry, and one that changes nothing is none.
     *
     * Not one of the events that may land mid-edit: like every other edit to
     * the box, it settles the session first, so the typing so far is its own
     * entry and the link is the next one. A locked box refuses it.
     */
    data class SetTextLink(
        val elementId: String,
        val start: Int,
        val end: Int,
        val target: LinkTarget?,
    ) : EditorEvent
    /**
     * Appends a build to the selected slide's build order, which is where a new
     * one lands: the order is the order they play in.
     *
     * One history entry. A build naming an element the slide doesn't hold is a
     * no-op, at any depth: a build for nothing would hold a step open for an
     * element that isn't there to reveal.
     */
    data class AddBuild(val build: Build) : EditorEvent
    /**
     * Writes the build order that walks an element through its own states: a
     * gallery through its pictures, a code block or a diagram through its steps,
     * one click each after the first, per `Slide.elementSteps`.
     *
     * The element's existing step builds go first, so pressing it twice leaves
     * the slide with one set rather than two, and adding a picture or a step and
     * pressing it again re-times the walk instead of appending to it. Every other
     * build for the element is left exactly where it is.
     *
     * One history entry. An id the slide doesn't hold is a no-op, and so is a set
     * of steps the slide already carries.
     */
    data class AddElementSteps(val elementId: String) : EditorEvent
    /**
     * Replaces the build at [index] with [build]: what every control in the build
     * order writes, from its effect to its trigger. One history entry, and an
     * index the slide has no build at, an element it doesn't hold, or a build
     * already exactly like this, is a no-op.
     */
    data class UpdateBuild(val index: Int, val build: Build) : EditorEvent
    /** Drops the build at [index]. One history entry; an index out of range is a
     * no-op. The element stays exactly where it is, and shows from step 0 again
     * once nothing brings it in. */
    data class RemoveBuild(val index: Int) : EditorEvent
    /**
     * Moves the build at [from] to [to], the reorder the build order's rows do.
     * Steps are a function of the order, so this is what re-times a slide. One
     * history entry, and either index out of range, or a move to where it
     * already is, is a no-op.
     */
    data class MoveBuild(val from: Int, val to: Int) : EditorEvent
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
    /**
     * The caret's field moved its selection to `[start, end)` of [elementId]'s
     * text, collapsed for a bare caret. Sent by the field on every move during a
     * session; one for an element that isn't the one being edited is ignored.
     *
     * View state, like the marquee: no document, no history entry, and it keeps
     * the session open.
     */
    data class EditSelectionChanged(val elementId: String, val start: Int, val end: Int) : EditorEvent
    /**
     * Shows version [index] of the selected code block, clamped into the versions
     * it has. Nothing in the document changes, so no history entry: which version
     * you are looking at is where you are, not an edit.
     */
    data class SelectCodeVersion(val index: Int) : EditorEvent
    /**
     * Adds a version to the code block [elementId], as a copy of the one on show,
     * and moves to it: a rewrite starts from the text it rewrites.
     *
     * One history entry. A no-op when the id is not an unlocked code block on the
     * selected slide, the way every element edit is.
     */
    data class AddCodeVersion(val elementId: String) : EditorEvent
    /**
     * Takes version [index] off the code block [elementId]. The block's steps
     * follow the text they pointed at, and the ones pointing at the version that
     * went fall back to the nearest one left.
     *
     * One history entry. A no-op when the id is stale, when the block has only
     * the one version (a block with no source at all is not a state), or when
     * [index] names no version it has.
     */
    data class RemoveCodeVersion(val elementId: String, val index: Int) : EditorEvent
    /**
     * Moves version [from] of the code block [elementId] to sit at [to], steps
     * following their text like [RemoveCodeVersion]'s do. One history entry, and
     * a move that lands where it started is a no-op.
     */
    data class MoveCodeVersion(val elementId: String, val from: Int, val to: Int) : EditorEvent
    /**
     * Picks step [index] of the selected code block for the inspector to edit,
     * null picking none. Nothing in the document changes, so no history entry:
     * which row you are on is where you are, not an edit.
     *
     * [EditorState.codeVersion] moves to the version that step plays, so the
     * canvas shows the source the step is written against. An index the block
     * has no step at changes nothing: the row that was picked stays picked.
     */
    data class SelectCodeStep(val index: Int?) : EditorEvent
    /**
     * Adds a step to the code block [elementId], behind the one being edited (at
     * the end when none is), and picks it.
     *
     * A copy of the picked step, so a new state starts from the one before it the
     * way a new version starts from the text it rewrites; with no step picked it
     * is a plain step on the version showing, which reveals everything and
     * highlights nothing.
     *
     * One history entry, and the slide's builds follow their steps. A no-op when
     * the id is not an unlocked code block on the selected slide.
     */
    data class AddCodeStep(val elementId: String) : EditorEvent
    /**
     * Takes step [index] off the code block [elementId]. Builds behind it come
     * back one and the build that played it goes, per `Slide.removingCodeStep`.
     *
     * One history entry. A no-op when the id is stale or when [index] names no
     * step the block has.
     */
    data class RemoveCodeStep(val elementId: String, val index: Int) : EditorEvent
    /**
     * Moves step [from] of the code block [elementId] to sit at [to], builds
     * following the step they played. One history entry, and a move that lands
     * where it started is a no-op.
     */
    data class MoveCodeStep(val elementId: String, val from: Int, val to: Int) : EditorEvent
    /**
     * Replaces step [index] of the code block [elementId] with [step]: what every
     * control on the step row writes, from its revealed lines to its version.
     *
     * [CodeStep.version] is clamped into the versions the block has, so a row
     * pointing past the end lands on the last one rather than on nothing, and a
     * [CodeStep.scrollTo] that is no line at all goes back to null. Its
     * diagnostics are `sanitized`, so a span is never reversed. One history
     * entry, and a step already exactly like this is a no-op.
     */
    data class UpdateCodeStep(
        val elementId: String,
        val index: Int,
        val step: CodeStep,
    ) : EditorEvent
    /**
     * Picks step [index] of the selected diagram for the inspector to edit, null
     * picking none: [SelectCodeStep]'s twin. No history entry, and an index the
     * diagram has no step at changes nothing.
     *
     * The canvas previews the picked step's reveal and highlight
     * ([EditorState.shownDiagramStep]); null puts the whole diagram back.
     */
    data class SelectDiagramStep(val index: Int?) : EditorEvent
    /**
     * Adds a step to the diagram [elementId], behind the one being edited (at the
     * end when none is), and picks it: [AddCodeStep]'s twin.
     *
     * A copy of the picked step, so a new state starts from the one before it;
     * with no step picked it is a plain step, which reveals every node and
     * highlights none. One history entry, and the slide's builds follow their
     * steps. A no-op when the id is not an unlocked diagram on the selected slide.
     */
    data class AddDiagramStep(val elementId: String) : EditorEvent
    /**
     * Takes step [index] off the diagram [elementId]. Builds behind it come back
     * one and the build that played it goes, per `Slide.removingDiagramStep`.
     *
     * One history entry. A no-op when the id is stale or when [index] names no
     * step the diagram has.
     */
    data class RemoveDiagramStep(val elementId: String, val index: Int) : EditorEvent
    /**
     * Moves step [from] of the diagram [elementId] to sit at [to], builds
     * following the step they played. One history entry, and a move that lands
     * where it started is a no-op.
     */
    data class MoveDiagramStep(val elementId: String, val from: Int, val to: Int) : EditorEvent
    /**
     * Replaces step [index] of the diagram [elementId] with [step]: what both of
     * the step row's fields write, through `parseNodeIds`.
     *
     * Ids are taken as given, ones no node carries included, since a field mid-word
     * holds exactly those. One history entry, and a step already exactly like this
     * is a no-op.
     */
    data class UpdateDiagramStep(
        val elementId: String,
        val index: Int,
        val step: DiagramStep,
    ) : EditorEvent
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
     * What the deck is called, which is what [EditorState.title] shows. One
     * history entry, so a rename typed into a title bar is undone like any other
     * edit, and a name the deck already has is a no-op. Blank is refused rather
     * than accepted: an untitled deck is called "Untitled", not nothing.
     *
     * The bundle on disk keeps whatever name it was saved under. Renaming the
     * folder is the shell's business (it owns the file dialogs), and a deck that
     * renamed its own bundle out from under an open window would be worse.
     */
    data class RenameDocument(val name: String) : EditorEvent
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
    /**
     * The font file already written under [assetId] added to the deck as the cut
     * of [family] at [weight] and [italic], so text set in that family draws in
     * it wherever the deck is opened. A cut the deck already has (same family,
     * weight and slant) is replaced. One history entry, and a deck already
     * carrying exactly this is a no-op.
     *
     * The bytes are the shell's to write first, the way an image's are; see
     * `addDeckFont` in `:cupboard:ui`, which writes, reads the file and sends this.
     */
    data class AddDeckFont(
        val assetId: String,
        val family: String,
        val weight: Int,
        val italic: Boolean,
    ) : EditorEvent
    /**
     * The font under [assetId] dropped from the deck. Text set in its family
     * stays set in it and falls back like any face that isn't there. One history
     * entry, and an id the deck doesn't carry is a no-op.
     */
    data class RemoveDeckFont(val assetId: String) : EditorEvent
    data object Undo : EditorEvent
    data object Redo : EditorEvent
    data object ToggleSidebar : EditorEvent
    /** Shows or hides the Object List. [ToggleSidebar]'s kind: chrome, no history entry. */
    data object ToggleObjectList : EditorEvent
    /** What the Object List's search field holds. Chrome, no history entry. */
    data class SetObjectListFilter(val text: String) : EditorEvent
    /** Opens or closes a group's row in the Object List. Chrome, no history entry. */
    data class ToggleObjectExpanded(val id: String) : EditorEvent
    data object ToggleNotes : EditorEvent
    /** The strip's top edge, dragged. [ToggleNotes]' kind, and never under [NotesMinHeight]. */
    data class ResizeNotes(val height: Float) : EditorEvent
    /** The caret arrived in the strip. [BeginTextEdit]'s kind: a caret is not an edit. */
    data object BeginNotesEdit : EditorEvent
    /**
     * The selected slide's notes as they stand after a keystroke in the strip.
     * [PreviewSlide]'s semantics: it folds into the document and makes no
     * history entry, [EndNotesEdit] does that.
     */
    data class EditNotes(val notes: String) : EditorEvent
    /**
     * The caret left the notes: everything typed since it arrived becomes one
     * history entry, or none when the notes came back the way they started.
     * Nothing at all when the caret was not there, so a late blur costs nothing.
     */
    data object EndNotesEdit : EditorEvent
    /** View toggles, [ToggleNotes]' kind: no document, no history entry. */
    data object ToggleRulers : EditorEvent
    data object ToggleGuides : EditorEvent
    /**
     * The canvas's zoom and scroll, view state like [ToggleRulers]: no document,
     * no history entry. [SetZoom]'s 0 is Fit Slide, which also recentres;
     * [ZoomBy] is a pinch, anchored on a document point relative to the slide
     * centre; [PanCanvas] is in document units and [CanvasResized] in dp.
     */
    data class SetZoom(val percent: Int) : EditorEvent
    data object ZoomIn : EditorEvent
    data object ZoomOut : EditorEvent
    data object ZoomToSelection : EditorEvent
    data object ZoomToFitContent : EditorEvent
    data class ZoomBy(val factor: Float, val anchorX: Float, val anchorY: Float) : EditorEvent
    data class PanCanvas(val dx: Float, val dy: Float) : EditorEvent
    data class CanvasResized(val width: Float, val height: Float) : EditorEvent
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
    /**
     * Picks the Format segment, which is a preference rather than a view: a
     * segment this selection doesn't offer is ignored, so a stale click can't
     * leave the inspector pointing at a pane that isn't there.
     *
     * Chrome, like [SelectInspectorTab]: no document, no history entry. Unlike
     * it, it opens nothing: a segment is only reachable with the panel already up.
     */
    data class SelectFormatSegment(val segment: FormatSegment) : EditorEvent
    /** Opens or closes one collapsible section. [SelectFormatSegment]'s kind. */
    data class ToggleInspectorSection(val section: InspectorSection) : EditorEvent
    /** Picks the Animate segment. [SelectFormatSegment]'s kind, with nothing to
     * refuse: every element offers all three. */
    data class SelectAnimateSegment(val segment: AnimateSegment) : EditorEvent
    /**
     * The deck as the file now holds it, arriving from the bundle watcher rather
     * than from a person: another tool wrote `document.json`, a checkout swapped
     * it, a script regenerated it.
     *
     * Replaces the document and resets history the way a fresh open does, since
     * an undo that stepped back past it would be undoing someone else's edit. The
     * selection stays where it still exists, and re-anchors by index where it
     * doesn't, the way an undo's does.
     *
     * Ignored when it equals what autosave last wrote, which is this editor's own
     * write coming back round through the watcher, or what the editor already
     * shows, which is a write that changed nothing.
     */
    data class Reload(val document: Document) : EditorEvent
    /**
     * Bytes under `assets/` changed; nothing in the document did. Bumps
     * [EditorState.assetGeneration] so renderers re-read their images. Chrome as
     * far as the loop goes: no document, no selection, no history.
     */
    data object RefreshAssets : EditorEvent
}

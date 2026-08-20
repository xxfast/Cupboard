package io.github.xxfast.cupboard.screens.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.addElements
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.applyingStyle
import io.github.xxfast.cupboard.document.drawnBounds
import io.github.xxfast.cupboard.document.duplicated
import io.github.xxfast.cupboard.document.groupElements
import io.github.xxfast.cupboard.document.insertionIndexAfter
import io.github.xxfast.cupboard.document.moveSlide
import io.github.xxfast.cupboard.document.newId
import io.github.xxfast.cupboard.document.removeElements
import io.github.xxfast.cupboard.document.removeSlide
import io.github.xxfast.cupboard.document.reorderElements
import io.github.xxfast.cupboard.document.setSlideSkipped
import io.github.xxfast.cupboard.document.slideAt
import io.github.xxfast.cupboard.document.slideGroup
import io.github.xxfast.cupboard.document.toggleCollapsed
import io.github.xxfast.cupboard.document.ungroupElement
import io.github.xxfast.cupboard.document.updateElements
import io.github.xxfast.cupboard.document.updateSlide
import io.github.xxfast.cupboard.document.withNewIds
import io.github.xxfast.cupboard.editor.alignFrames
import io.github.xxfast.cupboard.editor.distributeFrames
import io.github.xxfast.cupboard.screens.editor.EditorEvent.AddSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.AlignElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CancelPreview
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ClearAll
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CloseInspector
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ContextClick
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Copy
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CopyElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CopySlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CopyStyle
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Cut
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CutElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CutSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Delete
import io.github.xxfast.cupboard.screens.editor.EditorEvent.DeleteElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.DeleteSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.DistributeElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Duplicate
import io.github.xxfast.cupboard.screens.editor.EditorEvent.DuplicateElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.DuplicateSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EndMarquee
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EndSlideDrag
import io.github.xxfast.cupboard.screens.editor.EditorEvent.FlipElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.FocusPane
import io.github.xxfast.cupboard.screens.editor.EditorEvent.GroupElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.MoveSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Paste
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PasteStyle
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewMarquee
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewSlideDrag
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Redo
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ReorderElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectElement
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectInspectorTab
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectSlideAt
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetElementsLocked
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetSlideSkipped
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleCollapsed
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleElementSelection
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleNotes
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleSidebar
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Undo
import io.github.xxfast.cupboard.screens.editor.EditorEvent.UngroupElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.UpdateElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.UpdateSlide
import io.github.xxfast.kstore.KStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.milliseconds

/** How long the editor sits still before the document is written out. */
private val AutosaveDebounce = 500.milliseconds

/**
 * How far back undo reaches. Whole documents, so this is a memory bound as much
 * as a policy one; the oldest entry falls off the bottom when it's reached.
 */
private const val HistoryLimit = 100

/**
 * How far a pasted copy lands from the one before it, in document units. Only
 * the second paste of a clipboard onwards is offset: the first lands where the
 * copy was taken, which is what makes cut then paste a move.
 */
private const val PasteOffset = 24f

/**
 * What the editor is carrying, elements or slides, never both: a copy replaces
 * whatever was there, the way one system pasteboard would.
 *
 * Presenter-local like the history, and app-session-only: this is not the OS
 * pasteboard, nothing here is persisted, and none of it belongs in EditorState,
 * which would otherwise serialize a whole second copy of the document.
 */
private sealed interface Clipboard {
    data class Elements(val elements: List<Element>) : Clipboard
    data class Slides(val slides: List<Slide>) : Clipboard
}

/** Pushes [document], dropping the oldest entry once [HistoryLimit] is reached. */
private fun ArrayDeque<Document>.push(document: Document) {
    addLast(document)
    if (size > HistoryLimit) removeFirst()
}

/**
 * The selected slide's element with [id], null when the id no longer resolves.
 *
 * Element events always resolve against the stored copy rather than trusting the
 * one they carry: whether an element is locked is the document's answer, and a
 * shell holding a stale copy must not be able to edit its way around the lock.
 */
private fun EditorState.element(id: String): Element? =
    selectedSlide.elements.firstOrNull { it.id == id }

/** [element], but only when it is unlocked, i.e. when an edit may touch it. */
private fun EditorState.unlockedElement(id: String): Element? = element(id)?.takeIf { !it.locked }

/** The stored, unlocked elements of [ids], in the order asked for. */
private fun EditorState.unlockedElements(ids: List<String>): List<Element> =
    ids.mapNotNull { id -> unlockedElement(id) }

/**
 * The stored elements of [ids] in z-order rather than in the order asked for.
 *
 * What the clipboard stores in: a selection has an order of its own, made click
 * by click, and pasting in that order would shuffle what draws over what.
 */
private fun EditorState.stackedElements(ids: List<String>): List<Element> {
    val wanted: Set<String> = ids.toSet()
    return selectedSlide.elements.filter { it.id in wanted }
}

/**
 * The incoming elements an edit is allowed to land: locked ones are filtered out
 * one by one, so a batch that half of the selection may not answer still lands
 * for the other half.
 */
private fun EditorState.editable(elements: List<Element>): List<Element> =
    elements.filter { unlockedElement(it.id) != null }

/** Folds [elements] back into the document through their slide. */
private fun EditorState.withElements(elements: List<Element>): EditorState =
    copy(document = document.updateSlide(selectedSlide.updateElements(elements)))

/**
 * Takes [ids] off the selected slide and out of the selection, or null when none
 * of them was there to take: a caller that gets null skips the history entry,
 * the same identity test the document helpers hand back.
 *
 * The selection is rewritten here rather than left to dangle, unlike an undone
 * group: a deleted element is gone for good, so there is no later redo for a
 * kept id to come back to.
 */
private fun EditorState.withoutElements(ids: Set<String>): EditorState? {
    val slide: Slide = selectedSlide
    val trimmed: Slide = slide.removeElements(ids)
    if (trimmed === slide) return null

    return copy(
        document = document.updateSlide(trimmed),
        selectedElementIds = selectedElementIds.filter { it !in ids },
    )
}

/**
 * The slide with [id] taken out, or null when it wasn't there to take.
 *
 * The gap closes up, so the selection lands on whatever now holds the removed
 * slide's index: the slide after it, the one before when it was last, or the
 * blank slide left behind by removing them all. Testing the selected id rather
 * than the removed one covers a selected slide that was hidden inside a
 * collapsed one and went with it.
 */
private fun EditorState.withoutSlide(id: String): EditorState? {
    val index: Int = document.slides.indexOfFirst { it.id == id }
    val remaining: Document = document.removeSlide(id)
    if (remaining === document) return null

    return if (remaining.slides.any { it.id == selectedSlideId }) copy(document = remaining)
    else copy(
        document = remaining,
        selectedSlideId = remaining.slideAt(index)?.id ?: selectedSlideId,
        selectedElementIds = emptyList(),
    )
}

/**
 * Copies of [elements] laid on top of the selected slide, [offset] units down and
 * right, and selected: what paste and duplicate both come down to. Fresh ids all
 * the way down, so a group's children are as new as the group.
 */
private fun EditorState.pasting(elements: List<Element>, offset: Float): EditorState {
    val copies: List<Element> = elements.map { element ->
        val fresh: Element = element.withNewIds()
        if (offset == 0f) fresh else fresh.update(frame = fresh.frame.translate(offset, offset))
    }

    return copy(
        document = document.updateSlide(selectedSlide.addElements(copies)),
        selectedElementIds = copies.map { it.id },
    )
}

/**
 * Copies of [payload] spliced in right after the selected slide, the first of
 * them selected.
 *
 * Depths are re-based rather than kept: the shallowest slide of the payload
 * lands at the selected slide's depth and the rest keep their distance from it,
 * so a copied group pastes as a group wherever it is pasted. [payload] is
 * expected to be non-empty.
 */
private fun EditorState.pastingSlides(payload: List<Slide>): EditorState {
    // Past the selected slide's deeper run: pasted between a parent and its
    // children, the copies would take those children for themselves.
    val at: Int = document.insertionIndexAfter(selectedSlide.id)
    val shallowest: Int = payload.minOf { it.depth }
    val copies: List<Slide> = payload.map { slide ->
        slide.duplicated().copy(depth = selectedSlide.depth + slide.depth - shallowest)
    }

    return copy(
        document = document.copy(
            slides = document.slides.take(at) + copies + document.slides.drop(at),
        ),
        selectedSlideId = copies.first().id,
        selectedElementIds = emptyList(),
    )
}

/**
 * [restored] swapped in, with the slide selection re-anchored to [index] when
 * the swap has taken the selected slide away. [index] is where the selection sat
 * before the swap, so it lands on the slide that took its place, which is where
 * a deletion would have left it anyway.
 */
private fun EditorState.restoring(restored: Document, index: Int): EditorState =
    if (restored.slides.any { it.id == selectedSlideId }) copy(document = restored)
    else copy(
        document = restored,
        selectedSlideId = restored.slideAt(index)?.id ?: selectedSlideId,
    )

/**
 * The specific event a generic Edit verb comes down to: [slide] over the
 * selected slide's id when the navigator holds the focus, [elements] over the
 * element selection when the canvas does.
 *
 * The two are picked apart here so the verbs themselves stay one line each and
 * the focus rule lives in one place rather than four.
 */
private fun EditorState.targeting(
    slide: (String) -> EditorEvent,
    elements: (List<String>) -> EditorEvent,
): EditorEvent = when (focusedPane) {
    EditorPane.Navigator -> slide(selectedSlide.id)
    EditorPane.Canvas -> elements(selectedElementIds)
}

/**
 * The pane this event puts the keyboard focus in, null when it leaves focus
 * where it was.
 *
 * Focus follows interaction rather than arriving as an event of its own: a
 * shell that had to report focus alongside every click would be keeping a
 * second copy of the rule, and the pane an event belongs to is not a shell's
 * opinion. [EditorEvent.FocusPane] covers what is left over, and reduces
 * itself. Never a history entry either way.
 */
private fun EditorEvent.focusing(): EditorPane? = when (this) {
    is SelectSlide, is SelectSlideAt, is ToggleCollapsed, is AddSlide, is DuplicateSlide,
    is CutSlide, is CopySlide, is DeleteSlide, is MoveSlide, is SetSlideSkipped,
        -> EditorPane.Navigator

    is SelectElement, is SelectElements, is ToggleElementSelection, is ContextClick,
    is PreviewMarquee,
        -> EditorPane.Canvas

    else -> null
}

/**
 * The editor screen's logic, once, for every shell.
 *
 * A composable presenter run by Molecule: [events] fold into state, state comes
 * back out. Nothing here knows about windows, menus or NSViews, so the SwiftUI
 * host and the Compose shell share the same reductions instead of each having
 * their own copy.
 *
 * Persistence rides along: it's a function of the document, not a separate
 * subscriber that has to be started and stopped in step with the editor.
 */
@Composable
fun EditorPresenter(
    initialState: EditorState,
    events: Flow<EditorEvent>,
    documentStore: KStore<Document>,
): EditorState {
    var state: EditorState by remember { mutableStateOf(initialState) }

    // History is the presenter's, not the state's: past documents are what undo
    // needs, and nothing downstream (persistence, shells, restoration) wants them.
    val undone: ArrayDeque<Document> = remember { ArrayDeque() }
    val redone: ArrayDeque<Document> = remember { ArrayDeque() }

    LaunchedEffect(Unit) {
        // The document as it stood before the gesture in flight; null when no
        // gesture is running. Undo wants the pre-gesture document, and by
        // commit time the previews have already folded into state.
        var gestureBase: Document? = null

        // The clipboard, and how many times what's on it has been pasted: the
        // count is what cascades repeated pastes instead of stacking them.
        // Style rides on its own, so copying an element never costs you it.
        var clipboard: Clipboard? = null
        var pastes = 0
        var styleSource: Element? = null

        // The whole reduction as one function, so the generic Edit verbs can
        // re-enter it with the specific event they resolve to instead of
        // carrying a second copy of its body.
        fun reduce(event: EditorEvent): EditorState = when (event) {
            // Selecting a slide drops the element selection: the handles
            // would otherwise ring an element on a slide you can't see.
            is SelectSlide ->
                state.copy(selectedSlideId = event.id, selectedElementIds = emptyList())

            is SelectSlideAt -> state.document.allSlides().getOrNull(event.index)
                ?.let { state.copy(selectedSlideId = it.id, selectedElementIds = emptyList()) }
                ?: state

            is SelectElement -> state.copy(selectedElementIds = listOfNotNull(event.id))

            is SelectElements -> state.copy(selectedElementIds = event.ids)

            is ToggleElementSelection -> state.copy(
                selectedElementIds =
                    if (event.id in state.selectedElementIds) state.selectedElementIds - event.id
                    else state.selectedElementIds + event.id,
            )

            // A right-click on something already selected must not shrink the
            // selection to it: the menu it opens speaks for everything that
            // was selected. Anything else selects like a plain click.
            //
            // The bare copy is the point: it leaves the selection alone but
            // says the click landed, which is what lets the focus step below
            // tell an answered event from a turned-away one.
            is ContextClick ->
                if (event.elementId != null && event.elementId in state.selectedElementIds) state.copy()
                else state.copy(selectedElementIds = listOfNotNull(event.elementId))

            // The selection follows the rectangle instead of waiting for the
            // release, so the canvas can ring what is about to be caught.
            // Document order, not sweep order: the marquee has no order of
            // its own, and z-order is the one the slide already agrees on.
            is PreviewMarquee -> state.copy(
                marquee = event.rect,
                selectedElementIds = state.selectedSlide.elements
                    .filter { it.drawnBounds().overlaps(event.rect) }
                    .map { it.id },
            )

            EndMarquee -> state.copy(marquee = null)

            // Previews fold into the document (the canvas renders from
            // state, nothing else shows them) but leave history alone: the
            // gesture is one edit, and it isn't done yet.
            is PreviewSlide -> {
                if (gestureBase == null) gestureBase = state.document
                state.copy(
                    document = state.document.updateSlide(event.slide),
                    isPreviewing = true,
                )
            }

            is UpdateSlide -> {
                undone.push(gestureBase ?: state.document)
                gestureBase = null
                redone.clear()
                state.copy(
                    document = state.document.updateSlide(event.slide),
                    isPreviewing = false,
                )
            }

            CancelPreview -> gestureBase
                ?.let { base ->
                    gestureBase = null
                    state.copy(document = base, isPreviewing = false)
                }
                ?: state

            // The element events below are the slide ones at finer grain, so
            // they carry the same history rules: one entry per settled edit,
            // however many elements it moves. All of them skip locked
            // elements individually, and an edit left with nothing to do is
            // a no-op rather than an empty history entry.
            // SetElementsLocked is the only way back into a locked element.
            is PreviewElements -> state.editable(event.elements)
                .takeIf { it.isNotEmpty() }
                ?.let { elements ->
                    if (gestureBase == null) gestureBase = state.document
                    state.withElements(elements).copy(isPreviewing = true)
                }
                ?: state

            is UpdateElements -> state.editable(event.elements)
                .takeIf { it.isNotEmpty() }
                ?.let { elements ->
                    undone.push(gestureBase ?: state.document)
                    gestureBase = null
                    redone.clear()
                    state.withElements(elements).copy(isPreviewing = false)
                }
                ?: state

            is FlipElements -> state.unlockedElements(event.ids)
                .map { current ->
                    when (event.axis) {
                        FlipAxis.Horizontal ->
                            current.update(flippedHorizontally = !current.flippedHorizontally)

                        FlipAxis.Vertical ->
                            current.update(flippedVertically = !current.flippedVertically)
                    }
                }
                .takeIf { it.isNotEmpty() }
                ?.let { flipped ->
                    undone.push(state.document)
                    redone.clear()
                    state.withElements(flipped)
                }
                ?: state

            // Locks are the exception that reads the locked elements too,
            // and locking what is already locked is not an edit.
            is SetElementsLocked -> event.ids
                .mapNotNull { id -> state.element(id) }
                .filter { it.locked != event.locked }
                .map { it.update(locked = event.locked) }
                .takeIf { it.isNotEmpty() }
                ?.let { relocked ->
                    undone.push(state.document)
                    redone.clear()
                    state.withElements(relocked)
                }
                ?: state

            // Clamped at the ends, so a move that changes nothing comes back
            // as the same slide and costs no history entry.
            is ReorderElements -> {
                val slide: Slide = state.selectedSlide
                val movable: List<String> = state.unlockedElements(event.ids).map { it.id }
                val reordered: Slide? = slide.reorderElements(movable, event.move)
                    .takeIf { it !== slide }

                if (reordered == null) state
                else {
                    undone.push(state.document)
                    redone.clear()
                    state.copy(document = state.document.updateSlide(reordered))
                }
            }

            // Grouping selects what it made, ungrouping selects what it
            // freed: either way the selection is the thing now on screen.
            is GroupElements -> {
                val slide: Slide = state.selectedSlide
                val groupId: String = newId()
                val grouped: Slide? = slide.groupElements(event.ids, groupId)
                    .takeIf { it !== slide }

                if (grouped == null) state
                else {
                    undone.push(state.document)
                    redone.clear()
                    state.copy(
                        document = state.document.updateSlide(grouped),
                        selectedElementIds = listOf(groupId),
                    )
                }
            }

            is UngroupElements -> {
                val slide: Slide = state.selectedSlide
                val group: GroupElement? = state.unlockedElement(event.id) as? GroupElement
                val ungrouped: Slide? = group
                    ?.let { slide.ungroupElement(event.id) }
                    ?.takeIf { it !== slide }

                if (group == null || ungrouped == null) state
                else {
                    undone.push(state.document)
                    redone.clear()
                    state.copy(
                        document = state.document.updateSlide(ungrouped),
                        selectedElementIds = group.children.map { it.id },
                    )
                }
            }

            // Both come back with only the elements that actually moved, so
            // an align that was already aligned makes no history entry.
            is AlignElements -> alignFrames(
                elements = state.selectedElements.filter { !it.locked },
                edge = event.edge,
                slideWidth = state.document.slideWidth,
                slideHeight = state.document.slideHeight,
            )
                .takeIf { it.isNotEmpty() }
                ?.let { aligned ->
                    undone.push(state.document)
                    redone.clear()
                    state.withElements(aligned)
                }
                ?: state

            is DistributeElements ->
                distributeFrames(state.selectedElements.filter { !it.locked }, event.axis)
                    .takeIf { it.isNotEmpty() }
                    ?.let { spread ->
                        undone.push(state.document)
                        redone.clear()
                        state.withElements(spread)
                    }
                    ?: state

            // Deletion is the one edit that takes ids away for good, so it
            // is also the one that rewrites the selection instead of
            // letting it dangle. Locked elements are skipped like anywhere
            // else, which is what makes a lock worth having.
            is DeleteElements -> state
                .withoutElements(state.unlockedElements(event.ids).mapTo(mutableSetOf()) { it.id })
                ?.let { deleted ->
                    undone.push(state.document)
                    redone.clear()
                    deleted
                }
                ?: state

            // Everything unlocked, which on a slide with nothing unlocked
            // is nothing at all, and so no history entry either.
            ClearAll -> state
                .withoutElements(
                    state.selectedSlide.elements
                        .filterNot { it.locked }
                        .mapTo(mutableSetOf()) { it.id },
                )
                ?.let { cleared ->
                    undone.push(state.document)
                    redone.clear()
                    cleared
                }
                ?: state

            is DeleteSlide -> state.withoutSlide(event.id)
                ?.let { deleted ->
                    undone.push(state.document)
                    redone.clear()
                    deleted
                }
                ?: state

            // The clipboard events below. Copying is not an edit: it makes
            // no history entry, reads locked elements like any other, and
            // leaves both the document and the last copy alone when it
            // resolves to nothing. Cutting is a copy and a delete at once,
            // so it plays by the delete rules instead: unlocked only, one
            // history entry, and no entry when there was nothing to take.
            is CopyElements -> state.stackedElements(event.ids)
                .takeIf { it.isNotEmpty() }
                ?.let { copied ->
                    clipboard = Clipboard.Elements(copied)
                    pastes = 0
                    state
                }
                ?: state

            is CutElements -> {
                val cut: List<Element> = state.stackedElements(event.ids).filterNot { it.locked }
                state.withoutElements(cut.mapTo(mutableSetOf()) { it.id })
                    ?.let { removed ->
                        undone.push(state.document)
                        redone.clear()
                        clipboard = Clipboard.Elements(cut)
                        pastes = 0
                        removed
                    }
                    ?: state
            }

            is CopySlide -> state.document.slideGroup(event.id)
                .takeIf { it.isNotEmpty() }
                ?.let { copied ->
                    clipboard = Clipboard.Slides(copied)
                    pastes = 0
                    state
                }
                ?: state

            // The group is read before the removal, so a collapsed slide
            // carries the run it was hiding onto the clipboard and cut then
            // paste puts the whole group back rather than just its head.
            is CutSlide -> {
                val cut: List<Slide> = state.document.slideGroup(event.id)
                state.withoutSlide(event.id)
                    ?.let { removed ->
                        undone.push(state.document)
                        redone.clear()
                        clipboard = Clipboard.Slides(cut)
                        pastes = 0
                        removed
                    }
                    ?: state
            }

            Paste -> when (val payload: Clipboard? = clipboard) {
                null -> state

                is Clipboard.Elements -> {
                    undone.push(state.document)
                    redone.clear()
                    state.pasting(payload.elements, PasteOffset * pastes++)
                }

                // No offset to cascade: a slide has nowhere to land but
                // between two other slides.
                is Clipboard.Slides -> {
                    undone.push(state.document)
                    redone.clear()
                    state.pastingSlides(payload.slides)
                }
            }

            // Duplicating is a copy and a paste that never touch the
            // clipboard, so whatever you were carrying survives it.
            is DuplicateElements -> state.stackedElements(event.ids)
                .filterNot { it.locked }
                .takeIf { it.isNotEmpty() }
                ?.let { originals ->
                    undone.push(state.document)
                    redone.clear()
                    state.pasting(originals, PasteOffset)
                }
                ?: state

            // The copy goes past the original's deeper run rather than
            // straight after it: dropped between a slide and the run under
            // it, the duplicate would take that run for itself, collapsed
            // or not.
            is DuplicateSlide -> state.document.slideGroup(event.id)
                .takeIf { it.isNotEmpty() }
                ?.let { group ->
                    val after: Int = state.document.insertionIndexAfter(event.id)
                    val copies: List<Slide> = group.map { it.duplicated() }
                    undone.push(state.document)
                    redone.clear()
                    state.copy(
                        document = state.document.copy(
                            slides = state.document.slides.take(after) + copies +
                                state.document.slides.drop(after),
                        ),
                        selectedSlideId = copies.first().id,
                        selectedElementIds = emptyList(),
                    )
                }
                ?: state

            // The blank inherits the anchor's depth, so New Slide on a
            // child makes a sibling, not a top-level slide out of place.
            is AddSlide -> state.document.slides.firstOrNull { it.id == event.afterId }
                ?.let { anchor ->
                    val at: Int = state.document.insertionIndexAfter(anchor.id)
                    val fresh = Slide(depth = anchor.depth)
                    undone.push(state.document)
                    redone.clear()
                    state.copy(
                        document = state.document.copy(
                            slides = state.document.slides.take(at) + fresh +
                                state.document.slides.drop(at),
                        ),
                        selectedSlideId = fresh.id,
                        selectedElementIds = emptyList(),
                    )
                }
                ?: state

            // A drag shows through the loop and moves nothing until it lands,
            // so the previews below are the marquee's kind rather than the
            // element ones': no document, no history, just the gap to draw.
            is PreviewSlideDrag ->
                state.copy(slideDrag = SlideDrag(event.slideId, event.afterId, event.nest))

            EndSlideDrag -> state.copy(slideDrag = null)

            // The drop always ends the drag, whether or not it moved anything:
            // a row put back where it came from is a finished gesture too, it
            // just isn't an edit.
            is MoveSlide -> {
                val moved: Document = state.document.moveSlide(event.id, event.afterId, event.nest)
                if (moved === state.document) state.copy(slideDrag = null)
                else {
                    undone.push(state.document)
                    redone.clear()
                    state.copy(
                        document = moved,
                        selectedSlideId = event.id,
                        selectedElementIds = emptyList(),
                        slideDrag = null,
                    )
                }
            }

            is SetSlideSkipped -> {
                val updated: Document = state.document.setSlideSkipped(event.id, event.skipped)
                if (updated === state.document) state
                else {
                    undone.push(state.document)
                    redone.clear()
                    state.copy(document = updated)
                }
            }

            is CopyStyle -> state.element(event.id)
                ?.let { source ->
                    styleSource = source
                    state
                }
                ?: state

            is PasteStyle -> styleSource
                ?.let { source -> state.unlockedElements(event.ids).map { it.applyingStyle(source) } }
                ?.takeIf { it.isNotEmpty() }
                ?.let { styled ->
                    undone.push(state.document)
                    redone.clear()
                    state.withElements(styled)
                }
                ?: state

            // Disclosure is not an edit, so it makes no history entry, the
            // same way Keynote won't undo a twisty. The document still
            // changes: collapsed state is stored on the slide.
            is ToggleCollapsed -> state.copy(document = state.document.toggleCollapsed(event.slideId))

            // The slide selection is left alone as long as it still
            // resolves, which is the common case. It can now fail to:
            // redoing a deletion takes the selected slide away, and undoing
            // one takes away the blank slide that deleting the last one
            // left. Where it fails, the selection re-anchors to the index it
            // sat at, clamped, so it lands on the same slide the deletion
            // itself would have moved it to rather than silently falling
            // back to the first slide of the deck.
            //
            // Element ids are left to dangle, deliberately: undoing a group
            // takes the group's id away, redoing an ungroup takes the
            // children's, EditorState.selectedElements drops ids that no
            // longer resolve, and a dangling id that comes back on the next
            // redo is a selection restored rather than a selection lost.
            Undo -> undone.removeLastOrNull()
                ?.let { previous ->
                    redone.push(state.document)
                    state.restoring(previous, state.selectedSlideIndex())
                }
                ?: state

            Redo -> redone.removeLastOrNull()
                ?.let { next ->
                    undone.push(state.document)
                    state.restoring(next, state.selectedSlideIndex())
                }
                ?: state

            ToggleSidebar -> state.copy(sidebarOpen = !state.sidebarOpen)

            ToggleNotes -> state.copy(showNotes = !state.showNotes)

            // A tab always opens the inspector. Whether clicking the tab
            // that's already showing closes it is the shell's call: it
            // sends CloseInspector when that's what it means.
            is SelectInspectorTab -> state.copy(inspectorTab = event.tab, inspectorOpen = true)

            CloseInspector -> state.copy(inspectorOpen = false)

            // The Edit-menu verbs, focus resolved: the same reductions again
            // with the target the focused pane names. Re-entered rather than
            // repeated, so a generic Cut comes out as a CutSlide or a
            // CutElements exactly, history entry and no-op included.
            Cut -> reduce(state.targeting(::CutSlide, ::CutElements))

            Copy -> reduce(state.targeting(::CopySlide, ::CopyElements))

            Duplicate -> reduce(state.targeting(::DuplicateSlide, ::DuplicateElements))

            Delete -> reduce(state.targeting(::DeleteSlide, ::DeleteElements))

            // Focus on its own. The post-step below moves it for every event
            // that implies a pane; this is the one that says so outright.
            is FocusPane -> state.copy(focusedPane = event.pane)
        }

        events.collect { event ->
            val reduced: EditorState = reduce(event)

            // Focus follows interaction: an event that speaks for a pane moves
            // the keyboard focus into it, everything else leaves it where it
            // was. Only an event the reduction answered, though: a reduction
            // that turned one away hands back the very state it was given, and
            // a select that landed on no slide is no interaction with the
            // navigator.
            val focused: EditorPane? = if (reduced === state) null else event.focusing()

            state = reduced.copy(
                canUndo = undone.isNotEmpty(),
                canRedo = redone.isNotEmpty(),
                canPaste = clipboard != null,
                canPasteStyle = styleSource != null,
                focusedPane = focused ?: reduced.focusedPane,
            )
        }
    }

    // The document as it was opened. Writing that back would rewrite the file on
    // a plain open, which is how a good file turns into a bad one after a crash.
    val opened: Document = remember { initialState.document }

    /*
     * Interim persistence: one whole Document as a single JSON file, no history,
     * no assets on the side. The real `.cupboard` bundle format comes later and
     * this goes away with it. Replaces the old DocumentAutosaver, which had to
     * be wired to the store's change callback by every shell.
     *
     * Debounce comes free from the effect's cancellation: a new document cancels
     * the pending delay, so a run of edits costs one write.
     */
    LaunchedEffect(state.document) {
        // Only a plain open is exempt. Undoing all the way back lands on the very
        // document we opened, and that has to be written: the edit it undoes is
        // already on disk. History being non-empty is what tells the two apart.
        val untouched = undone.isEmpty() && redone.isEmpty()
        if (state.document === opened && untouched) return@LaunchedEffect
        delay(AutosaveDebounce)
        documentStore.set(state.document)
    }

    return state
}

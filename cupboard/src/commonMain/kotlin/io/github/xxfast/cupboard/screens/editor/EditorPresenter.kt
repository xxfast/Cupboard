package io.github.xxfast.cupboard.screens.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.reorderElement
import io.github.xxfast.cupboard.document.toggleCollapsed
import io.github.xxfast.cupboard.document.updateElement
import io.github.xxfast.cupboard.document.updateSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CancelPreview
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CloseInspector
import io.github.xxfast.cupboard.screens.editor.EditorEvent.FlipElement
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewElement
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Redo
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ReorderElement
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectElement
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectInspectorTab
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectSlideAt
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleCollapsed
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleElementLock
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleNotes
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleSidebar
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Undo
import io.github.xxfast.cupboard.screens.editor.EditorEvent.UpdateElement
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

/** Folds [element] back into the document through its slide. */
private fun EditorState.withElement(element: Element): EditorState =
    copy(document = document.updateSlide(selectedSlide.updateElement(element)))

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

        events.collect { event ->
            state = when (event) {
                // Selecting a slide drops the element selection: the handles
                // would otherwise ring an element on a slide you can't see.
                is SelectSlide -> state.copy(selectedSlideId = event.id, selectedElementId = null)

                is SelectSlideAt -> state.document.allSlides().getOrNull(event.index)
                    ?.let { state.copy(selectedSlideId = it.id, selectedElementId = null) }
                    ?: state

                is SelectElement -> state.copy(selectedElementId = event.id)

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
                // they carry the same history rules. All of them ignore a locked
                // element: ToggleElementLock is the only way back in.
                is PreviewElement -> state.unlockedElement(event.element.id)
                    ?.let {
                        if (gestureBase == null) gestureBase = state.document
                        state.withElement(event.element).copy(isPreviewing = true)
                    }
                    ?: state

                is UpdateElement -> state.unlockedElement(event.element.id)
                    ?.let {
                        undone.push(gestureBase ?: state.document)
                        gestureBase = null
                        redone.clear()
                        state.withElement(event.element).copy(isPreviewing = false)
                    }
                    ?: state

                is FlipElement -> state.unlockedElement(event.id)
                    ?.let { current ->
                        undone.push(state.document)
                        redone.clear()
                        state.withElement(
                            when (event.axis) {
                                FlipAxis.Horizontal ->
                                    current.update(flippedHorizontally = !current.flippedHorizontally)

                                FlipAxis.Vertical ->
                                    current.update(flippedVertically = !current.flippedVertically)
                            }
                        )
                    }
                    ?: state

                is ToggleElementLock -> state.element(event.id)
                    ?.let { current ->
                        undone.push(state.document)
                        redone.clear()
                        state.withElement(current.update(locked = !current.locked))
                    }
                    ?: state

                // Clamped at the ends, so a move that changes nothing comes back
                // as the same slide and costs no history entry.
                is ReorderElement -> {
                    val slide: Slide = state.selectedSlide
                    val reordered: Slide? = state.unlockedElement(event.id)
                        ?.let { slide.reorderElement(event.id, event.move) }
                        ?.takeIf { it !== slide }

                    if (reordered == null) state
                    else {
                        undone.push(state.document)
                        redone.clear()
                        state.copy(document = state.document.updateSlide(reordered))
                    }
                }

                // Disclosure is not an edit, so it makes no history entry, the
                // same way Keynote won't undo a twisty. The document still
                // changes: collapsed state is stored on the slide.
                is ToggleCollapsed -> state.copy(document = state.document.toggleCollapsed(event.slideId))

                // Selection is left alone across both. Nothing can delete a
                // slide or an element yet, so the selected ids still resolve in
                // the restored document; deletion has to revisit this.
                Undo -> undone.removeLastOrNull()
                    ?.let { previous ->
                        redone.push(state.document)
                        state.copy(document = previous)
                    }
                    ?: state

                Redo -> redone.removeLastOrNull()
                    ?.let { next ->
                        undone.push(state.document)
                        state.copy(document = next)
                    }
                    ?: state

                ToggleSidebar -> state.copy(sidebarOpen = !state.sidebarOpen)

                ToggleNotes -> state.copy(showNotes = !state.showNotes)

                // A tab always opens the inspector. Whether clicking the tab
                // that's already showing closes it is the shell's call: it
                // sends CloseInspector when that's what it means.
                is SelectInspectorTab -> state.copy(inspectorTab = event.tab, inspectorOpen = true)

                CloseInspector -> state.copy(inspectorOpen = false)
            }.copy(canUndo = undone.isNotEmpty(), canRedo = redone.isNotEmpty())
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

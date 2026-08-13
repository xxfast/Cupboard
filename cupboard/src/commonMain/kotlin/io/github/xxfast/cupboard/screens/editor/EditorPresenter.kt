package io.github.xxfast.cupboard.screens.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.toggleCollapsed
import io.github.xxfast.cupboard.document.updateSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CancelPreview
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CloseInspector
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Redo
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectElement
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectInspectorTab
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectSlideAt
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleCollapsed
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleNotes
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleSidebar
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Undo
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

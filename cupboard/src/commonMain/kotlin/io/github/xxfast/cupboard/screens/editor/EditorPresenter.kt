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
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectElement
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectSlideAt
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleCollapsed
import io.github.xxfast.cupboard.screens.editor.EditorEvent.UpdateSlide
import io.github.xxfast.kstore.KStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.milliseconds

/** How long the editor sits still before the document is written out. */
private val AutosaveDebounce = 500.milliseconds

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

    LaunchedEffect(Unit) {
        events.collect { event ->
            state = when (event) {
                // Selecting a slide drops the element selection: the handles
                // would otherwise ring an element on a slide you can't see.
                is SelectSlide -> state.copy(selectedSlideId = event.id, selectedElementId = null)

                is SelectSlideAt -> state.document.allSlides().getOrNull(event.index)
                    ?.let { state.copy(selectedSlideId = it.id, selectedElementId = null) }
                    ?: state

                is SelectElement -> state.copy(selectedElementId = event.id)
                is UpdateSlide -> state.copy(document = state.document.updateSlide(event.slide))
                is ToggleCollapsed -> state.copy(document = state.document.toggleCollapsed(event.slideId))
            }
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
        if (state.document === opened) return@LaunchedEffect
        delay(AutosaveDebounce)
        documentStore.set(state.document)
    }

    return state
}

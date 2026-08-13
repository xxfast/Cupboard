package io.github.xxfast.cupboard.screens.editor

import app.cash.molecule.RecompositionMode.Immediate
import app.cash.molecule.moleculeFlow
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.ZOrderMove
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Host-agnostic view model for the editor screen: [states] out, event methods in.
 *
 * No base class on purpose. Every shell gets the same object: Compose collects
 * [states], the SwiftUI host collects it on the main dispatcher and pokes its
 * `@Observable` bridge, and a future WinUI host does the same through
 * kotlin-native-nuget. A lifecycle-library base class would only be available to
 * one of them.
 *
 * [states] is lazily shared, so the presenter starts with the first collector:
 * construct this at startup, then collect it before driving events at it.
 * Call [close] when the editor goes away; a document app that keeps one editor
 * for its whole life can let process exit do it.
 */
class EditorViewModel(
    private val initialState: EditorState,
    private val documentStore: KStore<Document>,
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) {
    /** Opens [initialDocument] on its first slide with content. */
    constructor(
        initialDocument: Document,
        documentStore: KStore<Document>,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) : this(EditorState.opening(initialDocument), documentStore, dispatcher)

    // Private: this class is exported to ObjC (and later to .NET), and a
    // CoroutineScope on the public surface is a Kotlin type those hosts have no
    // business seeing. They get `states` and the on-methods, nothing else.
    //
    // Shells must pass their serialized main dispatcher, not keep the Unconfined
    // default. Molecule's snapshot notifier schedules Snapshot.sendApplyNotifications
    // on this context; under Unconfined that runs synchronously inside the global
    // write observer, which intermittently starves recomposition: the presenter
    // sits on a pending invalidation until some unrelated snapshot write (a window
    // resize) flushes it, which reads as the editor freezing and magically reviving.
    // Unconfined stays as the default for tests, which pump the loop themselves.
    private val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())

    // Bigger than a screen that only refreshes: an editor emits bursts (a slide
    // select that clears a selection, keystrokes) and none of them may be lost.
    private val events: MutableSharedFlow<EditorEvent> = MutableSharedFlow(extraBufferCapacity = 64)

    val states: StateFlow<EditorState> =
        moleculeFlow(Immediate) { EditorPresenter(initialState, events, documentStore) }
            .stateIn(scope, SharingStarted.Lazily, initialState)

    fun onSelectSlide(id: String) { scope.launch { events.emit(SelectSlide(id)) } }
    fun onSelectSlideAt(index: Int) { scope.launch { events.emit(SelectSlideAt(index)) } }
    fun onSelectElement(id: String?) { scope.launch { events.emit(SelectElement(id)) } }
    fun onUpdateSlide(slide: Slide) { scope.launch { events.emit(UpdateSlide(slide)) } }
    fun onPreviewSlide(slide: Slide) { scope.launch { events.emit(PreviewSlide(slide)) } }
    fun onCancelPreview() { scope.launch { events.emit(CancelPreview) } }
    fun onUpdateElement(element: Element) { scope.launch { events.emit(UpdateElement(element)) } }
    fun onPreviewElement(element: Element) { scope.launch { events.emit(PreviewElement(element)) } }
    fun onReorderElement(id: String, move: ZOrderMove) { scope.launch { events.emit(ReorderElement(id, move)) } }
    fun onToggleElementLock(id: String) { scope.launch { events.emit(ToggleElementLock(id)) } }
    fun onFlipElement(id: String, axis: FlipAxis) { scope.launch { events.emit(FlipElement(id, axis)) } }
    fun onToggleCollapsed(slideId: String) { scope.launch { events.emit(ToggleCollapsed(slideId)) } }
    fun onUndo() { scope.launch { events.emit(Undo) } }
    fun onRedo() { scope.launch { events.emit(Redo) } }
    fun onToggleSidebar() { scope.launch { events.emit(ToggleSidebar) } }
    fun onToggleNotes() { scope.launch { events.emit(ToggleNotes) } }
    fun onSelectInspectorTab(tab: InspectorTab) { scope.launch { events.emit(SelectInspectorTab(tab)) } }
    fun onCloseInspector() { scope.launch { events.emit(CloseInspector) } }

    fun close() {
        scope.cancel()
    }
}

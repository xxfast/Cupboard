package io.github.xxfast.cupboard.screens.editor

import app.cash.molecule.RecompositionMode.Immediate
import app.cash.molecule.moleculeFlow
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GuideAxis
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.editor.AlignEdge
import io.github.xxfast.cupboard.editor.Axis
import io.github.xxfast.cupboard.editor.SnapKind
import io.github.xxfast.cupboard.screens.editor.EditorEvent.AddSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.AlignElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.BeginTextEdit
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CancelPreview
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ClearAll
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CloseInspector
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CommitGuide
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
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EndGuideDrag
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EndMarquee
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EndSlideDrag
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EndTextEdit
import io.github.xxfast.cupboard.screens.editor.EditorEvent.FlipElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.FocusPane
import io.github.xxfast.cupboard.screens.editor.EditorEvent.GroupElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.InsertElement
import io.github.xxfast.cupboard.screens.editor.EditorEvent.MoveSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Paste
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PasteStyle
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewGuide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewMarquee
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewSlideDrag
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Redo
import io.github.xxfast.cupboard.screens.editor.EditorEvent.RemoveGuide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ReorderElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectElement
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectInspectorTab
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectSlideAt
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetElementsLocked
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetSlideSkipped
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetSnap
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleCollapsed
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleElementSelection
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleGuides
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleNotes
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleRulers
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleSidebar
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Undo
import io.github.xxfast.cupboard.screens.editor.EditorEvent.UngroupElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.UpdateElements
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
    fun onSelectElements(ids: List<String>) { scope.launch { events.emit(SelectElements(ids)) } }
    fun onToggleElementSelection(id: String) { scope.launch { events.emit(ToggleElementSelection(id)) } }
    fun onContextClick(id: String?) { scope.launch { events.emit(ContextClick(id)) } }
    fun onPreviewMarquee(rect: Frame) { scope.launch { events.emit(PreviewMarquee(rect)) } }
    fun onEndMarquee() { scope.launch { events.emit(EndMarquee) } }
    fun onUpdateSlide(slide: Slide) { scope.launch { events.emit(UpdateSlide(slide)) } }
    fun onPreviewSlide(slide: Slide) { scope.launch { events.emit(PreviewSlide(slide)) } }
    fun onCancelPreview() { scope.launch { events.emit(CancelPreview) } }
    fun onUpdateElements(elements: List<Element>) { scope.launch { events.emit(UpdateElements(elements)) } }
    fun onPreviewElements(elements: List<Element>) { scope.launch { events.emit(PreviewElements(elements)) } }
    fun onBeginTextEdit(id: String) { scope.launch { events.emit(BeginTextEdit(id)) } }
    fun onEndTextEdit() { scope.launch { events.emit(EndTextEdit) } }
    fun onInsertElement(element: Element) { scope.launch { events.emit(InsertElement(element)) } }
    fun onReorderElements(ids: List<String>, move: ZOrderMove) { scope.launch { events.emit(ReorderElements(ids, move)) } }
    fun onSetElementsLocked(ids: List<String>, locked: Boolean) { scope.launch { events.emit(SetElementsLocked(ids, locked)) } }
    fun onFlipElements(ids: List<String>, axis: FlipAxis) { scope.launch { events.emit(FlipElements(ids, axis)) } }
    fun onGroupElements(ids: List<String>) { scope.launch { events.emit(GroupElements(ids)) } }
    fun onUngroupElements(id: String) { scope.launch { events.emit(UngroupElements(id)) } }
    fun onAlignElements(edge: AlignEdge) { scope.launch { events.emit(AlignElements(edge)) } }
    fun onDistributeElements(axis: Axis) { scope.launch { events.emit(DistributeElements(axis)) } }
    fun onDeleteElements(ids: List<String>) { scope.launch { events.emit(DeleteElements(ids)) } }
    fun onClearAll() { scope.launch { events.emit(ClearAll) } }
    fun onDeleteSlide(id: String) { scope.launch { events.emit(DeleteSlide(id)) } }
    fun onAddSlide(afterId: String) { scope.launch { events.emit(AddSlide(afterId)) } }
    fun onMoveSlide(id: String, afterId: String?, nest: Boolean = false) { scope.launch { events.emit(MoveSlide(id, afterId, nest)) } }
    fun onPreviewSlideDrag(slideId: String, afterId: String?, nest: Boolean = false, translationY: Float = 0f) { scope.launch { events.emit(PreviewSlideDrag(slideId, afterId, nest, translationY)) } }
    fun onEndSlideDrag() { scope.launch { events.emit(EndSlideDrag) } }
    fun onSetSlideSkipped(id: String, skipped: Boolean) { scope.launch { events.emit(SetSlideSkipped(id, skipped)) } }
    fun onCopyElements(ids: List<String>) { scope.launch { events.emit(CopyElements(ids)) } }
    fun onCutElements(ids: List<String>) { scope.launch { events.emit(CutElements(ids)) } }
    fun onCopySlide(id: String) { scope.launch { events.emit(CopySlide(id)) } }
    fun onCutSlide(id: String) { scope.launch { events.emit(CutSlide(id)) } }
    fun onPaste() { scope.launch { events.emit(Paste) } }
    fun onDuplicateElements(ids: List<String>) { scope.launch { events.emit(DuplicateElements(ids)) } }
    fun onDuplicateSlide(id: String) { scope.launch { events.emit(DuplicateSlide(id)) } }
    fun onFocusPane(pane: EditorPane) { scope.launch { events.emit(FocusPane(pane)) } }
    fun onCut() { scope.launch { events.emit(Cut) } }
    fun onCopy() { scope.launch { events.emit(Copy) } }
    fun onDuplicate() { scope.launch { events.emit(Duplicate) } }
    fun onDelete() { scope.launch { events.emit(Delete) } }
    fun onCopyStyle(id: String) { scope.launch { events.emit(CopyStyle(id)) } }
    fun onPasteStyle(ids: List<String>) { scope.launch { events.emit(PasteStyle(ids)) } }
    fun onToggleCollapsed(slideId: String) { scope.launch { events.emit(ToggleCollapsed(slideId)) } }
    fun onUndo() { scope.launch { events.emit(Undo) } }
    fun onRedo() { scope.launch { events.emit(Redo) } }
    fun onToggleSidebar() { scope.launch { events.emit(ToggleSidebar) } }
    fun onToggleNotes() { scope.launch { events.emit(ToggleNotes) } }
    fun onToggleRulers() { scope.launch { events.emit(ToggleRulers) } }
    fun onToggleGuides() { scope.launch { events.emit(ToggleGuides) } }
    fun onSetSnap(kind: SnapKind, enabled: Boolean) { scope.launch { events.emit(SetSnap(kind, enabled)) } }
    fun onPreviewGuide(id: String?, axis: GuideAxis, position: Float) { scope.launch { events.emit(PreviewGuide(id, axis, position)) } }
    fun onCommitGuide(id: String?, axis: GuideAxis, position: Float) { scope.launch { events.emit(CommitGuide(id, axis, position)) } }
    fun onRemoveGuide(id: String) { scope.launch { events.emit(RemoveGuide(id)) } }
    fun onEndGuideDrag() { scope.launch { events.emit(EndGuideDrag) } }
    fun onSelectInspectorTab(tab: InspectorTab) { scope.launch { events.emit(SelectInspectorTab(tab)) } }
    fun onCloseInspector() { scope.launch { events.emit(CloseInspector) } }

    fun close() {
        scope.cancel()
    }
}

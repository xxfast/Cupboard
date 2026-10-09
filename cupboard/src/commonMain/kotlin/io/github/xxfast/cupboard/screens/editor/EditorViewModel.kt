package io.github.xxfast.cupboard.screens.editor

import app.cash.molecule.RecompositionMode.Immediate
import app.cash.molecule.moleculeFlow
import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.DiagramStep
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GuideAxis
import io.github.xxfast.cupboard.document.InMemoryAssetStore
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.PlaceholderRole
import io.github.xxfast.cupboard.document.PlaybackSettings
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.SlideTransition
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.document.encodeToString
import io.github.xxfast.cupboard.editor.AlignEdge
import io.github.xxfast.cupboard.editor.Axis
import io.github.xxfast.cupboard.editor.SnapKind
import io.github.xxfast.cupboard.screens.editor.EditorEvent.AddBuild
import io.github.xxfast.cupboard.screens.editor.EditorEvent.AddCodeStep
import io.github.xxfast.cupboard.screens.editor.EditorEvent.AddDiagramStep
import io.github.xxfast.cupboard.screens.editor.EditorEvent.AddElementSteps
import io.github.xxfast.cupboard.screens.editor.EditorEvent.MoveCodeStep
import io.github.xxfast.cupboard.screens.editor.EditorEvent.MoveDiagramStep
import io.github.xxfast.cupboard.screens.editor.EditorEvent.RemoveCodeStep
import io.github.xxfast.cupboard.screens.editor.EditorEvent.RemoveDiagramStep
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectCodeStep
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectDiagramStep
import io.github.xxfast.cupboard.screens.editor.EditorEvent.UpdateCodeStep
import io.github.xxfast.cupboard.screens.editor.EditorEvent.UpdateDiagramStep
import io.github.xxfast.cupboard.screens.editor.EditorEvent.AddLayout
import io.github.xxfast.cupboard.screens.editor.EditorEvent.AddPlaceholder
import io.github.xxfast.cupboard.screens.editor.EditorEvent.AddSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.AlignElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ApplyLayout
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ApplyObjectStyle
import io.github.xxfast.cupboard.screens.editor.EditorEvent.AddCodeVersion
import io.github.xxfast.cupboard.screens.editor.EditorEvent.BeginTextEdit
import io.github.xxfast.cupboard.screens.editor.EditorEvent.MoveCodeVersion
import io.github.xxfast.cupboard.screens.editor.EditorEvent.RemoveCodeVersion
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectCodeVersion
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CancelPreview
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CanvasResized
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PanCanvas
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetZoom
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ZoomBy
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ZoomIn
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ZoomOut
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ZoomToFitContent
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ZoomToSelection
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ChangeTheme
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
import io.github.xxfast.cupboard.screens.editor.EditorEvent.DeleteObjectStyle
import io.github.xxfast.cupboard.screens.editor.EditorEvent.DeleteSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.DeleteUserTheme
import io.github.xxfast.cupboard.screens.editor.EditorEvent.DistributeElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Duplicate
import io.github.xxfast.cupboard.screens.editor.EditorEvent.DuplicateElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.DuplicateSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EditSlideLayouts
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EndGuideDrag
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EndMarquee
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EndObjectDrag
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EndSlideDrag
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EndTextEdit
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ExitSlideLayouts
import io.github.xxfast.cupboard.screens.editor.EditorEvent.FlipElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.FocusPane
import io.github.xxfast.cupboard.screens.editor.EditorEvent.GroupElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.InsertElement
import io.github.xxfast.cupboard.screens.editor.EditorEvent.MoveBuild
import io.github.xxfast.cupboard.screens.editor.EditorEvent.MoveElement
import io.github.xxfast.cupboard.screens.editor.EditorEvent.MoveSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Paste
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PasteStyle
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewGuide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewMarquee
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewObjectDrag
import io.github.xxfast.cupboard.screens.editor.EditorEvent.PreviewSlideDrag
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ReapplyLayout
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Redo
import io.github.xxfast.cupboard.screens.editor.EditorEvent.RemoveBuild
import io.github.xxfast.cupboard.screens.editor.EditorEvent.RemoveGuide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.RenameDocument
import io.github.xxfast.cupboard.screens.editor.EditorEvent.RenameObjectStyle
import io.github.xxfast.cupboard.screens.editor.EditorEvent.RenameSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ReorderElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SaveAsTheme
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SaveObjectStyle
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectAnimateSegment
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectElement
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectFormatSegment
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectInspectorTab
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.IndentSlides
import io.github.xxfast.cupboard.screens.editor.EditorEvent.StepSlideSelection
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectAllSlides
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleSlideSelection
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ExtendSlideSelection
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SelectSlideAt
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetDocumentBackground
import io.github.xxfast.cupboard.screens.editor.EditorEvent.RenameElement
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetElementsHidden
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetElementsLocked
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetObjectListFilter
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetSlideSize
import io.github.xxfast.cupboard.screens.editor.EditorEvent.AddDeckFont
import io.github.xxfast.cupboard.screens.editor.EditorEvent.RemoveDeckFont
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetElementLinks
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetTextLink
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EditSelectionChanged
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetPlayback
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetSlideSkipped
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetSlideTransition
import io.github.xxfast.cupboard.screens.editor.EditorEvent.SetSnap
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleCollapsed
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleElementSelection
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleGuides
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleInspector
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleInspectorSection
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleNotes
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleRulers
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleObjectExpanded
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleObjectList
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ToggleSidebar
import io.github.xxfast.cupboard.screens.editor.EditorEvent.Undo
import io.github.xxfast.cupboard.screens.editor.EditorEvent.UngroupElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.UpdateBuild
import io.github.xxfast.cupboard.screens.editor.EditorEvent.UpdateElements
import io.github.xxfast.cupboard.screens.editor.EditorEvent.UpdateSlide
import io.github.xxfast.cupboard.screens.editor.EditorEvent.UseAsDefaultShapeStyle
import io.github.xxfast.cupboard.screens.editor.EditorEvent.UseAsDefaultTextStyle
import io.github.xxfast.kstore.KStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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
    /** The user's saved themes; null for a host with nowhere to keep them. */
    private val themeStore: KStore<List<Theme>>? = null,
    /**
     * The deck's bytes: `assets/` inside its bundle, or memory for a host with
     * no bundle. Public because it is the shells' way in as well as the
     * presenter's: a drag-and-dropped image is bytes the shell has and the
     * document model only ever holds the id they were written under.
     *
     * A dependency, not state. Nothing about it changes as the deck is edited,
     * so it has no business in [EditorState].
     */
    val assets: AssetStore = InMemoryAssetStore(),
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    /**
     * The bundle this editor is open on, as a path string; empty for a host with
     * no bundle behind it (the preview shells, most tests).
     *
     * A plain [String] rather than a `Path` because this is the one thing every
     * shell asks the view model about that isn't state: which document is this
     * window, so a second Open of the same deck raises the window that already
     * has it. The ObjC and .NET hosts see a string; nothing about kotlinx-io
     * crosses to them.
     *
     * Fixed for the life of the view model. Save As opens a new one on the new
     * bundle rather than moving this one, so a window's identity never changes
     * under it, see `Cupboard.saveAs`.
     */
    val location: String = "",
) {
    /** Opens [initialDocument] on its first slide with content. */
    constructor(
        initialDocument: Document,
        documentStore: KStore<Document>,
        themeStore: KStore<List<Theme>>? = null,
        assets: AssetStore = InMemoryAssetStore(),
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        location: String = "",
    ) : this(
        EditorState.opening(initialDocument),
        documentStore,
        themeStore,
        assets,
        dispatcher,
        location,
    )

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

    // A channel, not a shared flow: a shared flow drops what is emitted before its
    // first subscriber, and the presenter only subscribes once something collects
    // `states`. A canvas reports its size as it lays out, which can be before that,
    // and losing it would leave Fit with no number until the window is resized.
    // Unbounded for the same reason the old buffer was big: an editor emits bursts
    // (a slide select that clears a selection, keystrokes) and none may be lost.
    private val events: Channel<EditorEvent> = Channel(Channel.UNLIMITED)

    val states: StateFlow<EditorState> =
        moleculeFlow(Immediate) {
            EditorPresenter(initialState, events.receiveAsFlow(), documentStore, themeStore, assets)
        }.stateIn(scope, SharingStarted.Lazily, initialState)

    fun onSelectSlide(id: String) { scope.launch { events.send(SelectSlide(id)) } }
    fun onSelectSlideAt(index: Int) { scope.launch { events.send(SelectSlideAt(index)) } }
    fun onExtendSlideSelection(id: String) { scope.launch { events.send(ExtendSlideSelection(id)) } }
    fun onToggleSlideSelection(id: String) { scope.launch { events.send(ToggleSlideSelection(id)) } }
    fun onSelectAllSlides() { scope.launch { events.send(SelectAllSlides) } }
    fun onStepSlideSelection(delta: Int, extend: Boolean) { scope.launch { events.send(StepSlideSelection(delta, extend)) } }
    fun onIndentSlides(delta: Int) { scope.launch { events.send(IndentSlides(delta)) } }
    fun onSelectElement(id: String?) { scope.launch { events.send(SelectElement(id)) } }
    fun onSelectElements(ids: List<String>) { scope.launch { events.send(SelectElements(ids)) } }
    fun onToggleElementSelection(id: String) { scope.launch { events.send(ToggleElementSelection(id)) } }
    fun onContextClick(id: String?) { scope.launch { events.send(ContextClick(id)) } }
    fun onPreviewMarquee(rect: Frame) { scope.launch { events.send(PreviewMarquee(rect)) } }
    fun onEndMarquee() { scope.launch { events.send(EndMarquee) } }
    fun onUpdateSlide(slide: Slide) { scope.launch { events.send(UpdateSlide(slide)) } }
    fun onPreviewSlide(slide: Slide) { scope.launch { events.send(PreviewSlide(slide)) } }
    fun onCancelPreview() { scope.launch { events.send(CancelPreview) } }
    fun onUpdateElements(elements: List<Element>) { scope.launch { events.send(UpdateElements(elements)) } }
    fun onPreviewElements(elements: List<Element>) { scope.launch { events.send(PreviewElements(elements)) } }
    fun onBeginTextEdit(id: String) { scope.launch { events.send(BeginTextEdit(id)) } }
    fun onEndTextEdit() { scope.launch { events.send(EndTextEdit) } }
    fun onEditSelectionChanged(elementId: String, start: Int, end: Int) { scope.launch { events.send(EditSelectionChanged(elementId, start, end)) } }
    fun onSelectCodeVersion(index: Int) { scope.launch { events.send(SelectCodeVersion(index)) } }
    fun onAddCodeVersion(elementId: String) { scope.launch { events.send(AddCodeVersion(elementId)) } }
    fun onRemoveCodeVersion(elementId: String, index: Int) { scope.launch { events.send(RemoveCodeVersion(elementId, index)) } }
    fun onMoveCodeVersion(elementId: String, from: Int, to: Int) { scope.launch { events.send(MoveCodeVersion(elementId, from, to)) } }
    fun onSelectCodeStep(index: Int?) { scope.launch { events.send(SelectCodeStep(index)) } }
    fun onAddCodeStep(elementId: String) { scope.launch { events.send(AddCodeStep(elementId)) } }
    fun onRemoveCodeStep(elementId: String, index: Int) { scope.launch { events.send(RemoveCodeStep(elementId, index)) } }
    fun onMoveCodeStep(elementId: String, from: Int, to: Int) { scope.launch { events.send(MoveCodeStep(elementId, from, to)) } }
    fun onUpdateCodeStep(elementId: String, index: Int, step: CodeStep) { scope.launch { events.send(UpdateCodeStep(elementId, index, step)) } }
    fun onSelectDiagramStep(index: Int?) { scope.launch { events.send(SelectDiagramStep(index)) } }
    fun onAddDiagramStep(elementId: String) { scope.launch { events.send(AddDiagramStep(elementId)) } }
    fun onRemoveDiagramStep(elementId: String, index: Int) { scope.launch { events.send(RemoveDiagramStep(elementId, index)) } }
    fun onMoveDiagramStep(elementId: String, from: Int, to: Int) { scope.launch { events.send(MoveDiagramStep(elementId, from, to)) } }
    fun onUpdateDiagramStep(elementId: String, index: Int, step: DiagramStep) { scope.launch { events.send(UpdateDiagramStep(elementId, index, step)) } }
    fun onInsertElement(element: Element) { scope.launch { events.send(InsertElement(element)) } }
    fun onReorderElements(ids: List<String>, move: ZOrderMove) { scope.launch { events.send(ReorderElements(ids, move)) } }
    fun onSetElementsLocked(ids: List<String>, locked: Boolean) { scope.launch { events.send(SetElementsLocked(ids, locked)) } }
    fun onSetElementsHidden(ids: List<String>, hidden: Boolean) { scope.launch { events.send(SetElementsHidden(ids, hidden)) } }
    fun onRenameElement(id: String, name: String?) { scope.launch { events.send(RenameElement(id, name)) } }
    fun onFlipElements(ids: List<String>, axis: FlipAxis) { scope.launch { events.send(FlipElements(ids, axis)) } }
    fun onGroupElements(ids: List<String>) { scope.launch { events.send(GroupElements(ids)) } }
    fun onUngroupElements(id: String) { scope.launch { events.send(UngroupElements(id)) } }
    fun onAlignElements(edge: AlignEdge) { scope.launch { events.send(AlignElements(edge)) } }
    fun onDistributeElements(axis: Axis) { scope.launch { events.send(DistributeElements(axis)) } }
    fun onDeleteElements(ids: List<String>) { scope.launch { events.send(DeleteElements(ids)) } }
    fun onClearAll() { scope.launch { events.send(ClearAll) } }
    fun onDeleteSlide(id: String) { scope.launch { events.send(DeleteSlide(id)) } }
    fun onAddSlide(afterId: String, layoutId: String? = null) { scope.launch { events.send(AddSlide(afterId, layoutId)) } }
    fun onMoveSlideTo(id: String, afterId: String?, depth: Int) { scope.launch { events.send(MoveSlide(id, afterId, depth = depth)) } }
    fun onMoveSlide(id: String, afterId: String?, nest: Boolean = false) { scope.launch { events.send(MoveSlide(id, afterId, nest)) } }
    fun onPreviewSlideDrag(slideId: String, afterId: String?, nest: Boolean = false, translationY: Float = 0f) { scope.launch { events.send(PreviewSlideDrag(slideId, afterId, nest, translationY)) } }
    fun onEndSlideDrag() { scope.launch { events.send(EndSlideDrag) } }
    fun onPreviewObjectDrag(id: String, beforeId: String?, translationY: Float = 0f) { scope.launch { events.send(PreviewObjectDrag(id, beforeId, translationY)) } }
    fun onEndObjectDrag() { scope.launch { events.send(EndObjectDrag) } }
    fun onMoveElement(id: String, beforeId: String?) { scope.launch { events.send(MoveElement(id, beforeId)) } }
    fun onSetSlideSkipped(id: String, skipped: Boolean) { scope.launch { events.send(SetSlideSkipped(id, skipped)) } }
    fun onSetSlideTransition(slideId: String, transition: SlideTransition?) { scope.launch { events.send(SetSlideTransition(slideId, transition)) } }
    fun onSetPlayback(settings: PlaybackSettings) { scope.launch { events.send(SetPlayback(settings)) } }
    fun onSetElementLinks(ids: List<String>, target: LinkTarget?) { scope.launch { events.send(SetElementLinks(ids, target)) } }
    fun onSetTextLink(elementId: String, start: Int, end: Int, target: LinkTarget?) { scope.launch { events.send(SetTextLink(elementId, start, end, target)) } }
    fun onAddBuild(build: Build) { scope.launch { events.send(AddBuild(build)) } }
    fun onAddElementSteps(elementId: String) { scope.launch { events.send(AddElementSteps(elementId)) } }
    fun onUpdateBuild(index: Int, build: Build) { scope.launch { events.send(UpdateBuild(index, build)) } }
    fun onRemoveBuild(index: Int) { scope.launch { events.send(RemoveBuild(index)) } }
    fun onMoveBuild(from: Int, to: Int) { scope.launch { events.send(MoveBuild(from, to)) } }
    fun onCopyElements(ids: List<String>) { scope.launch { events.send(CopyElements(ids)) } }
    fun onCutElements(ids: List<String>) { scope.launch { events.send(CutElements(ids)) } }
    fun onCopySlide(id: String) { scope.launch { events.send(CopySlide(id)) } }
    fun onCutSlide(id: String) { scope.launch { events.send(CutSlide(id)) } }
    fun onPaste() { scope.launch { events.send(Paste) } }
    fun onDuplicateElements(ids: List<String>) { scope.launch { events.send(DuplicateElements(ids)) } }
    fun onDuplicateSlide(id: String) { scope.launch { events.send(DuplicateSlide(id)) } }
    fun onFocusPane(pane: EditorPane) { scope.launch { events.send(FocusPane(pane)) } }
    fun onCut() { scope.launch { events.send(Cut) } }
    fun onCopy() { scope.launch { events.send(Copy) } }
    fun onDuplicate() { scope.launch { events.send(Duplicate) } }
    fun onDelete() { scope.launch { events.send(Delete) } }
    fun onCopyStyle(id: String) { scope.launch { events.send(CopyStyle(id)) } }
    fun onPasteStyle(ids: List<String>) { scope.launch { events.send(PasteStyle(ids)) } }
    fun onToggleCollapsed(slideId: String) { scope.launch { events.send(ToggleCollapsed(slideId)) } }
    fun onEditSlideLayouts() { scope.launch { events.send(EditSlideLayouts) } }
    fun onExitSlideLayouts() { scope.launch { events.send(ExitSlideLayouts) } }
    fun onApplyLayout(slideId: String, layoutId: String?) { scope.launch { events.send(ApplyLayout(slideId, layoutId)) } }
    fun onReapplyLayout(slideId: String) { scope.launch { events.send(ReapplyLayout(slideId)) } }
    fun onAddLayout() { scope.launch { events.send(AddLayout) } }
    fun onRenameSlide(id: String, title: String) { scope.launch { events.send(RenameSlide(id, title)) } }
    fun onAddPlaceholder(role: PlaceholderRole) { scope.launch { events.send(AddPlaceholder(role)) } }
    fun onChangeTheme(name: String) { scope.launch { events.send(ChangeTheme(name)) } }
    fun onSaveAsTheme(name: String) { scope.launch { events.send(SaveAsTheme(name)) } }
    fun onDeleteUserTheme(name: String) { scope.launch { events.send(DeleteUserTheme(name)) } }
    fun onApplyObjectStyle(ids: List<String>, styleId: String) { scope.launch { events.send(ApplyObjectStyle(ids, styleId)) } }
    fun onSaveObjectStyle(shapeId: String, name: String) { scope.launch { events.send(SaveObjectStyle(shapeId, name)) } }
    fun onDeleteObjectStyle(styleId: String) { scope.launch { events.send(DeleteObjectStyle(styleId)) } }
    fun onRenameObjectStyle(styleId: String, name: String) { scope.launch { events.send(RenameObjectStyle(styleId, name)) } }
    fun onUseAsDefaultTextStyle(id: String) { scope.launch { events.send(UseAsDefaultTextStyle(id)) } }
    fun onUseAsDefaultShapeStyle(id: String) { scope.launch { events.send(UseAsDefaultShapeStyle(id)) } }
    fun onSetDocumentBackground(background: SlideBackground?) { scope.launch { events.send(SetDocumentBackground(background)) } }
    fun onRenameDocument(name: String) { scope.launch { events.send(RenameDocument(name)) } }
    fun onSetSlideSize(width: Float, height: Float, scaleContent: Boolean) { scope.launch { events.send(SetSlideSize(width, height, scaleContent)) } }
    fun onAddDeckFont(assetId: String, family: String, weight: Int, italic: Boolean) { scope.launch { events.send(AddDeckFont(assetId, family, weight, italic)) } }
    fun onRemoveDeckFont(assetId: String) { scope.launch { events.send(RemoveDeckFont(assetId)) } }
    fun onUndo() { scope.launch { events.send(Undo) } }
    fun onRedo() { scope.launch { events.send(Redo) } }
    fun onToggleSidebar() { scope.launch { events.send(ToggleSidebar) } }
    fun onToggleObjectList() { scope.launch { events.send(ToggleObjectList) } }
    fun onSetObjectListFilter(text: String) { scope.launch { events.send(SetObjectListFilter(text)) } }
    fun onToggleObjectExpanded(id: String) { scope.launch { events.send(ToggleObjectExpanded(id)) } }
    fun onToggleNotes() { scope.launch { events.send(ToggleNotes) } }
    fun onToggleRulers() { scope.launch { events.send(ToggleRulers) } }
    fun onToggleGuides() { scope.launch { events.send(ToggleGuides) } }
    fun onSetSnap(kind: SnapKind, enabled: Boolean) { scope.launch { events.send(SetSnap(kind, enabled)) } }
    fun onSetZoom(percent: Int) { scope.launch { events.send(SetZoom(percent)) } }
    fun onZoomIn() { scope.launch { events.send(ZoomIn) } }
    fun onZoomOut() { scope.launch { events.send(ZoomOut) } }
    fun onZoomToSelection() { scope.launch { events.send(ZoomToSelection) } }
    fun onZoomToFitContent() { scope.launch { events.send(ZoomToFitContent) } }
    fun onZoomBy(factor: Float, anchorX: Float, anchorY: Float) { scope.launch { events.send(ZoomBy(factor, anchorX, anchorY)) } }
    fun onPanCanvas(dx: Float, dy: Float) { scope.launch { events.send(PanCanvas(dx, dy)) } }
    fun onCanvasResized(width: Float, height: Float) { scope.launch { events.send(CanvasResized(width, height)) } }
    fun onPreviewGuide(id: String?, axis: GuideAxis, position: Float) { scope.launch { events.send(PreviewGuide(id, axis, position)) } }
    fun onCommitGuide(id: String?, axis: GuideAxis, position: Float) { scope.launch { events.send(CommitGuide(id, axis, position)) } }
    fun onRemoveGuide(id: String) { scope.launch { events.send(RemoveGuide(id)) } }
    fun onEndGuideDrag() { scope.launch { events.send(EndGuideDrag) } }
    fun onSelectInspectorTab(tab: InspectorTab) { scope.launch { events.send(SelectInspectorTab(tab)) } }
    fun onCloseInspector() { scope.launch { events.send(CloseInspector) } }
    fun onToggleInspector() { scope.launch { events.send(ToggleInspector) } }
    fun onSelectFormatSegment(segment: FormatSegment) { scope.launch { events.send(SelectFormatSegment(segment)) } }
    fun onSelectAnimateSegment(segment: AnimateSegment) { scope.launch { events.send(SelectAnimateSegment(segment)) } }
    fun onToggleInspectorSection(section: InspectorSection) { scope.launch { events.send(ToggleInspectorSection(section)) } }

    fun close() {
        scope.cancel()
    }
}

/**
 * The deck as it stands right now, as `document.json` would hold it.
 *
 * For the shells that have to put a deck somewhere this view model's own store
 * does not reach: a save-as to a bundle the user just picked, an export, a
 * clipboard. Reads the current state, so it is whatever the last event left.
 */
fun EditorViewModel.documentJson(): String = states.value.document.encodeToString()

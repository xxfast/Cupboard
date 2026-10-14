package io.github.xxfast.cupboard.screens.editor

import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.molecule.RecompositionMode.Immediate
import app.cash.molecule.SnapshotNotifier
import app.cash.molecule.moleculeFlow
import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.BundleChange
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
import io.github.xxfast.cupboard.editor.DragSample
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
import io.github.xxfast.cupboard.screens.editor.EditorEvent.BreakApartElement
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
import io.github.xxfast.cupboard.screens.editor.EditorEvent.CommitNudge
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
import io.github.xxfast.cupboard.screens.editor.EditorEvent.BeginNotesEdit
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EditNotes
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EndMarquee
import io.github.xxfast.cupboard.screens.editor.EditorEvent.EndNotesEdit
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
import io.github.xxfast.cupboard.screens.editor.EditorEvent.NudgeSelection
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
import io.github.xxfast.cupboard.screens.editor.EditorEvent.ResizeNotes
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

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
    /**
     * The bundle's changes from outside this editor, mapped onto
     * [EditorEvent.Reload] and [EditorEvent.RefreshAssets] and merged into the
     * event flow, so a reload is an ordinary event and the watcher runs exactly
     * as long as the presenter does. Empty for a host with no bundle behind it.
     *
     * Not an on-method: what is on disk is the core's to notice, not a shell's
     * to report, so the ObjC and .NET surface stays as it was.
     */
    private val changes: Flow<BundleChange> = emptyFlow(),
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
        changes: Flow<BundleChange> = emptyFlow(),
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        location: String = "",
    ) : this(
        EditorState.opening(initialDocument),
        documentStore,
        themeStore,
        assets,
        changes,
        dispatcher,
        location,
    )

    // Private: this class is exported to ObjC (and later to .NET), and a
    // CoroutineScope on the public surface is a Kotlin type those hosts have no
    // business seeing. They get `states` and the on-methods, nothing else.
    //
    // Shells pass their main dispatcher, and the immediate flavour where there is
    // one: every hop between an on-method and `states` is then a plain call when
    // the caller is already on the main thread, so a wheel tick is folded,
    // recomposed and emitted before the on-method returns. On a dispatched main
    // dispatcher each hop is a queued block instead, and on macOS the renderer's
    // own blocks sit between them, one per frame. Unconfined stays the default
    // for tests, which pump the loop themselves.
    private val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())

    // A channel, not a shared flow: a shared flow drops what is emitted before its
    // first subscriber, and the presenter only subscribes once something collects
    // `states`. A canvas reports its size as it lays out, which can be before that,
    // and losing it would leave Fit with no number until the window is resized.
    // Unbounded for the same reason the old buffer was big: an editor emits bursts
    // (a slide select that clears a selection, keystrokes) and none may be lost.
    private val events: Channel<EditorEvent> = Channel(Channel.UNLIMITED)

    // The apply notifications are ours to send, see [send] and [applyObserver]:
    // molecule's own notifier launches them on this scope, which on an immediate
    // dispatcher runs inside the global write observer, before the write it is
    // for is recorded. That notification then carries nothing, and the state
    // sits unflushed until some unrelated snapshot write comes along.
    val states: StateFlow<EditorState> =
        moleculeFlow(Immediate, snapshotNotifier = SnapshotNotifier.External) {
            EditorPresenter(
                initialState = initialState,
                events = merge(events.receiveAsFlow(), changes.map { change -> change.toEvent() }),
                documentStore = documentStore,
                themeStore = themeStore,
                assets = assets,
            )
        }.stateIn(scope, SharingStarted.Lazily, initialState)

    // A write the loop makes on its own, a store load landing in a LaunchedEffect
    // say, is flushed from here. The yield is the point: it always dispatches,
    // immediate dispatcher or not, so the notification is sent after the write
    // is recorded rather than from inside the observer that saw it start.
    private var applyScheduled: Boolean = false
    private val applyObserver: ObserverHandle = Snapshot.registerGlobalWriteObserver {
        if (!applyScheduled) {
            applyScheduled = true
            scope.launch {
                yield()
                applyScheduled = false
                Snapshot.sendApplyNotifications()
            }
        }
    }

    /**
     * Queues [event] for the presenter and flushes the snapshot it writes. On an
     * immediate dispatcher the fold has already run by the second line, so the
     * notification recomposes and emits the new state before this returns; on a
     * dispatched one the fold hasn't happened yet, the call finds nothing to
     * send, and [applyObserver] flushes it when it does.
     */
    private fun send(event: EditorEvent) {
        scope.launch { events.send(event) }
        Snapshot.sendApplyNotifications()
    }

    fun onSelectSlide(id: String) { send(SelectSlide(id)) }
    fun onSelectSlideAt(index: Int) { send(SelectSlideAt(index)) }
    fun onExtendSlideSelection(id: String) { send(ExtendSlideSelection(id)) }
    fun onToggleSlideSelection(id: String) { send(ToggleSlideSelection(id)) }
    fun onSelectAllSlides() { send(SelectAllSlides) }
    fun onStepSlideSelection(delta: Int, extend: Boolean) { send(StepSlideSelection(delta, extend)) }
    fun onIndentSlides(delta: Int) { send(IndentSlides(delta)) }
    fun onSelectElement(id: String?) { send(SelectElement(id)) }
    fun onSelectElements(ids: List<String>) { send(SelectElements(ids)) }
    fun onToggleElementSelection(id: String) { send(ToggleElementSelection(id)) }
    fun onContextClick(id: String?) { send(ContextClick(id)) }
    fun onPreviewMarquee(rect: Frame) { send(PreviewMarquee(rect)) }
    fun onEndMarquee() { send(EndMarquee) }
    fun onUpdateSlide(slide: Slide) { send(UpdateSlide(slide)) }
    fun onPreviewSlide(slide: Slide) { send(PreviewSlide(slide)) }
    fun onCancelPreview() { send(CancelPreview) }
    fun onUpdateElements(elements: List<Element>) { send(UpdateElements(elements)) }
    fun onPreviewElements(elements: List<Element>) { send(PreviewElements(elements)) }
    /** A canvas move or resize sample: [onPreviewElements] plus what the alignment overlay is drawn from. */
    fun onPreviewDrag(elements: List<Element>, drag: DragSample) { send(PreviewElements(elements, drag)) }
    /** An arrow key down or repeating on the canvas, in points: 1, or 10 with shift. */
    fun onNudgeSelection(dx: Float, dy: Float) { send(NudgeSelection(dx, dy)) }
    /** The arrow key back up: the run settles as one undo entry. */
    fun onCommitNudge() { send(CommitNudge) }
    fun onBeginTextEdit(id: String) { send(BeginTextEdit(id)) }
    fun onEndTextEdit() { send(EndTextEdit) }
    fun onEditSelectionChanged(elementId: String, start: Int, end: Int) { send(EditSelectionChanged(elementId, start, end)) }
    fun onSelectCodeVersion(index: Int) { send(SelectCodeVersion(index)) }
    fun onAddCodeVersion(elementId: String) { send(AddCodeVersion(elementId)) }
    fun onRemoveCodeVersion(elementId: String, index: Int) { send(RemoveCodeVersion(elementId, index)) }
    fun onMoveCodeVersion(elementId: String, from: Int, to: Int) { send(MoveCodeVersion(elementId, from, to)) }
    fun onSelectCodeStep(index: Int?) { send(SelectCodeStep(index)) }
    fun onAddCodeStep(elementId: String) { send(AddCodeStep(elementId)) }
    fun onRemoveCodeStep(elementId: String, index: Int) { send(RemoveCodeStep(elementId, index)) }
    fun onMoveCodeStep(elementId: String, from: Int, to: Int) { send(MoveCodeStep(elementId, from, to)) }
    fun onUpdateCodeStep(elementId: String, index: Int, step: CodeStep) { send(UpdateCodeStep(elementId, index, step)) }
    fun onSelectDiagramStep(index: Int?) { send(SelectDiagramStep(index)) }
    fun onAddDiagramStep(elementId: String) { send(AddDiagramStep(elementId)) }
    fun onRemoveDiagramStep(elementId: String, index: Int) { send(RemoveDiagramStep(elementId, index)) }
    fun onMoveDiagramStep(elementId: String, from: Int, to: Int) { send(MoveDiagramStep(elementId, from, to)) }
    fun onUpdateDiagramStep(elementId: String, index: Int, step: DiagramStep) { send(UpdateDiagramStep(elementId, index, step)) }
    fun onInsertElement(element: Element) { send(InsertElement(element)) }
    fun onReorderElements(ids: List<String>, move: ZOrderMove) { send(ReorderElements(ids, move)) }
    fun onSetElementsLocked(ids: List<String>, locked: Boolean) { send(SetElementsLocked(ids, locked)) }
    fun onSetElementsHidden(ids: List<String>, hidden: Boolean) { send(SetElementsHidden(ids, hidden)) }
    fun onRenameElement(id: String, name: String?) { send(RenameElement(id, name)) }
    fun onFlipElements(ids: List<String>, axis: FlipAxis) { send(FlipElements(ids, axis)) }
    fun onGroupElements(ids: List<String>) { send(GroupElements(ids)) }
    fun onUngroupElements(id: String) { send(UngroupElements(id)) }
    fun onBreakApartElement(id: String) { send(BreakApartElement(id)) }
    fun onAlignElements(edge: AlignEdge) { send(AlignElements(edge)) }
    fun onDistributeElements(axis: Axis) { send(DistributeElements(axis)) }
    fun onDeleteElements(ids: List<String>) { send(DeleteElements(ids)) }
    fun onClearAll() { send(ClearAll) }
    fun onDeleteSlide(id: String) { send(DeleteSlide(id)) }
    fun onAddSlide(afterId: String, layoutId: String? = null) { send(AddSlide(afterId, layoutId)) }
    fun onMoveSlideTo(id: String, afterId: String?, depth: Int) { send(MoveSlide(id, afterId, depth = depth)) }
    fun onMoveSlide(id: String, afterId: String?, nest: Boolean = false) { send(MoveSlide(id, afterId, nest)) }
    fun onPreviewSlideDrag(slideId: String, afterId: String?, nest: Boolean = false, translationY: Float = 0f) { send(PreviewSlideDrag(slideId, afterId, nest, translationY)) }
    fun onEndSlideDrag() { send(EndSlideDrag) }
    fun onPreviewObjectDrag(id: String, beforeId: String?, translationY: Float = 0f) { send(PreviewObjectDrag(id, beforeId, translationY)) }
    fun onEndObjectDrag() { send(EndObjectDrag) }
    fun onMoveElement(id: String, beforeId: String?) { send(MoveElement(id, beforeId)) }
    fun onSetSlideSkipped(id: String, skipped: Boolean) { send(SetSlideSkipped(id, skipped)) }
    fun onSetSlideTransition(slideId: String, transition: SlideTransition?) { send(SetSlideTransition(slideId, transition)) }
    fun onSetPlayback(settings: PlaybackSettings) { send(SetPlayback(settings)) }
    fun onSetElementLinks(ids: List<String>, target: LinkTarget?) { send(SetElementLinks(ids, target)) }
    fun onSetTextLink(elementId: String, start: Int, end: Int, target: LinkTarget?) { send(SetTextLink(elementId, start, end, target)) }
    fun onAddBuild(build: Build) { send(AddBuild(build)) }
    fun onAddElementSteps(elementId: String) { send(AddElementSteps(elementId)) }
    fun onUpdateBuild(index: Int, build: Build) { send(UpdateBuild(index, build)) }
    fun onRemoveBuild(index: Int) { send(RemoveBuild(index)) }
    fun onMoveBuild(from: Int, to: Int) { send(MoveBuild(from, to)) }
    fun onCopyElements(ids: List<String>) { send(CopyElements(ids)) }
    fun onCutElements(ids: List<String>) { send(CutElements(ids)) }
    fun onCopySlide(id: String) { send(CopySlide(id)) }
    fun onCutSlide(id: String) { send(CutSlide(id)) }
    fun onPaste() { send(Paste) }
    fun onDuplicateElements(ids: List<String>) { send(DuplicateElements(ids)) }
    fun onDuplicateSlide(id: String) { send(DuplicateSlide(id)) }
    fun onFocusPane(pane: EditorPane) { send(FocusPane(pane)) }
    fun onCut() { send(Cut) }
    fun onCopy() { send(Copy) }
    fun onDuplicate() { send(Duplicate) }
    fun onDelete() { send(Delete) }
    fun onCopyStyle(id: String) { send(CopyStyle(id)) }
    fun onPasteStyle(ids: List<String>) { send(PasteStyle(ids)) }
    fun onToggleCollapsed(slideId: String) { send(ToggleCollapsed(slideId)) }
    fun onEditSlideLayouts() { send(EditSlideLayouts) }
    fun onExitSlideLayouts() { send(ExitSlideLayouts) }
    fun onApplyLayout(slideId: String, layoutId: String?) { send(ApplyLayout(slideId, layoutId)) }
    fun onReapplyLayout(slideId: String) { send(ReapplyLayout(slideId)) }
    fun onAddLayout() { send(AddLayout) }
    fun onRenameSlide(id: String, title: String) { send(RenameSlide(id, title)) }
    fun onAddPlaceholder(role: PlaceholderRole) { send(AddPlaceholder(role)) }
    fun onChangeTheme(name: String) { send(ChangeTheme(name)) }
    fun onSaveAsTheme(name: String) { send(SaveAsTheme(name)) }
    fun onDeleteUserTheme(name: String) { send(DeleteUserTheme(name)) }
    fun onApplyObjectStyle(ids: List<String>, styleId: String) { send(ApplyObjectStyle(ids, styleId)) }
    fun onSaveObjectStyle(shapeId: String, name: String) { send(SaveObjectStyle(shapeId, name)) }
    fun onDeleteObjectStyle(styleId: String) { send(DeleteObjectStyle(styleId)) }
    fun onRenameObjectStyle(styleId: String, name: String) { send(RenameObjectStyle(styleId, name)) }
    fun onUseAsDefaultTextStyle(id: String) { send(UseAsDefaultTextStyle(id)) }
    fun onUseAsDefaultShapeStyle(id: String) { send(UseAsDefaultShapeStyle(id)) }
    fun onSetDocumentBackground(background: SlideBackground?) { send(SetDocumentBackground(background)) }
    fun onRenameDocument(name: String) { send(RenameDocument(name)) }
    fun onSetSlideSize(width: Float, height: Float, scaleContent: Boolean) { send(SetSlideSize(width, height, scaleContent)) }
    fun onAddDeckFont(assetId: String, family: String, weight: Int, italic: Boolean) { send(AddDeckFont(assetId, family, weight, italic)) }
    fun onRemoveDeckFont(assetId: String) { send(RemoveDeckFont(assetId)) }
    fun onUndo() { send(Undo) }
    fun onRedo() { send(Redo) }
    fun onToggleSidebar() { send(ToggleSidebar) }
    fun onToggleObjectList() { send(ToggleObjectList) }
    fun onSetObjectListFilter(text: String) { send(SetObjectListFilter(text)) }
    fun onToggleObjectExpanded(id: String) { send(ToggleObjectExpanded(id)) }
    fun onToggleNotes() { send(ToggleNotes) }
    fun onResizeNotes(height: Float) { send(ResizeNotes(height)) }
    fun onBeginNotesEdit() { send(BeginNotesEdit) }
    fun onEditNotes(notes: String) { send(EditNotes(notes)) }
    fun onEndNotesEdit() { send(EndNotesEdit) }
    fun onToggleRulers() { send(ToggleRulers) }
    fun onToggleGuides() { send(ToggleGuides) }
    fun onSetSnap(kind: SnapKind, enabled: Boolean) { send(SetSnap(kind, enabled)) }
    fun onSetZoom(percent: Int) { send(SetZoom(percent)) }
    fun onZoomIn() { send(ZoomIn) }
    fun onZoomOut() { send(ZoomOut) }
    fun onZoomToSelection() { send(ZoomToSelection) }
    fun onZoomToFitContent() { send(ZoomToFitContent) }
    fun onZoomBy(factor: Float, anchorX: Float, anchorY: Float) { send(ZoomBy(factor, anchorX, anchorY)) }
    fun onPanCanvas(dx: Float, dy: Float) { send(PanCanvas(dx, dy)) }
    fun onCanvasResized(width: Float, height: Float) { send(CanvasResized(width, height)) }
    fun onPreviewGuide(id: String?, axis: GuideAxis, position: Float) { send(PreviewGuide(id, axis, position)) }
    fun onCommitGuide(id: String?, axis: GuideAxis, position: Float) { send(CommitGuide(id, axis, position)) }
    fun onRemoveGuide(id: String) { send(RemoveGuide(id)) }
    fun onEndGuideDrag() { send(EndGuideDrag) }
    fun onSelectInspectorTab(tab: InspectorTab) { send(SelectInspectorTab(tab)) }
    fun onCloseInspector() { send(CloseInspector) }
    fun onToggleInspector() { send(ToggleInspector) }
    fun onSelectFormatSegment(segment: FormatSegment) { send(SelectFormatSegment(segment)) }
    fun onSelectAnimateSegment(segment: AnimateSegment) { send(SelectAnimateSegment(segment)) }
    fun onToggleInspectorSection(section: InspectorSection) { send(ToggleInspectorSection(section)) }

    fun close() {
        applyObserver.dispose()
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

private fun BundleChange.toEvent(): EditorEvent = when (this) {
    is BundleChange.Deck -> EditorEvent.Reload(document)
    BundleChange.Assets -> EditorEvent.RefreshAssets
}

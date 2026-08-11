package io.github.xxfast.cupboard.winui

import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.screens.editor.EditorState
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/** One navigator row, flattened for XAML. The C# side projects this into an ObservableCollection. */
class WinOutlineRow(
    val slideId: String,
    val title: String,
    val depth: Int,
    val slideIndex: Int,
    val hasChildren: Boolean,
    val collapsed: Boolean,
)

/**
 * [EditorState] projected to types the NuGet interop can map.
 *
 * The shared state is not exported as-is on purpose: it carries the whole
 * [io.github.xxfast.cupboard.document.Document], whose elements are a sealed
 * hierarchy, and the interop generator has no C# property type for one ("No C#
 * property type for SpecializedProtocol ... Element"). Nothing is lost, because
 * a WinUI host has no canvas to draw elements onto yet: it renders the outline
 * and the menu-enablement flags, which is exactly what this carries. When the
 * canvas arrives it will be a native view fed by its own calls, not by this.
 */
class WinEditorState(
    val outline: List<WinOutlineRow>,
    /** Slides in the whole deck, collapsed ones included: the status bar counts
     * the deck, not the rows currently on screen. */
    val slideCount: Int,
    val selectedSlideId: String,
    val selectedSlideIndex: Int,
    val selectedSlideTitle: String,
    val canUndo: Boolean,
    val canRedo: Boolean,
)

/**
 * The .NET-facing face of the shared [EditorViewModel].
 *
 * A thin wrapper rather than exporting [EditorViewModel] straight, for three
 * reasons. Its constructor takes a `KStore<Document>`, which no C# caller can
 * build, so the store stays behind [WindowsApp.bootstrap]. Its event methods take
 * domain types (`Slide`); the ones here take ids and indices, which is what a
 * XAML binding actually has to hand. And its state needs the projection above.
 *
 * [states] is a `StateFlow`, which kotlin-native-nuget projects as a
 * `KotlinStateFlow` with a synchronous `.Value` plus async enumeration: exactly
 * the shape an `INotifyPropertyChanged` adapter wants.
 */
class WinEditorViewModel {
    // Private so CoroutineScope is not part of the NuGet export surface.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Blocking is right here: there is no editor to show until the document
    // loads, and the host constructs this before its first frame. Same call the
    // macOS and desktop shells make.
    private val documentStore = requireDocumentStore()
    private val viewModel = EditorViewModel(
        initialDocument = runBlocking { documentStore.get() } ?: sampleDocument(),
        documentStore = documentStore,
    )

    /**
     * Editor state for the host to bind. Lazily shared: the presenter starts on
     * the first collector, so the adapter must enumerate this before driving
     * events at the methods below.
     */
    val states: StateFlow<WinEditorState> by lazy {
        viewModel.states
            .map { it.toWin() }
            .stateIn(scope, SharingStarted.Lazily, viewModel.states.value.toWin())
    }

    fun onSelectSlide(id: String) { viewModel.onSelectSlide(id) }

    fun onSelectSlideAt(index: Int) { viewModel.onSelectSlideAt(index) }

    /** Pass null to clear the selection. */
    fun onSelectElement(id: String?) { viewModel.onSelectElement(id) }

    fun onToggleCollapsed(slideId: String) { viewModel.onToggleCollapsed(slideId) }

    fun onUndo() { viewModel.onUndo() }

    fun onRedo() { viewModel.onRedo() }

    /** Stops the editor and its autosave. The host calls this when its window closes. */
    fun close() {
        scope.cancel()
        viewModel.close()
    }
}

private fun EditorState.toWin(): WinEditorState = WinEditorState(
    outline = outline().map { entry ->
        WinOutlineRow(
            slideId = entry.slideId,
            title = entry.title,
            depth = entry.depth,
            slideIndex = entry.slideIndex,
            hasChildren = entry.hasChildren,
            collapsed = entry.collapsed,
        )
    },
    slideCount = document.allSlides().size,
    selectedSlideId = selectedSlideId,
    selectedSlideIndex = selectedSlideIndex(),
    selectedSlideTitle = selectedSlide.title,
    canUndo = canUndo,
    canRedo = canRedo,
)

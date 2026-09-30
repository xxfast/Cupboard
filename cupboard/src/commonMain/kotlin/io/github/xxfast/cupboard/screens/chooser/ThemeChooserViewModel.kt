package io.github.xxfast.cupboard.screens.chooser

import app.cash.molecule.RecompositionMode.Immediate
import app.cash.molecule.moleculeFlow
import io.github.xxfast.cupboard.document.SlideSizePreset
import io.github.xxfast.cupboard.screens.chooser.ThemeChooserEvent.SelectCategory
import io.github.xxfast.cupboard.screens.chooser.ThemeChooserEvent.SelectSize
import io.github.xxfast.cupboard.screens.chooser.ThemeChooserEvent.SelectTheme
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
 * Host-agnostic view model for the new-deck chooser: [states] out, event methods
 * in. The editor's shape, see `EditorViewModel`, for the same reasons.
 *
 * [states] is lazily shared, so the presenter starts with the first collector.
 * The picks are all it holds: Create reads [ThemeChooserState.selectedTheme] and
 * [ThemeChooserState.size] off the last state and hands them to
 * `Cupboard.newDocument`. Call [close] when the sheet goes away.
 */
class ThemeChooserViewModel(
    private val initialState: ThemeChooserState = ThemeChooserState(),
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) {
    // Private for the reason the editor's is: this class is exported to ObjC and
    // .NET, and a CoroutineScope is no type of theirs. Shells pass their main
    // dispatcher; Unconfined stays the default for tests.
    private val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())

    private val events: MutableSharedFlow<ThemeChooserEvent> = MutableSharedFlow(extraBufferCapacity = 16)

    val states: StateFlow<ThemeChooserState> =
        moleculeFlow(Immediate) { ThemeChooserPresenter(initialState, events) }
            .stateIn(scope, SharingStarted.Lazily, initialState)

    fun onSelectCategory(name: String) { scope.launch { events.emit(SelectCategory(name)) } }
    fun onSelectTheme(name: String) { scope.launch { events.emit(SelectTheme(name)) } }
    fun onSelectSize(preset: SlideSizePreset) { scope.launch { events.emit(SelectSize(preset)) } }

    fun close() {
        scope.cancel()
    }
}

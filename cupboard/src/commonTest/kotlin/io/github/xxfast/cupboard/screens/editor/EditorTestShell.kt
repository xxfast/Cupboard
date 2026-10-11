package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.BundleChange
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.inMemoryDocumentStore
import io.github.xxfast.cupboard.document.sampleDocument
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * A view model wired the way every shell wires one. [EditorViewModel.states]
 * is lazily shared, so nothing runs until something collects: the collector
 * here stands in for the shell, and closes the editor when the test ends.
 *
 * [changes] stands in for the bundle watcher, for the tests that are about what
 * the editor does when its file changes under it.
 */
internal fun TestScope.editor(
    document: Document = sampleDocument(),
    changes: Flow<BundleChange> = emptyFlow(),
): EditorViewModel {
    val viewModel = EditorViewModel(
        initialDocument = document,
        documentStore = inMemoryDocumentStore(document),
        changes = changes,
    )
    backgroundScope.launch(Dispatchers.Unconfined, start = UNDISPATCHED) {
        try {
            viewModel.states.collect { }
        } finally {
            viewModel.close()
        }
    }
    return viewModel
}

/**
 * An event reaches the state through the presenter's composition, which is
 * prompt but not synchronous, so tests wait for the state they asked for
 * rather than reading [EditorViewModel.states] on the next line. Real time,
 * not the test scheduler's: the presenter runs on its own dispatcher.
 */
internal suspend fun EditorViewModel.await(predicate: (EditorState) -> Boolean): EditorState =
    withContext(Dispatchers.Default) {
        withTimeout(5.seconds) { states.first(predicate) }
    }

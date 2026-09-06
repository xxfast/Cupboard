package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * An editor over `<directory>/document.json`, creating [directory] if missing.
 *
 * Interim persistence, per the roadmap: one JSON file per machine rather than a
 * document-per-file open/save flow. Every shell pointing at the same directory
 * is the point, they are front ends onto one document, not separate apps.
 *
 * [dispatcher] is the host's serialized main dispatcher; never Unconfined, see
 * [EditorViewModel]'s scope note. Kotlin/Native hosts that have no main loop of
 * their own (the .NET one) pass a single-parallelism worker instead.
 */
fun Cupboard.editor(directory: Path, dispatcher: CoroutineDispatcher): EditorViewModel {
    SystemFileSystem.createDirectories(directory)
    val store: KStore<Document> = storeOf(
        file = Path(directory, DOCUMENT_FILE_NAME),
        default = sampleDocument(),
    )
    val themes: KStore<List<Theme>> = storeOf(
        file = Path(directory, THEME_LIBRARY_FILE_NAME),
        default = emptyList(),
    )
    // Blocking is right here: there is no editor to show until the document loads.
    val initial: Document = runBlocking { store.get() } ?: sampleDocument()
    return EditorViewModel(initial, store, themes, dispatcher)
}

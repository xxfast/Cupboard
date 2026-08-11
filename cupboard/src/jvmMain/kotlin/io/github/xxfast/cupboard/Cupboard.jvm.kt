package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * An editor over `<directory>/document.json`, creating [directory] if missing,
 * defaulting to `~/.cupboard` so this shell and the macOS one edit one document.
 *
 * Interim persistence, per the roadmap: one JSON file per machine rather than a
 * document-per-file open/save flow. Tests pass a temp directory.
 *
 * [dispatcher] is the host's serialized main dispatcher; never Unconfined, see
 * [EditorViewModel]'s scope note.
 *
 * The body duplicates the native factory's four lines on purpose: sharing them
 * would need an intermediate source set spanning jvm and native (the default
 * hierarchy has none) for the sake of a `runBlocking` that isn't common code.
 */
fun Cupboard.editor(
    directory: Path = Path(System.getProperty("user.home"), ".cupboard"),
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
): EditorViewModel {
    SystemFileSystem.createDirectories(directory)
    val store: KStore<Document> = storeOf(
        file = Path(directory, DOCUMENT_FILE_NAME),
        default = sampleDocument(),
    )
    // Blocking is right here: there is no editor to show until the document loads.
    val initial: Document = runBlocking { store.get() } ?: sampleDocument()
    return EditorViewModel(initial, store, dispatcher)
}

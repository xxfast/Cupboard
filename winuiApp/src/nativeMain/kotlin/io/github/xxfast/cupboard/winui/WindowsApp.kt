package io.github.xxfast.cupboard.winui

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Where the document lives, set once by the host.
 *
 * Kotlin/Native has no notion of the .NET app's local data folder, and the
 * .NET side has no business constructing a KStore, so the host hands us a
 * directory at startup and we own the file inside it. Module-level rather than
 * injected because every view model in the process edits the same document.
 */
private var documentStore: KStore<Document>? = null

/** Entry points exported to .NET hosts through kotlin-native-nuget. */
object WindowsApp {
    /**
     * Points the editor at `<storageDirectory>/document.json`, creating the
     * directory if it is missing. Call once before any [WinEditorViewModel];
     * calling again is a no-op, so a host that re-creates its window is safe.
     */
    fun bootstrap(storageDirectory: String) {
        if (documentStore != null) return
        val directory = Path(storageDirectory)
        SystemFileSystem.createDirectories(directory)
        documentStore = storeOf(
            file = Path(directory, DOCUMENT_FILE_NAME),
            default = sampleDocument(),
        )
    }

    private const val DOCUMENT_FILE_NAME: String = "document.json"
}

/** The bootstrapped store, or a loud failure: an editor with nowhere to save is a bug. */
internal fun requireDocumentStore(): KStore<Document> = checkNotNull(documentStore) {
    "Call WindowsApp.bootstrap(storageDirectory) before creating a view model."
}

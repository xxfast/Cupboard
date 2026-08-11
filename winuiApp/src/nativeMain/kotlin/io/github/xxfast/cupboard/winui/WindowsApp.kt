package io.github.xxfast.cupboard.winui

import kotlinx.io.files.Path

/**
 * Where the document lives, set once by the host.
 *
 * Kotlin/Native has no notion of the .NET app's local data folder, so the host
 * hands us a directory at startup and the shared factory owns the file inside
 * it. Module-level rather than injected because every view model in the process
 * edits the same document.
 */
private var documentDirectory: Path? = null

/** Entry points exported to .NET hosts through kotlin-native-nuget. */
object WindowsApp {
    /**
     * Points the editor at `<storageDirectory>/document.json`. Call once before
     * any [WinEditorViewModel]; calling again is a no-op, so a host that
     * re-creates its window is safe.
     *
     * Only the directory is kept here. Creating it and opening the store is
     * `Cupboard.editor`'s job, the same call the macOS and desktop shells make.
     */
    fun bootstrap(storageDirectory: String) {
        if (documentDirectory != null) return
        require(storageDirectory.isNotBlank()) { "storageDirectory must not be blank." }
        documentDirectory = Path(storageDirectory)
    }
}

/** The bootstrapped directory, or a loud failure: an editor with nowhere to save is a bug. */
internal fun requireDocumentDirectory(): Path = checkNotNull(documentDirectory) {
    "Call WindowsApp.bootstrap(storageDirectory) before creating a view model."
}

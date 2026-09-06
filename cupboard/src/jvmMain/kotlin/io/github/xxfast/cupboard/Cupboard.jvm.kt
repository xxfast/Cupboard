package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.document.CupboardBundle
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.FileAssetStore
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path

/**
 * An editor over a `.cupboard` bundle, creating it if missing, defaulting to
 * `~/.cupboard/Untitled.cupboard` so this shell and the macOS one edit one deck.
 *
 * Still one deck per machine until open/save-as lands, but the thing on disk is
 * now the real format: a folder with `document.json` and `assets/` in it. A
 * pre-bundle `~/.cupboard/document.json` moves into it on the way past, see
 * [CupboardBundle.migrate].
 *
 * The theme library is not the deck's, it is the machine's, so it stays beside
 * the bundle rather than inside it: `<bundle's folder>/themes.json`.
 *
 * [dispatcher] is the host's serialized main dispatcher; never Unconfined, see
 * [EditorViewModel]'s scope note.
 *
 * The body duplicates the native factory's few lines on purpose: the layout is
 * shared through [CupboardBundle], and what is left is a `runBlocking` that has
 * no common declaration across jvm and native to be written once.
 */
fun Cupboard.editor(
    bundle: Path = CupboardBundle.default(Path(System.getProperty("user.home"), ".cupboard")),
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
): EditorViewModel {
    val directory: Path = bundle.parent ?: Path(".")
    CupboardBundle.migrate(directory, bundle)
    val store: KStore<Document> = CupboardBundle.documentStore(bundle)
    val assets: FileAssetStore = CupboardBundle.assetStore(bundle)
    val themes: KStore<List<Theme>> = storeOf(
        file = Path(directory, THEME_LIBRARY_FILE_NAME),
        default = emptyList(),
    )
    // Blocking is right here: there is no editor to show until the document loads.
    val initial: Document = runBlocking { store.get() } ?: sampleDocument()
    return EditorViewModel(initial, store, themes, assets, dispatcher)
}

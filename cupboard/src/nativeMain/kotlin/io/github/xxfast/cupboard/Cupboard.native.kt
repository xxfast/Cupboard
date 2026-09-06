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
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path

/**
 * An editor over the `.cupboard` bundle at [bundle], creating it if missing.
 *
 * Still one deck per machine until open/save-as lands, but the thing on disk is
 * the real format: a folder with `document.json` and `assets/` in it. Every
 * shell pointing at the same bundle is the point, they are front ends onto one
 * deck, not separate apps. A pre-bundle `document.json` in the folder above
 * moves in on the way past, see [CupboardBundle.migrate].
 *
 * The theme library is the machine's rather than the deck's, so it stays beside
 * the bundle: `<bundle's folder>/themes.json`.
 *
 * [dispatcher] is the host's serialized main dispatcher; never Unconfined, see
 * [EditorViewModel]'s scope note. Kotlin/Native hosts that have no main loop of
 * their own (the .NET one) pass a single-parallelism worker instead.
 */
fun Cupboard.editor(bundle: Path, dispatcher: CoroutineDispatcher): EditorViewModel {
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

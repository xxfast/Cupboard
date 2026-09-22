package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.document.CupboardBundle
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.DocumentLoad
import io.github.xxfast.cupboard.document.RecentDocuments
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.decodeDocument
import io.github.xxfast.cupboard.document.instantiating
import io.github.xxfast.cupboard.document.showcaseDocument
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * What came back from opening a deck the user picked.
 *
 * Two outcomes rather than a nullable view model, because a failure has to say
 * something: a deck from a newer Cupboard, a damaged one and a folder that was
 * never a deck are three different sentences, and the shell putting them in an
 * alert should not be composing them. [Failed.reason] is that sentence, ready
 * to show.
 *
 * `Cupboard.editor` still hands back a view model directly: it opens the deck
 * this machine already had, which is not a thing that can fail into a dialog.
 */
sealed interface OpenResult {
    data class Opened(val viewModel: EditorViewModel) : OpenResult

    /** [reason] is written for the person who double-clicked the file. */
    data class Failed(val reason: String) : OpenResult
}

/**
 * The editor over the bundle at [bundle], creating it if it is not there yet.
 *
 * The shared half of every platform's `Cupboard.editor`: what is left up there
 * is a `runBlocking`, which has no declaration common to jvm and native to be
 * written once.
 *
 * [directory] is Cupboard's own folder on this machine, where the recents list
 * and the theme library live. Never the deck: those two are the person's, not
 * the document's, so they must not travel when a bundle is mailed on.
 */
internal suspend fun openEditor(
    bundle: Path,
    directory: Path,
    dispatcher: CoroutineDispatcher,
): EditorViewModel {
    // The pre-bundle `<directory>/document.json` only ever belonged to the
    // default deck, so only opening that one goes looking for it.
    if (bundle.toString() == CupboardBundle.default(directory).toString()) {
        CupboardBundle.migrate(directory, bundle)
    }
    val store: KStore<Document> = CupboardBundle.documentStore(bundle)
    return editorOver(bundle, directory, store.get() ?: showcaseDocument(), store, dispatcher)
}

/**
 * The editor over a deck the user picked, or why it could not be opened.
 *
 * Read through [decodeDocument] rather than through the store the editor will
 * use, so a deck this build cannot read is named instead of quietly opening as
 * the sample deck: a store hands back its default for a file it fails on.
 */
internal suspend fun openBundle(
    bundle: Path,
    directory: Path,
    dispatcher: CoroutineDispatcher,
): OpenResult {
    if (!CupboardBundle.isBundle(bundle)) return OpenResult.Failed("Not a Cupboard deck")
    val json: String = CupboardBundle.read(bundle) ?: return OpenResult.Failed("Not a Cupboard deck")

    return when (val load: DocumentLoad = decodeDocument(json)) {
        is DocumentLoad.TooNew ->
            OpenResult.Failed("This deck needs a newer Cupboard (format ${load.version})")

        is DocumentLoad.Corrupt -> OpenResult.Failed(load.reason)

        is DocumentLoad.Loaded -> OpenResult.Opened(
            editorOver(
                bundle,
                directory,
                load.document,
                CupboardBundle.documentStore(bundle),
                dispatcher,
            ),
        )
    }
}

/**
 * Lays out a new bundle in [directory] and returns where it went.
 *
 * Named after the first [name] nothing else answers to: `Untitled.cupboard`,
 * then `Untitled 2.cupboard`, the way every document app numbers them. The deck
 * inside takes that same name, so the title bar and the file agree from the
 * first frame.
 *
 * Nothing is opened here. A shell makes the bundle and then opens it like any
 * other, which is one path through the editor rather than two.
 */
internal fun createBundle(directory: Path, name: String): Path {
    val bundle: Path = uniqueBundle(directory, name)
    CupboardBundle.create(bundle, freshDocument(bundle.deckName()))
    return bundle
}

/**
 * [createBundle] for the feature showcase: a bundle in [directory] holding
 * [showcaseDocument], and where it went.
 *
 * A bundle of its own rather than a window onto an in-memory deck, so Help >
 * Open Feature Showcase lands on the one path into the editor that every other
 * way in takes, and so a reader can scribble on the deck without losing it on
 * quit. Numbered up like any other new bundle, so opening it twice is two decks
 * rather than one overwritten.
 */
internal fun createShowcaseBundle(directory: Path): Path {
    val bundle: Path = uniqueBundle(directory, SHOWCASE_NAME)
    CupboardBundle.create(bundle, showcaseDocument().copy(name = bundle.deckName()))
    return bundle
}

/**
 * Writes this editor's deck and everything it points at into a new bundle at
 * [target], and opens an editor on that.
 *
 * A copy rather than a move: the bundle the editor was on stays exactly as it
 * is, which is what Save As means everywhere else. The new editor is a new view
 * model because [EditorViewModel.location] is fixed for its life; the caller
 * closes the old one once its window is showing this one.
 *
 * The deck is renamed to the file the user chose, so a "Talk.cupboard" is a
 * deck called Talk. Assets are copied one at a time through the store interface
 * rather than by copying the folder: the bundle is the format, not the file
 * layout, and a shell that saved to a memory-backed editor still works.
 */
internal suspend fun saveBundleAs(
    viewModel: EditorViewModel,
    target: Path,
    directory: Path,
    dispatcher: CoroutineDispatcher,
): EditorViewModel {
    val bundle: Path = target.withBundleExtension()
    val document: Document = viewModel.states.value.document.copy(name = bundle.deckName())
    CupboardBundle.create(bundle, document)

    val assets = CupboardBundle.assetStore(bundle)
    for (id in viewModel.assets.ids()) {
        val bytes: ByteArray = viewModel.assets.read(id) ?: continue
        assets.write(id, bytes)
    }

    return editorOver(bundle, directory, document, CupboardBundle.documentStore(bundle), dispatcher)
}

/** The decks this machine opened last. See [RecentDocuments]. */
internal fun recentsIn(directory: Path): RecentDocuments =
    RecentDocuments(Path(directory, RECENTS_FILE_NAME))

/**
 * The stores every editor gets, whichever way its deck was read.
 *
 * Remembering the deck happens here rather than in the callers so every way in
 * (the default deck, an Open, a Save As) lands in the recents list, and none of
 * them has to say so.
 */
private suspend fun editorOver(
    bundle: Path,
    directory: Path,
    document: Document,
    store: KStore<Document>,
    dispatcher: CoroutineDispatcher,
): EditorViewModel {
    recentsIn(directory).remember(bundle)
    val themes: KStore<List<Theme>> = storeOf(
        file = Path(directory, THEME_LIBRARY_FILE_NAME),
        default = emptyList(),
    )
    return EditorViewModel(
        initialDocument = document,
        documentStore = store,
        themeStore = themes,
        assets = CupboardBundle.assetStore(bundle),
        dispatcher = dispatcher,
        location = bundle.toString(),
    )
}

/**
 * What a new deck opens on: one slide, on the deck's title layout.
 *
 * Never an empty deck. A [Document] with no slides is a canvas with nothing to
 * draw and a navigator with nothing to select, and every reduction in the
 * editor assumes there is a slide to be on.
 */
private fun freshDocument(name: String): Document {
    val deck = Document(name = name)
    return deck.copy(slides = listOf(Slide().instantiating(deck.layouts.firstOrNull())))
}

/** `<directory>/<name>.cupboard`, numbered up until nothing is in the way. */
private fun uniqueBundle(directory: Path, name: String): Path {
    val base: String = name.trim().ifBlank { UNTITLED }
    return generateSequence(1) { it + 1 }
        .map { attempt -> Path(directory, bundleName(base, attempt)) }
        .first { !SystemFileSystem.exists(it) }
}

private fun bundleName(base: String, attempt: Int): String =
    if (attempt == 1) "$base.${CupboardBundle.EXTENSION}"
    else "$base $attempt.${CupboardBundle.EXTENSION}"

/**
 * This path with `.cupboard` on the end, for a save dialog that handed one back
 * without it. Already-extended paths come back untouched.
 */
private fun Path.withBundleExtension(): Path =
    if (name.endsWith(".${CupboardBundle.EXTENSION}")) this
    else Path(parent ?: Path("."), "$name.${CupboardBundle.EXTENSION}")

/** What the deck inside a bundle is called: the folder's name without the extension. */
private fun Path.deckName(): String =
    name.removeSuffix(".${CupboardBundle.EXTENSION}").ifBlank { UNTITLED }

/** What a deck is called before it is called anything. */
private const val UNTITLED: String = "Untitled"

/** What the showcase bundle is called on disk, and so what its deck is named. */
private const val SHOWCASE_NAME: String = "Feature Showcase"

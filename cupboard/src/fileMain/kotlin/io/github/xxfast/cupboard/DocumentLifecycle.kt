package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.BuiltInThemes
import io.github.xxfast.cupboard.document.CupboardBundle
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.DocumentLoad
import io.github.xxfast.cupboard.document.FileAssetStore
import io.github.xxfast.cupboard.document.RecentDocuments
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideSizePreset
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.deck
import io.github.xxfast.cupboard.document.decodeDocument
import io.github.xxfast.cupboard.document.encodeToString
import io.github.xxfast.cupboard.document.instantiating
import io.github.xxfast.cupboard.document.namedIn
import io.github.xxfast.cupboard.document.showcaseAssets
import io.github.xxfast.cupboard.document.showcaseDocument
import io.github.xxfast.cupboard.document.sweep
import io.github.xxfast.cupboard.document.watchBundle
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.io.IOException
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
    // Nothing here yet means the store's default, the showcase, is what opens:
    // a fresh machine's first deck, so it gets the showcase's bytes too. A
    // bundle that already has a deck is never written into.
    if (!CupboardBundle.isBundle(bundle)) CupboardBundle.addAssets(bundle, showcaseAssets())
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

        is DocumentLoad.Loaded -> {
            // The one place a bundle loses what its deck stopped pointing at,
            // and only for a deck that was read: here no editor is on it yet,
            // so no undo can ask for the bytes back, where a save has one and
            // a reload may be seeing assets land ahead of their deck. Before
            // the editor, whose watcher would take this for a change from
            // outside.
            try {
                CupboardBundle.assetStore(bundle).sweep(json)
            } catch (sweeping: IOException) {
                // A bundle that will not be written to still opens, strays and all.
            }

            OpenResult.Opened(
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
}

/**
 * Lays out a new bundle in [directory] and returns where it went.
 *
 * Named after the first [name] nothing else answers to: `Untitled.cupboard`,
 * then `Untitled 2.cupboard`, the way every document app numbers them. The deck
 * inside takes that same name, so the title bar and the file agree from the
 * first frame.
 *
 * The deck is on [theme] at [size] from the start: what the new-deck chooser
 * picked, or the app's own look at 16:9 for a shell that asked nothing.
 *
 * Nothing is opened here. A shell makes the bundle and then opens it like any
 * other, which is one path through the editor rather than two.
 */
internal fun createBundle(
    directory: Path,
    name: String,
    theme: Theme = BuiltInThemes.Cupboard,
    size: SlideSizePreset = SlideSizePreset.Widescreen,
): Path {
    val bundle: Path = uniqueBundle(directory, name)
    CupboardBundle.create(bundle, freshDocument(bundle.deckName(), theme, size))
    return bundle
}

/**
 * The feature showcase's bundle in [directory], and where it is.
 *
 * One bundle, always the same one: laid down from [showcaseDocument] and
 * [showcaseAssets] the first time and opened as it stands every time after, so a
 * reader can scribble on the deck and find it still scribbled on.
 * [resetShowcaseBundle] puts it back.
 *
 * A showcase laid down before it shipped any bytes keeps having none until it
 * is reset: every slide that points at them reads without them.
 *
 * A bundle rather than a window onto an in-memory deck, so Help > Feature
 * Showcase lands on the one path into the editor that every other way in takes.
 */
internal fun showcaseBundle(directory: Path): Path {
    val bundle: Path = Path(directory, "$SHOWCASE_NAME.${CupboardBundle.EXTENSION}")
    if (!CupboardBundle.isBundle(bundle)) CupboardBundle.create(bundle, showcaseDeck(), showcaseAssets())
    return bundle
}

/**
 * [showcaseBundle] back to how it shipped: the deck rewritten from
 * [showcaseDocument], every asset added since gone, and [showcaseAssets] put
 * back in their place.
 *
 * Nothing is closed here. An editor still open on the bundle would autosave its
 * deck straight back over this one, so a shell stops it first.
 */
internal fun resetShowcaseBundle(directory: Path): Path {
    val bundle: Path = showcaseBundle(directory)
    CupboardBundle.replace(bundle, showcaseDeck(), showcaseAssets())
    return bundle
}

private fun showcaseDeck(): Document = showcaseDocument().copy(name = SHOWCASE_NAME)

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
 * deck called Talk. Assets are copied one at a time through the stores rather
 * than by copying the folder: the bundle is the format, not the file layout,
 * and a shell that saved to a memory-backed editor still works. See
 * [copyAssets].
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
    copyAssets(viewModel.assets, CupboardBundle.assetStore(bundle), document.encodeToString())

    return editorOver(bundle, directory, document, CupboardBundle.documentStore(bundle), dispatcher)
}

/**
 * What [json], the deck being copied, names in [source] into [target], each
 * under the id it had. See [namedIn].
 *
 * The rest stays where it is: what the deck stopped pointing at is only still
 * in [source] for an undo there, and the copy opens with no history to undo.
 * Nothing is deleted here, [source] is left as it was.
 *
 * File to file where [source] is a folder too, which is every editor a shell
 * opens on a bundle: a movie is streamed across by [FileAssetStore.copyFrom]
 * and never read into memory. Any other store has only bytes to give, so those
 * go across through [AssetStore.read] and [AssetStore.write].
 */
private suspend fun copyAssets(source: AssetStore, target: FileAssetStore, json: String) {
    for (id in source.ids().filter(namedIn(json))) {
        if (source is FileAssetStore) {
            target.copyFrom(id, source.pathOf(id))
            continue
        }

        val bytes: ByteArray = source.read(id) ?: continue
        target.write(id, bytes)
    }
}

/**
 * What came back from moving a deck: Rename and Move To, which are one thing,
 * a bundle ending up at another path.
 *
 * Two outcomes for [OpenResult]'s reason: a name that is taken and a folder
 * that will not be written to are sentences for an alert, and the shell showing
 * them should not be composing them.
 */
sealed interface MoveResult {
    /**
     * The editor on the bundle where it is now. A new one, and the one that was
     * passed in is closed, unless the deck was already there: then it is the
     * same one back, still running.
     */
    data class Moved(val viewModel: EditorViewModel) : MoveResult

    /** [reason] is written for the person who typed the name. Nothing moved. */
    data class Failed(val reason: String) : MoveResult
}

/**
 * Moves this editor's bundle to [target], and opens an editor on it there.
 *
 * The bundle itself moves rather than being copied and deleted, so everything
 * the filesystem hangs off it (Finder tags, dates) comes along. Across volumes
 * there is no such move, and the deck is laid out again at [target] instead.
 *
 * A new name is the deck's name too, the way Save As has it, so the title bar
 * and the file keep agreeing. A move to another folder leaves the name alone.
 *
 * The new editor is a new view model, [EditorViewModel.location] being fixed
 * for its life, so the undo history stays behind with the old one. That one is
 * closed here rather than by the caller: its store points at a folder that is
 * no longer there, and an autosave from it would have nowhere to land. The deck
 * as it stood is written into the moved bundle first, so an edit still waiting
 * on its debounce is not lost with it.
 */
internal suspend fun moveBundle(
    viewModel: EditorViewModel,
    target: Path,
    directory: Path,
    dispatcher: CoroutineDispatcher,
): MoveResult {
    val source = Path(viewModel.location)
    val bundle: Path = target.withBundleExtension()
    if (bundle.toString() == source.toString()) return MoveResult.Moved(viewModel)
    if (!CupboardBundle.isBundle(source)) return MoveResult.Failed("This deck has no file to move")

    val folder: Path = bundle.parent ?: Path(".")
    if (SystemFileSystem.metadataOrNull(folder)?.isDirectory != true) {
        return MoveResult.Failed("The folder \"${folder.name}\" is not there")
    }

    // A change of case alone names the same file on a case-insensitive volume,
    // so that one is not something being in the way.
    val recased: Boolean = bundle.toString().equals(source.toString(), ignoreCase = true)
    if (!recased && SystemFileSystem.exists(bundle)) {
        return MoveResult.Failed("\"${bundle.deckName()}\" is already taken in \"${folder.name}\"")
    }

    val current: Document = viewModel.states.value.document
    val document: Document =
        if (bundle.name == source.name) current
        else current.copy(name = bundle.deckName())

    try {
        SystemFileSystem.atomicMove(source, bundle)
    } catch (moving: IOException) {
        // Another volume, most likely. Copied through the stores, the way Save
        // As copies, and the original only goes once the copy is whole.
        try {
            CupboardBundle.create(bundle, document)
            val copy: FileAssetStore = CupboardBundle.assetStore(bundle)
            copyAssets(viewModel.assets, copy, document.encodeToString())
        } catch (copying: IOException) {
            deleteTree(bundle)
            return MoveResult.Failed(copying.message ?: moving.message ?: "The deck could not be moved")
        }
        deleteTree(source)
    }

    // Over whatever the move carried: the name, and any edit autosave had not
    // got to yet.
    CupboardBundle.create(bundle, document)
    recentsIn(directory).forget(source)

    val moved: EditorViewModel =
        editorOver(bundle, directory, document, CupboardBundle.documentStore(bundle), dispatcher)
    viewModel.close()
    return MoveResult.Moved(moved)
}

/** [path] and everything under it. A path that is not there is a no-op. */
private fun deleteTree(path: Path) {
    val metadata = SystemFileSystem.metadataOrNull(path) ?: return
    if (metadata.isDirectory) {
        for (child in SystemFileSystem.list(path)) deleteTree(child)
    }
    SystemFileSystem.delete(path, mustExist = false)
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
        changes = watchBundle(bundle),
        dispatcher = dispatcher,
        location = bundle.toString(),
    )
}

/**
 * What a new deck opens on: one slide, on the first layout of [theme], at [size].
 *
 * Themed and sized before the slide goes in, so the slide takes the layout as it
 * stands on this deck rather than being rescaled after. See [Theme.deck] for how
 * a theme with layouts drawn for [size] differs from one scaled to it.
 *
 * Never an empty deck. A [Document] with no slides is a canvas with nothing to
 * draw and a navigator with nothing to select, and every reduction in the
 * editor assumes there is a slide to be on.
 */
private fun freshDocument(name: String, theme: Theme, size: SlideSizePreset): Document {
    val deck: Document = theme.deck(name, size)

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

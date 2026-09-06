package io.github.xxfast.cupboard.document

import io.github.xxfast.cupboard.DOCUMENT_FILE_NAME
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString

/**
 * The `.cupboard` document format: a folder, not a file.
 *
 * ```
 * Talk.cupboard/
 *   document.json     the deck
 *   assets/           the bytes it points at, one file per asset id
 * ```
 *
 * A folder rather than a zip because that is what the editor wants to be doing
 * all day: autosave rewrites one small JSON file and an image import drops one
 * file beside it, neither repacking an archive. macOS shows it as a single item
 * once the extension is declared a package, and on the platforms that don't, a
 * folder you can open and read is not the worst thing a presentation tool ever
 * did.
 *
 * Nothing above this knows the layout: [documentStore] and [assetStore] are the
 * only two ways in, and both hand back the interfaces the editor already takes.
 */
object CupboardBundle {
    /** Without the dot. What the file dialogs filter on. */
    const val EXTENSION: String = "cupboard"

    /** The bundle a shell opens when the user has not picked one yet. */
    const val DEFAULT_NAME: String = "Untitled.cupboard"

    private const val ASSETS_DIRECTORY: String = "assets"

    /** The default bundle inside [directory], which is `~/.cupboard` for every shell today. */
    fun default(directory: Path): Path = Path(directory, DEFAULT_NAME)

    /**
     * The deck inside [bundle], creating the bundle if it is not there yet.
     *
     * Defaults to [sampleDocument] the way the factories always have: an editor
     * that opens on an empty canvas teaches you nothing about what it can do.
     */
    fun documentStore(bundle: Path): KStore<Document> {
        SystemFileSystem.createDirectories(bundle)
        return storeOf(file = documentFile(bundle), default = sampleDocument())
    }

    /** The bytes inside [bundle]. The folder appears on the first write, see [FileAssetStore]. */
    fun assetStore(bundle: Path): FileAssetStore = FileAssetStore(Path(bundle, ASSETS_DIRECTORY))

    /**
     * Whether [path] is something this app can open: a folder with a deck in it.
     *
     * The extension is a naming convention for the dialogs, not the test. A
     * bundle someone renamed is still a bundle, and an empty folder called
     * `Talk.cupboard` is not one yet.
     */
    fun isBundle(path: Path): Boolean {
        if (SystemFileSystem.metadataOrNull(path)?.isDirectory != true) return false
        return SystemFileSystem.exists(documentFile(path))
    }

    /**
     * Lays out an empty bundle at [path]: `document.json` holding [document],
     * and the `assets/` folder beside it.
     *
     * Written straight rather than through kstore so this stays callable off a
     * coroutine, which is what a "New" menu item and a save-as both want.
     */
    fun create(path: Path, document: Document = Document()) {
        SystemFileSystem.createDirectories(Path(path, ASSETS_DIRECTORY))
        SystemFileSystem.sink(documentFile(path)).buffered()
            .use { it.writeString(document.encodeToString()) }
    }

    /**
     * Moves a pre-bundle `<directory>/document.json` into [bundle].
     *
     * The interim persistence this replaces wrote one loose JSON file per
     * machine. Everyone running Cupboard has one, and it holds real work, so it
     * becomes the deck of the default bundle instead of being orphaned beside
     * it. Only ever runs once: after the move there is no loose file left, and a
     * bundle that already has a deck is never overwritten.
     */
    fun migrate(directory: Path, bundle: Path) {
        val legacy = Path(directory, DOCUMENT_FILE_NAME)
        if (!SystemFileSystem.exists(legacy)) return
        if (SystemFileSystem.exists(documentFile(bundle))) return
        SystemFileSystem.createDirectories(Path(bundle, ASSETS_DIRECTORY))
        SystemFileSystem.atomicMove(legacy, documentFile(bundle))
    }

    private fun documentFile(bundle: Path): Path = Path(bundle, DOCUMENT_FILE_NAME)
}

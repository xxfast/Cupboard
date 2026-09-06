package io.github.xxfast.cupboard.document

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * An [AssetStore] over a real folder: one file per asset, named by its id.
 *
 * Flat on purpose. A `.cupboard` bundle is meant to be openable in Finder, and
 * `assets/hero.png` beside `document.json` is the whole story. [directory] is
 * created on the first write rather than at construction, so building a store
 * for a bundle you have not written to yet touches nothing.
 *
 * IO here is synchronous inside a suspend function, no dispatcher hop: assets
 * move on user actions (an import, a delete), never per frame, and the source
 * set spans jvm and native, which do not share an IO dispatcher to hop onto.
 */
class FileAssetStore(private val directory: Path) : AssetStore {
    override suspend fun read(id: String): ByteArray? {
        val file: Path = fileFor(id)
        if (!SystemFileSystem.exists(file)) return null
        return SystemFileSystem.source(file).buffered().use { it.readByteArray() }
    }

    override suspend fun write(id: String, bytes: ByteArray) {
        SystemFileSystem.createDirectories(directory)
        SystemFileSystem.sink(fileFor(id)).buffered().use { it.write(bytes) }
    }

    override suspend fun delete(id: String) {
        SystemFileSystem.delete(fileFor(id), mustExist = false)
    }

    override suspend fun ids(): List<String> {
        if (!SystemFileSystem.exists(directory)) return emptyList()
        return SystemFileSystem.list(directory)
            .filter { SystemFileSystem.metadataOrNull(it)?.isRegularFile == true }
            .map { it.name }
    }

    /**
     * Ids name a file in [directory] and nothing else. Anything with a separator
     * in it would reach out of the bundle, which is a bug in the caller rather
     * than a case to handle.
     */
    private fun fileFor(id: String): Path {
        require(id.isNotBlank()) { "an asset id must not be blank" }
        require('/' !in id && '\\' !in id) { "an asset id must be a single file name, was '$id'" }
        return Path(directory, id)
    }
}

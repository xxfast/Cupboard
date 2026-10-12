package io.github.xxfast.cupboard.document

import kotlinx.io.IOException
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

    /**
     * The file at [path] copied in under [id], over whatever was there.
     *
     * [write] for bytes that are already a file: streamed from one to the other
     * a segment at a time, so a two gigabyte recording never has to fit in
     * memory on its way into the bundle. A [path] that cannot be read throws,
     * and so does a disk that fills up part way; what had landed by then is
     * taken back out, so a failed copy leaves no half a movie behind.
     *
     * Copying a file onto itself is a no-op rather than a truncation: opening
     * the sink empties the file the source is still reading.
     */
    suspend fun copyFrom(id: String, path: String) {
        val source = Path(path)
        val target: Path = fileFor(id)
        val onItself: Boolean = SystemFileSystem.exists(target) &&
            SystemFileSystem.resolve(source) == SystemFileSystem.resolve(target)
        if (onItself) return

        SystemFileSystem.createDirectories(directory)
        SystemFileSystem.source(source).buffered().use { from ->
            try {
                SystemFileSystem.sink(target).buffered().use { to -> from.transferTo(to) }
            } catch (copying: IOException) {
                SystemFileSystem.delete(target, mustExist = false)
                throw copying
            }
        }
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
     * Where [id]'s bytes sit on disk, whether or not anything is there yet.
     *
     * The one thing an [AssetStore] deliberately cannot answer, and the one thing
     * a platform media player needs: AVPlayer and MediaPlayer open a file, not a
     * ByteArray, and a movie is far too large to be read through [read] just to
     * be written back out to a temporary file. So the hosts that have a real
     * bundle hand the path over, and the ones that don't ([InMemoryAssetStore])
     * have no movies to play in the first place.
     */
    fun pathOf(id: String): String = fileFor(id).toString()

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

/**
 * The file at [path] written into this store under [id].
 *
 * The one place a shell hands over a file rather than bytes. A store over a
 * real folder copies it across, see [FileAssetStore.copyFrom]; any other has
 * nowhere to put a file but memory, so it is read whole and given to
 * [AssetStore.write].
 *
 * An extension here rather than a member of [AssetStore]: a path means nothing
 * on the web targets the interface spans.
 */
suspend fun AssetStore.writeFile(id: String, path: String) {
    if (this is FileAssetStore) return copyFrom(id, path)

    val bytes: ByteArray = SystemFileSystem.source(Path(path)).buffered().use { it.readByteArray() }
    write(id, bytes)
}

package io.github.xxfast.cupboard.document

import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A file going into a store as a file: what a movie or a sound takes on its way
 * into a deck, where [AssetStore.write] would want the whole of it in memory.
 *
 * Real files in a real folder, for [CupboardBundleTest]'s reason. The recording
 * here is a few segments long rather than a few bytes, so a copy that stopped
 * at its first buffer would not pass.
 */
class FileAssetStoreTest {
    @Test
    fun copiesAFileInUnderTheId() = runTest {
        val directory: Path = tempDirectory()
        val picked: Path = file(directory, "Recording.mov", recording)
        val store = FileAssetStore(Path(directory, "assets"))

        store.copyFrom("movie.mov", picked.toString())

        assertContentEquals(recording, store.read("movie.mov"))
        assertEquals(listOf("movie.mov"), store.ids())
        // A copy: the file the user picked is still theirs, and still whole.
        assertContentEquals(recording, read(picked))
    }

    @Test
    fun copiesOverWhatWasThere() = runTest {
        val directory: Path = tempDirectory()
        val picked: Path = file(directory, "Recording.mov", recording)
        val store = FileAssetStore(Path(directory, "assets"))
        store.write("movie.mov", ByteArray(recording.size * 2) { 1 })

        store.copyFrom("movie.mov", picked.toString())

        assertContentEquals(recording, store.read("movie.mov"))
    }

    @Test
    fun leavesNothingBehindForAFileThatIsNotThere() = runTest {
        val directory: Path = tempDirectory()
        val store = FileAssetStore(Path(directory, "assets"))

        assertFailsWith<IOException> {
            store.copyFrom("movie.mov", Path(directory, "Gone.mov").toString())
        }

        assertEquals(emptyList(), store.ids())
    }

    @Test
    fun copyingAnAssetOntoItselfLeavesItWhole() = runTest {
        val store = FileAssetStore(Path(tempDirectory(), "assets"))
        store.write("movie.mov", recording)

        // Save As over the bundle the editor is already on: the sink would
        // empty the very file the source is reading.
        store.copyFrom("movie.mov", store.pathOf("movie.mov"))

        assertContentEquals(recording, store.read("movie.mov"))
    }

    @Test
    fun writesAFileIntoAFolderByCopyingIt() = runTest {
        val directory: Path = tempDirectory()
        val picked: Path = file(directory, "Recording.mov", recording)
        val store: AssetStore = FileAssetStore(Path(directory, "assets"))

        store.writeFile("movie.mov", picked.toString())

        assertContentEquals(recording, store.read("movie.mov"))
    }

    @Test
    fun writesAFileIntoMemoryAsItsBytes() = runTest {
        val picked: Path = file(tempDirectory(), "Recording.mov", recording)
        val store = InMemoryAssetStore()

        store.writeFile("movie.mov", picked.toString())

        assertContentEquals(recording, store.read("movie.mov"))
        assertEquals(listOf("movie.mov"), store.ids())
    }

    /** Past a handful of kotlinx-io's 8k segments, and no two of them alike. */
    private val recording: ByteArray = ByteArray(100_003) { (it % 251).toByte() }

    private fun file(directory: Path, name: String, bytes: ByteArray): Path {
        val file = Path(directory, name)
        SystemFileSystem.sink(file).buffered().use { it.write(bytes) }
        return file
    }

    private fun read(file: Path): ByteArray =
        SystemFileSystem.source(file).buffered().use { it.readByteArray() }

    private fun tempDirectory(): Path = Path(createTempDirectory("cupboard-assets").toString())
}

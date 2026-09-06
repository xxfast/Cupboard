package io.github.xxfast.cupboard.document

import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `.cupboard` format itself, on a real filesystem: the layout is the format,
 * so an assertion about it belongs against real files rather than a fake.
 */
class CupboardBundleTest {
    @Test
    fun createsADocumentAndAnAssetsFolder() {
        val bundle = Path(tempDirectory(), "Talk.cupboard")

        CupboardBundle.create(bundle, Document(name = "Talk"))

        assertTrue(CupboardBundle.isBundle(bundle))
        assertTrue(SystemFileSystem.metadataOrNull(Path(bundle, "assets"))?.isDirectory == true)
        val json: String = SystemFileSystem.source(Path(bundle, "document.json"))
            .buffered().use { it.readString() }
        assertEquals("Talk", (decodeDocument(json) as DocumentLoad.Loaded).document.name)
    }

    @Test
    fun isNotABundleUntilThereIsADeckInIt() {
        val directory = tempDirectory()
        assertFalse(CupboardBundle.isBundle(Path(directory, "nothing-here.cupboard")))

        // A folder with the right name but no deck is not one yet, and neither
        // is a plain file however it is named.
        val empty = Path(directory, "empty.cupboard")
        SystemFileSystem.createDirectories(empty)
        assertFalse(CupboardBundle.isBundle(empty))

        val file = Path(directory, "notafolder.cupboard")
        SystemFileSystem.sink(file).buffered().use { it.writeString("{}") }
        assertFalse(CupboardBundle.isBundle(file))
    }

    @Test
    fun keepsAssetsInsideTheBundle() = runTest {
        val bundle = Path(tempDirectory(), "Talk.cupboard")
        val store = CupboardBundle.assetStore(bundle)
        val bytes = byteArrayOf(7, 7, 7)

        store.write("hero.png", bytes)

        assertContentEquals(bytes, store.read("hero.png"))
        // On disk where Finder would show it, not in some sidecar folder.
        assertTrue(SystemFileSystem.exists(Path(bundle, "assets", "hero.png")))

        store.write("other.jpg", byteArrayOf(1))
        assertEquals(setOf("hero.png", "other.jpg"), store.ids().toSet())

        store.delete("hero.png")
        assertNull(store.read("hero.png"))
        assertEquals(listOf("other.jpg"), store.ids())
    }

    @Test
    fun readsNothingFromABundleThatHasNoAssetsYet() = runTest {
        val store = CupboardBundle.assetStore(Path(tempDirectory(), "Fresh.cupboard"))

        assertEquals(emptyList(), store.ids())
        assertNull(store.read("hero.png"))
    }

    @Test
    fun movesALegacyDocumentIntoTheBundle() {
        val directory = tempDirectory()
        SystemFileSystem.createDirectories(directory)
        val legacy = Path(directory, "document.json")
        val saved = Document(name = "Written before bundles")
        SystemFileSystem.sink(legacy).buffered().use { it.writeString(saved.encodeToString()) }
        val bundle = CupboardBundle.default(directory)

        CupboardBundle.migrate(directory, bundle)

        assertFalse(SystemFileSystem.exists(legacy), "the loose file was left behind")
        assertTrue(CupboardBundle.isBundle(bundle))
        val json: String = SystemFileSystem.source(Path(bundle, "document.json"))
            .buffered().use { it.readString() }
        assertEquals(saved.name, (decodeDocument(json) as DocumentLoad.Loaded).document.name)
    }

    @Test
    fun neverOverwritesADeckTheBundleAlreadyHas() {
        val directory = tempDirectory()
        SystemFileSystem.createDirectories(directory)
        SystemFileSystem.sink(Path(directory, "document.json")).buffered()
            .use { it.writeString(Document(name = "Legacy").encodeToString()) }
        val bundle = CupboardBundle.default(directory)
        CupboardBundle.create(bundle, Document(name = "Already here"))

        CupboardBundle.migrate(directory, bundle)

        val json: String = SystemFileSystem.source(Path(bundle, "document.json"))
            .buffered().use { it.readString() }
        assertEquals("Already here", (decodeDocument(json) as DocumentLoad.Loaded).document.name)
    }

    private fun tempDirectory(): Path = Path(createTempDirectory("cupboard-bundle").toString())
}

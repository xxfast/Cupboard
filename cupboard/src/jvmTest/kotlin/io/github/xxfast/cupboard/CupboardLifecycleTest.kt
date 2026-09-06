package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.document.CupboardBundle
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.DocumentLoad
import io.github.xxfast.cupboard.document.decodeDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * New, Open and Save As, on real bundles in real folders: these three are
 * nothing but what they leave on disk, so there is nothing here worth faking.
 *
 * Every call passes its own [directory], so the recents list and theme library
 * these write land in the temp folder rather than in the developer's own
 * `~/.cupboard`.
 */
class CupboardLifecycleTest {
    @Test
    fun makesANumberedBundleWithADeckInIt() {
        val directory: Path = tempDirectory()

        val first: Path = Cupboard.newDocument(directory)
        val second: Path = Cupboard.newDocument(directory)

        assertEquals("Untitled.cupboard", first.name)
        assertEquals("Untitled 2.cupboard", second.name)
        assertTrue(CupboardBundle.isBundle(first))
        assertTrue(SystemFileSystem.metadataOrNull(Path(first, "assets"))?.isDirectory == true)

        // Named after the file it is in, and never empty: an editor opening on a
        // deck with no slides has nothing to select and nothing to draw.
        val fresh: Document = deckIn(first)
        assertEquals("Untitled", fresh.name)
        assertEquals(1, fresh.slides.size)
        assertEquals("Untitled 2", deckIn(second).name)
    }

    @Test
    fun opensADeckAndRemembersIt() {
        val directory: Path = tempDirectory()
        val bundle: Path = Cupboard.newDocument(directory, "Talk")

        val result: OpenResult = Cupboard.openDocument(bundle, Dispatchers.Unconfined, directory)

        val opened = assertIs<OpenResult.Opened>(result)
        assertEquals("Talk", opened.viewModel.states.value.document.name)
        // The window's own identity, and the string it hands back to a Save.
        assertEquals(bundle.toString(), opened.viewModel.location)
        assertEquals(listOf(bundle.toString()), Cupboard.recentDocuments(directory))

        Cupboard.forgetRecent(bundle, directory)
        assertTrue(Cupboard.recentDocuments(directory).isEmpty())

        opened.viewModel.close()
    }

    @Test
    fun refusesADeckFromANewerCupboard() {
        val directory: Path = tempDirectory()
        val bundle = Path(directory, "Future.cupboard")
        SystemFileSystem.createDirectories(bundle)
        write(Path(bundle, "document.json"), """{"formatVersion":99,"name":"Future"}""")

        val result: OpenResult = Cupboard.openDocument(bundle, Dispatchers.Unconfined, directory)

        assertEquals(
            "This deck needs a newer Cupboard (format 99)",
            assertIs<OpenResult.Failed>(result).reason,
        )
    }

    @Test
    fun refusesAFolderThatIsNotADeck() {
        val directory: Path = tempDirectory()
        val folder = Path(directory, "Holiday Photos")
        SystemFileSystem.createDirectories(folder)

        val opened: OpenResult = Cupboard.openDocument(folder, Dispatchers.Unconfined, directory)
        assertEquals("Not a Cupboard deck", assertIs<OpenResult.Failed>(opened).reason)

        // Nor is a path with nothing at all on the end of it.
        val missing = Path(directory, "Nothing.cupboard")
        val absent: OpenResult = Cupboard.openDocument(missing, Dispatchers.Unconfined, directory)
        assertEquals("Not a Cupboard deck", assertIs<OpenResult.Failed>(absent).reason)
    }

    @Test
    fun savesACopyWithItsAssetsUnderTheNewName() {
        val directory: Path = tempDirectory()
        val bundle: Path = Cupboard.newDocument(directory, "Origin")
        val opened = assertIs<OpenResult.Opened>(
            Cupboard.openDocument(bundle, Dispatchers.Unconfined, directory),
        )
        val hero = byteArrayOf(7, 7, 7)
        runBlocking { opened.viewModel.assets.write("hero.png", hero) }

        // No extension: a save panel hands one back without it, and the deck
        // still has to end up a bundle.
        val elsewhere: Path = tempDirectory()
        val saved = Cupboard.saveAs(
            opened.viewModel,
            Path(elsewhere, "Copy"),
            Dispatchers.Unconfined,
            directory,
        )

        val copy = Path(elsewhere, "Copy.cupboard")
        assertTrue(CupboardBundle.isBundle(copy))
        assertEquals(copy.toString(), saved.location)
        // Renamed to the file the user picked, on disk as well as in the editor.
        assertEquals("Copy", saved.states.value.document.name)
        assertEquals("Copy", deckIn(copy).name)
        assertContentEquals(hero, runBlocking { saved.assets.read("hero.png") })

        // A copy, not a move: what it was saved from is still there, still named
        // what it was, and still holding its own assets.
        assertEquals("Origin", deckIn(bundle).name)
        assertContentEquals(hero, runBlocking { opened.viewModel.assets.read("hero.png") })
        assertEquals(
            listOf(copy.toString(), bundle.toString()),
            Cupboard.recentDocuments(directory),
        )

        opened.viewModel.close()
        saved.close()
    }

    private fun deckIn(bundle: Path): Document {
        val json: String = checkNotNull(CupboardBundle.read(bundle)) { "no deck in $bundle" }
        return assertIs<DocumentLoad.Loaded>(decodeDocument(json)).document
    }

    private fun write(file: Path, text: String) {
        SystemFileSystem.sink(file).buffered().use { it.writeString(text) }
    }

    private fun tempDirectory(): Path = Path(createTempDirectory("cupboard-lifecycle").toString())
}

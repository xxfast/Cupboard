package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.document.BuiltInThemes
import io.github.xxfast.cupboard.document.CupboardBundle
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.DocumentLoad
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.InMemoryAssetStore
import io.github.xxfast.cupboard.document.PlaceholderRole
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.ShowcasePictureId
import io.github.xxfast.cupboard.document.SlideSizePreset
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.VideoElement
import io.github.xxfast.cupboard.document.placeholderRole
import io.github.xxfast.cupboard.document.decodeDocument
import io.github.xxfast.cupboard.document.encodeToString
import io.github.xxfast.cupboard.document.showcaseAssets
import io.github.xxfast.cupboard.document.showcaseDocument
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
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
        // Pointed at by the deck: a copy takes what the deck names and no more.
        point(bundle, at = listOf("hero.png", "movie.mov"))
        val opened = assertIs<OpenResult.Opened>(
            Cupboard.openDocument(bundle, Dispatchers.Unconfined, directory),
        )
        val hero = byteArrayOf(7, 7, 7)
        // Long enough to cross a few buffers on its way: assets go file to file.
        val movie = ByteArray(100_003) { (it % 251).toByte() }
        runBlocking {
            opened.viewModel.assets.write("hero.png", hero)
            opened.viewModel.assets.write("movie.mov", movie)
        }

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
        assertEquals(setOf("hero.png", "movie.mov"), runBlocking { saved.assets.ids() }.toSet())
        assertContentEquals(hero, runBlocking { saved.assets.read("hero.png") })
        assertContentEquals(movie, runBlocking { saved.assets.read("movie.mov") })

        // A copy, not a move: what it was saved from is still there, still named
        // what it was, and still holding its own assets.
        assertEquals("Origin", deckIn(bundle).name)
        assertContentEquals(hero, runBlocking { opened.viewModel.assets.read("hero.png") })
        assertContentEquals(movie, runBlocking { opened.viewModel.assets.read("movie.mov") })
        assertEquals(
            listOf(copy.toString(), bundle.toString()),
            Cupboard.recentDocuments(directory),
        )

        opened.viewModel.close()
        saved.close()
    }

    @Test
    fun savesAMemoryBackedDecksAssetsIntoTheBundle() {
        val directory: Path = tempDirectory()
        val hero = byteArrayOf(7, 7, 7)
        // An editor with no bundle behind it, as the preview shells run: it has
        // no files to copy, only the bytes its store hands over.
        val scratch = EditorViewModel(
            initialDocument = Document(name = "Scratch").pointingAt(listOf("hero.png")),
            documentStore = CupboardBundle.documentStore(Path(directory, "Scratch.cupboard")),
            assets = InMemoryAssetStore(mapOf("hero.png" to hero)),
        )

        val saved = Cupboard.saveAs(
            scratch,
            Path(directory, "Kept"),
            Dispatchers.Unconfined,
            directory,
        )

        assertEquals(listOf("hero.png"), runBlocking { saved.assets.ids() })
        assertContentEquals(hero, runBlocking { saved.assets.read("hero.png") })

        scratch.close()
        saved.close()
    }

    @Test
    fun savingOverItsOwnBundleKeepsItsAssets() {
        val directory: Path = tempDirectory()
        val bundle: Path = Cupboard.newDocument(directory, "Origin")
        val opened = assertIs<OpenResult.Opened>(
            Cupboard.openDocument(bundle, Dispatchers.Unconfined, directory),
        )
        val movie = ByteArray(100_003) { (it % 251).toByte() }
        runBlocking { opened.viewModel.assets.write("movie.mov", movie) }

        // The save panel's own file picked again: every asset is then copied
        // onto itself, which must not be how a movie gets emptied.
        val saved = Cupboard.saveAs(opened.viewModel, bundle, Dispatchers.Unconfined, directory)

        assertEquals(bundle.toString(), saved.location)
        assertContentEquals(movie, runBlocking { saved.assets.read("movie.mov") })

        opened.viewModel.close()
        saved.close()
    }

    @Test
    fun renamesTheBundleAndTheDeckWithIt() {
        val directory: Path = tempDirectory()
        val bundle: Path = Cupboard.newDocument(directory, "Origin")
        val opened = assertIs<OpenResult.Opened>(
            Cupboard.openDocument(bundle, Dispatchers.Unconfined, directory),
        )
        val hero = byteArrayOf(7, 7, 7)
        runBlocking { opened.viewModel.assets.write("hero.png", hero) }

        // No extension, as the name field hands it over.
        val result: MoveResult =
            Cupboard.move(opened.viewModel, Path(directory, "Talk"), Dispatchers.Unconfined, directory)

        val renamed = Path(directory, "Talk.cupboard")
        val moved: EditorViewModel = assertIs<MoveResult.Moved>(result).viewModel
        assertEquals(renamed.toString(), moved.location)
        // A move, not a copy: nothing is left where it was.
        assertFalse(SystemFileSystem.exists(bundle))
        assertEquals("Talk", moved.states.value.document.name)
        assertEquals("Talk", deckIn(renamed).name)
        assertContentEquals(hero, runBlocking { moved.assets.read("hero.png") })
        // The list follows the deck rather than keeping a row for a path that is gone.
        assertEquals(listOf(renamed.toString()), Cupboard.recentDocuments(directory))

        moved.close()
    }

    @Test
    fun movesToAnotherFolderUnderTheSameName() {
        val directory: Path = tempDirectory()
        val bundle: Path = Cupboard.newDocument(directory, "Talk")
        // A deck whose name has drifted from its file keeps it across a move
        // that is only about the folder.
        write(Path(bundle, "document.json"), deckIn(bundle).copy(name = "Better Talk").encodeToString())
        val opened = assertIs<OpenResult.Opened>(
            Cupboard.openDocument(bundle, Dispatchers.Unconfined, directory),
        )

        val elsewhere: Path = tempDirectory()
        val result: MoveResult = Cupboard.move(
            opened.viewModel,
            Path(elsewhere, bundle.name),
            Dispatchers.Unconfined,
            directory,
        )

        val moved: EditorViewModel = assertIs<MoveResult.Moved>(result).viewModel
        assertEquals(Path(elsewhere, "Talk.cupboard").toString(), moved.location)
        assertFalse(SystemFileSystem.exists(bundle))
        assertEquals("Better Talk", deckIn(Path(elsewhere, "Talk.cupboard")).name)

        moved.close()
    }

    @Test
    fun aMoveOntoAnotherDeckOrNowhereChangesNothing() {
        val directory: Path = tempDirectory()
        val bundle: Path = Cupboard.newDocument(directory, "Origin")
        val taken: Path = Cupboard.newDocument(directory, "Taken")
        val opened = assertIs<OpenResult.Opened>(
            Cupboard.openDocument(bundle, Dispatchers.Unconfined, directory),
        )

        val onto: MoveResult =
            Cupboard.move(opened.viewModel, taken, Dispatchers.Unconfined, directory)
        val nowhere: MoveResult = Cupboard.move(
            opened.viewModel,
            Path(Path(directory, "Missing"), "Origin"),
            Dispatchers.Unconfined,
            directory,
        )
        val same: MoveResult =
            Cupboard.move(opened.viewModel, Path(directory, "Origin"), Dispatchers.Unconfined, directory)

        assertIs<MoveResult.Failed>(onto)
        assertIs<MoveResult.Failed>(nowhere)
        // Where it already is: the same editor back, still the one running.
        assertTrue(assertIs<MoveResult.Moved>(same).viewModel === opened.viewModel)
        assertEquals("Origin", deckIn(bundle).name)
        assertEquals("Taken", deckIn(taken).name)

        opened.viewModel.close()
    }

    @Test
    fun opensTheSameShowcaseEveryTimeWithItsEdits() {
        val directory: Path = tempDirectory()

        val first: Path = Cupboard.showcase(directory)
        assertEquals("Feature Showcase.cupboard", first.name)
        assertEquals(showcaseDocument().slides.size, deckIn(first).slides.size)
        assertHoldsTheShowcaseAssets(first)

        write(Path(first, "document.json"), Document(name = "Scribbled").encodeToString())
        runBlocking { CupboardBundle.assetStore(first).delete(ShowcasePictureId) }
        val second: Path = Cupboard.showcase(directory)

        // Not numbered up, and not written over: the reader's edits are still
        // there, and so is the picture they deleted being gone.
        assertEquals(first.toString(), second.toString())
        assertEquals("Scribbled", deckIn(second).name)
        assertNull(runBlocking { CupboardBundle.assetStore(second).read(ShowcasePictureId) })
    }

    @Test
    fun aShowcaseFromBeforeTheAssetsShippedIsLeftWithout() {
        val directory: Path = tempDirectory()
        val bundle = Path(directory, "Feature Showcase.cupboard")
        CupboardBundle.create(bundle, showcaseDocument())

        val opened: Path = Cupboard.showcase(directory)

        assertEquals(bundle.toString(), opened.toString())
        assertTrue(runBlocking { CupboardBundle.assetStore(opened).ids() }.isEmpty())
    }

    @Test
    fun resetsTheShowcaseToHowItShipped() {
        val directory: Path = tempDirectory()
        val bundle: Path = Cupboard.showcase(directory)
        write(Path(bundle, "document.json"), Document(name = "Scribbled").encodeToString())
        runBlocking { CupboardBundle.assetStore(bundle).write("hero.png", byteArrayOf(7)) }

        val reset: Path = Cupboard.resetShowcase(directory)

        assertEquals(bundle.toString(), reset.toString())
        val deck: Document = deckIn(reset)
        assertEquals("Feature Showcase", deck.name)
        assertEquals(showcaseDocument().slides.size, deck.slides.size)
        // What the reader added goes with the deck that pointed at it, and what
        // the showcase shipped with comes back.
        assertNull(runBlocking { CupboardBundle.assetStore(reset).read("hero.png") })
        assertHoldsTheShowcaseAssets(reset)
    }

    @Test
    fun resetPutsTheAssetsIntoAShowcaseThatNeverHadThem() {
        val directory: Path = tempDirectory()
        CupboardBundle.create(Path(directory, "Feature Showcase.cupboard"), showcaseDocument())

        assertHoldsTheShowcaseAssets(Cupboard.resetShowcase(directory))
    }

    @Test
    fun aFreshMachinesFirstDeckHasTheShowcaseAssets() {
        val directory: Path = tempDirectory()
        val bundle = Path(directory, "First.cupboard")

        val viewModel = Cupboard.editor(bundle, Dispatchers.Unconfined, directory)

        assertHoldsTheShowcaseAssets(bundle)
        viewModel.close()
    }

    @Test
    fun aDeckAlreadyInTheBundleGetsNoShowcaseAssets() {
        val directory: Path = tempDirectory()
        val bundle: Path = Cupboard.newDocument(directory, "Talk")

        val viewModel = Cupboard.editor(bundle, Dispatchers.Unconfined, directory)

        assertTrue(runBlocking { CupboardBundle.assetStore(bundle).ids() }.isEmpty())
        viewModel.close()
    }

    @Test
    fun openingADeckDropsWhatItStoppedPointingAt() {
        val directory: Path = tempDirectory()
        val bundle: Path = Cupboard.newDocument(directory, "Talk")
        point(bundle, at = listOf("hero.png", "movie.mov", "poster.jpg"))
        val hero = byteArrayOf(7, 7, 7)
        val movie = ByteArray(4_099) { (it % 251).toByte() }
        val poster = byteArrayOf(9)
        runBlocking {
            val assets = CupboardBundle.assetStore(bundle)
            assets.write("hero.png", hero)
            assets.write("movie.mov", movie)
            assets.write("poster.jpg", poster)
            // What a replaced movie and a deleted image leave behind.
            assets.write("old.mov", byteArrayOf(1, 2))
            assets.write("gone.png", byteArrayOf(3))
        }

        val opened = assertIs<OpenResult.Opened>(
            Cupboard.openDocument(bundle, Dispatchers.Unconfined, directory),
        )

        assertEquals(setOf("hero.png", "movie.mov", "poster.jpg"), filesIn(Path(bundle, "assets")))
        assertContentEquals(hero, runBlocking { opened.viewModel.assets.read("hero.png") })
        assertContentEquals(movie, runBlocking { opened.viewModel.assets.read("movie.mov") })
        assertContentEquals(poster, runBlocking { opened.viewModel.assets.read("poster.jpg") })

        opened.viewModel.close()
    }

    @Test
    fun aDeckThatCannotBeReadLosesNothing() {
        // Neither of these says what the deck points at, so neither is a
        // reason to believe it points at nothing.
        val unreadable: Map<String, String> = mapOf(
            "Damaged" to """{"name":"Damaged","slides":[{"elements":""",
            "Future" to """{"formatVersion":99,"name":"Future"}""",
        )

        for ((name, json) in unreadable) {
            val directory: Path = tempDirectory()
            val bundle = Path(directory, "$name.cupboard")
            CupboardBundle.create(bundle, Document(name = name))
            write(Path(bundle, "document.json"), json)
            runBlocking {
                CupboardBundle.assetStore(bundle).write("hero.png", byteArrayOf(7))
                CupboardBundle.assetStore(bundle).write("movie.mov", byteArrayOf(8))
            }

            val result: OpenResult = Cupboard.openDocument(bundle, Dispatchers.Unconfined, directory)

            assertIs<OpenResult.Failed>(result, name)
            assertEquals(setOf("hero.png", "movie.mov"), filesIn(Path(bundle, "assets")), name)
            assertEquals(json, CupboardBundle.read(bundle), name)
        }
    }

    @Test
    fun aDeckThatWillNotBeWrittenToStillOpens() {
        val directory: Path = tempDirectory()
        val bundle: Path = Cupboard.newDocument(directory, "Locked")
        runBlocking { CupboardBundle.assetStore(bundle).write("stray.png", byteArrayOf(1)) }
        val assets = File(Path(bundle, "assets").toString())
        assertTrue(assets.setWritable(false))

        try {
            val result: OpenResult = Cupboard.openDocument(bundle, Dispatchers.Unconfined, directory)

            // Tidying up is not what opening a deck is for: one that cannot be
            // tidied opens as it is.
            assertIs<OpenResult.Opened>(result).viewModel.close()
            assertEquals(setOf("stray.png"), filesIn(Path(bundle, "assets")))
        } finally {
            assets.setWritable(true)
        }
    }

    @Test
    fun saveAsLeavesWhatTheDeckStoppedPointingAtBehind() {
        val directory: Path = tempDirectory()
        val bundle: Path = Cupboard.newDocument(directory, "Origin")
        point(bundle, at = listOf("hero.png"))
        val opened = assertIs<OpenResult.Opened>(
            Cupboard.openDocument(bundle, Dispatchers.Unconfined, directory),
        )
        val hero = byteArrayOf(7, 7, 7)
        runBlocking {
            opened.viewModel.assets.write("hero.png", hero)
            // Still in the bundle for an undo in this editor to find, which the
            // copy, opening with no history, has no use for.
            opened.viewModel.assets.write("deleted.png", byteArrayOf(1))
        }

        val saved = Cupboard.saveAs(
            opened.viewModel,
            Path(directory, "Copy"),
            Dispatchers.Unconfined,
            directory,
        )

        assertEquals(setOf("hero.png"), filesIn(Path(directory, "Copy.cupboard", "assets")))
        assertContentEquals(hero, runBlocking { saved.assets.read("hero.png") })
        // A copy takes less, it does not take away: the original keeps both.
        assertEquals(setOf("hero.png", "deleted.png"), filesIn(Path(bundle, "assets")))

        opened.viewModel.close()
        saved.close()
    }

    @Test
    fun theEditorOverABundleDeletesNothing() {
        val directory: Path = tempDirectory()
        val bundle: Path = Cupboard.newDocument(directory, "Talk")
        runBlocking { CupboardBundle.assetStore(bundle).write("stray.png", byteArrayOf(1)) }

        // No sweep on this way in: it opens the showcase over a deck it cannot
        // read, and the showcase names none of that deck's assets.
        val viewModel = Cupboard.editor(bundle, Dispatchers.Unconfined, directory)

        assertEquals(setOf("stray.png"), filesIn(Path(bundle, "assets")))
        viewModel.close()
    }

    @Test
    fun theEditorOverADeckItCannotReadDeletesNothing() {
        val directory: Path = tempDirectory()
        val bundle: Path = Cupboard.newDocument(directory, "Future")
        write(Path(bundle, "document.json"), """{"formatVersion":99,"name":"Future"}""")
        runBlocking { CupboardBundle.assetStore(bundle).write("hero.png", byteArrayOf(7)) }

        val viewModel = Cupboard.editor(bundle, Dispatchers.Unconfined, directory)

        assertEquals(setOf("hero.png"), filesIn(Path(bundle, "assets")))
        viewModel.close()
    }

    @Test
    fun aNewDeckTakesTheThemeAndSizeTheChooserPicked() {
        val directory: Path = tempDirectory()

        val bundle: Path = Cupboard.newDocument(
            directory,
            theme = BuiltInThemes.BasicBlack,
            size = SlideSizePreset.Standard,
        )

        val deck: Document = deckIn(bundle)
        assertEquals("Basic Black", deck.themeName)
        assertEquals(1440f, deck.slideWidth)
        assertEquals(1080f, deck.slideHeight)
        assertEquals(BuiltInThemes.BasicBlack.layouts.map { it.title }, deck.layouts.map { it.title })
        // Keynote's own 4:3 masters rather than the 16:9 ones squeezed: its 82pt title at 1440/1024.
        val title = deck.slides.single().elements.first { it.placeholderRole == PlaceholderRole.Title }
        assertEquals(82f * 1440f / 1024f, (title as TextElement).fontSize, 0.1f)
        // The one slide is on the title layout, as it stands on this 4:3 deck.
        val slide: Slide = deck.slides.single()
        assertEquals(deck.layouts.first().id, slide.layoutId)
        assertTrue(slide.elements.all { it.frame.x + it.frame.width <= 1440f })
    }

    private fun assertHoldsTheShowcaseAssets(bundle: Path) {
        val store = CupboardBundle.assetStore(bundle)
        for ((id, bytes) in showcaseAssets()) {
            assertContentEquals(bytes, runBlocking { store.read(id) }, "$id in $bundle")
        }
    }

    /** Rewrites [bundle]'s deck so it names every id in [at], the way an insert would have left it. */
    private fun point(bundle: Path, at: List<String>) {
        write(Path(bundle, "document.json"), deckIn(bundle).pointingAt(at).encodeToString())
    }

    /** This deck with a slide naming every id in [ids]: a movie for a `.mov`, a picture for the rest. */
    private fun Document.pointingAt(ids: List<String>): Document {
        val frame = Frame(0f, 0f, 100f, 100f)
        val slide = Slide(
            elements = ids.map { id ->
                if (id.endsWith(".mov")) VideoElement(frame = frame, assetId = id)
                else ImageElement(frame = frame, assetId = id)
            },
        )

        return copy(slides = slides + slide)
    }

    private fun filesIn(folder: Path): Set<String> =
        SystemFileSystem.list(folder).map { it.name }.toSet()

    private fun deckIn(bundle: Path): Document {
        val json: String = checkNotNull(CupboardBundle.read(bundle)) { "no deck in $bundle" }
        return assertIs<DocumentLoad.Loaded>(decodeDocument(json)).document
    }

    private fun write(file: Path, text: String) {
        SystemFileSystem.sink(file).buffered().use { it.writeString(text) }
    }

    private fun tempDirectory(): Path = Path(createTempDirectory("cupboard-lifecycle").toString())
}

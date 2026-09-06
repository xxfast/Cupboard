package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.document.CupboardBundle
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The factory is what every shell calls now, so it is tested the way they call
 * it: a real `.cupboard` bundle with a real kstore-file inside. Only the folder
 * around it is a temp one.
 *
 * Documents are compared by slide titles, never by equality: ids are fresh
 * uuids, so two [sampleDocument] calls are never equal to each other.
 */
class CupboardFactoryTest {
    @Test
    fun opensTheSampleDeckWhenThereIsNoBundleYet() {
        val bundle = bundle()
        val viewModel = Cupboard.editor(bundle, Dispatchers.Unconfined)

        assertEquals(sampleDocument().titles(), viewModel.states.value.document.titles())
        // Neither the bundle nor the folder over it existed, so the factory made
        // them: a shell pointing at a fresh machine must not have to.
        assertTrue(SystemFileSystem.exists(bundle), "the factory did not create $bundle")

        viewModel.close()
    }

    @Test
    fun opensTheDeckAlreadyInTheBundle() {
        val bundle = bundle()
        SystemFileSystem.createDirectories(bundle)
        val saved = sampleDocument().let { document ->
            document.copy(slides = document.slides.map { it.copy(title = "Saved ${it.title}") })
        }
        // Seeded through kstore itself rather than hand-written JSON: whatever
        // kstore does to the format, this writes what the factory reads.
        runBlocking { storeOf<Document>(file = Path(bundle, "document.json")).set(saved) }

        val viewModel = Cupboard.editor(bundle, Dispatchers.Unconfined)

        val opened = viewModel.states.value.document
        assertEquals(saved.titles(), opened.titles())
        assertTrue(opened.allSlides().all { it.title.startsWith("Saved ") })

        viewModel.close()
    }

    @Test
    fun adoptsTheDocumentTheInterimFormatLeftBehind() {
        // Everyone who ran Cupboard before bundles has a loose
        // `~/.cupboard/document.json` with real work in it. Opening the default
        // bundle has to find that work, not start them on a fresh sample deck.
        val directory = Path(createTempDirectory("cupboard-factory").toString(), ".cupboard")
        SystemFileSystem.createDirectories(directory)
        val legacy = sampleDocument().let { document ->
            document.copy(slides = document.slides.map { it.copy(title = "Legacy ${it.title}") })
        }
        runBlocking { storeOf<Document>(file = Path(directory, "document.json")).set(legacy) }

        val viewModel = Cupboard.editor(CupboardBundle.default(directory), Dispatchers.Unconfined)

        assertEquals(legacy.titles(), viewModel.states.value.document.titles())
        assertFalse(
            SystemFileSystem.exists(Path(directory, "document.json")),
            "the loose file was left beside the bundle",
        )
        assertTrue(CupboardBundle.isBundle(CupboardBundle.default(directory)))

        viewModel.close()
    }

    private fun Document.titles(): List<String> = allSlides().map { it.title }

    /** A bundle two levels below a real temp dir, so neither it nor its folder exists yet. */
    private fun bundle(): Path =
        CupboardBundle.default(Path(createTempDirectory("cupboard-factory").toString(), ".cupboard"))
}

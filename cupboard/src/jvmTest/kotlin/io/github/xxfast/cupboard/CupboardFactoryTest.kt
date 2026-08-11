package io.github.xxfast.cupboard

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
import kotlin.test.assertTrue

/**
 * The factory is what every shell calls now, so it is tested the way they call
 * it: a real directory with a real kstore-file inside. Only the directory is a
 * temp one.
 *
 * Documents are compared by slide titles, never by equality: ids are fresh
 * uuids, so two [sampleDocument] calls are never equal to each other.
 */
class CupboardFactoryTest {
    @Test
    fun opensTheSampleDeckWhenThereIsNoFileYet() {
        val directory = tempDirectory()
        val viewModel = Cupboard.editor(directory, Dispatchers.Unconfined)

        assertEquals(sampleDocument().titles(), viewModel.states.value.document.titles())
        // The directory did not exist, so the factory made it: a shell pointing
        // at a fresh machine must not have to.
        assertTrue(SystemFileSystem.exists(directory), "the factory did not create $directory")

        viewModel.close()
    }

    @Test
    fun opensTheDocumentAlreadyOnDisk() {
        val directory = tempDirectory()
        SystemFileSystem.createDirectories(directory)
        val saved = sampleDocument().let { document ->
            document.copy(slides = document.slides.map { it.copy(title = "Saved ${it.title}") })
        }
        // Seeded through kstore itself rather than hand-written JSON: whatever
        // kstore does to the format, this writes what the factory reads.
        runBlocking { storeOf<Document>(file = Path(directory, "document.json")).set(saved) }

        val viewModel = Cupboard.editor(directory, Dispatchers.Unconfined)

        val opened = viewModel.states.value.document
        assertEquals(saved.titles(), opened.titles())
        assertTrue(opened.allSlides().all { it.title.startsWith("Saved ") })

        viewModel.close()
    }

    private fun Document.titles(): List<String> = allSlides().map { it.title }

    /** A path one level below a real temp dir, so it does not exist yet. */
    private fun tempDirectory(): Path =
        Path(createTempDirectory("cupboard-factory").toString(), ".cupboard")
}

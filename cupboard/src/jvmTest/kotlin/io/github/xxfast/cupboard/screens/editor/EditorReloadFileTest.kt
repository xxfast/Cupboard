package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.OpenResult
import io.github.xxfast.cupboard.document.CupboardBundle
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.DocumentLoad
import io.github.xxfast.cupboard.document.decodeDocument
import io.github.xxfast.cupboard.document.encodeToString
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.document.showcaseDocument
import io.github.xxfast.cupboard.document.updateSlide
import io.github.xxfast.cupboard.openBundle
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The reload end to end: a real bundle, opened the way a shell opens one, its
 * watcher running against the real filesystem. The editor runs on one thread of
 * its own, the way a shell's main dispatcher has it, so the watcher's changes and
 * the test's events take turns rather than racing.
 *
 * Long waits: on macOS the JDK's watch service polls every two seconds, and a
 * change only arrives after the debounce on top of that.
 */
class EditorReloadFileTest {
    /**
     * The reload's ignore rule compares what comes off disk with what went on by
     * value, so the two have to be equal after a round trip through the file.
     */
    @Test
    fun aDeckWrittenByTheStoreReadsBackEqual() = runBlocking {
        for (document in listOf(sampleDocument(), showcaseDocument())) {
            val bundle = Path(createTempDirectory("cupboard-roundtrip").toString(), "Talk.cupboard")
            CupboardBundle.documentStore(bundle).set(document)

            val load = decodeDocument(CupboardBundle.read(bundle)!!)
            assertEquals(DocumentLoad.Loaded(document), load)
        }
    }

    @Test
    fun theEditorsOwnAutosaveDoesNotComeBack() = editing { bundle, viewModel ->
        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "Edited"))
        awaitFile(bundle) { deck -> deck.slides.any { it.title == "Edited" } }

        delay(WatcherLatency)

        val state = viewModel.states.value
        assertEquals("Edited", state.selectedSlide.title)
        assertTrue(state.canUndo)
    }

    @Test
    fun anExternalRewriteLandsAndClearsHistory() = editing { bundle, viewModel ->
        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "Edited"))
        awaitFile(bundle) { deck -> deck.slides.any { it.title == "Edited" } }
        viewModel.await(15.seconds) { !it.savePending }

        val external: Document = viewModel.states.value.document.retitled("From disk")
        writeDeck(bundle, external.encodeToString())

        val reloaded = viewModel.await(15.seconds) { it.document == external }
        assertFalse(reloaded.canUndo)
        assertFalse(reloaded.canRedo)
        assertFalse(reloaded.savePending)
    }

    @Test
    fun aReloadIsNotWrittenBack() = editing { bundle, viewModel ->
        val external: Document = viewModel.states.value.document.retitled("From disk")
        writeDeck(bundle, external.encodeToString())
        val bytes: ByteArray = readDeck(bundle)

        viewModel.await(15.seconds) { it.document == external }
        delay(1500.milliseconds)

        assertContentEquals(bytes, readDeck(bundle))
    }

    @Test
    fun anExternalAssetWriteBumpsTheGeneration() = editing { bundle, viewModel ->
        val before: Int = viewModel.states.value.assetGeneration
        SystemFileSystem.sink(Path(bundle, "assets", "hero.png")).buffered()
            .use { it.write(byteArrayOf(1, 2, 3)) }

        viewModel.await(15.seconds) { it.assetGeneration > before }
    }

    /**
     * Opens a fresh bundle the way a shell does, collects it the way a shell
     * does, and gives the watcher a moment to register before [block] starts
     * writing under it.
     */
    private fun editing(block: suspend CoroutineScope.(Path, EditorViewModel) -> Unit) = runBlocking {
        val directory = Path(createTempDirectory("cupboard-reload").toString())
        val bundle = Path(directory, "Talk.cupboard")
        CupboardBundle.create(bundle, sampleDocument())

        val main: ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val viewModel: EditorViewModel = (openBundle(bundle, directory, main) as OpenResult.Opened).viewModel
        val shell: Job = collect(viewModel)
        delay(1.seconds)

        try {
            block(bundle, viewModel)
        } finally {
            shell.cancel()
            viewModel.close()
            main.close()
        }
    }

    private fun CoroutineScope.collect(viewModel: EditorViewModel): Job =
        launch(Dispatchers.Default, start = UNDISPATCHED) { viewModel.states.collect { } }

    private suspend fun EditorViewModel.await(
        timeout: Duration,
        predicate: (EditorState) -> Boolean,
    ): EditorState = withTimeout(timeout) { states.first(predicate) }

    private fun Document.retitled(title: String): Document = updateSlide(slides.first().copy(title = title))

    private fun writeDeck(bundle: Path, json: String) {
        SystemFileSystem.sink(Path(bundle, "document.json")).buffered().use { it.writeString(json) }
    }

    private fun readDeck(bundle: Path): ByteArray =
        SystemFileSystem.source(Path(bundle, "document.json")).buffered().use { it.readByteArray() }

    private suspend fun awaitFile(bundle: Path, predicate: (Document) -> Boolean): Document =
        withTimeout(5.seconds) {
            while (true) {
                val load = CupboardBundle.read(bundle)?.let { decodeDocument(it) }
                if (load is DocumentLoad.Loaded && predicate(load.document)) return@withTimeout load.document
                delay(5)
            }
            error("unreachable")
        }

    private companion object {
        /** Past the poll and the debounce both, with room to spare. */
        val WatcherLatency: Duration = 4.seconds
    }
}

package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.files.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * Autosave lives inside [EditorPresenter] now, so it's tested the way it runs:
 * a real view model over a real kstore-file in a temp dir. The point is that
 * bytes land on disk, so nothing here is faked.
 */
class EditorAutosaveTest {
    @Test
    fun anEditReachesTheFile() = runBlocking {
        val file = tempFile()
        val viewModel = EditorViewModel(sampleDocument(), storeOf(file = file))
        val shell = collect(viewModel)

        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "Persisted"))

        val saved = awaitDocument(file) { document -> document.allSlides().any { it.title == "Persisted" } }
        assertNotNull(saved, "the edit never reached $file")
        assertEquals(viewModel.states.value.document.slides.size, saved.slides.size)

        shell.cancel()
        viewModel.close()
    }

    @Test
    fun openingADocumentDoesNotRewriteIt() = runBlocking {
        val file = tempFile()
        val viewModel = EditorViewModel(sampleDocument(), storeOf(file = file))
        val shell = collect(viewModel)

        // Selection is not a document change, so it must not trigger a write
        // either. Well past the debounce: a write would have happened by now.
        viewModel.onSelectSlideAt(3)
        delay(1.seconds)

        assertNull(read(file), "opening the document rewrote $file")

        shell.cancel()
        viewModel.close()
    }

    /**
     * Stands in for a shell: the state flow is lazily shared, so this is what
     * starts the presenter. Undispatched so it has subscribed by the time the
     * test starts driving events at it.
     */
    private fun CoroutineScope.collect(viewModel: EditorViewModel): Job =
        launch(Dispatchers.Default, start = UNDISPATCHED) { viewModel.states.collect { } }

    private fun tempFile(): Path =
        Path(createTempDirectory("cupboard-autosave").toString(), "document.json")

    /**
     * Reads through a throwaway store every time: a live one would answer from
     * its cache and tell us nothing about what's actually on disk.
     */
    private suspend fun read(file: Path): Document? = storeOf<Document>(file = file).get()

    private suspend fun awaitDocument(file: Path, predicate: (Document) -> Boolean): Document? =
        withTimeoutOrNull(5.seconds) {
            while (true) {
                val saved = read(file)
                if (saved != null && predicate(saved)) return@withTimeoutOrNull saved
                delay(5)
            }
            error("unreachable")
        }
}

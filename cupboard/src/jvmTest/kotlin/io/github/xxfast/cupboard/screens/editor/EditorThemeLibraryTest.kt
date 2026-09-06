package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.inMemoryDocumentStore
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.files.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The theme library the way it runs: a real view model over a real kstore-file in
 * a temp dir. [EditorAutosaveTest]'s shape, one file over: the point is that a
 * theme saved out of one editor is there in the next one, so nothing is faked.
 */
class EditorThemeLibraryTest {
    @Test
    fun aSavedThemeReachesTheFileAndComesBackInTheNextEditor() = runBlocking {
        val file = tempFile()

        val first = EditorViewModel(sampleDocument(), inMemoryDocumentStore(), store(file))
        val writing = collect(first)
        first.onSaveAsTheme("Mine")

        val saved = awaitThemes(file) { themes -> themes.any { it.name == "Mine" } }
        assertNotNull(saved, "the saved theme never reached $file")
        assertEquals(1, saved.size)
        assertEquals(first.states.value.document.layouts, saved.single().layouts)

        writing.cancel()
        first.close()

        val second = EditorViewModel(sampleDocument(), inMemoryDocumentStore(), store(file))
        val reading = collect(second)
        val loaded = withTimeoutOrNull(5.seconds) {
            second.states.first { state -> state.userThemes.any { it.name == "Mine" } }
        }
        assertNotNull(loaded, "the library never loaded from $file")
        assertTrue(loaded.themes.any { it.name == "Mine" })

        reading.cancel()
        second.close()
    }

    /** An editor that only reads the library must never write it back out. */
    @Test
    fun openingTheLibraryDoesNotRewriteIt() = runBlocking {
        val file = tempFile()
        val viewModel = EditorViewModel(sampleDocument(), inMemoryDocumentStore(), store(file))
        val shell = collect(viewModel)

        // Well past the debounce: a write would have happened by now.
        delay(1.seconds)
        assertNull(read(file), "opening the editor wrote $file")

        shell.cancel()
        viewModel.close()
    }

    @Test
    fun deletingAThemeReachesTheFileToo() = runBlocking {
        val file = tempFile()
        val viewModel = EditorViewModel(sampleDocument(), inMemoryDocumentStore(), store(file))
        val shell = collect(viewModel)

        viewModel.onSaveAsTheme("Mine")
        assertNotNull(awaitThemes(file) { themes -> themes.any { it.name == "Mine" } })

        viewModel.onDeleteUserTheme("Mine")
        assertNotNull(
            awaitThemes(file) { themes -> themes.isEmpty() },
            "the deletion never reached $file",
        )

        shell.cancel()
        viewModel.close()
    }

    /** Stands in for a shell: the state flow is lazily shared, so this starts the presenter. */
    private fun CoroutineScope.collect(viewModel: EditorViewModel): Job =
        launch(Dispatchers.Default, start = UNDISPATCHED) { viewModel.states.collect { } }

    private fun store(file: Path): KStore<List<Theme>> = storeOf(file = file, default = emptyList())

    private fun tempFile(): Path =
        Path(createTempDirectory("cupboard-themes").toString(), "themes.json")

    /** A throwaway store every time: a live one would answer from its own cache. */
    private suspend fun read(file: Path): List<Theme>? = storeOf<List<Theme>>(file = file).get()

    private suspend fun awaitThemes(file: Path, predicate: (List<Theme>) -> Boolean): List<Theme>? =
        withTimeoutOrNull(5.seconds) {
            while (true) {
                val saved = read(file)
                if (saved != null && predicate(saved)) return@withTimeoutOrNull saved
                delay(5)
            }
            error("unreachable")
        }
}

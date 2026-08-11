package io.github.xxfast.cupboard.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.files.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Real kstore-file over a temp dir: the point of the autosaver is that bytes
 * land on disk, so nothing here is faked. Debounce is 10ms to keep it quick.
 */
class DocumentAutosaverTest {
    private val debounce = 10.milliseconds

    @Test
    fun aMutationEventuallyPersists() = runBlocking {
        val file = tempFile()
        val scope = CoroutineScope(Dispatchers.Default)
        val editor = EditorStore(sampleDocument())
        val stop = editor.autosaveTo(writer(file), scope, debounce)

        editor.updateSlide(editor.selectedSlide.copy(title = "Persisted"))

        val saved = awaitDocument(file) { document -> document.allSlides().any { it.title == "Persisted" } }
        assertNotNull(saved, "the edit never reached $file")
        assertEquals(editor.document.slides.size, saved.slides.size)

        stop()
        scope.cancel()
    }

    @Test
    fun stopHaltsFurtherWrites() = runBlocking {
        val file = tempFile()
        val scope = CoroutineScope(Dispatchers.Default)
        val editor = EditorStore(sampleDocument())
        val stop = editor.autosaveTo(writer(file), scope, debounce)

        editor.updateSlide(editor.selectedSlide.copy(title = "Before"))
        assertNotNull(
            awaitDocument(file) { document -> document.allSlides().any { it.title == "Before" } },
            "the first edit never reached $file",
        )

        stop()
        editor.updateSlide(editor.selectedSlide.copy(title = "After"))
        // Well past the debounce: if a write were still coming, it would be here.
        delay(200)

        val saved = assertNotNull(read(file))
        assertTrue(saved.allSlides().any { it.title == "Before" })
        assertTrue(saved.allSlides().none { it.title == "After" }, "wrote after being stopped")

        scope.cancel()
    }

    private fun tempFile(): Path =
        Path(createTempDirectory("cupboard-autosave").toString(), "document.json")

    private fun writer(file: Path): KStore<Document> = storeOf(file = file)

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

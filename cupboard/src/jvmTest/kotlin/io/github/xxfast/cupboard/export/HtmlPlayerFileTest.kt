package io.github.xxfast.cupboard.export

import java.io.File
import java.util.Base64
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlinx.io.files.Path

class HtmlPlayerFileTest {
    private val directory: File = kotlin.io.path.createTempDirectory("html-player").toFile()

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun theFileIsThePageByteForByte() {
        // Frames big enough that their base64 runs over several chunks.
        val file = File(directory, "deck.html")
        exportHtmlPlayer(builtDeck(notes = "a < b & c, é ✓"), FakeRasterizer(size = 97), Path(file.path))

        val page: String = exportHtmlPlayer(builtDeck(notes = "a < b & c, é ✓"), FakeRasterizer(size = 97))
        assertEquals(page, file.readText(Charsets.UTF_8))
    }

    @Test
    fun aFailedExportLeavesNoHalfAFile() {
        val file = File(directory, "deck.html")
        val failing = object : Rasterizer {
            override fun frame(slideIndex: Int, step: Int): RasterFrame = error("could not draw")
        }

        assertFailsWith<IllegalStateException> {
            exportHtmlPlayer(plainDeck(2), failing, Path(file.path))
        }
        assertFalse(file.exists())
    }

    @Test
    fun base64InChunksIsBase64() {
        val random = Random(25)
        val sizes: List<Int> = (0..10) + listOf(3071, 3072, 3073, 6144, 6145, 20_000)
        for (size in sizes) {
            val data: ByteArray = random.nextBytes(size)
            val streamed: String = buildString { appendBase64(data) }
            assertEquals(Base64.getEncoder().encodeToString(data), streamed, "$size bytes")
        }
    }
}

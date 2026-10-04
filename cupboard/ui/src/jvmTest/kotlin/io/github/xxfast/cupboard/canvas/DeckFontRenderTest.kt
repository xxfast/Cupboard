package io.github.xxfast.cupboard.canvas

import io.github.xxfast.cupboard.document.DeckFont
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.FontInfo
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.InMemoryAssetStore
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.inMemoryDocumentStore
import io.github.xxfast.cupboard.document.parseFontInfo
import io.github.xxfast.cupboard.export.SlideRasterizer
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import io.github.xxfast.cupboard.screens.editor.addDeckFont
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * A deck's own face, drawn. The bytes are JetBrains Mono Regular (SIL Open Font
 * License 1.1, its notice in the file's own name table), copied in from the CuP
 * demo so this reads nothing outside its own resources.
 *
 * The deck names the face something no system has installed, so the only way
 * the text can come out in it is through the deck.
 */
class DeckFontRenderTest {
    private val bytes: ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/fonts/JetBrainsMono-Regular.ttf")).readBytes()

    private val face = DeckFont(assetId = "face.ttf", family = "Deck Only Face")

    private fun deck(fontName: String?, fonts: List<DeckFont>): Document = Document(
        slideWidth = 600f,
        slideHeight = 160f,
        slides = listOf(
            Slide(
                elements = listOf(
                    TextElement(
                        id = "text",
                        frame = Frame(20f, 20f, 560f, 120f),
                        text = "Hamburgefonstiv 0Oil1",
                        fontSize = 48f,
                        fontName = fontName,
                    ),
                ),
            ),
        ),
        fonts = fonts,
    )

    /** The deck's only slide as ARGB, with every font it carries warmed first, the way an export does. */
    private fun render(document: Document): IntArray {
        val store = InMemoryAssetStore()
        val cache = DeckFontCache()
        runBlocking {
            store.write(face.assetId, bytes)
            cache.warm(document.fonts, store)
        }
        return SlideRasterizer(document, store, width = 600, fonts = cache).frame(0, 0).argb
    }

    @Test
    fun theFileReadsAsWhatItIs() {
        assertEquals(FontInfo("JetBrains Mono", 400, false), parseFontInfo(bytes))
    }

    @Test
    fun textNamingADeckFontDrawsInIt() {
        val generic: IntArray = render(deck(fontName = null, fonts = emptyList()))
        val embedded: IntArray = render(deck(fontName = face.family, fonts = listOf(face)))

        val differing: Int = generic.indices.count { generic[it] != embedded[it] }
        assertTrue(differing > 500, "the deck's face is not the fallback ($differing pixels differ)")
    }

    @Test
    fun aNameTheDeckNoLongerCarriesFallsBack() {
        val generic: IntArray = render(deck(fontName = null, fonts = emptyList()))
        val orphaned: IntArray = render(deck(fontName = face.family, fonts = emptyList()))

        assertContentEquals(generic, orphaned)
    }

    @Test
    fun theSharedHelperWritesReadsAndAdds() = runBlocking {
        val document = Document()
        val viewModel = EditorViewModel(document, inMemoryDocumentStore(document))
        // The shell's collector: states are lazily shared, so nothing folds an
        // event until something is watching.
        val shell: Job = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.states.collect { }
        }
        try {
            assertFalse(viewModel.addDeckFont("not a font".encodeToByteArray(), "ttf"))
            assertEquals(emptyList(), viewModel.assets.ids(), "nothing written for a file that isn't one")

            assertTrue(viewModel.addDeckFont(bytes, "TTF"))
            val added: DeckFont = withTimeout(5.seconds) {
                viewModel.states.first { it.document.fonts.isNotEmpty() }
            }.document.fonts.single()

            assertEquals("JetBrains Mono", added.family)
            assertTrue(added.assetId.endsWith(".ttf"))
            assertContentEquals(bytes, viewModel.assets.read(added.assetId))
        } finally {
            shell.cancel()
            viewModel.close()
        }
    }
}

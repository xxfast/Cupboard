package io.github.xxfast.cupboard.canvas

import io.github.xxfast.cupboard.document.DeckFont
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.InMemoryAssetStore
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.export.SlideRasterizer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Characters that ask for their emoji form get it, even in a face with a text
 * glyph of its own (#45). The face is JetBrains Mono, which draws `⚠` itself.
 */
class EmojiPresentationTest {
    @Test
    fun aSelectorMarksItsCharacter() {
        assertEquals(listOf(0..1), "⚠️ No stack traces".emojiPresentationRanges())
    }

    @Test
    fun noSelectorMarksNothing() {
        assertEquals(emptyList(), "⚠ ❌ ✅ 🔶 plain".emojiPresentationRanges())
    }

    @Test
    fun aKeycapStaysWhole() {
        assertEquals(listOf(2..4), "a 1️⃣ b".emojiPresentationRanges())
    }

    @Test
    fun aJoinedSequenceStaysWhole() {
        // ❤ FE0F ZWJ 🔥: the selector is on the first half, and the fire goes with it.
        assertEquals(listOf(0..4), "❤️‍🔥!".emojiPresentationRanges())
    }

    @Test
    fun aSelectorMidSequenceTakesTheWholeSequence() {
        // 🏳 FE0F ZWJ 🌈, the rainbow flag.
        assertEquals(listOf(1..6), " 🏳️‍🌈".emojiPresentationRanges())
    }

    @Test
    fun eachMarkedCharacterIsItsOwnStretch() {
        assertEquals(listOf(0..1, 3..4), "⚠️ ☀️".emojiPresentationRanges())
    }

    private val bytes: ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/fonts/JetBrainsMono-Regular.ttf")).readBytes()

    private val face = DeckFont(assetId = "face.ttf", family = "Deck Only Face")

    /** [text] in the deck's JetBrains Mono, drawn: how many of its pixels are warning-sign yellow. */
    private fun yellowIn(text: String): Int {
        val document = Document(
            slideWidth = 300f,
            slideHeight = 160f,
            slides = listOf(
                Slide(
                    elements = listOf(
                        TextElement(
                            id = "text",
                            frame = Frame(20f, 20f, 260f, 120f),
                            text = text,
                            fontSize = 80f,
                            fontName = face.family,
                        ),
                    ),
                ),
            ),
            fonts = listOf(face),
        )
        val store = InMemoryAssetStore()
        val cache = DeckFontCache()
        runBlocking {
            store.write(face.assetId, bytes)
            cache.warm(document.fonts, store)
        }
        val argb: IntArray = SlideRasterizer(document, store, width = 300, fonts = cache).frame(0, 0).argb
        return argb.count { pixel ->
            val red: Int = (pixel shr 16) and 0xFF
            val green: Int = (pixel shr 8) and 0xFF
            val blue: Int = pixel and 0xFF
            red > 180 && green > 130 && blue < 100
        }
    }

    @Test
    fun aWarningSignAskingForItsEmojiDrawsInColour() {
        // Nothing to draw it in on a machine without a colour face.
        if (emojiFontFamily() == null) return

        assertEquals(0, yellowIn("⚠"), "the face's own sign is the text one")
        assertTrue(yellowIn("⚠️") > 200, "the sign asking for its emoji is the colour one")
    }
}

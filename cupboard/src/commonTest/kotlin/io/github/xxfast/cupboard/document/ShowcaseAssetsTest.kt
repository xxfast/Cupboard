package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bytes the showcase is laid down with, checked against the deck that
 * points at them: every id the deck names is there, every one of them is what
 * its extension says it is, and two lay-downs hold the same bytes.
 */
class ShowcaseAssetsTest {

    @Test
    fun theSameBytesEveryTime() {
        val first: Map<String, ByteArray> = showcaseAssets()
        val second: Map<String, ByteArray> = showcaseAssets()

        assertEquals(first.keys, second.keys)
        for ((id, bytes) in first) assertContentEquals(bytes, second.getValue(id), id)
    }

    @Test
    fun everyAssetTheDeckNamesShips() {
        val referenced: Set<String> = showcaseDocument().assetIds()
        val shipped: Set<String> = showcaseAssets().keys

        // Not vacuous: each kind of reference the deck makes is in there.
        assertTrue(
            referenced.containsAll(
                listOf(ShowcasePictureId, ShowcaseNightId, ShowcaseTileId, ShowcaseToneId),
            ),
            "the deck stopped pointing at some of its assets: $referenced",
        )
        assertEquals(emptySet(), referenced - shipped, "named by the deck and never shipped")
    }

    @Test
    fun smallEnoughToGenerateOnEveryLayDown() {
        val total: Int = showcaseAssets().values.sumOf { it.size }
        assertTrue(total < 200_000, "the showcase's assets came to $total bytes")
    }

    @Test
    fun thePicturesAreGifsOfTheSizeTheDeckRecords() {
        val assets: Map<String, ByteArray> = showcaseAssets()
        val sizes: Map<String, Pair<Int, Int>> = mapOf(
            ShowcasePictureId to (ShowcasePictureWidth to ShowcasePictureHeight),
            ShowcaseNightId to (ShowcasePictureWidth to ShowcasePictureHeight),
            ShowcaseTileId to (ShowcaseTileSize to ShowcaseTileSize),
        )

        for ((id, size) in sizes) {
            val bytes: ByteArray = assets.getValue(id)
            assertEquals("GIF89a", bytes.ascii(0, 6), id)
            assertEquals(size, bytes.leShort(6) to bytes.leShort(8), id)
            assertEquals(0x3B, bytes.last().toInt(), "$id ends on the trailer")
        }
    }

    @Test
    fun theToneIsAWellFormedWav() {
        val wav: ByteArray = showcaseAssets().getValue(ShowcaseToneId)

        assertEquals("RIFF", wav.ascii(0, 4))
        assertEquals(wav.size - 8, wav.leInt(4), "the RIFF length")
        assertEquals("WAVE", wav.ascii(8, 4))
        assertEquals("fmt ", wav.ascii(12, 4))
        assertEquals(16, wav.leInt(16), "the fmt chunk's length")
        assertEquals(1, wav.leShort(20), "PCM")
        assertEquals(1, wav.leShort(22), "mono")
        assertEquals(22050, wav.leInt(24), "the sample rate")
        assertEquals(22050 * 2, wav.leInt(28), "bytes a second")
        assertEquals(2, wav.leShort(32), "bytes a frame")
        assertEquals(16, wav.leShort(34), "bits a sample")
        assertEquals("data", wav.ascii(36, 4))

        val data: Int = wav.leInt(40)
        assertEquals(wav.size - 44, data, "the data length matches what follows")
        val seconds: Float = data / 2f / 22050
        assertTrue(seconds in 1.4f..1.6f, "the tone runs $seconds seconds")
        // Faded in and out, so it starts and stops at silence rather than a click.
        assertEquals(0, wav.leSignedShort(44), "the first sample")
        assertEquals(0, wav.leSignedShort(wav.size - 2), "the last sample")
    }

    @Test
    fun everyTextLinkIsInsideItsTextAndCoversSomething() {
        val boxes: List<TextElement> = showcaseDocument().slides
            .flatMap { slide -> slide.elements.flatMap { it.subtree() } }
            .filterIsInstance<TextElement>()
        assertTrue(boxes.any { it.links.isNotEmpty() }, "no text box in the deck has a range link")

        for (box in boxes) {
            for (link in box.links) {
                assertTrue(
                    link.start in 0 until link.end && link.end <= box.text.length,
                    "[${link.start}, ${link.end}) is outside '${box.text}'",
                )
            }
        }
    }
}

/** Every asset id anything on this deck points at, layouts and group children included. */
private fun Document.assetIds(): Set<String> {
    val elements: List<Element> = (slides + layouts).flatMap { slide ->
        slide.elements.flatMap { it.subtree() }
    }
    val backgrounds: List<SlideBackground?> = (slides + layouts).map { it.background } + background

    return buildSet {
        for (element in elements) when (element) {
            is ImageElement -> element.assetId?.let(::add)
            is GalleryElement -> element.images.forEach { add(it.assetId) }
            is ShapeElement -> element.imageFill?.let { add(it.assetId) }
            is VideoElement -> {
                element.assetId?.let(::add)
                element.posterAssetId?.let(::add)
            }
            is AudioElement -> element.assetId?.let(::add)
            else -> Unit
        }
        for (each in backgrounds) if (each is SlideBackground.Image) add(each.assetId)
        for (font in fonts) add(font.assetId)
    }
}

private fun Element.subtree(): List<Element> =
    listOf(this) + ((this as? GroupElement)?.children?.flatMap { it.subtree() } ?: emptyList())

private fun ByteArray.ascii(from: Int, length: Int): String =
    (from until from + length).map { this[it].toInt().toChar() }.joinToString("")

private fun ByteArray.leShort(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.leSignedShort(at: Int): Int = leShort(at).toShort().toInt()

private fun ByteArray.leInt(at: Int): Int = leShort(at) or (leShort(at + 2) shl 16)

package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement

/**
 * A rasteriser that draws nothing: a 4x4 frame whose pixels say which slide and
 * step were asked for.
 *
 * Every writer here is tested on bytes rather than on pictures, so what it needs
 * from a rasteriser is that it answers, and that two frames differ. The real one
 * lives in `:cupboard:ui`, where Skia is, and is tested there.
 */
internal class FakeRasterizer(private val size: Int = 4) : Rasterizer {
    /** Every frame that was asked for, in order, for the tests about coverage. */
    val asked: MutableList<ExportFrame> = mutableListOf()

    override fun frame(slideIndex: Int, step: Int): RasterFrame {
        asked += ExportFrame(slideIndex, step)
        val pixels = IntArray(size * size) { at ->
            (0xFF shl 24) or ((slideIndex * 20 + at) shl 16) or (step * 30 shl 8) or at
        }
        return RasterFrame(size, size, pixels)
    }
}

/** A deck of [count] plain slides, each with a note and nothing on it. */
internal fun plainDeck(count: Int): Document = Document(
    name = "Test deck",
    slides = List(count) { index ->
        Slide(id = "slide-$index", title = "Slide ${index + 1}", notes = "Note ${index + 1}")
    },
)

/**
 * One slide with two builds on it, which is three steps: the slide as it opens,
 * and one per click.
 */
internal fun builtDeck(notes: String = "Remember the point"): Document = Document(
    name = "Built deck",
    slides = listOf(
        Slide(
            id = "built",
            title = "Built",
            notes = notes,
            elements = listOf(
                TextElement(id = "first", frame = Frame(0f, 0f, 400f, 80f), text = "First"),
                TextElement(id = "second", frame = Frame(0f, 100f, 400f, 80f), text = "Second"),
            ),
            builds = listOf(Build(elementId = "first"), Build(elementId = "second")),
        ),
    ),
)

/**
 * The entries of a stored ZIP, read back off the local headers.
 *
 * Enough of a reader to check what the writer wrote, and no more: every entry
 * this package produces is stored, so a header is a length and the bytes follow.
 */
internal fun readStoredZip(archive: ByteArray): Map<String, ByteArray> {
    val entries = mutableMapOf<String, ByteArray>()
    var at = 0
    while (at + 30 <= archive.size && archive.leInt(at) == 0x04034B50) {
        val nameLength: Int = archive.leShort(at + 26)
        val extraLength: Int = archive.leShort(at + 28)
        val size: Int = archive.leInt(at + 22)
        val name: String = archive.decodeToString(at + 30, at + 30 + nameLength)
        val start: Int = at + 30 + nameLength + extraLength
        entries[name] = archive.copyOfRange(start, start + size)
        at = start + size
    }
    return entries
}

internal fun ByteArray.leShort(at: Int): Int =
    (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

internal fun ByteArray.leInt(at: Int): Int = leShort(at) or (leShort(at + 2) shl 16)

internal fun ByteArray.beInt(at: Int): Int =
    ((this[at].toInt() and 0xFF) shl 24) or
        ((this[at + 1].toInt() and 0xFF) shl 16) or
        ((this[at + 2].toInt() and 0xFF) shl 8) or
        (this[at + 3].toInt() and 0xFF)

/**
 * These bytes as one char each, which is what a test that walks a PDF's offsets
 * needs: `decodeToString` reads UTF-8, and a file with image data in it has
 * multi-byte sequences that would put every offset past them out of step.
 */
internal fun ByteArray.latin1(): String =
    buildString(size) { for (byte in this@latin1) append(((byte.toInt() and 0xFF).toChar())) }

/** Where [needle] occurs in these bytes, for the tests that count structures. */
internal fun ByteArray.occurrencesOf(needle: ByteArray): Int {
    var count = 0
    outer@ for (start in 0..size - needle.size) {
        for (offset in needle.indices) if (this[start + offset] != needle[offset]) continue@outer
        count++
    }
    return count
}

/** Where [needle] first occurs at or after [from], or -1. */
internal fun ByteArray.indexOfSequence(needle: ByteArray, from: Int = 0): Int {
    outer@ for (start in from..size - needle.size) {
        for (offset in needle.indices) if (this[start + offset] != needle[offset]) continue@outer
        return start
    }
    return -1
}

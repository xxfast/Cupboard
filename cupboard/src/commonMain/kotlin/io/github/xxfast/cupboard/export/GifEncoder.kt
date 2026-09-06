package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Document

/**
 * A deck as an animated GIF: the one export that plays itself anywhere a picture
 * goes, which is what a README, a chat message and a conference CFP all take.
 *
 * Written by hand for the same reason the rest of this package is, and it is the
 * fussiest of them: 256 colours, so the slides have to be quantised, and LZW,
 * so they have to be compressed. The palette is a fixed 6x7x6 cube rather than a
 * per-deck one, which costs a little fidelity and buys a global table every frame
 * shares, and ordered dithering hides the banding that a fixed cube would
 * otherwise put across a gradient background.
 */

/** How the deck is written out as an animation. */
data class GifOptions(
    /** Pixels across. Frames are scaled down to this, keeping the deck's aspect. */
    val width: Int = 960,
    /**
     * How long a frame is held. GIF counts in hundredths of a second, so this
     * lands on the nearest 10ms, and anything under 20ms is a delay most viewers
     * quietly replace with 100ms.
     */
    val frameDelayMs: Int = 1500,
    /** Walk the build order rather than showing each slide once, whole. */
    val everyBuild: Boolean = true,
    val loop: Boolean = true,
)

/** Greens get the extra level: the eye reads detail in green before red or blue. */
private const val RedLevels: Int = 6
private const val GreenLevels: Int = 7
private const val BlueLevels: Int = 6

/** The 4x4 ordered dither, in sixteenths, which is what breaks up a flat gradient. */
private val BayerMatrix: IntArray = intArrayOf(
    0, 8, 2, 10,
    12, 4, 14, 6,
    3, 11, 1, 9,
    15, 7, 13, 5,
)

/** The deck as an animated GIF, one frame per slide or per step. */
fun exportGif(
    document: Document,
    rasterizer: Rasterizer,
    options: GifOptions = GifOptions(),
): ByteArray {
    val frames: List<RasterFrame> = document.exportFrames(options.everyBuild)
        .map { rasterizer.frame(it.slideIndex, it.step).scaledTo(options.width) }

    val width: Int = frames.firstOrNull()?.width ?: 1
    val height: Int = frames.firstOrNull()?.height ?: 1
    val delay: Int = (options.frameDelayMs / 10).coerceIn(2, 65535)

    val out = ByteSink(1 shl 18)
    out.text("GIF89a")
    out.leShort(width)
    out.leShort(height)
    // Global table, 8 bits of colour resolution, unsorted, 256 entries.
    out.byte(0xF7)
    out.byte(0)
    out.byte(0)
    out.bytes(globalPalette())

    if (options.loop) {
        // NETSCAPE2.0, the extension every viewer reads a loop count out of.
        out.byte(0x21)
        out.byte(0xFF)
        out.byte(11)
        out.text("NETSCAPE2.0")
        out.byte(3)
        out.byte(1)
        out.leShort(0)
        out.byte(0)
    }

    for (frame in frames) {
        out.byte(0x21)
        out.byte(0xF9)
        out.byte(4)
        // Disposal 1 (leave the frame up), no user input, no transparency.
        out.byte(0x04)
        out.leShort(delay)
        out.byte(0)
        out.byte(0)

        out.byte(0x2C)
        out.leShort(0)
        out.leShort(0)
        out.leShort(frame.width)
        out.leShort(frame.height)
        // No local table, not interlaced.
        out.byte(0)
        out.byte(8)
        out.bytes(subBlocked(lzw(quantised(frame))))
        out.byte(0)
    }

    out.byte(0x3B)
    return out.toByteArray()
}

/** The 6x7x6 cube, then greys in the four entries the cube leaves over. */
private fun globalPalette(): ByteArray {
    val table = ByteArray(256 * 3)
    for (red in 0 until RedLevels) {
        for (green in 0 until GreenLevels) {
            for (blue in 0 until BlueLevels) {
                val index: Int = (red * GreenLevels + green) * BlueLevels + blue
                table[index * 3] = level(red, RedLevels).toByte()
                table[index * 3 + 1] = level(green, GreenLevels).toByte()
                table[index * 3 + 2] = level(blue, BlueLevels).toByte()
            }
        }
    }

    val used: Int = RedLevels * GreenLevels * BlueLevels
    for (index in used until 256) {
        val grey: Int = (index - used) * 255 / (255 - used)
        table[index * 3] = grey.toByte()
        table[index * 3 + 1] = grey.toByte()
        table[index * 3 + 2] = grey.toByte()
    }
    return table
}

/** Level [index] of [levels], spread across the full 0..255 range. */
private fun level(index: Int, levels: Int): Int = index * 255 / (levels - 1)

/** The frame as one palette index per pixel, dithered on the way down. */
private fun quantised(frame: RasterFrame): ByteArray {
    val indices = ByteArray(frame.width * frame.height)
    for (y in 0 until frame.height) {
        for (x in 0 until frame.width) {
            val at: Int = y * frame.width + x
            val pixel: Int = frame.argb[at]
            // Whatever is transparent sits over black, as it does everywhere
            // else here: the slide is dark, and GIF's one transparent index is
            // worth more spent on nothing than on a slide's corners.
            val alpha: Int = (pixel ushr 24) and 0xFF
            val threshold: Int = BayerMatrix[(y % 4) * 4 + x % 4]
            val red: Int = ((pixel shr 16) and 0xFF) * alpha / 255
            val green: Int = ((pixel shr 8) and 0xFF) * alpha / 255
            val blue: Int = (pixel and 0xFF) * alpha / 255

            val redIndex: Int = dithered(red, RedLevels, threshold)
            val greenIndex: Int = dithered(green, GreenLevels, threshold)
            val blueIndex: Int = dithered(blue, BlueLevels, threshold)
            indices[at] = ((redIndex * GreenLevels + greenIndex) * BlueLevels + blueIndex).toByte()
        }
    }
    return indices
}

/**
 * Which level of [levels] a channel lands on, with the dither's [threshold]
 * nudging it up or down by up to half a step.
 *
 * That nudge is the whole trick: a value halfway between two levels goes up in
 * half the pixels around it and down in the other half, so the pair reads as the
 * colour that is not in the palette.
 */
private fun dithered(value: Int, levels: Int, threshold: Int): Int {
    val step: Int = 255 / (levels - 1)
    val nudged: Int = value + (threshold - 8) * step / 16
    return (nudged.coerceIn(0, 255) * (levels - 1) + 127) / 255
}

/** GIF's LZW: 8-bit codes in, variable-width codes out, packed low bit first. */
private fun lzw(indices: ByteArray): ByteArray {
    val clear = 256
    val end = 257
    val out = ByteSink(indices.size / 2 + 64)
    var accumulator = 0
    var bits = 0
    var codeSize = 9

    fun emit(code: Int) {
        accumulator = accumulator or (code shl bits)
        bits += codeSize
        while (bits >= 8) {
            out.byte(accumulator and 0xFF)
            accumulator = accumulator ushr 8
            bits -= 8
        }
    }

    val dictionary = HashMap<Int, Int>(4096)
    var next = 258
    emit(clear)
    if (indices.isEmpty()) {
        emit(end)
        if (bits > 0) out.byte(accumulator and 0xFF)
        return out.toByteArray()
    }

    var prefix: Int = indices[0].toInt() and 0xFF
    for (at in 1 until indices.size) {
        val byte: Int = indices[at].toInt() and 0xFF
        val key: Int = (prefix shl 8) or byte
        val known: Int? = dictionary[key]
        if (known != null) {
            prefix = known
            continue
        }

        emit(prefix)
        dictionary[key] = next
        next++
        // The decoder's table runs exactly one entry behind this one: it only
        // learns an entry once it has read the code *after* the one that made
        // it. So the width goes up one entry later than the table filling would
        // suggest, and a reader that widened in step with the table would read
        // the following code at the wrong size. That is off-by-one enough to
        // corrupt every frame past the 255th code, so it is worth the sentence.
        if (next == 4096) {
            emit(clear)
            dictionary.clear()
            next = 258
            codeSize = 9
        } else if (next > (1 shl codeSize)) {
            codeSize++
        }
        prefix = byte
    }

    emit(prefix)
    emit(end)
    if (bits > 0) out.byte(accumulator and 0xFF)
    return out.toByteArray()
}

/** [data] as GIF sub-blocks: at most 255 bytes each, behind a length byte. */
private fun subBlocked(data: ByteArray): ByteArray {
    val out = ByteSink(data.size + data.size / 255 + 8)
    var at = 0
    while (at < data.size) {
        val length: Int = minOf(255, data.size - at)
        out.byte(length)
        out.bytes(data.copyOfRange(at, at + length))
        at += length
    }
    return out.toByteArray()
}

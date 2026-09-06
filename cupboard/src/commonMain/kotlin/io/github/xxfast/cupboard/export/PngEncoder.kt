package io.github.xxfast.cupboard.export

/**
 * A [RasterFrame] as a PNG file, written by hand.
 *
 * Pure Kotlin, so every target has it: the Skia rasteriser can encode a PNG of
 * its own, but the writers in this package cannot reach Skia, and the HTML player
 * and the PPTX media need bytes on whatever target built the deck. Eight-bit
 * RGBA, no interlacing, every scanline on filter 0, and the pixel data through
 * [zlibStored], which is why the file is about as large as its pixels: fidelity
 * over size, and a PNG is read the same either way.
 *
 * There is no JPEG here and there will not be one until something in the graph
 * can encode it: a baseline JPEG encoder is a DCT and a Huffman coder, which is a
 * great deal more code than a stored-block PNG, and every consumer of these
 * exports reads PNG.
 */
fun encodePng(frame: RasterFrame): ByteArray {
    val raw = ByteArray(frame.height * (1 + frame.width * 4))
    var at = 0
    for (y in 0 until frame.height) {
        // Filter 0 (None) for every scanline: filtering only pays off with a
        // compressor behind it, and there is not one.
        raw[at++] = 0
        for (x in 0 until frame.width) {
            val pixel: Int = frame.argb[y * frame.width + x]
            raw[at++] = ((pixel shr 16) and 0xFF).toByte()
            raw[at++] = ((pixel shr 8) and 0xFF).toByte()
            raw[at++] = (pixel and 0xFF).toByte()
            raw[at++] = ((pixel ushr 24) and 0xFF).toByte()
        }
    }

    val out = ByteSink(raw.size + 256)
    out.bytes(PngSignature)

    val header = ByteSink(13)
    header.beInt(frame.width)
    header.beInt(frame.height)
    header.byte(8)
    // Colour type 6: truecolour with alpha.
    header.byte(6)
    header.byte(0)
    header.byte(0)
    header.byte(0)
    out.chunk("IHDR", header.toByteArray())

    out.chunk("IDAT", zlibStored(raw))
    out.chunk("IEND", ByteArray(0))
    return out.toByteArray()
}

/** The eight bytes every PNG opens with. */
private val PngSignature: ByteArray = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
)

/** One PNG chunk: length, type, data, and the CRC over the type and data. */
private fun ByteSink.chunk(type: String, data: ByteArray) {
    val typed: ByteArray = type.encodeToByteArray()
    beInt(data.size)
    bytes(typed)
    bytes(data)
    beInt(crc32(typed + data))
}

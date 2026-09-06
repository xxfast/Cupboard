package io.github.xxfast.cupboard.export

/**
 * A growable byte buffer, which common Kotlin has none of.
 *
 * Every writer in this package builds a binary file a few bytes at a time, and a
 * `MutableList<Byte>` boxes every one of them. This is the same thing over a
 * plain array that doubles when it fills, plus the field orders the formats are
 * written in: big-endian for PNG and PDF, little-endian for GIF and ZIP.
 */
internal class ByteSink(initial: Int = 64) {
    private var buffer: ByteArray = ByteArray(initial.coerceAtLeast(16))

    /** How many bytes have been written, which is also the next one's offset. */
    var size: Int = 0
        private set

    fun byte(value: Int) {
        grow(1)
        buffer[size++] = value.toByte()
    }

    fun bytes(data: ByteArray) {
        grow(data.size)
        data.copyInto(buffer, size)
        size += data.size
    }

    /** [value] as UTF-8, which is what every text format here is written in. */
    fun text(value: String) {
        bytes(value.encodeToByteArray())
    }

    /** Big-endian 32 bits: PNG chunk lengths and CRCs, and zlib's Adler. */
    fun beInt(value: Int) {
        byte(value ushr 24)
        byte(value ushr 16)
        byte(value ushr 8)
        byte(value)
    }

    /** Little-endian 16 bits: GIF's screen and image descriptors, ZIP's headers. */
    fun leShort(value: Int) {
        byte(value)
        byte(value ushr 8)
    }

    /** Little-endian 32 bits: ZIP's sizes, CRCs and offsets. */
    fun leInt(value: Int) {
        byte(value)
        byte(value ushr 8)
        byte(value ushr 16)
        byte(value ushr 24)
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun grow(extra: Int) {
        if (size + extra <= buffer.size) return
        var capacity: Int = buffer.size * 2
        while (capacity < size + extra) capacity *= 2
        buffer = buffer.copyOf(capacity)
    }
}

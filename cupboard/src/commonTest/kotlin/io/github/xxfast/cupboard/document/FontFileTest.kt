package io.github.xxfast.cupboard.document

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The font file reader against sfnt files built byte by byte here: a table
 * directory and the three tables it reads, and nothing a real font would need
 * to draw.
 */
class FontFileTest {
    private class Name(val platform: Int, val language: Int, val id: Int, val text: String)

    private fun windows(id: Int, text: String, language: Int = 0x0409) = Name(3, language, id, text)

    private fun mac(id: Int, text: String) = Name(1, 0, id, text)

    private fun ByteArrayBuilder.u16(value: Int) {
        add((value shr 8) and 0xFF)
        add(value and 0xFF)
    }

    private fun ByteArrayBuilder.u32(value: Long) {
        u16(((value shr 16) and 0xFFFF).toInt())
        u16((value and 0xFFFF).toInt())
    }

    private class ByteArrayBuilder {
        val bytes: MutableList<Byte> = mutableListOf()
        val size: Int get() = bytes.size

        fun add(value: Int) {
            bytes += value.toByte()
        }

        fun addAll(more: ByteArray) {
            bytes += more.toList()
        }

        fun build(): ByteArray = bytes.toByteArray()
    }

    private fun encode(name: Name): ByteArray = when (name.platform) {
        1 -> name.text.encodeToByteArray()
        else -> ByteArrayBuilder().apply { name.text.forEach { u16(it.code) } }.build()
    }

    private fun nameTable(vararg names: Name): ByteArray {
        val strings: List<ByteArray> = names.map { encode(it) }
        return ByteArrayBuilder().apply {
            u16(0)
            u16(names.size)
            u16(6 + 12 * names.size)
            var offset = 0
            names.forEachIndexed { index, name ->
                u16(name.platform)
                u16(if (name.platform == 3) 1 else 0)
                u16(name.language)
                u16(name.id)
                u16(strings[index].size)
                u16(offset)
                offset += strings[index].size
            }
            strings.forEach { addAll(it) }
        }.build()
    }

    private fun os2(weight: Int, italic: Boolean): ByteArray = ByteArray(78).also { table ->
        table[4] = (weight shr 8).toByte()
        table[5] = weight.toByte()
        table[63] = if (italic) 1 else 0
    }

    private fun head(italic: Boolean): ByteArray = ByteArray(54).also { table ->
        table[45] = if (italic) 2 else 0
    }

    /** A font file with [tables] in order, their offsets counted from [base] (a collection's own header). */
    private fun sfnt(
        vararg tables: Pair<String, ByteArray>,
        version: Long = 0x00010000,
        base: Int = 0,
    ): ByteArray = ByteArrayBuilder().apply {
        u32(version)
        u16(tables.size)
        u16(0)
        u16(0)
        u16(0)
        var offset: Int = base + 12 + 16 * tables.size
        for ((tag, table) in tables) {
            tag.forEach { add(it.code) }
            u32(0)
            u32(offset.toLong())
            u32(table.size.toLong())
            offset += table.size
        }
        tables.forEach { (_, table) -> addAll(table) }
    }.build()

    @Test
    fun readsTheTypographicFamilyWeightAndSlant() {
        val file: ByteArray = sfnt(
            "OS/2" to os2(weight = 300, italic = true),
            "name" to nameTable(windows(1, "Inter Light"), windows(16, "Inter")),
        )

        assertEquals(FontInfo("Inter", 300, true), parseFontInfo(file))
    }

    @Test
    fun fallsBackToTheLegacyFamily() {
        val file: ByteArray = sfnt("name" to nameTable(windows(2, "Regular"), windows(1, "Fira Code")))

        assertEquals(FontInfo("Fira Code", 400, false), parseFontInfo(file))
    }

    @Test
    fun prefersWindowsEnglishThenMac() {
        val french: ByteArray = sfnt(
            "name" to nameTable(mac(1, "Mac Name"), windows(1, "Nom", language = 0x040C), windows(1, "English Name")),
        )
        assertEquals("English Name", parseFontInfo(french)?.family)

        val macOnly: ByteArray = sfnt("name" to nameTable(mac(1, "Mac Name")))
        assertEquals("Mac Name", parseFontInfo(macOnly)?.family)
    }

    @Test
    fun takesTheSlantFromHeadWithoutAnOs2() {
        val file: ByteArray = sfnt(
            "head" to head(italic = true),
            "name" to nameTable(windows(1, "Old Style")),
        )

        assertEquals(FontInfo("Old Style", 400, true), parseFontInfo(file))
    }

    @Test
    fun readsOpenTypeAndTheFirstFaceOfACollection() {
        val otf: ByteArray = sfnt("name" to nameTable(windows(1, "Source Serif")), version = 0x4F54544F)
        assertEquals("Source Serif", parseFontInfo(otf)?.family)

        // ttcf, version 1.0, one face, at 16: right after this header.
        val face: ByteArray = sfnt("name" to nameTable(windows(1, "First Face")), base = 16)
        val collection: ByteArray = ByteArrayBuilder().apply {
            "ttcf".forEach { add(it.code) }
            u16(1)
            u16(0)
            u32(1)
            u32(16)
            addAll(face)
        }.build()
        assertEquals("First Face", parseFontInfo(collection)?.family)
    }

    @Test
    fun refusesWoffAndAFileWithNoFamily() {
        val woff: ByteArray = sfnt("name" to nameTable(windows(1, "Web")), version = 0x774F4646)
        assertNull(parseFontInfo(woff))

        val nameless: ByteArray = sfnt("OS/2" to os2(400, false))
        assertNull(parseFontInfo(nameless))

        val blank: ByteArray = sfnt("name" to nameTable(windows(1, "   ")))
        assertNull(parseFontInfo(blank))
    }

    @Test
    fun everyTruncationIsNullRatherThanACrash() {
        val file: ByteArray = sfnt(
            "OS/2" to os2(weight = 700, italic = false),
            "name" to nameTable(windows(16, "Inter")),
        )

        for (length in 0 until file.size) {
            assertNull(parseFontInfo(file.copyOf(length)), "cut at $length")
        }
        assertEquals(FontInfo("Inter", 700, false), parseFontInfo(file))
    }

    @Test
    fun garbageNeverThrows() {
        val random = Random(seed = 7)
        repeat(2_000) {
            parseFontInfo(random.nextBytes(random.nextInt(0, 256)))
        }

        // Garbage that starts out looking like a font: a real header over noise.
        val header: ByteArray = sfnt("name" to nameTable(windows(1, "Inter"))).copyOf(12)
        repeat(2_000) {
            parseFontInfo(header + random.nextBytes(random.nextInt(0, 512)))
        }

        assertNull(parseFontInfo(ByteArray(0)))
        assertNull(parseFontInfo("not a font at all".encodeToByteArray()))
        val farCollection = byteArrayOf(0x74, 0x74, 0x63, 0x66, 0, 1, 0, 0, 0, 0, 0, 1, 0x7F, -1, -1, -1)
        assertNull(parseFontInfo(farCollection))
    }
}

package io.github.xxfast.cupboard.document

/**
 * What a font file says about itself: the family it belongs to, and the weight
 * and slant of the cut it holds. What a [DeckFont] is made of.
 */
data class FontInfo(
    val family: String,
    val weight: Int,
    val italic: Boolean,
)

/**
 * The family, weight and slant of a TrueType or OpenType file (`.ttf`, `.otf`),
 * or of the first face in a collection (`.ttc`). Null for anything else,
 * WOFF and WOFF2 included, and for a file too broken to say.
 *
 * Plain arithmetic over the sfnt table directory rather than a font engine, so
 * the core can answer it on every target, mingwX64 included. Three tables are
 * read and nothing is drawn:
 * - `name` for the family: the typographic family (nameID 16) where there is
 *   one, since that is what groups a Light and a Bold under one name, and the
 *   legacy family (nameID 1) otherwise. Windows English first, then Mac, then
 *   whatever record is left.
 * - `OS/2` for the weight (`usWeightClass`) and the slant (`fsSelection` bit 0).
 * - `head` for the slant (`macStyle` bit 1) on a file with no `OS/2`.
 *
 * Every read is bounds-checked against the bytes: this parses files users pick,
 * and a truncated or hostile one is a null, never a crash.
 */
fun parseFontInfo(bytes: ByteArray): FontInfo? {
    val file = SfntBytes(bytes)
    val start: Int = when (file.u32(0)) {
        TagTtcf -> file.u32(12)?.toIntOrNull() ?: return null
        else -> 0
    }

    return file.face(start)
}

private const val TagTtcf: Long = 0x74746366 // "ttcf"
private const val TagTrueType: Long = 0x00010000
private const val TagOtto: Long = 0x4F54544F // "OTTO"
private const val TagTrue: Long = 0x74727565 // "true"
private const val TagName: Long = 0x6E616D65 // "name"
private const val TagOs2: Long = 0x4F532F32 // "OS/2"
private const val TagHead: Long = 0x68656164 // "head"

private const val NameTypographicFamily: Int = 16
private const val NameFamily: Int = 1

private const val PlatformUnicode: Int = 0
private const val PlatformMac: Int = 1
private const val PlatformWindows: Int = 3

/** No real font has this many tables; past it, the count is garbage. */
private const val MaxTables: Int = 512

/** A table's span in the file, already checked to lie inside it. */
private class Table(val offset: Int, val length: Int)

/** A name record, with its string's span already checked to lie inside the file. */
private class NameRecord(
    val platform: Int,
    val language: Int,
    val nameId: Int,
    val offset: Int,
    val length: Int,
)

private class SfntBytes(private val bytes: ByteArray) {
    fun u8(at: Int): Int? = if (at in bytes.indices) bytes[at].toInt() and 0xFF else null

    fun u16(at: Int): Int? {
        if (at < 0 || at > bytes.size - 2) return null
        return ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
    }

    fun u32(at: Int): Long? {
        if (at < 0 || at > bytes.size - 4) return null
        val high: Long = u16(at)?.toLong() ?: return null
        val low: Long = u16(at + 2)?.toLong() ?: return null
        return (high shl 16) or low
    }

    fun face(start: Int): FontInfo? {
        val version: Long = u32(start) ?: return null
        if (version != TagTrueType && version != TagOtto && version != TagTrue) return null

        val tables: Map<Long, Table> = tables(start) ?: return null
        val family: String = tables[TagName]?.let { family(it) } ?: return null
        val os2: Table? = tables[TagOs2]
        val head: Table? = tables[TagHead]

        val weight: Int = os2?.let { u16(it.offset + 4) }
            ?.takeIf { it in 1..1000 }
            ?: 400

        // fsSelection sits 62 bytes in, which every OS/2 version reaches; a table
        // cut shorter than that says nothing about the slant, so head does.
        val italic: Boolean = os2?.takeIf { it.length >= 64 }
            ?.let { u16(it.offset + 62) }
            ?.let { it and 0x1 != 0 }
            ?: head?.takeIf { it.length >= 46 }
                ?.let { u16(it.offset + 44) }
                ?.let { it and 0x2 != 0 }
            ?: false

        return FontInfo(family, weight, italic)
    }

    private fun tables(start: Int): Map<Long, Table>? {
        val count: Int = u16(start + 4) ?: return null
        if (count == 0 || count > MaxTables) return null

        return buildMap {
            for (index in 0 until count) {
                val record: Int = start + 12 + index * 16
                val tag: Long = u32(record) ?: return null
                val offset: Long = u32(record + 8) ?: return null
                val length: Long = u32(record + 12) ?: return null
                if (offset + length > bytes.size) continue
                put(tag, Table(offset.toInt(), length.toInt()))
            }
        }
    }

    private fun family(table: Table): String? {
        val count: Int = u16(table.offset + 2) ?: return null
        val strings: Int = table.offset + (u16(table.offset + 4) ?: return null)
        val end: Int = table.offset + table.length

        val records: List<NameRecord> = buildList {
            for (index in 0 until count) {
                val at: Int = table.offset + 6 + index * 12
                if (at + 12 > end) break
                val length: Int = u16(at + 8) ?: break
                val offset: Int = strings + (u16(at + 10) ?: break)
                if (offset + length > bytes.size) continue
                add(
                    NameRecord(
                        platform = u16(at) ?: break,
                        language = u16(at + 4) ?: break,
                        nameId = u16(at + 6) ?: break,
                        offset = offset,
                        length = length,
                    ),
                )
            }
        }

        return listOf(NameTypographicFamily, NameFamily).firstNotNullOfOrNull { id ->
            val named: List<NameRecord> = records.filter { it.nameId == id }
            preferred(named).firstNotNullOfOrNull { record ->
                decode(record)?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
    }

    /** [records] in the order worth trying them: Windows English, any Windows, Mac English, the rest. */
    private fun preferred(records: List<NameRecord>): List<NameRecord> = records.sortedBy { record ->
        when {
            record.platform == PlatformWindows && record.language == 0x0409 -> 0
            record.platform == PlatformWindows && record.language and 0xFF == 0x09 -> 1
            record.platform == PlatformWindows -> 2
            record.platform == PlatformMac && record.language == 0 -> 3
            record.platform == PlatformUnicode -> 4
            else -> 5
        }
    }

    /**
     * The record's string. Windows and Unicode names are UTF-16BE; Mac ones are
     * Mac Roman, read here as Latin-1, which is exact for the ASCII nearly every
     * family name is written in.
     */
    private fun decode(record: NameRecord): String? = when (record.platform) {
        PlatformWindows, PlatformUnicode -> buildString {
            for (at in record.offset until record.offset + record.length - 1 step 2) {
                append(Char(u16(at) ?: return null))
            }
        }

        else -> buildString {
            for (at in record.offset until record.offset + record.length) {
                append(Char(u8(at) ?: return null))
            }
        }
    }
}

private fun Long.toIntOrNull(): Int? = if (this in 0..Int.MAX_VALUE) toInt() else null

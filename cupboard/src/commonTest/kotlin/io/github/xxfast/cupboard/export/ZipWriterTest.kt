package io.github.xxfast.cupboard.export

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ZipWriterTest {
    private val entries = listOf(
        ZipEntry("first.txt", "the first entry".encodeToByteArray()),
        ZipEntry("folder/second.bin", ByteArray(300) { it.toByte() }),
    )

    @Test
    fun itOpensOnALocalFileHeader() {
        val archive: ByteArray = zipArchive(entries)
        assertEquals(0x04034B50, archive.leInt(0))
        // Stored, so the two sizes agree and the bytes are the bytes.
        assertEquals(0, archive.leShort(8), "method 0")
        assertEquals(entries[0].bytes.size, archive.leInt(18))
        assertEquals(entries[0].bytes.size, archive.leInt(22))
    }

    @Test
    fun everyEntryComesBackOut() {
        val read: Map<String, ByteArray> = readStoredZip(zipArchive(entries))
        assertEquals(setOf("first.txt", "folder/second.bin"), read.keys)
        for (entry in entries) assertContentEquals(entry.bytes, read[entry.path])
    }

    @Test
    fun theCentralDirectoryCountsWhatIsInIt() {
        val archive: ByteArray = zipArchive(entries)
        val end: Int = archive.size - 22
        assertEquals(0x06054B50, archive.leInt(end))
        assertEquals(2, archive.leShort(end + 8), "entries on this disk")
        assertEquals(2, archive.leShort(end + 10), "entries in all")

        val directory: Int = archive.leInt(end + 16)
        assertEquals(0x02014B50, archive.leInt(directory))
        assertEquals(archive.leInt(end + 12), end - directory, "the directory's own size")
    }

    @Test
    fun eachEntryCarriesItsChecksum() {
        val archive: ByteArray = zipArchive(entries)
        assertEquals(crc32(entries[0].bytes), archive.leInt(14))

        val second: Int = 30 + "first.txt".length + entries[0].bytes.size
        assertEquals(0x04034B50, archive.leInt(second))
        assertEquals(crc32(entries[1].bytes), archive.leInt(second + 14))
    }

    @Test
    fun anEmptyArchiveIsStillOne() {
        val archive: ByteArray = zipArchive(emptyList())
        assertEquals(22, archive.size)
        assertEquals(0x06054B50, archive.leInt(0))
    }
}

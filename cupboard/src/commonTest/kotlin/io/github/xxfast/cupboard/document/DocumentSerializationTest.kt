package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What a `.cupboard` bundle's `document.json` promises across versions: this
 * build reads every deck it has ever written, and refuses, out loud, one it
 * cannot.
 */
class DocumentSerializationTest {
    @Test
    fun writesTheFormatVersionAndReadsItBack() {
        val json: String = sampleDocument().encodeToString()

        assertTrue(json.contains("\"formatVersion\": $CURRENT_FORMAT_VERSION"), json.take(120))
        val loaded = assertIs<DocumentLoad.Loaded>(decodeDocument(json))
        assertEquals(CURRENT_FORMAT_VERSION, loaded.document.formatVersion)
    }

    @Test
    fun readsAVersionlessDeckAsVersionOne() {
        // Every deck written before the field existed. `ignoreUnknownKeys` is
        // what carries new fields forwards; this is the other direction.
        val json = """{ "id": "doc", "name": "Old", "slides": [] }"""

        val loaded = assertIs<DocumentLoad.Loaded>(decodeDocument(json))
        assertEquals(1, loaded.document.formatVersion)
        assertEquals("Old", loaded.document.name)
    }

    @Test
    fun refusesADeckFromANewerCupboard() {
        // Named rather than half-decoded: the fields this build recognises may
        // well parse, and be wrong.
        val json = """{ "formatVersion": 99, "id": "doc", "name": "Future", "slides": [] }"""

        assertEquals(DocumentLoad.TooNew(99), decodeDocument(json))
    }

    @Test
    fun reportsGarbageAsCorruptRatherThanThrowing() {
        assertIs<DocumentLoad.Corrupt>(decodeDocument("not a deck at all"))
        assertIs<DocumentLoad.Corrupt>(decodeDocument("[]"))
        // A version it can read, holding something it cannot.
        assertIs<DocumentLoad.Corrupt>(decodeDocument("""{ "formatVersion": 1, "slides": 7 }"""))
    }
}

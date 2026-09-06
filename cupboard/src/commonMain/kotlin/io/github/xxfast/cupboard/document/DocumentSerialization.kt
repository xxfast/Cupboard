package io.github.xxfast.cupboard.document

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The shape of `document.json` this build writes, and the newest it can read.
 *
 * Version 1 is every deck there has ever been: the field is new, and a file
 * without it is a version 1 file. See [Document.formatVersion] for when to bump.
 */
const val CURRENT_FORMAT_VERSION: Int = 1

val DocumentJson: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Document.encodeToString(): String = DocumentJson.encodeToString(Document.serializer(), this)

/**
 * What came back from reading a deck.
 *
 * Three outcomes rather than a document-or-throw, because the two failures want
 * different words in front of the user: a deck from a newer Cupboard is a
 * "update to open this" and a mangled one is a "this file is damaged". Callers
 * that genuinely cannot fail (tests over their own JSON) can still say so by
 * matching [Loaded] and failing on anything else.
 */
sealed interface DocumentLoad {
    data class Loaded(val document: Document) : DocumentLoad

    /** Written by a Cupboard newer than this one. [version] is the file's. */
    data class TooNew(val version: Int) : DocumentLoad

    /** Not a Cupboard document, or no longer one. [reason] is for the log, not the user. */
    data class Corrupt(val reason: String) : DocumentLoad
}

/**
 * Reads a deck, refusing one from the future.
 *
 * The version is peeked off a lenient element parse before the real decode, so
 * a newer format is named rather than half-decoded into whatever this build's
 * fields happen to match. `ignoreUnknownKeys` makes forward-compatible fields
 * free; the version is for the changes that are not.
 */
fun decodeDocument(json: String): DocumentLoad {
    val root: JsonObject = try {
        DocumentJson.parseToJsonElement(json).jsonObject
    } catch (e: SerializationException) {
        return DocumentLoad.Corrupt(e.message ?: "not a JSON object")
    } catch (e: IllegalArgumentException) {
        return DocumentLoad.Corrupt(e.message ?: "not a JSON object")
    }

    // Absent means version 1: every deck written before the field existed. So
    // does nonsense in the slot, which the decode below then rejects as corrupt.
    val version: Int = (root[FORMAT_VERSION_KEY] as? JsonPrimitive)?.intOrNull ?: 1
    if (version > CURRENT_FORMAT_VERSION) return DocumentLoad.TooNew(version)

    return try {
        DocumentLoad.Loaded(DocumentJson.decodeFromJsonElement(Document.serializer(), root))
    } catch (e: SerializationException) {
        DocumentLoad.Corrupt(e.message ?: "could not be read as a document")
    } catch (e: IllegalArgumentException) {
        DocumentLoad.Corrupt(e.message ?: "could not be read as a document")
    }
}

private const val FORMAT_VERSION_KEY: String = "formatVersion"

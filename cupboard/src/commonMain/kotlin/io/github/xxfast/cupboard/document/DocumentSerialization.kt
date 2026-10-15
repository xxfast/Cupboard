package io.github.xxfast.cupboard.document

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The shape of `document.json` this build writes, and the newest it can read.
 *
 * Version 1 is every deck written before the field existed: a file without it
 * is a version 1 file. Version 2 is the first with a [LineElement], `"type":
 * "line"`, which an older build would reject as corrupt; the version turns that
 * into "update to open this" instead. Version 1 lines are shapes, and reading one
 * rewrites them (see [migrateLegacyLines]). Version 3 stores a shadow as an
 * offset along an angle with an opacity of its own, where version 2 stored a
 * step and folded the opacity into the colour; an older build would read the
 * new one as its default shadow, so reading a version 2 one rewrites it (see
 * [migrateLegacyShadows]). See [Document.formatVersion] for when to bump.
 */
const val CURRENT_FORMAT_VERSION: Int = 3

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
        // Migrated, the deck is a current one, and is stamped as such: saved
        // under its old number, the lines it now holds would reach an older
        // build that reads them as corrupt rather than as too new.
        val lines: JsonElement = migrateLegacyLines(root)
        val migrated: JsonElement =
            if (version < SHADOW_ANGLE_VERSION) migrateLegacyShadows(lines) else lines
        val document: Document = DocumentJson.decodeFromJsonElement(Document.serializer(), migrated)
        DocumentLoad.Loaded(document.copy(formatVersion = CURRENT_FORMAT_VERSION))
    } catch (e: SerializationException) {
        DocumentLoad.Corrupt(e.message ?: "could not be read as a document")
    } catch (e: IllegalArgumentException) {
        DocumentLoad.Corrupt(e.message ?: "could not be read as a document")
    }
}

private const val FORMAT_VERSION_KEY: String = "formatVersion"

/** The first version whose shadows are an offset along an angle rather than a step. */
private const val SHADOW_ANGLE_VERSION: Int = 3

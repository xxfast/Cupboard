package io.github.xxfast.cupboard.document

import kotlinx.serialization.json.Json

val DocumentJson: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Document.encodeToString(): String = DocumentJson.encodeToString(Document.serializer(), this)

fun decodeDocument(json: String): Document = DocumentJson.decodeFromString(Document.serializer(), json)

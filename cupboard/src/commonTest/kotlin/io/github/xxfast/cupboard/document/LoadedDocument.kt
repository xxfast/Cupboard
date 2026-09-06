package io.github.xxfast.cupboard.document

import kotlin.test.fail

/**
 * The document out of a [decodeDocument], for the tests that are about what a
 * deck decodes into rather than about the three ways loading can end.
 *
 * A failure names what came back instead, so a test that accidentally writes an
 * unreadable deck says so rather than dying on a cast.
 */
fun loadedDocument(json: String): Document = when (val load: DocumentLoad = decodeDocument(json)) {
    is DocumentLoad.Loaded -> load.document
    else -> fail("expected a loaded document, got $load")
}

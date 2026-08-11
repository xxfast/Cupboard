package io.github.xxfast.cupboard.document

import io.github.xxfast.kstore.Codec
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.storeOf

/**
 * A document store that never leaves memory.
 *
 * The editor always persists, so every host has to hand it somewhere to write.
 * Hosts with no document file of their own (the iOS and web preview shells) and
 * tests that care about the editor rather than the bytes use this: it behaves
 * like a real store and forgets everything when the process ends.
 */
fun inMemoryDocumentStore(initial: Document? = null): KStore<Document> =
    storeOf(codec = InMemoryDocumentCodec(initial), default = initial)

private class InMemoryDocumentCodec(private var stored: Document?) : Codec<Document> {
    override suspend fun encode(value: Document?) { stored = value }
    override suspend fun decode(): Document? = stored
}

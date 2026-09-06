package io.github.xxfast.cupboard.document

/**
 * The bytes a deck owns but does not carry: images, movies, fonts.
 *
 * The document model holds asset *ids*, never bytes, so a deck stays a JSON file
 * you can read. This is the other half: where those ids resolve. Inside a
 * `.cupboard` bundle it is the `assets/` folder, but nothing above it knows that,
 * which is what lets the preview shells and every test run on memory alone.
 *
 * Ids are `<newId()>.<ext>` and are chosen by whoever writes: the extension is a
 * hint for the reader (and for anyone poking at the bundle in Finder), never
 * something this looks at. Ids are opaque to the store, so `id` must be one path
 * segment, no separators.
 */
interface AssetStore {
    /** The bytes under [id], or null if nothing is there. Never throws for a missing asset. */
    suspend fun read(id: String): ByteArray?

    /** Writes [bytes] under [id], replacing whatever was there. */
    suspend fun write(id: String, bytes: ByteArray)

    /** Removes [id]. A no-op if it was not there. */
    suspend fun delete(id: String)

    /** Every id currently held, in no particular order. */
    suspend fun ids(): List<String>
}

/**
 * An asset store that never leaves memory.
 *
 * What the hosts with no bundle of their own (the iOS and web preview shells)
 * and the tests that care about the editor rather than the bytes get: it behaves
 * like a real store and forgets everything when the process ends. The in-memory
 * counterpart of [inMemoryDocumentStore].
 */
class InMemoryAssetStore : AssetStore {
    private val assets: MutableMap<String, ByteArray> = mutableMapOf()

    override suspend fun read(id: String): ByteArray? = assets[id]

    override suspend fun write(id: String, bytes: ByteArray) {
        assets[id] = bytes
    }

    override suspend fun delete(id: String) {
        assets.remove(id)
    }

    override suspend fun ids(): List<String> = assets.keys.toList()
}

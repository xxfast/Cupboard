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
 * Deletes every asset [json], a deck's `document.json`, does not name, and
 * returns the ids that went.
 *
 * Named is the id turning up anywhere in the text, see [namedIn], not a walk
 * over the model finding it: a walk loses the user's bytes the day someone adds
 * a field that holds an asset and forgets it here, and a scan of the text
 * cannot. An id that only turns up in a slide's words is a file kept for
 * nothing, which is the cheap way to be wrong.
 *
 * For a deck being opened and nowhere else. Not on a save: undo lives in
 * memory, so an image deleted, swept and then undone is an element with nothing
 * behind it. Not on a reload: a tool rewriting a bundle may put its assets down
 * before the deck that names them.
 */
suspend fun AssetStore.sweep(json: String): List<String> {
    val unnamed: List<String> = ids().filterNot(namedIn(json))
    for (id in unnamed) delete(id)

    return unnamed
}

/**
 * Whether an asset id is one [json] names: what [sweep] keeps, and what a copy
 * of the deck takes with it.
 *
 * Wrong only ever towards yes. Case is ignored, since the volumes a bundle
 * sits on mostly ignore it too. And an id is only looked for when every writer
 * of JSON spells it as it is: letters, digits, `.`, `-`, `_` and spaces. Any
 * other is named whatever the text says, because an escaped `\u00e9` in the
 * text and the accented letter on disk are one asset and nothing here can tell.
 */
internal fun namedIn(json: String): (String) -> Boolean {
    val text: String = json.lowercase()

    fun named(id: String): Boolean {
        val verbatim: Boolean = id.all { char ->
            char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in ".-_ "
        }

        return !verbatim || id.lowercase() in text
    }

    return ::named
}

/**
 * An asset store that never leaves memory.
 *
 * What the hosts with no bundle of their own (the iOS and web preview shells)
 * and the tests that care about the editor rather than the bytes get: it behaves
 * like a real store and forgets everything when the process ends. The in-memory
 * counterpart of [inMemoryDocumentStore].
 *
 * [initial] is what it holds before anything is written: the showcase's bytes,
 * for a preview shell that opens on the showcase.
 */
class InMemoryAssetStore(initial: Map<String, ByteArray> = emptyMap()) : AssetStore {
    private val assets: MutableMap<String, ByteArray> = initial.toMutableMap()

    override suspend fun read(id: String): ByteArray? = assets[id]

    override suspend fun write(id: String, bytes: ByteArray) {
        assets[id] = bytes
    }

    override suspend fun delete(id: String) {
        assets.remove(id)
    }

    override suspend fun ids(): List<String> = assets.keys.toList()
}

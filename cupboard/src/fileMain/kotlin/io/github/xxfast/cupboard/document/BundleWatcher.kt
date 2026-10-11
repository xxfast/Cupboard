package io.github.xxfast.cupboard.document

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemPathSeparator
import kotlin.time.Duration.Companion.milliseconds

/**
 * How long a burst of writes sits before it is read: tools write in bursts, and a
 * regenerated bundle touches many files.
 */
private val ReloadDebounce = 300.milliseconds

/**
 * Raw change notifications for everything one level under [directory] plus
 * `assets/` beneath it. Cold: the platform watcher starts on collect and is
 * torn down on cancellation. Emits the changed file's path when the platform
 * knows it, or the directory's own path (or the `assets` directory's) when all
 * it knows is that something in there changed. Must not fail when `assets/` is
 * not there yet, and must notice it once it appears.
 */
internal expect fun fileChanges(directory: Path): Flow<Path>

/**
 * [bundle]'s changes from outside, debounced and decoded. See [BundleChange].
 *
 * Paths, not events, are what a burst is made of: kstore alone writes a temp
 * file and moves it over the deck, and a script regenerating the bundle touches
 * every file in it. So a burst is gathered until it has been quiet for a whole
 * [ReloadDebounce], and only then is anything read, once.
 *
 * Our own autosave comes through here too. Telling it apart is the editor's
 * business, not this one's: only the editor knows what it last wrote.
 */
internal fun watchBundle(bundle: Path): Flow<BundleChange> = channelFlow {
    val deck: String = CupboardBundle.documentFile(bundle).toString()
    val assets: String = CupboardBundle.assetsDirectory(bundle).toString()

    // Handed over through a channel rather than gathered by the collector, so
    // a burst is only ever touched from this loop and nothing races it.
    val changes: Channel<Path> = Channel(Channel.UNLIMITED)
    launch { fileChanges(bundle).collect { path -> changes.send(path) } }

    while (true) {
        val batch: MutableSet<String> = mutableSetOf(changes.receive().toString())
        do {
            delay(ReloadDebounce)
            var arrived = false
            while (true) {
                val path: Path = changes.tryReceive().getOrNull() ?: break
                arrived = true
                batch += path.toString()
            }
        } while (arrived)

        // A deck that does not decode is dropped, not reported: mid-burst that is
        // a file still being written, and the write that finishes it is an event
        // of its own. A deck from a newer Cupboard is dropped for the same
        // reason an open refuses it, and the editor keeps what it has.
        if (bundle.toString() in batch || deck in batch) {
            // Gone between the event and the read is a checkout halfway through,
            // and the rest of it is on its way.
            val json: String? = try {
                CupboardBundle.read(bundle)
            } catch (e: IOException) {
                null
            }
            val load: DocumentLoad? = json?.let { decodeDocument(it) }
            if (load is DocumentLoad.Loaded) send(BundleChange.Deck(load.document))
        }

        if (batch.any { it == assets || it.startsWith("$assets$SystemPathSeparator") }) {
            send(BundleChange.Assets)
        }
    }
}
    // A watcher that fails is an editor that stops noticing, not one that
    // stops: this flow is merged into the presenter's events, so an exception
    // here would take the whole loop down with it.
    .catch { }
    .flowOn(Dispatchers.Default)

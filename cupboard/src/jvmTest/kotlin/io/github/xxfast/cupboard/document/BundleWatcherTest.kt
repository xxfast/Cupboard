package io.github.xxfast.cupboard.document

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The watcher against a real bundle on a real filesystem, since what it is for is
 * what the platform reports. Generous timeouts throughout: on macOS the JDK's
 * watch service is a poller, every two seconds at its most eager.
 */
class BundleWatcherTest {
    @Test
    fun aRewrittenDeckArrivesDecoded() = runBlocking {
        val bundle = bundle()
        val arrived = Channel<BundleChange>(Channel.UNLIMITED)
        val watching = watch(bundle, arrived)

        val rewritten = Document(id = "doc", name = "Rewritten")
        writeDeck(bundle, rewritten.encodeToString())

        assertEquals(rewritten, arrived.awaitDeck().document)
        watching.cancel()
    }

    /**
     * Mid-write is the normal case for a file another tool is producing, so a deck
     * that does not decode is not news. The write that finishes it is.
     */
    @Test
    fun aDeckThatDoesNotDecodeIsDroppedUntilOneThatDoesArrives() = runBlocking {
        val bundle = bundle()
        val arrived = Channel<BundleChange>(Channel.UNLIMITED)
        val watching = watch(bundle, arrived)

        writeDeck(bundle, "{ \"slides\": [ this is not")
        assertNull(withTimeoutOrNull(1500.milliseconds) { arrived.receive() })

        val whole = Document(id = "doc", name = "Whole")
        writeDeck(bundle, whole.encodeToString())

        assertEquals(whole, arrived.awaitDeck().document)
        watching.cancel()
    }

    @Test
    fun anAssetWriteArrivesAsAssets() = runBlocking {
        val bundle = bundle()
        val arrived = Channel<BundleChange>(Channel.UNLIMITED)
        val watching = watch(bundle, arrived)

        writeAsset(bundle, "hero.png")

        arrived.awaitAssets()
        watching.cancel()
    }

    /** A bundle laid down before it had any bytes gets its `assets/` later. */
    @Test
    fun anAssetsFolderThatAppearsLaterIsWatchedToo() = runBlocking {
        val bundle = bundle()
        SystemFileSystem.delete(Path(bundle, "assets"))
        val arrived = Channel<BundleChange>(Channel.UNLIMITED)
        val watching = watch(bundle, arrived)

        writeAsset(bundle, "hero.png")
        arrived.awaitAssets()

        writeAsset(bundle, "second.png")
        arrived.awaitAssets()
        watching.cancel()
    }

    private fun bundle(): Path {
        val bundle = Path(createTempDirectory("cupboard-watcher").toString(), "Talk.cupboard")
        CupboardBundle.create(bundle, Document(id = "doc", name = "Talk"))
        return bundle
    }

    /**
     * Starts watching [bundle] into [arrived], and gives the platform watcher a
     * moment to register: a write that beats it there was never going to be seen.
     */
    private suspend fun CoroutineScope.watch(bundle: Path, arrived: Channel<BundleChange>): Job {
        val job = launch(Dispatchers.Default) { watchBundle(bundle).collect { arrived.send(it) } }
        delay(1.seconds)
        return job
    }

    private fun writeDeck(bundle: Path, json: String) {
        SystemFileSystem.sink(Path(bundle, "document.json")).buffered().use { it.writeString(json) }
    }

    private fun writeAsset(bundle: Path, id: String) {
        SystemFileSystem.createDirectories(Path(bundle, "assets"))
        SystemFileSystem.sink(Path(bundle, "assets", id)).buffered().use { it.write(byteArrayOf(1, 2, 3)) }
    }

    private suspend fun Channel<BundleChange>.awaitDeck(): BundleChange.Deck =
        withTimeout(15.seconds) { receiveAsFlow().first { it is BundleChange.Deck } as BundleChange.Deck }

    private suspend fun Channel<BundleChange>.awaitAssets() {
        withTimeout(15.seconds) { receiveAsFlow().first { it == BundleChange.Assets } }
    }
}

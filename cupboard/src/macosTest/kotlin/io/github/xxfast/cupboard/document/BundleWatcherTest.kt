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
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The GCD watcher against a real bundle on a real filesystem, the JVM test's
 * cases plus the one only per-file watches catch: an asset rewritten where it
 * stands, which its folder never hears about.
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

    /** The way kstore saves: a temp file moved over the deck. */
    @Test
    fun aDeckMovedOverTheOldOneArrivesDecoded() = runBlocking {
        val bundle = bundle()
        val arrived = Channel<BundleChange>(Channel.UNLIMITED)
        val watching = watch(bundle, arrived)

        val first = Document(id = "doc", name = "First")
        writeDeckAtomically(bundle, first.encodeToString())
        assertEquals(first, arrived.awaitDeck().document)

        // In place this time, onto the file the move left: only heard if the
        // watch moved over to it too.
        val second = Document(id = "doc", name = "Second")
        writeDeck(bundle, second.encodeToString())
        assertEquals(second, arrived.awaitDeck().document)
        watching.cancel()
    }

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

    @Test
    fun anAssetRewrittenInPlaceArrivesAsAssets() = runBlocking {
        val bundle = bundle(assets = mapOf("hero.png" to byteArrayOf(1, 2, 3)))
        val arrived = Channel<BundleChange>(Channel.UNLIMITED)
        val watching = watch(bundle, arrived)

        writeAsset(bundle, "hero.png", byteArrayOf(4, 5, 6))

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

    private fun bundle(assets: Map<String, ByteArray> = emptyMap()): Path {
        val folder = Path(SystemTemporaryDirectory, "cupboard-watcher-${Random.nextLong().toULong()}")
        val bundle = Path(folder, "Talk.cupboard")
        CupboardBundle.create(bundle, Document(id = "doc", name = "Talk"), assets)
        return bundle
    }

    /**
     * Starts watching [bundle] into [arrived], and gives the watches a moment to
     * open: a write that beats them there was never going to be seen.
     */
    private suspend fun CoroutineScope.watch(bundle: Path, arrived: Channel<BundleChange>): Job {
        val job = launch(Dispatchers.Default) { watchBundle(bundle).collect { arrived.send(it) } }
        delay(500.milliseconds)
        return job
    }

    private fun writeDeck(bundle: Path, json: String) {
        SystemFileSystem.sink(Path(bundle, "document.json")).buffered().use { it.writeString(json) }
    }

    private fun writeDeckAtomically(bundle: Path, json: String) {
        val temp = Path(bundle, "document.json.temp")
        SystemFileSystem.sink(temp).buffered().use { it.writeString(json) }
        SystemFileSystem.atomicMove(temp, Path(bundle, "document.json"))
    }

    private fun writeAsset(bundle: Path, id: String, bytes: ByteArray = byteArrayOf(1, 2, 3)) {
        SystemFileSystem.createDirectories(Path(bundle, "assets"))
        SystemFileSystem.sink(Path(bundle, "assets", id)).buffered().use { it.write(bytes) }
    }

    private suspend fun Channel<BundleChange>.awaitDeck(): BundleChange.Deck =
        withTimeout(10.seconds) { receiveAsFlow().first { it is BundleChange.Deck } as BundleChange.Deck }

    private suspend fun Channel<BundleChange>.awaitAssets() {
        withTimeout(10.seconds) { receiveAsFlow().first { it == BundleChange.Assets } }
    }
}

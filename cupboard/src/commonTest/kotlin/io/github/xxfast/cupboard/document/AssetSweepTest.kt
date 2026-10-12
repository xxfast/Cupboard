package io.github.xxfast.cupboard.document

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [sweep] against a deck's text: what the text does not name goes, and anything
 * it might name stays. The second half is the one that matters, since what goes
 * is the user's bytes.
 */
class AssetSweepTest {
    @Test
    fun removesWhatTheTextDoesNotNameAndSaysSo() = runTest {
        val kept = byteArrayOf(1, 2, 3)
        val store = InMemoryAssetStore(
            mapOf(
                "kept.png" to kept,
                "poster.jpg" to byteArrayOf(4),
                "stray.png" to byteArrayOf(5),
                "old.mov" to byteArrayOf(6),
            ),
        )
        val json = """{"slides":[{"assetId":"kept.png","posterAssetId":"poster.jpg"}]}"""

        val removed: List<String> = store.sweep(json)

        assertEquals(setOf("stray.png", "old.mov"), removed.toSet())
        assertEquals(setOf("kept.png", "poster.jpg"), store.ids().toSet())
        assertContentEquals(kept, store.read("kept.png"))
    }

    @Test
    fun anEmptyStoreIsLeftAlone() = runTest {
        val store = InMemoryAssetStore()

        assertTrue(store.sweep("""{"assetId":"hero.png"}""").isEmpty())
        assertTrue(store.ids().isEmpty())
    }

    @Test
    fun aDeckThatNamesEverythingLosesNothing() = runTest {
        val store = InMemoryAssetStore(mapOf("a.png" to byteArrayOf(1), "b.mov" to byteArrayOf(2)))

        assertTrue(store.sweep("""["a.png","b.mov"]""").isEmpty())
        assertEquals(setOf("a.png", "b.mov"), store.ids().toSet())
    }

    @Test
    fun anIdOnlyInASlidesWordsIsKept() = runTest {
        // The cheap way to be wrong: a scan cannot tell a reference from a
        // sentence, and keeping a file for nothing costs a file.
        val store = InMemoryAssetStore(mapOf("hero.png" to byteArrayOf(1)))

        assertTrue(store.sweep("""{"text":"see hero.png for the picture"}""").isEmpty())
    }

    @Test
    fun anIdTheTextMaySpellAnotherWayIsNeverSwept() = runTest {
        // What a writer that escapes would have put for each of these is not
        // the id: `caf\u00e9.png`, `tom \u0026 jerry.png`, `say \"hi\".png`.
        // The deck still opens them, so they are not ours to take.
        val ids: List<String> = listOf("caf\u00e9.png", "tom & jerry.png", "say \"hi\".png")
        val store = InMemoryAssetStore(ids.associateWith { byteArrayOf(1) })
        val json = """{"a":"caf\u00e9.png","b":"tom \u0026 jerry.png","c":"say \"hi\".png"}"""

        assertTrue(store.sweep(json).isEmpty())
        assertEquals(ids.toSet(), store.ids().toSet())
    }

    @Test
    fun caseIsNotWhatUnnamesAnAsset() = runTest {
        // `Hero.PNG` on disk answers to `hero.png` on the volumes decks live on.
        val store = InMemoryAssetStore(mapOf("Hero.PNG" to byteArrayOf(1)))

        assertTrue(store.sweep("""{"assetId":"hero.png"}""").isEmpty())
    }
}

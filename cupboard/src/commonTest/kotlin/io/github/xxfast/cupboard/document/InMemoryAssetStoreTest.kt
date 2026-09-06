package io.github.xxfast.cupboard.document

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The store the preview shells and most tests run on. Tested against the same
 * expectations as the file-backed one, so the two stay swappable.
 */
class InMemoryAssetStoreTest {
    @Test
    fun readsBackWhatItWrote() = runTest {
        val store = InMemoryAssetStore()
        val bytes = byteArrayOf(1, 2, 3, 4)

        store.write("a.png", bytes)

        assertContentEquals(bytes, store.read("a.png"))
    }

    @Test
    fun hasNothingUnderAnIdItWasNeverGiven() = runTest {
        assertNull(InMemoryAssetStore().read("missing.png"))
    }

    @Test
    fun replacesOnASecondWrite() = runTest {
        val store = InMemoryAssetStore()

        store.write("a.png", byteArrayOf(1))
        store.write("a.png", byteArrayOf(9, 9))

        assertContentEquals(byteArrayOf(9, 9), store.read("a.png"))
        assertEquals(listOf("a.png"), store.ids())
    }

    @Test
    fun listsAndForgetsIds() = runTest {
        val store = InMemoryAssetStore()
        store.write("a.png", byteArrayOf(1))
        store.write("b.jpg", byteArrayOf(2))

        assertEquals(setOf("a.png", "b.jpg"), store.ids().toSet())

        store.delete("a.png")

        assertEquals(listOf("b.jpg"), store.ids())
        assertNull(store.read("a.png"))
        // Deleting what isn't there is a no-op, not a failure: the editor
        // dropping an element it already dropped must not be a crash.
        store.delete("a.png")
    }
}

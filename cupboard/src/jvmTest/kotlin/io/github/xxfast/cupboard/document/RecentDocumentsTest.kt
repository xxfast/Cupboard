package io.github.xxfast.cupboard.document

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The Open Recent menu's list, over real files: what it drops is decided by
 * what is on disk, so a fake filesystem would be testing the fake.
 */
class RecentDocumentsTest {
    @Test
    fun putsTheNewestFirst() = runTest {
        val directory: Path = tempDirectory()
        val recents = recents(directory)
        val one: Path = bundle(directory, "One")
        val two: Path = bundle(directory, "Two")

        recents.remember(one)
        recents.remember(two)

        assertEquals(listOf(two.toString(), one.toString()), recents.list())

        // Opening one again moves it up rather than listing it twice.
        recents.remember(one)
        assertEquals(listOf(one.toString(), two.toString()), recents.list())
    }

    @Test
    fun keepsTenAtMost() = runTest {
        val directory: Path = tempDirectory()
        val recents = recents(directory)
        val bundles: List<Path> = (1..12).map { bundle(directory, "Deck $it") }

        bundles.forEach { recents.remember(it) }

        val listed: List<String> = recents.list()
        assertEquals(10, listed.size)
        assertEquals(bundles.last().toString(), listed.first())
        assertFalse(bundles.first().toString() in listed, "the oldest deck was kept")
    }

    @Test
    fun forgetsWhatItIsToldTo() = runTest {
        val directory: Path = tempDirectory()
        val recents = recents(directory)
        val one: Path = bundle(directory, "One")
        val two: Path = bundle(directory, "Two")
        recents.remember(one)
        recents.remember(two)

        recents.forget(one)
        assertEquals(listOf(two.toString()), recents.list())

        // A deck that was never in the list is not an error.
        recents.forget(bundle(directory, "Three"))
        assertEquals(listOf(two.toString()), recents.list())
    }

    @Test
    fun skipsDecksThatAreNoLongerThere() = runTest {
        val directory: Path = tempDirectory()
        val recents = recents(directory)
        val here: Path = bundle(directory, "Here")
        val gone = Path(directory, "Gone.cupboard")

        recents.remember(here)
        recents.remember(gone)

        assertEquals(listOf(here.toString()), recents.list())

        // Skipped on the way out, not forgotten: a deck on an unplugged drive is
        // back in place, in order, the moment the drive is.
        CupboardBundle.create(gone, Document(name = "Gone"))
        assertEquals(listOf(gone.toString(), here.toString()), recents.list())
    }

    private fun recents(directory: Path): RecentDocuments =
        RecentDocuments(Path(directory, "recents.json"))

    private fun bundle(directory: Path, name: String): Path =
        Path(directory, "$name.cupboard").also { CupboardBundle.create(it, Document(name = name)) }

    private fun tempDirectory(): Path = Path(createTempDirectory("cupboard-recents").toString())
}

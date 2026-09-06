package io.github.xxfast.cupboard.document

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * The decks this machine opened last, most recent first.
 *
 * Paths as strings rather than as `Path`s: this is a list a File menu renders
 * and a shell hands back to `openDocument`, and it is the same list on the
 * hosts that never see a kotlinx-io type. Stored as JSON beside the theme
 * library, see `RECENTS_FILE_NAME`.
 *
 * [remember] is called by the factories rather than by the shells, so a deck
 * opened by a double-click in Finder lands in the list the same as one picked
 * from the menu.
 */
class RecentDocuments(file: Path) {
    private val store: KStore<List<String>> = storeOf(file = file, default = emptyList())

    /** Puts [path] at the top, moving it there if it was already in the list. */
    suspend fun remember(path: Path) {
        val entry: String = path.toString()
        val kept: List<String> = current().filterNot { it == entry }
        store.set((listOf(entry) + kept).take(LIMIT))
    }

    /** Takes [path] out. A path that was never in the list is a no-op. */
    suspend fun forget(path: Path) {
        val entry: String = path.toString()
        val remembered: List<String> = current()
        if (entry !in remembered) return
        store.set(remembered.filterNot { it == entry })
    }

    /**
     * The list as a menu should show it: newest first, and only decks that are
     * still there.
     *
     * Filtered on read rather than pruned on disk. A deck on an unplugged drive
     * is missing this afternoon and back tomorrow, and a list that forgot it in
     * between would be worse than one that skips a row.
     */
    suspend fun list(): List<String> = current().filter { SystemFileSystem.exists(Path(it)) }

    private suspend fun current(): List<String> = store.get() ?: emptyList()

    private companion object {
        /** As many rows as a File > Open Recent menu wants. Keynote shows ten. */
        const val LIMIT: Int = 10
    }
}

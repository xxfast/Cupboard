package io.github.xxfast.cupboard.document

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.darwin.DISPATCH_SOURCE_TYPE_VNODE
import platform.darwin.DISPATCH_VNODE_ATTRIB
import platform.darwin.DISPATCH_VNODE_DELETE
import platform.darwin.DISPATCH_VNODE_EXTEND
import platform.darwin.DISPATCH_VNODE_RENAME
import platform.darwin.DISPATCH_VNODE_WRITE
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t
import platform.darwin.dispatch_resume
import platform.darwin.dispatch_source_cancel
import platform.darwin.dispatch_source_create
import platform.darwin.dispatch_source_set_cancel_handler
import platform.darwin.dispatch_source_set_event_handler
import platform.darwin.dispatch_source_t
import platform.posix.O_EVTONLY
import platform.posix.close
import platform.posix.fstat
import platform.posix.open
import platform.posix.stat

/**
 * Over Grand Central Dispatch vnode sources rather than FSEvents: they work on
 * iOS as well as macOS, and `appleMain` is both. A vnode source watches one open
 * file, so there is one per thing worth watching: the bundle folder (an entry
 * came, went or was renamed over, which is how an atomic save lands), the deck
 * itself (a write in place), `assets/`, and each file in it, since a folder
 * hears nothing of a file rewritten where it stands.
 *
 * Every event re-checks the lot rather than working out what it meant. A watch
 * whose file was replaced is still on the old inode, a folder that appeared has
 * no watch yet, and one that went has a dead one: looking again is the same
 * answer for all three, and a deck's worth of files is cheap to look at.
 *
 * All of it lives on one serial queue, the one every handler runs on, so the
 * channel is the only thing another thread touches.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun fileChanges(directory: Path): Flow<Path> = callbackFlow {
    val queue: dispatch_queue_t = dispatch_queue_create("io.github.xxfast.cupboard.watcher", null)
    val deck: Path = CupboardBundle.documentFile(directory)
    val assets: Path = CupboardBundle.assetsDirectory(directory)
    val watches: MutableMap<String, Watch> = mutableMapOf()
    var started = false
    var closed = false

    fun unwatch(key: String) {
        watches.remove(key)?.let { watch -> dispatch_source_cancel(watch.source) }
    }

    /**
     * Watches [path] for [mask], keeping the watch there is while it is on the
     * same file. True only when a watch was opened, which for a folder that was
     * not there before is news of its own.
     */
    fun watch(path: Path, mask: ULong, onEvent: () -> Unit): Boolean {
        if (closed) return false
        val key: String = path.toString()
        val fd: Int = open(key, O_EVTONLY)
        if (fd < 0) {
            unwatch(key)
            return false
        }

        val inode: ULong? = inodeOf(fd)
        if (inode != null && watches[key]?.inode == inode) {
            close(fd)
            return false
        }

        unwatch(key)
        val source: dispatch_source_t =
            dispatch_source_create(DISPATCH_SOURCE_TYPE_VNODE, fd.toULong(), mask, queue)
        if (source == null) {
            close(fd)
            return false
        }

        dispatch_source_set_event_handler(source, onEvent)
        // The only moment the descriptor is safe to close: the source may still
        // be reading it right up until its cancellation lands.
        dispatch_source_set_cancel_handler(source) { close(fd) }
        watches[key] = Watch(source, inode)
        dispatch_resume(source)
        return true
    }

    fun refresh() {
        watch(directory, FOLDER_EVENTS) {
            trySend(directory)
            refresh()
        }

        watch(deck, FILE_EVENTS) {
            trySend(deck)
            refresh()
        }

        val opened: Boolean = watch(assets, FOLDER_EVENTS) {
            trySend(assets)
            refresh()
        }
        // `assets/` turning up is something landing in it, and whatever was
        // written there before this watch was open was never going to be heard.
        if (opened && started) trySend(assets)

        val files: Collection<Path> = try {
            SystemFileSystem.list(assets)
        } catch (e: IOException) {
            emptyList()
        }
        val present: Set<String> = files.map { it.toString() }.toSet()
        watches.keys
            .filter { key -> Path(key).parent?.toString() == assets.toString() && key !in present }
            .forEach { key -> unwatch(key) }

        for (file in files) {
            // Past the budget the folder's own watch still hears files come and
            // go; only a rewrite in place goes unnoticed, and only for these.
            if (file.toString() !in watches && watches.size >= MAX_WATCHES) break
            watch(file, FILE_EVENTS) {
                trySend(file)
                refresh()
            }
        }
    }

    dispatch_async(queue) {
        refresh()
        started = true
    }

    awaitClose {
        dispatch_async(queue) {
            closed = true
            watches.values.forEach { watch -> dispatch_source_cancel(watch.source) }
            watches.clear()
        }
    }
}

private class Watch(val source: dispatch_source_t, val inode: ULong?)

/** Which file [fd] is open on, so a path that now names another one can be told apart. */
@OptIn(ExperimentalForeignApi::class)
private fun inodeOf(fd: Int): ULong? = memScoped {
    val info: stat = alloc<stat>()
    if (fstat(fd, info.ptr) == 0) info.st_ino else null
}

/** Something in a folder came, went or was renamed over, or the folder itself went. */
private val FOLDER_EVENTS: ULong =
    (DISPATCH_VNODE_WRITE or DISPATCH_VNODE_DELETE or DISPATCH_VNODE_RENAME).toULong()

/** [FOLDER_EVENTS], plus a file growing or being truncated or touched in place. */
private val FILE_EVENTS: ULong =
    FOLDER_EVENTS or (DISPATCH_VNODE_EXTEND or DISPATCH_VNODE_ATTRIB).toULong()

/**
 * Descriptors one bundle may hold open. A deck has tens of assets, not
 * thousands, and the process limit is shared with everything else open.
 */
private const val MAX_WATCHES = 256

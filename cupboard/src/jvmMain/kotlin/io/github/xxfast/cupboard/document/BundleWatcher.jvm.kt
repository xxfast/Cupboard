package io.github.xxfast.cupboard.document

import com.sun.nio.file.SensitivityWatchEventModifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.io.files.Path
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.nio.file.Path as JavaPath

/**
 * Over [WatchService], which is the Linux and Windows desktop route's native
 * watcher and, on macOS, a poller: see [watching] for why it is told to hurry.
 *
 * Polled rather than taken, a quarter second at a time, because `take()` blocks
 * a thread that cancellation cannot reach, and a closed editor must not leave
 * one behind.
 */
internal actual fun fileChanges(directory: Path): Flow<Path> = flow {
    val root: JavaPath = Paths.get(directory.toString())
    val assets: JavaPath = Paths.get(CupboardBundle.assetsDirectory(directory).toString())
    val service: WatchService = FileSystems.getDefault().newWatchService()
    val watched: MutableMap<WatchKey, JavaPath> = mutableMapOf()

    fun watch(folder: JavaPath) {
        watched[folder.watching(service)] = folder
    }

    try {
        watch(root)
        if (Files.isDirectory(assets)) watch(assets)

        while (true) {
            currentCoroutineContext().ensureActive()
            val key: WatchKey = service.poll(POLL_MILLIS, MILLISECONDS) ?: continue
            val folder: JavaPath = watched[key] ?: continue

            for (event in key.pollEvents()) {
                // Too much at once for the queue: all that is left to say is
                // that something in this folder changed.
                if (event.kind() == OVERFLOW) {
                    emit(Path(folder.toString()))
                    continue
                }

                val changed: JavaPath = folder.resolve(event.context() as JavaPath)
                // A bundle laid down before its first asset gets `assets/` later,
                // and whatever lands in it before it is watched is covered by
                // the folder's own path going out below.
                if (changed == assets && Files.isDirectory(assets) && assets !in watched.values) {
                    watch(assets)
                }
                emit(Path(changed.toString()))
            }

            // A folder that went away takes its key with it; one that comes back
            // is registered afresh when its creation is seen.
            if (!key.reset()) watched.remove(key)
        }
    } finally {
        service.close()
    }
}.flowOn(Dispatchers.IO)

/**
 * Registers this folder with [service] for everything that can happen in it.
 *
 * macOS has no native backend for [WatchService], so the JDK polls, every ten
 * seconds by default: far too slow for a deck you are editing in another window.
 * [SensitivityWatchEventModifier.HIGH] brings that to two. It lives in
 * `jdk.unsupported`, so a runtime without that module registers plainly and
 * just notices later.
 */
private fun JavaPath.watching(service: WatchService): WatchKey {
    val kinds: Array<WatchEvent.Kind<*>> = arrayOf(ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
    return try {
        register(service, kinds, SensitivityWatchEventModifier.HIGH)
    } catch (e: Throwable) {
        register(service, *kinds)
    }
}

private const val POLL_MILLIS: Long = 250

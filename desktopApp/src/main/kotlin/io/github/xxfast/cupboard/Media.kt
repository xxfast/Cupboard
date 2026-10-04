package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.canvas.MediaPlayerHost
import io.github.xxfast.cupboard.canvas.MediaRequest
import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.FileAssetStore
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import io.github.xxfast.cupboard.screens.editor.MediaKind
import io.github.xxfast.cupboard.screens.editor.MovieExtensions
import io.github.xxfast.cupboard.screens.editor.SoundExtensions
import io.github.xxfast.cupboard.screens.editor.insertMovie
import io.github.xxfast.cupboard.screens.editor.insertSound
import io.github.xxfast.cupboard.screens.editor.mediaKind
import io.github.xxfast.cupboard.screens.editor.replaceMedia
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineEvent
import kotlin.math.log10
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** What this file inserts as, by its name alone. See [mediaKind]. */
internal fun File.mediaKind(): MediaKind? = mediaKind(extension)

/**
 * A native open panel filtered to movies or to sounds by [kind], and the file
 * picked in it. [title] is the panel's, since Insert and Replace both ask.
 *
 * [chooseImage]'s panel otherwise, for its reasons: AWT's, the filter set both
 * ways so Windows sees it too. Null is a cancel.
 */
internal fun chooseMedia(owner: Frame, kind: MediaKind, title: String): File? {
    val extensions: List<String> = if (kind == MediaKind.Movie) MovieExtensions else SoundExtensions
    val dialog = FileDialog(owner, title, FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> File(name).extension.lowercase() in extensions }
    dialog.file = extensions.joinToString(";") { "*.$it" }
    dialog.isVisible = true

    val directory: String = dialog.directory ?: return null
    val name: String = dialog.file ?: return null
    return File(directory, name).takeIf { it.isFile }
}

/**
 * The files a drop hands over, as [URI]s, in the order they were dragged. One
 * that doesn't parse is skipped rather than failing the drop: a selection
 * dragged out of a browser can carry all sorts.
 */
internal fun droppedFiles(uris: List<String>): List<File> = uris.mapNotNull { uri ->
    runCatching { File(URI(uri)) }.getOrNull()
}

/**
 * The files on the system clipboard, which is what a Finder or Explorer copy
 * puts there. Empty when there are none, and when the clipboard is busy.
 */
internal fun clipboardFiles(): List<File> {
    val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
    if (!clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) return emptyList()

    val files: List<*> = runCatching { clipboard.getData(DataFlavor.javaFileListFlavor) }
        .getOrNull() as? List<*> ?: return emptyList()
    return files.filterIsInstance<File>()
}

/**
 * [file] on the selected slide as a movie or a sound, by its extension, named
 * after it. False is a file of neither kind, or one that could not be read.
 *
 * Read off the main thread: a movie is the one asset big enough for the read
 * itself to stall a frame.
 */
internal suspend fun EditorViewModel.insertMediaFile(file: File): Boolean {
    val kind: MediaKind = file.mediaKind() ?: return false
    if (kind == MediaKind.Image) return false

    val bytes: ByteArray = readMedia(file) ?: return false
    if (kind == MediaKind.Movie) insertMovie(bytes, file.extension, file.name)
    else insertSound(bytes, file.extension, file.name)
    return true
}

/** [element] pointed at [file]'s bytes: the inspector's Replace. */
internal suspend fun EditorViewModel.replaceMediaFile(element: Element, file: File): Boolean {
    val bytes: ByteArray = readMedia(file) ?: return false
    return replaceMedia(element, bytes, file.extension)
}

private suspend fun readMedia(file: File): ByteArray? = withContext(Dispatchers.IO) {
    runCatching { file.readBytes() }.getOrNull()
}

/**
 * What plays a show's movies and sounds on the JVM, which has a sound API and no
 * movie codec.
 *
 * So a sound `javax.sound` can decode (wav, aiff, au) plays in-process on a
 * [Clip], trimmed, looped and at its volume, and stops when the slide does.
 * Everything else, every movie and the compressed sounds, opens in the OS's own
 * player, and a web video in the browser: those are out of the show's hands once
 * opened, so [stop] has nothing to stop for them.
 *
 * Every call lands on one worker thread, in order. That keeps a decode off the
 * show's frame, and it is what makes a [stop] right behind a [play] stop the clip
 * that play started rather than run before it. Nothing here throws back into the
 * composition: a failure is a sound that does not play, logged.
 */
internal class DesktopMediaPlayer(private val assets: AssetStore) : MediaPlayerHost, AutoCloseable {
    private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "Cupboard media").apply { isDaemon = true }
    }

    /** The clips playing, by element. Only ever touched on [worker]. */
    private val clips: MutableMap<String, Clip> = HashMap()

    override fun play(request: MediaRequest) = submit { start(request) }

    override fun stop(elementId: String) = submit { clips.remove(elementId)?.close() }

    override fun stopAll() = submit {
        clips.values.forEach(Clip::close)
        clips.clear()
    }

    override fun close() {
        stopAll()
        worker.shutdown()
    }

    private fun submit(task: () -> Unit) {
        try {
            worker.execute {
                try {
                    task()
                } catch (error: Exception) {
                    println("Cupboard: could not play media ($error)")
                }
            }
        } catch (_: RejectedExecutionException) {
            // Closed: the window this player belonged to is gone.
        }
    }

    private fun start(request: MediaRequest) {
        clips.remove(request.elementId)?.close()

        val assetId: String? = request.assetId
        val url: String? = request.webUrl
        if (assetId == null) {
            if (url != null) openInBrowser(url)
            return
        }

        val file: File = fileOf(assetId) ?: return
        if (request.isAudio && playClip(request, file)) return

        if (!Desktop.isDesktopSupported()) return
        val desktop: Desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.OPEN)) desktop.open(file)
    }

    /**
     * Where [assetId]'s bytes are on disk: in the bundle where the deck has one,
     * and a temporary copy where it lives in memory, since a player outside this
     * process can only be handed a file. Null for an asset that isn't there.
     */
    private fun fileOf(assetId: String): File? {
        if (assets is FileAssetStore) return File(assets.pathOf(assetId)).takeIf { it.isFile }

        val bytes: ByteArray = runBlocking { assets.read(assetId) } ?: return null
        return File.createTempFile("cupboard-", "-$assetId").apply {
            deleteOnExit()
            writeBytes(bytes)
        }
    }

    /**
     * [file] on a [Clip], honouring the request's trims, loop and volume. False
     * is a file `javax.sound` will not take, which the caller hands to the OS
     * instead.
     */
    private fun playClip(request: MediaRequest, file: File): Boolean {
        val clip: Clip = try {
            AudioSystem.getAudioInputStream(file).use { stream ->
                AudioSystem.getClip().apply { open(stream) }
            }
        } catch (_: Exception) {
            return false
        }

        val frames: Int = clip.frameLength
        val rate: Float = clip.format.frameRate
        fun frameAt(millis: Int): Int = (millis / 1000.0 * rate).toInt().coerceIn(0, frames)

        val start: Int = frameAt(request.trimStartMs)
        val end: Int = if (request.trimEndMs > 0) frameAt(request.trimEndMs) else frames
        // A trim past the end of the sound: taken, and nothing to play.
        if (end <= start) {
            clip.close()
            return true
        }

        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            val gain: FloatControl = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            val decibels: Float =
                if (request.volume <= 0f) gain.minimum
                else 20f * log10(request.volume)
            gain.value = decibels.coerceIn(gain.minimum, gain.maximum)
        }

        val id: String = request.elementId
        clips[id] = clip

        // A clip that runs out on its own is let go of here, back on the worker.
        // One stopped through [stop] has already left [clips], so this passes it.
        clip.addLineListener { event ->
            if (event.type == LineEvent.Type.STOP) submit {
                if (clips[id] === clip) clips.remove(id)?.close()
            }
        }

        clip.framePosition = start
        if (request.loop) {
            clip.setLoopPoints(start, end - 1)
            clip.loop(Clip.LOOP_CONTINUOUSLY)
            return true
        }

        clip.start()
        // A clip plays to its own end, so a trimmed end is a stop on a timer.
        if (end < frames) {
            val millis: Long = ((end - start) / rate * 1000).toLong()
            worker.schedule(
                { if (clips[id] === clip) clips.remove(id)?.close() },
                millis,
                TimeUnit.MILLISECONDS,
            )
        }
        return true
    }
}

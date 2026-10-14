@file:OptIn(ExperimentalForeignApi::class)

package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.graphics.asComposeImageBitmap
import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.FileAssetStore
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemVideoOutput
import platform.AVFoundation.AVVideoComposition
import platform.AVFoundation.addOutput
import platform.AVFoundation.asset
import platform.AVFoundation.forwardPlaybackEndTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.tracksWithMediaType
import platform.AVFoundation.videoComposition
import platform.AVFoundation.volume
import platform.AppKit.NSWorkspace
import platform.CoreFoundation.CFRetain
import platform.CoreGraphics.CGAffineTransformIsIdentity
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeMake
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.QuartzCore.CACurrentMediaTime
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/**
 * The macOS show's [MediaPlayerHost]: one AVPlayer per element, and for a movie
 * a video output the canvas reads its pictures off.
 *
 * Nothing of AppKit goes over the show. The canvas asks [picture] once a frame
 * and draws what it gets in the movie's place, so a movie turns with its
 * element, fades with it, and rides every build and transition the slide puts
 * it through, on the same frame as everything around it. And it plays for any
 * view of the show that draws it: the presenter display's current slide, and a
 * rehearsal, whose show is up but out of sight.
 *
 * Main thread only, like the session that owns it.
 */
internal class AVMediaPlayer(private val assets: AssetStore) : MediaPlayerHost {
    /** One element playing: its player, where its pictures come from (none for a sound). */
    private class Playing(
        val player: AVPlayer,
        val pixels: MoviePixels?,
        /** The loop's end-of-item observer, null for a movie that plays once. */
        val ends: NSObjectProtocol?,
    )

    private val playing: MutableMap<String, Playing> = mutableMapOf()

    override fun play(request: MediaRequest) {
        stop(request.elementId)

        val assetId: String? = request.assetId
        if (assetId == null) {
            // The canvas sends a web video through the link handler; this is the
            // same answer for a host that was handed one anyway.
            request.webUrl
                ?.let { NSURL.URLWithString(it) }
                ?.let { NSWorkspace.sharedWorkspace.openURL(it) }
            return
        }

        // A deck with no bundle under it has no movie to play, and a bundle
        // missing the file is a deck that plays its poster.
        val path: String = (assets as? FileAssetStore)?.pathOf(assetId) ?: return
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return

        val item: AVPlayerItem = AVPlayerItem.playerItemWithURL(NSURL.fileURLWithPath(path))
        if (request.trimEndMs > request.trimStartMs) {
            item.forwardPlaybackEndTime = milliseconds(request.trimEndMs)
        }
        val pixels: MoviePixels? = if (request.isAudio) null else MoviePixels(item)
        val player: AVPlayer = AVPlayer.playerWithPlayerItem(item)
        player.volume = request.volume.coerceIn(0f, 1f)

        val start: CValue<CMTime> = milliseconds(request.trimStartMs)
        // The end of the item is the trim's end where there is one: AVFoundation
        // posts this on reaching forwardPlaybackEndTime as well as the real end.
        val ends: NSObjectProtocol? = if (!request.loop) null else {
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = AVPlayerItemDidPlayToEndTimeNotification,
                `object` = item,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> begin(request.elementId, player, start) }
        }

        playing[request.elementId] = Playing(player, pixels, ends)
        begin(request.elementId, player, start)
    }

    override fun stop(elementId: String) {
        val entry: Playing = playing.remove(elementId) ?: return
        entry.ends?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        entry.player.pause()
        entry.player.replaceCurrentItemWithPlayerItem(null)
    }

    override fun stopAll() {
        playing.keys.toList().forEach(::stop)
    }

    override fun picture(elementId: String): MediaPicture? =
        playing[elementId]?.pixels?.latest()

    /**
     * Plays [player] from [start]. A seek first unless the start is the start,
     * and the play only once it lands, so a trimmed movie never shows a frame
     * from before its window. The completion is checked against the element
     * still playing this player: a stop during the seek stays stopped.
     */
    private fun begin(elementId: String, player: AVPlayer, start: CValue<CMTime>) {
        val fromTop: Boolean = start.useContents { value == 0L }
        if (fromTop) {
            player.seekToTime(start)
            player.play()
            return
        }

        val exact: CValue<CMTime> = CMTimeMake(value = 0, timescale = 1)
        player.seekToTime(start, toleranceBefore = exact, toleranceAfter = exact) { finished ->
            dispatch_async(dispatch_get_main_queue()) {
                if (finished && playing[elementId]?.player === player) player.play()
            }
        }
    }
}

/**
 * One movie's pictures, off a video output on its [item], copied into the one
 * bitmap its [MediaPicture] draws.
 *
 * Upright: the item is given a video composition built from its own asset, which
 * is what turns a movie shot on its side the way its track says to. A player
 * layer does that by itself, and a video output hands over the frames as stored.
 */
private class MoviePixels(item: AVPlayerItem) {
    private val output = AVPlayerItemVideoOutput(
        pixelBufferAttributes = mapOf<Any?, Any>(PixelFormatKey to kCVPixelFormatType_32BGRA),
    )

    /** Null until the first frame lands: the canvas draws the poster until then. */
    private var picture: MediaPicture? = null
    private var bitmap: Bitmap? = null

    /** Where a frame is copied on its way into [bitmap], kept from frame to frame. */
    private var bytes: ByteArray = ByteArray(0)

    init {
        // Only for a movie that needs turning: a composition is a render pass
        // on every frame, and one built off an asset with no picture in it is
        // not one AVFoundation will take.
        val track: AVAssetTrack? =
            item.asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
        if (track != null && !CGAffineTransformIsIdentity(track.preferredTransform)) {
            item.videoComposition =
                AVVideoComposition.videoCompositionWithPropertiesOfAsset(item.asset)
        }
        item.addOutput(output)
    }

    /** The picture with the newest frame in it, if the output has one it has not handed over. */
    fun latest(): MediaPicture? {
        val now: CValue<CMTime> = output.itemTimeForHostTime(CACurrentMediaTime())
        if (!output.hasNewPixelBufferForItemTime(now)) return picture

        val buffer: CVPixelBufferRef =
            output.copyPixelBufferForItemTime(now, null) ?: return picture
        try {
            copy(buffer)
        } finally {
            CVPixelBufferRelease(buffer)
        }
        return picture
    }

    private fun copy(buffer: CVPixelBufferRef) {
        CVPixelBufferLockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly)
        try {
            val width: Int = CVPixelBufferGetWidth(buffer).toInt()
            val height: Int = CVPixelBufferGetHeight(buffer).toInt()
            val rowBytes: Int = CVPixelBufferGetBytesPerRow(buffer).toInt()
            val base: CPointer<*> = CVPixelBufferGetBaseAddress(buffer) ?: return
            if (width <= 0 || height <= 0) return

            val size: Int = rowBytes * height
            if (bytes.size != size) bytes = ByteArray(size)
            bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), base, size.toULong()) }

            val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            val current: Bitmap? = bitmap
            val shown: MediaPicture? = picture
            // The same bitmap for as long as the movie keeps its size, redrawn in
            // place; a new one, and a new picture for the canvas to hold, when
            // it changes.
            if (current != null && shown != null &&
                current.width == width && current.height == height
            ) {
                current.installPixels(info, bytes, rowBytes)
                shown.frame++
                return
            }

            val made = Bitmap()
            made.installPixels(info, bytes, rowBytes)
            bitmap = made
            picture = MediaPicture(made.asComposeImageBitmap())
        } finally {
            CVPixelBufferUnlockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly)
        }
    }
}

/** CoreVideo's pixel format key, as the string a Foundation dictionary is keyed by. */
private val PixelFormatKey: Any? = CFBridgingRelease(CFRetain(kCVPixelBufferPixelFormatTypeKey))

/** [ms] as AVFoundation's time, in milliseconds. */
private fun milliseconds(ms: Int): CValue<CMTime> =
    CMTimeMake(value = ms.coerceAtLeast(0).toLong(), timescale = 1000)

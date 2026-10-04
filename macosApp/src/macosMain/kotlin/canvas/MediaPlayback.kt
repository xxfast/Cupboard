@file:OptIn(ExperimentalForeignApi::class)

package io.github.xxfast.cupboard.canvas

import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.FileAssetStore
import io.github.xxfast.cupboard.document.Frame
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.AVFoundation.forwardPlaybackEndTime
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.volume
import platform.AppKit.NSView
import platform.AppKit.NSViewHeightSizable
import platform.AppKit.NSViewWidthSizable
import platform.AppKit.NSWorkspace
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSMakeRect
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSPoint
import platform.Foundation.NSRect
import platform.Foundation.NSSize
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.min

/**
 * The macOS show's [MediaPlayerHost]: one AVPlayer per element, and for a movie
 * a layer laid over the show where the slide draws its poster.
 *
 * The layers live in one [MediaOverlay] added on top of the show's compose view
 * (the [attach]ed surface), never inside the compose scene: the canvas has no
 * idea a movie is decoding over it, which is the deal [MediaPlayerHost] makes.
 * The overlay maps slide units to view points with the same fit-and-centre the
 * show places the slide with (CuP's slide container and `SlideSurface` both fit
 * the slide inside the view and centre it), so a movie lands on its poster.
 *
 * Not applied to the layer: the element's rotation and opacity, and whatever a
 * slide transition is doing to it. A movie plays upright and opaque in its box,
 * which is the case that matters; a tilted movie is a later job.
 *
 * Main thread only, like the session that owns it.
 */
internal class AVMediaPlayer(
    private val assets: AssetStore,
    private val slideWidth: Float,
    private val slideHeight: Float,
) : MediaPlayerHost {
    /** One element playing: its player, its picture (none for a sound), where it sits. */
    private class Playing(
        val player: AVPlayer,
        val view: NSView?,
        val frame: Frame,
        /** The loop's end-of-item observer, null for a movie that plays once. */
        val ends: NSObjectProtocol?,
    )

    private val playing: MutableMap<String, Playing> = mutableMapOf()

    /** What the overlay is added to: the show's own view. Null plays sound only. */
    private var surface: NSView? = null

    private var overlay: MediaOverlay? = null

    /**
     * The view the show draws in. Set after construction rather than passed in,
     * because the show's composition is built with this player already in it.
     */
    fun attach(view: NSView) {
        surface = view
    }

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

        val view: NSView? = if (request.isAudio) null else videoView(player)
        playing[request.elementId] = Playing(player, view, request.frame, ends)
        layout()
        begin(request.elementId, player, start)
    }

    override fun stop(elementId: String) {
        val entry: Playing = playing.remove(elementId) ?: return
        entry.ends?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        entry.player.pause()
        entry.player.replaceCurrentItemWithPlayerItem(null)
        entry.view?.removeFromSuperview()
    }

    override fun stopAll() {
        playing.keys.toList().forEach(::stop)
        overlay?.removeFromSuperview()
        overlay = null
    }

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

    /** A plain view hosting the movie's layer, in the overlay. */
    private fun videoView(player: AVPlayer): NSView? {
        val host: MediaOverlay = overlay() ?: return null
        val layer: AVPlayerLayer = AVPlayerLayer.playerLayerWithPlayer(player)
        layer.videoGravity = AVLayerVideoGravityResizeAspect

        val view = NSView(NSMakeRect(0.0, 0.0, 0.0, 0.0))
        // The layer first, then wantsLayer: a layer-hosting view, so AppKit
        // draws nothing of its own over the picture.
        view.layer = layer
        view.wantsLayer = true
        host.addSubview(view)
        return view
    }

    private fun overlay(): MediaOverlay? {
        overlay?.let { return it }
        val surface: NSView = surface ?: return null
        val made = MediaOverlay(surface.bounds, ::layout)
        made.autoresizingMask = NSViewWidthSizable or NSViewHeightSizable
        surface.addSubview(made)
        overlay = made
        return made
    }

    /**
     * Every movie's view over its element's box, mapped from slide units the
     * way the show maps the slide: scaled to fit, centred. Run on every play and
     * on every resize of the overlay, which follows the show's view.
     */
    private fun layout() {
        val host: MediaOverlay = overlay ?: return
        val (width: Double, height: Double) = host.bounds.useContents { size.width to size.height }
        if (width <= 0.0 || height <= 0.0 || slideWidth <= 0f || slideHeight <= 0f) return

        val scale: Double = min(width / slideWidth, height / slideHeight)
        val left: Double = (width - slideWidth * scale) / 2
        val top: Double = (height - slideHeight * scale) / 2

        // A layer-hosting view's frame moves its layer, which would otherwise
        // animate there implicitly and trail the window edge on a resize.
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for (entry in playing.values) {
            val view: NSView = entry.view ?: continue
            view.frame = NSMakeRect(
                left + entry.frame.x * scale,
                top + entry.frame.y * scale,
                entry.frame.width * scale,
                entry.frame.height * scale,
            )
        }
        CATransaction.commit()
    }
}

/**
 * The layer the movies sit in: the show's size, flipped so slide units read top
 * down the way they do in the document, and transparent to the mouse, so a
 * click on a playing movie is still a click on the slide (a tap to restart it,
 * a link, a step).
 */
private class MediaOverlay(
    frame: CValue<NSRect>,
    private val onResize: () -> Unit,
) : NSView(frame) {
    override fun isFlipped(): Boolean = true

    override fun hitTest(point: CValue<NSPoint>): NSView? = null

    override fun setFrameSize(newSize: CValue<NSSize>) {
        super.setFrameSize(newSize)
        onResize()
    }
}

/** [ms] as AVFoundation's time, in milliseconds. */
private fun milliseconds(ms: Int): CValue<CMTime> =
    CMTimeMake(value = ms.coerceAtLeast(0).toLong(), timescale = 1000)

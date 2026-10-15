@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.xxfast.cupboard.canvas

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.PixelGeometry
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.jetbrains.skia.SurfaceProps
import org.jetbrains.skiko.FrameDispatcher
import org.jetbrains.skiko.SkikoDispatchers
import org.jetbrains.skiko.currentNanoTime
import platform.AppKit.NSView
import platform.AppKit.NSViewFrameDidChangeNotification
import platform.AppKit.NSWindow
import platform.AppKit.NSWindowDidChangeBackingPropertiesNotification
import platform.AppKit.NSWindowDidChangeOcclusionStateNotification
import platform.AppKit.NSWindowOcclusionStateVisible
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Metal.MTLCommandBufferProtocol
import platform.Metal.MTLCommandQueueProtocol
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.Metal.MTLDeviceProtocol
import platform.Metal.MTLPixelFormatBGRA8Unorm
import platform.QuartzCore.CAMetalDrawableProtocol
import platform.QuartzCore.CAMetalLayer
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCAGravityTopLeft
import platform.QuartzCore.kCALayerHeightSizable
import platform.QuartzCore.kCALayerWidthSizable
import platform.darwin.NSObjectProtocol

/**
 * The Metal surface a [ComposeNSView] draws into for one stay in a window. It stands in for
 * skiko's `SkiaLayer` and keeps its frame pacing exactly (same [FrameDispatcher] on the same
 * main queue, same layer set-up, same resize and occlusion handling), for one reason: it
 * owns its [DirectContext] and closes it in [dispose], on main.
 *
 * Skiko's `MacOsMetalRedrawer.dispose()` never closes its context before 0.153, and the
 * Compose we're on pins 0.144, so every detached layer left a whole Metal GPU for the
 * GC's cleaner thread to tear down. Skia's cleaner calls into C++ without leaving the
 * runnable state, and that teardown deallocates dispatch objects, which takes the ObjC
 * weak side-table lock. If main is at the same moment loading a weak reference to a
 * Kotlin object (AppKit's first responder is this view, read on every input-method
 * activation), it holds that lock and parks for the GC. The GC waits for the cleaner,
 * the cleaner waits for main: issue #37.
 */
internal class MetalCanvas(
    private val view: NSView,
    private val onRender: (canvas: Canvas, width: Int, height: Int, nanoTime: Long) -> Unit,
) {
    private val device: MTLDeviceProtocol = MTLCreateSystemDefaultDevice()
        ?: error("Metal is not supported on this system")

    private val queue: MTLCommandQueueProtocol = device.newCommandQueue()
        ?: error("Couldn't create Metal command queue")

    private val context: DirectContext =
        DirectContext.makeMetal(device.objcPtr(), queue.objcPtr())

    private val layer = CAMetalLayer()
    private val window: NSWindow = checkNotNull(view.window) { "MetalCanvas needs a window" }
    private val occlusions = Channel<Boolean>(Channel.CONFLATED)
    private var isOccluded = false
    private var isDisposed = false

    private val frames = FrameDispatcher(SkikoDispatchers.Main) { draw() }

    // Synchronous (no queue), as skiko's were: ComposeNSView posts a frame change right after
    // attaching and expects the bounds synced and a frame drawn before the post returns.
    private val observers: List<NSObjectProtocol> = listOf(
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = NSViewFrameDidChangeNotification,
            `object` = view,
            queue = null,
        ) { _ -> resize() },
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = NSWindowDidChangeBackingPropertiesNotification,
            `object` = window,
            queue = null,
        ) { _ -> resize() },
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = NSWindowDidChangeOcclusionStateNotification,
            `object` = window,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            isOccluded = window.occlusionState and NSWindowOcclusionStateVisible == 0uL
            occlusions.trySend(isOccluded)
        },
    )

    init {
        layer.device = device as objcnames.protocols.MTLDeviceProtocol?
        layer.pixelFormat = MTLPixelFormatBGRA8Unorm
        layer.opaque = false
        layer.framebufferOnly = false
        layer.contentsGravity = kCAGravityTopLeft
        layer.autoresizingMask = kCALayerWidthSizable or kCALayerHeightSizable
        layer.removeAllAnimations()
        view.layer = layer
        view.wantsLayer = true
        view.postsFrameChangedNotifications = true
        syncBounds()
        needRender()
    }

    fun needRender() {
        if (isDisposed) return
        frames.scheduleFrame()
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        frames.cancel()
        observers.forEach(NSNotificationCenter.defaultCenter::removeObserver)
        context.close()
        layer.removeFromSuperlayer()
    }

    private fun resize() {
        if (isDisposed) return
        syncBounds()
        render()
    }

    private fun syncBounds() {
        val scale: Double = window.backingScaleFactor
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        layer.contentsScale = scale
        layer.frame = view.frame
        view.frame.useContents {
            layer.drawableSize = CGSizeMake(size.width * scale, size.height * scale)
        }
        CATransaction.commit()
        CATransaction.flush()
    }

    private suspend fun draw() {
        render()

        // Not worth drawing fast for a window nobody can see; back off unless it shows again.
        if (!isOccluded) return
        withTimeoutOrNull(300) {
            @Suppress("ControlFlowWithEmptyBody")
            while (occlusions.receive()) {}
        }
    }

    private fun render() = autoreleasepool {
        if (isDisposed) return@autoreleasepool
        val scale: Double = window.backingScaleFactor
        val width: Int = view.frame.useContents { size.width * scale }.toInt()
        val height: Int = view.frame.useContents { size.height * scale }.toInt()
        if (width <= 0 || height <= 0) return@autoreleasepool

        val drawable: CAMetalDrawableProtocol = layer.nextDrawable() ?: return@autoreleasepool
        val target: BackendRenderTarget =
            BackendRenderTarget.makeMetal(width, height, drawable.texture.objcPtr())

        val surface: Surface? = Surface.makeFromBackendRenderTarget(
            context,
            target,
            SurfaceOrigin.TOP_LEFT,
            SurfaceColorFormat.BGRA_8888,
            ColorSpace.sRGB,
            SurfaceProps(pixelGeometry = PixelGeometry.UNKNOWN),
        )

        // Closed here whatever happens: one left to the GC drags the context's teardown
        // onto its cleaner thread, which is the deadlock this class exists to avoid.
        try {
            if (surface == null) return@autoreleasepool

            // White under the scene, as skiko's picture recording was.
            surface.canvas.clear(Color.WHITE)
            onRender(surface.canvas, width, height, currentNanoTime())

            // The scene can take us out of the window mid-frame, and then the context is
            // closed and there is nothing left to present to.
            if (isDisposed) return@autoreleasepool
            surface.flushAndSubmit()
            val present: MTLCommandBufferProtocol = queue.commandBuffer() ?: return@autoreleasepool
            present.label = "Present"
            present.presentDrawable(drawable)
            present.commit()
        } finally {
            surface?.close()
            target.close()
        }
    }
}

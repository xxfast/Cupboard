// Adapted from androidx.compose.ui.window.ComposeWindow (compose-multiplatform-core,
// Apache-2.0). That class owns its own NSWindow; this variant is an embeddable NSView
// so a SwiftUI app can host Compose content via NSViewRepresentable.
@file:OptIn(ExperimentalForeignApi::class, InternalComposeUiApi::class)

package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import platform.AppKit.NSEvent
import platform.AppKit.NSEventModifierFlagCommand
import platform.AppKit.NSEventModifierFlagControl
import platform.AppKit.NSEventModifierFlagOption
import platform.AppKit.NSEventModifierFlagShift
import platform.AppKit.NSKeyDown
import platform.AppKit.NSKeyUp
import platform.AppKit.NSTrackingActiveAlways
import platform.AppKit.NSTrackingActiveInKeyWindow
import platform.AppKit.NSTrackingArea
import platform.AppKit.NSTrackingAssumeInside
import platform.AppKit.NSTrackingInVisibleRect
import platform.AppKit.NSTrackingMouseEnteredAndExited
import platform.AppKit.NSTrackingMouseMoved
import platform.AppKit.NSView
import platform.AppKit.NSViewFrameDidChangeNotification
import platform.AppKit.NSWindow
import platform.Foundation.NSMakeRect
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSRect

// Same stub as compose's internal MacosTextInputService: enough for plain key-event
// typing, no NSTextInputClient/IME integration.
private class StubTextInputService : androidx.compose.ui.text.input.PlatformTextInputService {
    private var current: TextFieldValue? = null
    override fun startInput(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit,
    ) { current = value }

    override fun stopInput() { current = null }
    override fun showSoftwareKeyboard() {}
    override fun hideSoftwareKeyboard() {}
    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) { current = newValue }
}

private class MutableWindowInfo : WindowInfo {
    override var isWindowFocused: Boolean by mutableStateOf(true)
    override var containerSize: IntSize by mutableStateOf(IntSize.Zero)
}

/**
 * Compose content as an embeddable NSView, for a SwiftUI host to place via
 * `NSViewRepresentable`.
 *
 * Host it at origin (0,0) in its superview. Skiko's MacOsMetalRedrawer.syncBounds()
 * copies our frame onto the metal layer verbatim, origin included, and that layer is
 * our own backing layer, so any offset shifts the canvas and leaves an unpainted band
 * behind it. The macOS shell hosts us as the full-bleed window content layer, edge to
 * edge under the glass panels, so the origin is (0,0) for free.
 */
class ComposeNSView(
    frame: CValue<NSRect> = NSMakeRect(0.0, 0.0, 640.0, 360.0),
    private val content: @Composable () -> Unit,
) : NSView(frame) {
    private val windowInfo = MutableWindowInfo()
    private val textInputService = StubTextInputService()

    private val platformContext: PlatformContext =
        object : PlatformContext by PlatformContext.Empty() {
            override val windowInfo get() = this@ComposeNSView.windowInfo
            override val textInputService get() = this@ComposeNSView.textInputService
        }

    private val skiaLayer = SkiaLayer()
    private val scene = CanvasLayersComposeScene(
        coroutineContext = Dispatchers.Main,
        platformContext = platformContext,
        invalidate = skiaLayer::needRender,
    )

    private var isAttached = false
    private var isDisposed = false
    private var trackingArea: NSTrackingArea? = null

    init {
        skiaLayer.renderDelegate = object : SkikoRenderDelegate {
            override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
                if (isDisposed) return
                val sizeInPx = IntSize(width, height)
                windowInfo.containerSize = sizeInPx
                scene.size = sizeInPx
                scene.render(canvas.asComposeCanvas(), nanoTime)
            }
        }
        scene.setContent(content)
    }

    override fun wantsUpdateLayer() = true
    override fun acceptsFirstResponder() = true

    // SkiaLayer needs a window-backed view; defer attach until AppKit gives us one.
    override fun viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        if (isDisposed) return
        val window = this.window
        if (window != null && !isAttached) {
            skiaLayer.attachTo(this)
            isAttached = true
        }
        if (window != null) {
            scene.density = Density(window.backingScaleFactor.toFloat())
            window.makeFirstResponder(this)
            // Back in a window, so skiko can safely hear about frames again. The
            // post is the catch-up for whatever it missed while we were detached.
            postsFrameChangedNotifications = true
            if (isAttached) {
                NSNotificationCenter.defaultCenter.postNotificationName(
                    aName = NSViewFrameDidChangeNotification,
                    `object` = this,
                )
            }
        }
    }

    override fun viewDidChangeBackingProperties() {
        super.viewDidChangeBackingProperties()
        if (isDisposed) return
        window?.let { scene.density = Density(it.backingScaleFactor.toFloat()) }
    }

    override fun layout() {
        super.layout()
        if (isDisposed) return
        skiaLayer.needRender()
    }

    override fun viewWillMoveToWindow(newWindow: NSWindow?) {
        super.viewWillMoveToWindow(newWindow)
        // Skiko's frame observer reads nsView.window!! (MacOsMetalRedrawer.syncContentScale),
        // so a frame change while we sit between windows crashes it. SwiftUI does
        // exactly that: re-hosting sets the frame before adding us back, which is
        // what exiting play mode does. Go quiet until we have a window again.
        if (newWindow == null) postsFrameChangedNotifications = false
        updateTrackingAreas()
    }

    override fun updateTrackingAreas() {
        trackingArea?.let { removeTrackingArea(it) }
        trackingArea = null
        if (isDisposed) return
        trackingArea = NSTrackingArea(
            rect = bounds,
            options = NSTrackingActiveAlways or
                NSTrackingMouseEnteredAndExited or
                NSTrackingMouseMoved or
                NSTrackingActiveInKeyWindow or
                NSTrackingAssumeInside or
                NSTrackingInVisibleRect,
            owner = this,
            userInfo = null,
        )
        addTrackingArea(trackingArea!!)
    }

    override fun mouseDown(event: NSEvent) {
        window?.makeFirstResponder(this)
        onMouseEvent(event, PointerEventType.Press, PointerButton.Primary)
    }

    override fun mouseUp(event: NSEvent) = onMouseEvent(event, PointerEventType.Release, PointerButton.Primary)

    /**
     * The press is forwarded with its release in the same breath, because the
     * real rightMouseUp may never arrive: the press pops the context menu, and
     * NSMenu's tracking loop eats the release that dismisses it. A scene left
     * holding the secondary bit reclassifies every later left click as a right
     * click (the canvas reads the chord, not the changed button). The canvas
     * treats a secondary press as a click and never a gesture, so an instant
     * release is the same click to it.
     */
    override fun rightMouseDown(event: NSEvent) {
        onMouseEvent(event, PointerEventType.Press, PointerButton.Secondary)
        onMouseEvent(event, PointerEventType.Release, PointerButton.Secondary)
    }

    /** Usually eaten by the menu; harmless when it does arrive, the scene's
     * secondary bit is already up and a buttonless release moves nothing. */
    override fun rightMouseUp(event: NSEvent) = onMouseEvent(event, PointerEventType.Release, PointerButton.Secondary)
    override fun mouseMoved(event: NSEvent) = onMouseEvent(event, PointerEventType.Move)
    override fun mouseDragged(event: NSEvent) = onMouseEvent(event, PointerEventType.Move)
    override fun scrollWheel(event: NSEvent) = onMouseEvent(event, PointerEventType.Scroll)

    override fun keyDown(event: NSEvent) {
        if (isDisposed) return
        val consumed = scene.sendKeyEvent(event.toComposeEvent())
        if (!consumed) super.keyDown(event)
    }

    override fun keyUp(event: NSEvent) {
        if (isDisposed) return
        scene.sendKeyEvent(event.toComposeEvent())
    }

    /**
     * AppKit keeps delivering to a view after its host drops it: a tracking area
     * still fires `mouseMoved:` while the pointer is over where we used to be, and
     * the scene throws once it's closed. Drop the tracking area and gate every
     * entry point on [isDisposed].
     */
    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        trackingArea?.let { removeTrackingArea(it) }
        trackingArea = null
        postsFrameChangedNotifications = false
        if (isAttached) skiaLayer.detach()
        scene.close()
    }

    private fun onMouseEvent(event: NSEvent, eventType: PointerEventType, button: PointerButton? = null) {
        if (isDisposed) return
        // locationInWindow is bottom-left origin and window-relative; convert to
        // this view's coords and flip to compose's top-left origin.
        val local = convertPoint(event.locationInWindow, fromView = null)
        val (x, y) = local.useContents { x to y }
        val height = bounds.useContents { size.height }
        val density = scene.density.density
        scene.sendPointerEvent(
            eventType = eventType,
            position = Offset((x * density).toFloat(), ((height - y) * density).toFloat()),
            scrollDelta = Offset(event.deltaX.toFloat(), event.deltaY.toFloat()),
            // Key events carry these themselves, but pointer events only know what
            // the sender passes: without this, shift-click reaches the canvas as a
            // plain click.
            keyboardModifiers = event.composeModifiers,
            nativeEvent = event,
            button = button,
        )
    }

    private val NSEvent.composeModifiers: PointerKeyboardModifiers
        get() = PointerKeyboardModifiers(
            isAltPressed = modifierFlags and NSEventModifierFlagOption != 0UL,
            isShiftPressed = modifierFlags and NSEventModifierFlagShift != 0UL,
            isCtrlPressed = modifierFlags and NSEventModifierFlagControl != 0UL,
            isMetaPressed = modifierFlags and NSEventModifierFlagCommand != 0UL,
        )

    private fun NSEvent.toComposeEvent(): KeyEvent = KeyEvent(
        key = Key(keyCode.toLong()),
        type = when (type) {
            NSKeyDown -> KeyEventType.KeyDown
            NSKeyUp -> KeyEventType.KeyUp
            else -> KeyEventType.Unknown
        },
        codePoint = characters?.firstOrNull()?.code ?: 0,
        isAltPressed = modifierFlags and NSEventModifierFlagOption != 0UL,
        isShiftPressed = modifierFlags and NSEventModifierFlagShift != 0UL,
        isCtrlPressed = modifierFlags and NSEventModifierFlagControl != 0UL,
        isMetaPressed = modifierFlags and NSEventModifierFlagCommand != 0UL,
        nativeEvent = this,
    )
}

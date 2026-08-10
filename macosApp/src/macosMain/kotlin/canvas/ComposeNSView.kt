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
import platform.AppKit.NSViewHeightSizable
import platform.AppKit.NSViewWidthSizable
import platform.AppKit.NSWindow
import platform.Foundation.NSMakeRect
import platform.Foundation.NSRect

/**
 * Hosting wrapper for [ComposeNSView]. Skiko's MacOsMetalRedrawer.syncBounds() sets
 * the metal layer's frame to the view's frame verbatim, origin included, which is
 * only correct when the view sits at (0,0) in its superview (true for a window
 * contentView, false inside a SwiftUI layout). Keeping the compose view as a
 * full-size subview of this container pins its origin to (0,0), so the layer
 * geometry stays in sync no matter where the host places us.
 */
class ComposeHostView(private val composeView: ComposeNSView) : NSView(composeView.frame) {
    init {
        composeView.setFrame(bounds)
        composeView.autoresizingMask = NSViewWidthSizable or NSViewHeightSizable
        addSubview(composeView)
    }
}

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
    private var trackingArea: NSTrackingArea? = null

    init {
        skiaLayer.renderDelegate = object : SkikoRenderDelegate {
            override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
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
        val window = this.window
        if (window != null && !isAttached) {
            skiaLayer.attachTo(this)
            isAttached = true
        }
        if (window != null) {
            scene.density = Density(window.backingScaleFactor.toFloat())
            window.makeFirstResponder(this)
        }
    }

    override fun viewDidChangeBackingProperties() {
        super.viewDidChangeBackingProperties()
        window?.let { scene.density = Density(it.backingScaleFactor.toFloat()) }
    }

    override fun layout() {
        super.layout()
        val f = frame.useContents { "(${origin.x}, ${origin.y}) ${size.width}x${size.height}" }
        val l = layer?.frame?.useContents { "(${origin.x}, ${origin.y}) ${size.width}x${size.height}" }
        println("[ComposeNSView] layout frame=$f metalLayer=$l")
        skiaLayer.needRender()
    }

    override fun viewWillMoveToWindow(newWindow: NSWindow?) {
        super.viewWillMoveToWindow(newWindow)
        updateTrackingAreas()
    }

    override fun updateTrackingAreas() {
        trackingArea?.let { removeTrackingArea(it) }
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
    override fun rightMouseDown(event: NSEvent) = onMouseEvent(event, PointerEventType.Press, PointerButton.Secondary)
    override fun rightMouseUp(event: NSEvent) = onMouseEvent(event, PointerEventType.Release, PointerButton.Secondary)
    override fun mouseMoved(event: NSEvent) = onMouseEvent(event, PointerEventType.Move)
    override fun mouseDragged(event: NSEvent) = onMouseEvent(event, PointerEventType.Move)
    override fun scrollWheel(event: NSEvent) = onMouseEvent(event, PointerEventType.Scroll)

    override fun keyDown(event: NSEvent) {
        val consumed = scene.sendKeyEvent(event.toComposeEvent())
        if (!consumed) super.keyDown(event)
    }

    override fun keyUp(event: NSEvent) {
        scene.sendKeyEvent(event.toComposeEvent())
    }

    fun dispose() {
        if (isAttached) skiaLayer.detach()
        scene.close()
    }

    private fun onMouseEvent(event: NSEvent, eventType: PointerEventType, button: PointerButton? = null) {
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
            nativeEvent = event,
            button = button,
        )
    }

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

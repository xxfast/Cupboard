// Adapted from androidx.compose.ui.window.ComposeWindow (compose-multiplatform-core,
// Apache-2.0). That class owns its own NSWindow; this variant is an embeddable NSView
// so a SwiftUI app can host Compose content via NSViewRepresentable. Text input goes
// further than the upstream macOS support: this view is a real NSTextInputClient, so dead
// keys and input methods reach compose's text fields.
@file:OptIn(ExperimentalForeignApi::class, InternalComposeUiApi::class)

package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
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
import platform.AppKit.NSTextInputClientProtocol
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
import platform.CoreGraphics.CGPoint
import platform.Foundation.NSAttributedString
import platform.Foundation.NSMakeRange
import platform.Foundation.NSMakeRect
import platform.Foundation.NSNotFound
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSRange
import platform.Foundation.NSRect
import platform.Foundation.create

/**
 * The compose half of the text input bridge. Compose's internal MacosTextInputService is a
 * stub that drops every edit command, which is why the canvas could only ever type whatever
 * a raw key event carried. This one keeps the callback, so [ComposeNSView] can feed compose
 * the text AppKit's input method produced: dead keys (option-e then e), CJK candidates,
 * anything else an input source composes before it commits.
 *
 * It also mirrors what compose knows about the field, because NSTextInputClient asks: the
 * current value answers the range and substring queries, and the focused rect places the
 * candidate window.
 */
private class CanvasTextInputService : PlatformTextInputService {
    /** True between [startInput] and [stopInput], i.e. while a text field holds focus. */
    var isActive = false
        private set

    var value: TextFieldValue = TextFieldValue()
        private set

    /** Where the focused field sits, in compose pixels with a top-left origin. */
    var focusedRect: Rect? = null
        private set

    /** Set by the view, so a composition in flight is dropped when the field loses focus. */
    var onStopInput: () -> Unit = {}

    private var onEditCommand: ((List<EditCommand>) -> Unit)? = null

    override fun startInput(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit,
    ) {
        this.value = value
        this.onEditCommand = onEditCommand
        isActive = true
    }

    override fun stopInput() {
        isActive = false
        onEditCommand = null
        focusedRect = null
        value = TextFieldValue()
        onStopInput()
    }

    override fun showSoftwareKeyboard() {}
    override fun hideSoftwareKeyboard() {}

    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) { value = newValue }

    override fun notifyFocusedRect(rect: Rect) { focusedRect = rect }

    fun send(vararg commands: EditCommand) {
        onEditCommand?.invoke(commands.toList())
    }
}

/** AppKit ranges are unsigned and use NSNotFound for "there isn't one". */
private fun TextRange?.toNSRange(): CValue<NSRange> =
    if (this == null) NSMakeRange(NSNotFound.toULong(), 0uL)
    else NSMakeRange(min.toULong(), (max - min).toULong())

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
) : NSView(frame), NSTextInputClientProtocol {
    private val windowInfo = MutableWindowInfo()
    private val textInputService = CanvasTextInputService()

    // Set by the NSTextInputClient callbacks below, read by keyDown to decide whether the
    // input method already turned this keystroke into text.
    private var didHandleKeyAsText = false

    // The command chord [performKeyEquivalent] already offered to the scene and
    // nobody took, held only until the keyDown it comes back as arrives.
    private var keyEquivalentInFlight: NSEvent? = null

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
        // A composition left hanging when the field loses focus would keep painting an
        // underline AppKit no longer knows about, so throw it away with the field.
        textInputService.onStopInput = { inputContext?.discardMarkedText() }
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
        // Already offered to the scene as a key equivalent and turned down, so
        // this is the same keystroke coming round a second time, not a new one.
        // Dropped rather than passed to super, which beeps at a chord no
        // responder wants; the field branch below never beeps either.
        if (keyEquivalentInFlight === event) {
            keyEquivalentInFlight = null
            return
        }
        if (!textInputService.isActive) {
            val consumed = scene.sendKeyEvent(event.toComposeEvent())
            if (!consumed) super.keyDown(event)
            return
        }
        // While a field has focus the input method gets first look: it is what turns a dead
        // key or a CJK candidate into insertText:/setMarkedText: calls back on us. Whatever
        // it did not turn into text (arrows, backspace, enter, tab, escape, shortcuts the
        // menu left alone) goes on to compose, which handles those itself. Forwarding only
        // in that case is what stops a typed character landing twice.
        didHandleKeyAsText = false
        interpretKeyEvents(listOf(event))
        if (!didHandleKeyAsText) scene.sendKeyEvent(event.toComposeEvent())
    }

    override fun keyUp(event: NSEvent) {
        if (isDisposed) return
        scene.sendKeyEvent(event.toComposeEvent())
    }

    /**
     * Command chords, offered to the field before the menu bar gets them.
     *
     * AppKit walks the view hierarchy with this ahead of matching the main
     * menu's key equivalents, which is the only place we can get in front of
     * them. Mid-edit the app's Cut/Copy/Paste items are greyed out on purpose,
     * the element clipboard having no business firing while a caret is in
     * something, but a disabled item still owns Cmd+X/C/V and swallows the
     * chord rather than letting it through. So while a field has focus,
     * compose is asked first: it takes the editing chords and leaves the rest
     * (Cmd+S, Cmd+B) unclaimed for the menu, exactly as before.
     *
     * Only while a field has focus. With no caret anywhere nothing in the
     * scene would consume these anyway, and not asking keeps the menu's
     * behaviour untouched rather than merely unchanged-in-practice.
     */
    override fun performKeyEquivalent(event: NSEvent): Boolean {
        if (isDisposed) return false
        if (event.type != NSKeyDown) return false
        if (!textInputService.isActive) return false

        // Returning false sends the chord on to the menu, and if the menu has
        // nothing for it either AppKit delivers it again as an ordinary
        // keyDown. Remembering the event is how that second delivery is told
        // apart from a first one; AppKit hands both legs the same NSEvent.
        val consumed: Boolean = scene.sendKeyEvent(event.toComposeEvent())
        keyEquivalentInFlight = if (consumed) null else event
        return consumed
    }

    // NSTextInputClient. AppKit hands us the text an input source produced; we turn it into
    // the edit commands compose's text field speaks, and answer the queries the candidate
    // window needs to place itself.

    /** Committed text: replaces the composing region, if there is one. */
    override fun insertText(string: Any, replacementRange: CValue<NSRange>) {
        if (isDisposed) return
        val text: String = string.asPlainText() ?: return
        didHandleKeyAsText = true
        textInputService.send(CommitTextCommand(text, 1))
    }

    /** Text still being composed, drawn underlined until it commits. */
    override fun setMarkedText(
        string: Any,
        selectedRange: CValue<NSRange>,
        replacementRange: CValue<NSRange>,
    ) {
        if (isDisposed) return
        val text: String = string.asPlainText() ?: return
        didHandleKeyAsText = true
        // Empty marked text is the input method cancelling: empty the composing region,
        // then let go of it, or compose keeps an empty composition around forever.
        if (text.isEmpty()) textInputService.send(SetComposingTextCommand("", 1), FinishComposingTextCommand())
        else textInputService.send(SetComposingTextCommand(text, 1))
    }

    override fun unmarkText() {
        if (isDisposed) return
        textInputService.send(FinishComposingTextCommand())
    }

    override fun hasMarkedText(): Boolean = textInputService.value.composition != null

    override fun markedRange(): CValue<NSRange> = textInputService.value.composition.toNSRange()

    override fun selectedRange(): CValue<NSRange> = textInputService.value.selection.toNSRange()

    override fun attributedSubstringForProposedRange(
        range: CValue<NSRange>,
        actualRange: CPointer<NSRange>?,
    ): NSAttributedString? {
        val text: String = textInputService.value.text
        val (location, length) = range.useContents { location.toLong() to length.toLong() }
        val start = location.coerceIn(0L, text.length.toLong()).toInt()
        val end = (location + length).coerceIn(start.toLong(), text.length.toLong()).toInt()
        actualRange?.pointed?.let { actual ->
            actual.location = start.toULong()
            actual.length = (end - start).toULong()
        }
        if (start == end) return null
        return NSAttributedString.create(string = text.substring(start, end))
    }

    /** We draw the composition ourselves, so no attribute of AppKit's is honoured. */
    override fun validAttributesForMarkedText(): List<*> = emptyList<Any>()

    /** Where the candidate window hangs itself: screen coords, bottom-left origin. */
    override fun firstRectForCharacterRange(
        range: CValue<NSRange>,
        actualRange: CPointer<NSRange>?,
    ): CValue<NSRect> {
        val focused: Rect? = textInputService.focusedRect
        val height = bounds.useContents { size.height }
        val density = scene.density.density
        // Compose measures in pixels from the top-left; AppKit wants points from the bottom
        // -left. No focused rect yet (nothing has typed) means anywhere in the view will do.
        val local: CValue<NSRect> = if (focused == null) bounds else NSMakeRect(
            x = (focused.left / density).toDouble(),
            y = height - (focused.bottom / density).toDouble(),
            w = (focused.width / density).toDouble(),
            h = (focused.height / density).toDouble(),
        )
        val inWindow: CValue<NSRect> = convertRect(local, toView = null)
        return window?.convertRectToScreen(inWindow) ?: inWindow
    }

    /** Only used for mouse-driven candidate selection, which we do not offer. */
    override fun characterIndexForPoint(point: CValue<CGPoint>): ULong = NSNotFound.toULong()

    /**
     * Deliberately empty, and deliberately no super call: NSResponder's default beeps at
     * every selector it cannot service. Leaving [didHandleKeyAsText] false is the whole
     * point, it sends the raw event on to compose, which owns the editing and navigation
     * keys the input method just told us it does not want.
     */
    override fun doCommandBySelector(selector: COpaquePointer?) = Unit

    /** AppKit passes NSString or NSAttributedString; the former bridges to String already. */
    private fun Any.asPlainText(): String? = when (this) {
        is NSAttributedString -> string
        is String -> this
        else -> null
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
